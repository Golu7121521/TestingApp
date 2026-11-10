package com.playvid.playback

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.playvid.download.DownloadKind
import com.playvid.download.DownloadJob
import com.playvid.download.DownloadRepository
import com.playvid.download.DownloadService
import com.playvid.download.DownloadStatus
import com.playvid.domain.model.PlaybackRequest
import com.playvid.domain.repository.HistoryRepository
import com.playvid.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * App-wide playback session. It owns the single ExoPlayer, so playback keeps going when
 * the player screen is left (the mini player then controls it) and the UI never touches
 * player internals. All calls must come from the main thread.
 */
class PlaybackController(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val history: HistoryRepository,
    private val settingsRepo: SettingsRepository,
    private val appScope: CoroutineScope
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    private val youtube = YouTubeResolver(httpClient)

    private var exoPlayer: ExoPlayer? = null
    private var dataSourceFactory: DataSource.Factory? = null

    /** The player instance (created on first use). */
    val player: ExoPlayer get() = ensurePlayer()

    private val _current = MutableStateFlow<PlaybackRequest?>(null)
    /** The item of the active session, or null when nothing is loaded (mini player hidden). */
    val current: StateFlow<PlaybackRequest?> = _current.asStateFlow()

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(PlayerProgress())
    val progress: StateFlow<PlayerProgress> = _progress.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** One-shot messages for a Snackbar, e.g. "Video added to history". */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var hasStarted = false
    private var tickerJob: Job? = null

    // ---- Regular (non-YouTube) HLS/MP4 quality & track selection --------------------------
    private var manualQuality: Pair<Int, Int>? = null
    private var qualityApplied = false
    private var preferredQuality = 0
    private var dataSaverSetting = false

    // ---- YouTube: every quality/track is its own file; switching reopens the merged pair ---
    private var yt: YouTubeMedia? = null
    private var ytVideoIndex = 0
    private var ytAudioIndex = 0

    /** True only for a YouTube live broadcast: no per-quality files exist, just one HLS manifest
     *  with its own adaptive renditions, so it needs ExoPlayer's regular track-group handling. */
    private val isYtLive: Boolean get() = yt?.liveHlsUrl != null

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            syncState()
            if (playbackState == Player.STATE_ENDED) saveProgress()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) = syncState()

        override fun onTracksChanged(tracks: Tracks) {
            if (yt == null || isYtLive) rebuildTracks(tracks)
        }

        override fun onPlayerError(error: PlaybackException) {
            val exo = exoPlayer ?: return
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                exo.seekToDefaultPosition()
                exo.prepare()
                return
            }
            if (isDebuggable) Log.w(TAG, "Playback error ${error.errorCodeName}", error)
            val req = _current.value
            val detail = if (req != null && !req.isLocal && YouTubeResolver.isYouTubeUrl(req.source)) {
                val http = generateSequence<Throwable>(error) { it.cause }
                    .filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
                val mime = (error as? ExoPlaybackException)?.rendererFormat?.sampleMimeType.orEmpty()
                val mode = if (yt?.liveHlsUrl != null) "live" else if (yt?.videos?.isNotEmpty() == true) "direct" else "muxed"
                "\n[${error.errorCodeName}${http?.let { " HTTP ${it.responseCode}" }.orEmpty()} | $mode | $mime]"
            } else ""
            _state.update {
                it.copy(error = hintFor(error) + detail, isLoading = false, isBuffering = false, isPlaying = false)
            }
        }
    }

    private data class ParsedSource(
        val url: String,
        val clearKeyId: String? = null,
        val clearKeyValue: String? = null,
        val headers: Map<String, String> = emptyMap()
    )

    private val HEADER_PARAM_NAMES = mapOf(
        "cookie" to "Cookie",
        "referer" to "Referer",
        "origin" to "Origin",
        "user-agent" to "User-Agent",
        "useragent" to "User-Agent"
    )

    private fun parseSource(raw: String): ParsedSource {
        val barIndex = raw.indexOf('|')
        if (barIndex < 0) return ParsedSource(raw)
        val url = raw.substring(0, barIndex)
        var keyId: String? = null
        var keyValue: String? = null
        val headers = mutableMapOf<String, String>()
        raw.substring(barIndex + 1).split('&').forEach { pair ->
            val eq = pair.indexOf('=')
            if (eq < 0) return@forEach
            val k = pair.substring(0, eq)
            val v = pair.substring(eq + 1)
            when {
                k == "drmLicense" && v.contains(':') -> {
                    val sep = v.indexOf(':')
                    keyId = v.substring(0, sep)
                    keyValue = v.substring(sep + 1)
                }
                k == "drmScheme" -> { }
                HEADER_PARAM_NAMES.containsKey(k.lowercase()) -> {
                    headers[HEADER_PARAM_NAMES.getValue(k.lowercase())] = v
                }
            }
        }
        return ParsedSource(url, keyId, keyValue, headers)
    }

    private fun clearKeyDrmSessionManager(keyIdHex: String, keyHex: String): DrmSessionManager {
        fun hexToBase64Url(hex: String): String {
            val clean = hex.trim()
            val bytes = ByteArray(clean.length / 2) { i ->
                ((Character.digit(clean[i * 2], 16) shl 4) + Character.digit(clean[i * 2 + 1], 16)).toByte()
            }
            return android.util.Base64.encodeToString(
                bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
            )
        }
        val json = "{\"keys\":[{\"kty\":\"oct\",\"k\":\"${hexToBase64Url(keyHex)}\",\"kid\":\"${hexToBase64Url(keyIdHex)}\"}],\"type\":\"temporary\"}"
        val callback = LocalMediaDrmCallback(json.toByteArray(Charsets.UTF_8))
        return DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
            .build(callback)
    }

    private fun ensurePlayer(): ExoPlayer {
        exoPlayer?.let { return it }

        // Standard Web Browser client configuration to bypass 403 Forbidden on Tokenized / M3U8 links
        val streamingHttpClient = httpClient.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                    .header("User-Agent", CHROME_USER_AGENT)
                    .header("Accept", "*/*")
                
                // Add Origin/Referer automatically if missing for protected streams
                if (original.header("Origin") == null) {
                    requestBuilder.header("Origin", "${original.url.scheme}://${original.url.host}")
                }
                if (original.header("Referer") == null) {
                    requestBuilder.header("Referer", "${original.url.scheme}://${original.url.host}/")
                }
                chain.proceed(requestBuilder.build())
            }
            .build()

        val okHttp = OkHttpDataSource.Factory(streamingHttpClient)
            .setUserAgent(CHROME_USER_AGENT)

        val factory = DefaultDataSource.Factory(context, okHttp)
        dataSourceFactory = factory

        val mediaSourceFactory = DefaultMediaSourceFactory(factory)

        val created = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        created.addListener(listener)
        exoPlayer = created
        return created
    }

    // ---- Session -----------------------------------------------------------------------

    fun open(req: PlaybackRequest) {
        if (_current.value?.source == req.source && exoPlayer != null) return
        stopCurrent(save = true)

        _current.value = req
        hasStarted = false
        manualQuality = null
        qualityApplied = false
        yt = null
        ytVideoIndex = 0
        ytAudioIndex = 0
        _state.value = PlayerUiState(title = req.title, isLocal = req.isLocal)
        _progress.value = PlayerProgress()
        val exo = ensurePlayer()
        exo.volume = 1f

        scope.launch {
            val prefs = settingsRepo.settings.first()
            preferredQuality = prefs.preferredQuality
            dataSaverSetting = prefs.dataSaver

            val saved = history.get(req.source)
            val canResume = prefs.resumePlayback && saved != null &&
                saved.positionMs > 3_000 &&
                (saved.durationMs <= 0 || saved.positionMs < saved.durationMs - 5_000)
            val resumeAt = if (canResume) saved!!.positionMs else 0L
            if (_current.value?.source != req.source) return@launch

            var title = req.title
            var media: YouTubeMedia? = null
            if (!req.isLocal && YouTubeResolver.isYouTubeUrl(req.source)) {
                media = try {
                    withContext(Dispatchers.IO) { youtube.resolve(req.source) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (isDebuggable) Log.w(TAG, "YouTube resolve failed", e)
                    if (_current.value?.source == req.source) {
                        _state.update { it.copy(error = youtubeHint(e), isLoading = false, isBuffering = false) }
                    }
                    return@launch
                }
                if (_current.value?.source != req.source) return@launch
                title = media.title.ifBlank { req.title }
                yt = media
                ytVideoIndex = pickQualityIndex(media.videos.map { it.height }, prefs.dataSaver, prefs.preferredQuality)
                _current.value = req.copy(title = title)
                _state.update {
                    it.copy(
                        title = title,
                        qualities = media.videos.mapIndexed { i, v -> TrackOption(YT_MARKER, i, v.label, selected = i == ytVideoIndex, height = v.height, bitrate = v.bitrate) },
                        isAutoQuality = prefs.preferredQuality == 0 && !prefs.dataSaver,
                        audioTracks = media.audios.mapIndexed { i, a -> TrackOption(YT_MARKER, i, a.label, selected = i == 0) },
                        textTracks = emptyList(),
                        textEnabled = false,
                        downloadOptions = downloadOptionsFor(media),
                        audioDownloadOptions = audioDownloadOptionsFor(media)
                    )
                }
            }

            val parsedSource = if (media == null) parseSource(req.source) else null
            val sourceUrl = parsedSource?.url ?: req.source
            
            // Check for HLS extensions or parameters (.m3u8, /playlist, etc.)
            val isHls = media?.liveHlsUrl != null ||
                (!req.isLocal && (sourceUrl.lowercase().contains(".m3u8") || sourceUrl.lowercase().contains("playlist")))
            val isDash = parsedSource != null && sourceUrl.lowercase().contains(".mpd")

            if (media == null) {
                _state.update {
                    it.copy(
                        downloadOptions = if (req.isLocal || isHls || isDash) emptyList() else listOf(DownloadOption("Original", parsedSource!!.url)),
                        audioDownloadOptions = emptyList()
                    )
                }
                if (isHls && !req.isLocal) {
                    val sourceKey = req.source
                    val hlsUrl = parsedSource!!.url
                    appScope.launch {
                        val plan = runCatching { HlsPlaylistUtil.resolvePlan(httpClient, hlsUrl) }.getOrNull()
                        if (plan?.isVod == true && _current.value?.source == sourceKey) {
                            _state.update { it.copy(downloadOptions = listOf(DownloadOption("Original", hlsUrl))) }
                        }
                    }
                }
            }

            val item = MediaItem.Builder()
                .setUri(media?.liveHlsUrl ?: media?.muxedUrl ?: sourceUrl)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                .apply {
                    if (isHls) setMimeType(MimeTypes.APPLICATION_M3U8)
                    if (isDash) setMimeType(MimeTypes.APPLICATION_MPD)
                }
                .build()

            _state.update { it.copy(resumePrompt = if (canResume && !isHls && !isDash) resumeAt else null) }

            if (media != null && media.videos.isNotEmpty() && media.audios.isNotEmpty()) {
                setYtSource(media.videos[ytVideoIndex].url, media.audios[0].url, resumeAt)
            } else {
                val dsf: DataSource.Factory = if (parsedSource != null && parsedSource.headers.isNotEmpty()) {
                    OkHttpDataSource.Factory(httpClient.newBuilder().followRedirects(true).build())
                        .setUserAgent(parsedSource.headers["User-Agent"] ?: CHROME_USER_AGENT)
                        .setDefaultRequestProperties(parsedSource.headers)
                } else {
                    dataSourceFactory ?: DefaultDataSource.Factory(context)
                }

                val keyId = parsedSource?.clearKeyId
                val keyValue = parsedSource?.clearKeyValue
                val source = when {
                    isDash -> {
                        val factory = DashMediaSource.Factory(dsf)
                        if (keyId != null && keyValue != null) {
                            val drm = clearKeyDrmSessionManager(keyId, keyValue)
                            factory.setDrmSessionManagerProvider(DrmSessionManagerProvider { drm })
                        }
                        factory.createMediaSource(item)
                    }
                    isHls -> {
                        // Allow chunkless preparation to avoid infinite buffer on dynamic playlists
                        HlsMediaSource.Factory(dsf)
                            .setAllowChunklessPreparation(true)
                            .createMediaSource(item)
                    }
                    else -> DefaultMediaSourceFactory(dsf).createMediaSource(item)
                }
                exo.setMediaSource(source, resumeAt)
            }
            exo.prepare()
            exo.playWhenReady = !canResume
            startTicker()

            if (saved == null) _messages.tryEmit("Video added to history")
            history.saveProgress(req.source, title, req.isLocal, resumeAt, saved?.durationMs ?: 0L)
        }
    }

    private fun setYtSource(videoUrl: String, audioUrl: String, startPositionMs: Long) {
        val exo = exoPlayer ?: return
        val factory = dataSourceFactory ?: return
        val f = ProgressiveMediaSource.Factory(factory)
        exo.setMediaSource(
            MergingMediaSource(
                f.createMediaSource(MediaItem.fromUri(videoUrl)),
                f.createMediaSource(MediaItem.fromUri(audioUrl))
            ),
            startPositionMs
        )
    }

    private fun downloadOptionsFor(media: YouTubeMedia): List<DownloadOption> {
        if (media.liveHlsUrl != null) return emptyList()
        if (media.videos.isNotEmpty() && media.audios.isNotEmpty()) {
            val audio = (media.audios.firstOrNull { it.isM4a } ?: media.audios.first()).url
            return media.videos.map { DownloadOption(it.label, it.url, audio) }
        }
        media.muxedUrl?.let { return listOf(DownloadOption("Original", it)) }
        return emptyList()
    }

    private fun audioDownloadOptionsFor(media: YouTubeMedia): List<AudioDownloadOption> {
        if (media.liveHlsUrl != null) return emptyList()
        return media.audios.map { AudioDownloadOption(it.label, it.url) }
    }

    fun downloadVideo(option: DownloadOption) {
        val title = _current.value?.title?.ifBlank { "video" } ?: "video"
        val id = DownloadRepository.nextId()
        DownloadRepository.upsert(DownloadJob(id, title, DownloadKind.VIDEO, DownloadStatus.QUEUED, 0))
        DownloadService.enqueue(context, id, title, DownloadKind.VIDEO, option.videoUrl, option.audioUrl)
    }

    fun downloadAudio(option: AudioDownloadOption) {
        val title = _current.value?.title?.ifBlank { "audio" } ?: "audio"
        val id = DownloadRepository.nextId()
        DownloadRepository.upsert(DownloadJob(id, title, DownloadKind.AUDIO, DownloadStatus.QUEUED, 0))
        DownloadService.enqueue(context, id, title, DownloadKind.AUDIO, option.url, null)
    }

    fun fail() {
        stopCurrent(save = false)
        _current.value = null
        _state.value = PlayerUiState(isLoading = false, error = HINT_GENERIC)
    }

    fun onLeavePlayer() {
        val s = _state.value
        val ended = exoPlayer?.playbackState == Player.STATE_ENDED
        if (_current.value == null) return
        if (s.error != null || s.resumePrompt != null || ended) close() else saveProgress()
    }

    fun close() {
        stopCurrent(save = true)
        _current.value = null
        _state.value = PlayerUiState()
        _progress.value = PlayerProgress()
    }

    fun release() {
        stopCurrent(save = true)
        exoPlayer?.removeListener(listener)
        exoPlayer?.release()
        exoPlayer = null
        _current.value = null
    }

    private fun stopCurrent(save: Boolean) {
        tickerJob?.cancel()
        tickerJob = null
        if (save) saveProgress()
        exoPlayer?.let {
            it.stop()
            it.clearMediaItems()
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            var sinceSave = 0
            while (isActive) {
                delay(500)
                syncState()
                sinceSave += 500
                if (sinceSave >= 5_000) {
                    saveProgress()
                    sinceSave = 0
                }
            }
        }
    }

    private fun syncState() {
        val exo = exoPlayer ?: return
        if (_current.value == null) return
        val duration = exo.duration.let { if (it == C.TIME_UNSET) 0L else it }
        val playbackState = exo.playbackState
        if (playbackState == Player.STATE_READY) hasStarted = true

        _progress.value = PlayerProgress(
            positionMs = exo.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            isLive = exo.isCurrentMediaItemLive,
            liveOffsetMs = if (exo.isCurrentMediaItemLive) exo.currentLiveOffset.coerceAtLeast(0L) else 0L
        )
        _state.update {
            it.copy(
                isPlaying = exo.isPlaying,
                isLoading = !hasStarted && it.error == null,
                isBuffering = hasStarted && playbackState == Player.STATE_BUFFERING
            )
        }
    }

    // ---- Playback controls -------------------------------------------------------------

    fun togglePlay() {
        val exo = exoPlayer ?: return
        if (exo.isPlaying) {
            exo.pause()
        } else {
            if (exo.playbackState == Player.STATE_ENDED) exo.seekTo(0)
            exo.play()
        }
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun seekTo(positionMs: Long) {
        val exo = exoPlayer ?: return
        val duration = _progress.value.durationMs
        exo.seekTo(if (duration > 0) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L))
        syncState()
    }

    fun seekBy(deltaMs: Long) {
        val exo = exoPlayer ?: return
        seekTo(exo.currentPosition + deltaMs)
    }

    fun seekToLive() {
        val exo = exoPlayer ?: return
        exo.seekToDefaultPosition()
        exo.playWhenReady = true
        syncState()
    }

    fun setSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
        _state.update { it.copy(speed = speed) }
    }

    fun toggleMute() {
        val muted = !_state.value.isMuted
        exoPlayer?.volume = if (muted) 0f else 1f
        _state.update { it.copy(isMuted = muted) }
    }

    fun setResizeFill(fill: Boolean) = _state.update { it.copy(resizeFill = fill) }

    fun resume() {
        _state.update { it.copy(resumePrompt = null) }
        exoPlayer?.play()
    }

    fun startOver() {
        _state.update { it.copy(resumePrompt = null) }
        exoPlayer?.seekTo(0)
        exoPlayer?.play()
    }

    fun retry() {
        val exo = exoPlayer ?: return
        val req = _current.value ?: return
        if (!req.isLocal && YouTubeResolver.isYouTubeUrl(req.source)) {
            saveProgress()
            _current.value = null
            open(req)
            return
        }
        _state.update { it.copy(error = null, isLoading = !hasStarted) }
        exo.prepare()
        exo.play()
    }

    // ---- Track selection -----------------------------------------------------------------

    fun selectAutoQuality() {
        if (yt != null && !isYtLive) { selectQuality(0, auto = true); return }
        manualQuality = null
        updateParams { it.clearOverridesOfType(C.TRACK_TYPE_VIDEO) }
        exoPlayer?.let { rebuildTracks(it.currentTracks) }
    }

    fun selectQuality(option: TrackOption) {
        if (yt != null && !isYtLive) selectQuality(option.trackIndex, auto = false) else selectRegularQuality(option)
    }

    private fun selectQuality(index: Int, auto: Boolean) {
        val media = yt
        if (media != null) {
            val exo = exoPlayer ?: return
            val v = media.videos.getOrNull(index) ?: return
            val a = media.audios.getOrNull(ytAudioIndex) ?: media.audios.firstOrNull() ?: return
            ytVideoIndex = index
            val wasPlaying = exo.isPlaying
            setYtSource(v.url, a.url, exo.currentPosition)
            exo.prepare()
            exo.playWhenReady = wasPlaying
            _state.update {
                it.copy(qualities = it.qualities.mapIndexed { i, q -> q.copy(selected = i == index) }, isAutoQuality = auto)
            }
            return
        }
    }

    private fun selectRegularQuality(option: TrackOption) {
        val exo = exoPlayer ?: return
        val group = exo.currentTracks.groups.getOrNull(option.groupIndex) ?: return
        manualQuality = option.groupIndex to option.trackIndex
        updateParams { it.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex)) }
    }

    fun selectAudio(option: TrackOption) {
        val media = yt
        if (media != null && !isYtLive) {
            val exo = exoPlayer ?: return
            val a = media.audios.getOrNull(option.trackIndex) ?: return
            val v = media.videos.getOrNull(ytVideoIndex) ?: media.videos.firstOrNull() ?: return
            ytAudioIndex = option.trackIndex
            val wasPlaying = exo.isPlaying
            setYtSource(v.url, a.url, exo.currentPosition)
            exo.prepare()
            exo.playWhenReady = wasPlaying
            _state.update {
                it.copy(audioTracks = it.audioTracks.mapIndexed { i, t -> t.copy(selected = i == option.trackIndex) })
            }
            return
        }
        val exo = exoPlayer ?: return
        val group = exo.currentTracks.groups.getOrNull(option.groupIndex) ?: return
        updateParams { it.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex)) }
    }

    fun selectText(option: TrackOption?) {
        if (yt != null && !isYtLive) return
        if (option == null) {
            updateParams { it.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true) }
            return
        }
        val exo = exoPlayer ?: return
        val group = exo.currentTracks.groups.getOrNull(option.groupIndex) ?: return
        updateParams {
            it.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
        }
    }

    private fun updateParams(block: (TrackSelectionParameters.Builder) -> TrackSelectionParameters.Builder) {
        val exo = exoPlayer ?: return
        exo.trackSelectionParameters = block(exo.trackSelectionParameters.buildUpon()).build()
    }

    private fun rebuildTracks(tracks: Tracks) {
        val videos = mutableListOf<TrackOption>()
        val audio = mutableListOf<TrackOption>()
        val text = mutableListOf<TrackOption>()

        tracks.groups.forEachIndexed { gi, group ->
            for (ti in 0 until group.length) {
                if (!group.isTrackSupported(ti)) continue
                val format = group.getTrackFormat(ti)
                when (group.type) {
                    C.TRACK_TYPE_VIDEO -> if (format.height > 0) {
                        videos += TrackOption(
                            gi, ti, "${format.height}p",
                            selected = manualQuality == (gi to ti),
                            height = format.height, bitrate = format.bitrate
                        )
                    }
                    C.TRACK_TYPE_AUDIO -> audio += TrackOption(gi, ti, trackLabel(format, audio.size), group.isTrackSelected(ti))
                    C.TRACK_TYPE_TEXT -> text += TrackOption(gi, ti, trackLabel(format, text.size), group.isTrackSelected(ti))
                }
            }
        }

        val qualities = videos.groupBy { it.height }
            .map { (_, list) -> list.maxByOrNull { it.bitrate }!! }
            .sortedByDescending { it.height }

        if (!qualityApplied && qualities.isNotEmpty()) {
            qualityApplied = true
            val idx = pickQualityIndex(qualities.map { it.height }, dataSaverSetting, preferredQuality)
            if (idx != 0) {
                selectQuality(qualities[idx])
                return
            }
        }

        _state.update {
            it.copy(
                qualities = qualities,
                isAutoQuality = manualQuality == null,
                audioTracks = audio,
                textTracks = text,
                textEnabled = text.any { t -> t.selected }
            )
        }
    }

    private fun trackLabel(format: Format, index: Int): String {
        format.label?.takeIf { it.isNotBlank() }?.let { return it }
        val lang = format.language
        if (!lang.isNullOrBlank() && lang != C.LANGUAGE_UNDETERMINED) {
            Locale.forLanguageTag(lang).displayLanguage.takeIf { it.isNotBlank() }?.let { return it }
        }
        return "Track ${index + 1}"
    }

    // ---- Persistence & errors ----------------------------------------------------------

    fun saveProgress() {
        val req = _current.value ?: return
        val exo = exoPlayer ?: return
        if (!hasStarted) return
        val position = if (exo.playbackState == Player.STATE_ENDED) 0L else exo.currentPosition
        val duration = exo.duration.let { if (it == C.TIME_UNSET) 0L else it }
        appScope.launch {
            history.saveProgress(req.source, req.title, req.isLocal, position, duration)
        }
    }

    private fun youtubeHint(e: Exception): String = when (e) {
        is AgeRestrictedContentException -> "This video is age-restricted and can't be played."
        is ContentNotAvailableException -> "This YouTube video isn't available."
        is ReCaptchaException -> "YouTube asked for a captcha. Try again in a while."
        is IOException -> HINT_GENERIC
        else -> "Couldn't load this YouTube video. YouTube may have changed; try again later."
    }

    private fun hintFor(e: PlaybackException): String = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
            "The file can't be opened. It may have been moved or deleted."

        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
            "This video format isn't supported on your device."

        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
            "The video looks damaged or isn't a valid video."

        in 6000..6999 -> "This video is protected and can't be played."

        else -> HINT_GENERIC
    }

    private fun pickQualityIndex(heightsDesc: List<Int>, dataSaver: Boolean, preferred: Int): Int {
        if (heightsDesc.isEmpty()) return 0
        if (dataSaver) return heightsDesc.size - 1
        if (preferred == 0) return 0
        return heightsDesc.indices.minByOrNull { i -> kotlin.math.abs(heightsDesc[i] - preferred) } ?: 0
    }

    private companion object {
        const val TAG = "PlayVid"
        const val HINT_GENERIC = "Check the URL or your internet connection."
        const val YT_MARKER = -1
        // Browser standard desktop User-Agent to bypass streaming server blocks
        const val CHROME_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    }
}
