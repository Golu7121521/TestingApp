package com.universal.videoplayer.player

import android.app.Application
import android.media.MediaDrm
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import com.universal.videoplayer.App
import com.universal.videoplayer.data.AppSettings
import com.universal.videoplayer.data.Scaling
import com.universal.videoplayer.data.Thumbnails
import com.universal.videoplayer.resolver.DrmScheme
import com.universal.videoplayer.resolver.PlaybackRequest
import com.universal.videoplayer.resolver.ResolveResult
import com.universal.videoplayer.resolver.Resolvers
import com.universal.videoplayer.utils.UrlValidator
import com.universal.videoplayer.utils.domainOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

data class QueueItem(val url: String, val title: String)

data class TrackOpt(val label: String, val groupIndex: Int, val trackIndex: Int, val selected: Boolean)

data class PlayerUi(
    val active: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val errorDetail: String? = null,
    val title: String = "",
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val isLive: Boolean = false,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val muted: Boolean = false,
    val speed: Float = 1f,
    val scaling: Scaling = Scaling.FIT,
    val qualityAuto: Boolean = true,
    val qualities: List<TrackOpt> = emptyList(),
    val audios: List<TrackOpt> = emptyList(),
    val texts: List<TrackOpt> = emptyList(),
    val queueSize: Int = 0,
    val queueIndex: Int = 0,
)

class PlayerViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo = (app as App).repo

    private val _ui = MutableStateFlow(PlayerUi())
    val ui: StateFlow<PlayerUi> = _ui.asStateFlow()

    /** True while the activity is in Picture-in-Picture mode. */
    var inPip by mutableStateOf(false)

    private var req: PlaybackRequest? = null
    private var queue: List<QueueItem> = emptyList()
    private var qIndex = 0
    private var tickJob: Job? = null
    private var playJob: Job? = null
    private var thumbTried = false
    private var thumbPath: String? = null
    private var preBoostSpeed: Float? = null
    private var settingsSnapshot = AppSettings()

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            _ui.update { it.copy(buffering = state == Player.STATE_BUFFERING, loading = false) }
            if (state == Player.STATE_READY) maybeThumbnail()
            if (state == Player.STATE_ENDED) {
                save(ended = true)
                playNext()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _ui.update { it.copy(playing = isPlaying) }
        }

        override fun onVideoSizeChanged(v: VideoSize) {
            _ui.update { it.copy(videoWidth = v.width, videoHeight = v.height) }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            publishPosition()
        }

        override fun onTracksChanged(tracks: Tracks) = refreshTracks(tracks)

        override fun onPlayerError(error: PlaybackException) = handleError(error)
    }

    private var _player: ExoPlayer? = null
    val player: ExoPlayer
        get() = _player ?: ExoPlayer.Builder(app).build().also {
            it.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            it.addListener(listener)
            _player = it
        }

    // ---------------------------------------------------------------- playback entry points

    fun play(r: PlaybackRequest, resume: Boolean = true, q: List<QueueItem> = emptyList(), index: Int = 0) {
        playJob?.cancel()
        req = r
        queue = q
        qIndex = index
        thumbTried = false
        thumbPath = null
        playJob = viewModelScope.launch {
            val s = repo.settings.flow.first()
            settingsSnapshot = s
            _ui.value = PlayerUi(
                active = true, loading = true, title = titleOf(r), speed = s.speed, scaling = s.scaling,
                muted = _player?.volume == 0f, queueSize = q.size, queueIndex = index,
            )
            if (UrlValidator.normalize(r.url) == null) {
                fail("This is not a valid link."); return@launch
            }
            checkDrm(r)?.let { fail(it); return@launch }
            when (val res = Resolvers.resolve(r)) {
                is ResolveResult.Unsupported -> fail(res.reason)
                is ResolveResult.Success -> start(r, res, s, resume)
            }
        }
    }

    private fun checkDrm(r: PlaybackRequest): String? {
        if (r.drmScheme == DrmScheme.NONE) return null
        if (UrlValidator.normalize(r.drmLicense, requireScheme = true) == null) {
            return "Enter a valid DRM license URL (starting with http:// or https://)."
        }
        val ok = runCatching { MediaDrm.isCryptoSchemeSupported(drmUuid(r.drmScheme)) }.getOrDefault(false)
        return if (ok) null else "This device does not support ${r.drmScheme.label} DRM."
    }

    private fun drmUuid(s: DrmScheme): UUID = when (s) {
        DrmScheme.WIDEVINE -> C.WIDEVINE_UUID
        DrmScheme.PLAYREADY -> C.PLAYREADY_UUID
        else -> C.CLEARKEY_UUID
    }

    private suspend fun start(r: PlaybackRequest, res: ResolveResult.Success, s: AppSettings, resume: Boolean) {
        val hist = repo.getHistory(r.url)
        thumbPath = hist?.thumbPath
        val startPos = if (
            resume && s.autoResume && hist != null && hist.positionMs > 5_000 &&
            (hist.durationMs == 0L || hist.positionMs < hist.durationMs - 5_000)
        ) hist.positionMs else 0L

        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setUserAgent(r.userAgent)
            .setDefaultRequestProperties(r.headers())
        val factory = DefaultMediaSourceFactory(app)
            .setDataSourceFactory(DefaultDataSource.Factory(app, http))
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(if (s.retryOnError) 6 else 1))

        val item = MediaItem.Builder()
            .setUri(Uri.parse(res.uri))
            .apply { res.mime?.let { setMimeType(it) } }
            .setMediaMetadata(MediaMetadata.Builder().setTitle(titleOf(r)).build())
            .apply {
                if (r.drmScheme != DrmScheme.NONE) {
                    setDrmConfiguration(
                        MediaItem.DrmConfiguration.Builder(drmUuid(r.drmScheme))
                            .setLicenseUri(r.drmLicense.trim())
                            .setMultiSession(true)
                            .build()
                    )
                }
            }
            .build()

        val p = player
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().clearOverrides().build()
        p.setMediaSource(factory.createMediaSource(item), startPos)
        p.setPlaybackSpeed(s.speed)
        p.prepare()
        p.playWhenReady = true
        startTicker()
        save()
    }

    private fun fail(msg: String, detail: String? = null) {
        _ui.update { it.copy(active = true, loading = false, buffering = false, error = msg, errorDetail = detail) }
    }

    private fun handleError(e: PlaybackException) {
        if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            player.seekToDefaultPosition(); player.prepare(); return
        }
        var http: HttpDataSource.InvalidResponseCodeException? = null
        var c: Throwable? = e.cause
        while (c != null) {
            if (c is HttpDataSource.InvalidResponseCodeException) { http = c; break }
            c = c.cause
        }
        val msg = when {
            http != null -> when (http.responseCode) {
                401, 403 -> "The server refused access (HTTP ${http.responseCode}). This source may need authorization or request headers (see the Advance tab)."
                404 -> "The video was not found (HTTP 404). The link may have expired."
                else -> "The server returned an error (HTTP ${http.responseCode})."
            }
            e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "Network error. Check your connection and try again."
            e.errorCode in 6000..6999 -> "DRM error. The license could not be obtained or is not supported on this device."
            e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                e.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ||
                e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                e.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
                "This link is not a supported video format or stream."
            e.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                e.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
                e.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
                "This device cannot decode this video format."
            else -> "Playback failed."
        }
        fail(msg, "${e.errorCodeName}: ${e.message}")
    }

    fun retry() {
        val r = req ?: return
        val pos = _player?.currentPosition ?: 0L
        _ui.update { it.copy(error = null, errorDetail = null, loading = true) }
        val p = _player
        if (p != null && p.playbackState != Player.STATE_IDLE && p.currentMediaItem != null) {
            p.prepare(); p.playWhenReady = true; return
        }
        play(r, resume = pos == 0L, q = queue, index = qIndex)
    }

    fun close() {
        save()
        tickJob?.cancel()
        playJob?.cancel()
        _player?.run { stop(); clearMediaItems() }
        _ui.value = PlayerUi()
        req = null
    }

    fun pause() { _player?.pause() }
    fun togglePlay() { _player?.let { if (it.isPlaying) it.pause() else it.play() } }

    fun seekBy(deltaMs: Long) {
        val p = _player ?: return
        val dur = if (p.duration == C.TIME_UNSET) Long.MAX_VALUE else p.duration
        p.seekTo((p.currentPosition + deltaMs).coerceIn(0, dur))
        publishPosition()
    }

    fun seekTo(ms: Long) { _player?.seekTo(ms); publishPosition() }
    fun goLive() { _player?.seekToDefaultPosition(); _player?.play() }

    fun setSpeed(v: Float) {
        _player?.setPlaybackSpeed(v)
        _ui.update { it.copy(speed = v) }
    }

    /** Press-and-hold 2x boost; restores the previous speed on release. */
    fun boost(on: Boolean) {
        val p = _player ?: return
        if (on) {
            preBoostSpeed = p.playbackParameters.speed
            p.setPlaybackSpeed(2f)
        } else {
            preBoostSpeed?.let { p.setPlaybackSpeed(it) }
            preBoostSpeed = null
        }
    }

    fun toggleMute() {
        val p = _player ?: return
        val mute = p.volume > 0f
        p.volume = if (mute) 0f else 1f
        _ui.update { it.copy(muted = mute) }
    }

    fun setScaling(s: Scaling) = _ui.update { it.copy(scaling = s) }

    fun skipNext() = playNext()
    fun skipPrev() {
        if (qIndex > 0) startQueue(qIndex - 1)
    }

    private fun playNext() {
        if (qIndex + 1 < queue.size) startQueue(qIndex + 1)
    }

    private fun startQueue(i: Int) {
        val n = queue[i]
        play(PlaybackRequest(url = n.url, title = n.title), resume = false, q = queue, index = i)
    }

    // ---------------------------------------------------------------- tracks

    fun selectQuality(opt: TrackOpt?) {
        val p = _player ?: return
        var b = p.trackSelectionParameters.buildUpon()
        b = if (opt == null) b.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        else b.setOverrideForType(TrackSelectionOverride(p.currentTracks.groups[opt.groupIndex].mediaTrackGroup, opt.trackIndex))
        p.trackSelectionParameters = b.build()
    }

    fun selectAudio(opt: TrackOpt) {
        val p = _player ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(p.currentTracks.groups[opt.groupIndex].mediaTrackGroup, opt.trackIndex))
            .build()
    }

    fun selectText(opt: TrackOpt?) {
        val p = _player ?: return
        var b = p.trackSelectionParameters.buildUpon()
        b = if (opt == null) b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        else b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(p.currentTracks.groups[opt.groupIndex].mediaTrackGroup, opt.trackIndex))
        p.trackSelectionParameters = b.build()
    }

    private fun langName(code: String?): String? {
        if (code.isNullOrBlank() || code == "und") return null
        val n = Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH)
        return n.ifBlank { code }
    }

    private fun refreshTracks(t: Tracks) {
        val p = _player ?: return
        val q = mutableListOf<TrackOpt>()
        val a = mutableListOf<TrackOpt>()
        val x = mutableListOf<TrackOpt>()
        val heights = mutableSetOf<Int>()
        val auto = p.trackSelectionParameters.overrides.none { it.value.type == C.TRACK_TYPE_VIDEO }
        t.groups.forEachIndexed { gi, g ->
            for (i in 0 until g.length) {
                if (!g.isTrackSupported(i)) continue
                val f = g.getTrackFormat(i)
                when (g.type) {
                    C.TRACK_TYPE_VIDEO -> if (f.height > 0 && heights.add(f.height)) {
                        q += TrackOpt("${f.height}p", gi, i, !auto && g.isTrackSelected(i))
                    }
                    C.TRACK_TYPE_AUDIO -> {
                        val name = listOfNotNull(langName(f.language), f.label).distinct().joinToString(" - ")
                            .ifBlank { "Track ${a.size + 1}" }
                        a += TrackOpt(name, gi, i, g.isTrackSelected(i))
                    }
                    C.TRACK_TYPE_TEXT -> {
                        val name = listOfNotNull(langName(f.language), f.label).distinct().joinToString(" - ")
                            .ifBlank { "Subtitle ${x.size + 1}" }
                        x += TrackOpt(name, gi, i, g.isTrackSelected(i))
                    }
                }
            }
        }
        _ui.update {
            it.copy(
                qualityAuto = auto,
                qualities = q.sortedByDescending { o -> o.label.dropLast(1).toIntOrNull() ?: 0 },
                audios = a,
                texts = x,
            )
        }
    }

    // ---------------------------------------------------------------- progress, history, thumbnails

    private fun publishPosition() {
        val p = _player ?: return
        _ui.update {
            it.copy(
                positionMs = p.currentPosition,
                durationMs = p.duration.takeIf { d -> d != C.TIME_UNSET } ?: 0L,
                bufferedMs = p.bufferedPosition,
                isLive = p.isCurrentMediaItemLive,
            )
        }
    }

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            var n = 0
            while (isActive) {
                delay(500)
                if (!_ui.value.active) break
                publishPosition()
                if (++n % 10 == 0 && _player?.isPlaying == true) save()
            }
        }
    }

    private fun titleOf(r: PlaybackRequest): String =
        r.title ?: Uri.parse(r.url).lastPathSegment?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
            ?: domainOf(r.url)

    private fun save(ended: Boolean = false) {
        val r = req ?: return
        val p = _player ?: return
        if (p.currentMediaItem == null) return
        val s = settingsSnapshot
        if (!s.historyEnabled) return
        val live = p.isCurrentMediaItemLive
        val pos = if (!s.saveProgress || ended || live) 0L else p.currentPosition
        val dur = p.duration.takeIf { it != C.TIME_UNSET && !live } ?: 0L
        val title = titleOf(r)
        val thumb = thumbPath
        viewModelScope.launch { repo.recordProgress(r.url, title, dur, pos, thumb) }
    }

    private fun maybeThumbnail() {
        val r = req ?: return
        if (thumbTried || thumbPath != null || _player?.isCurrentMediaItemLive == true) return
        thumbTried = true
        viewModelScope.launch {
            val path = Thumbnails.generate(app, r.url, r.headers())
            if (path != null) {
                thumbPath = path
                save()
            }
        }
    }

    override fun onCleared() {
        _player?.release()
        _player = null
    }
}
