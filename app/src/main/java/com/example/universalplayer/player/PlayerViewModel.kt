@file:OptIn(UnstableApi::class)

package com.example.universalplayer.player

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import android.util.Base64
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider
import com.example.universalplayer.data.HistoryItem
import com.example.universalplayer.data.HistoryStore
import com.example.universalplayer.data.PlayOptions
import com.example.universalplayer.data.PlaylistStore
import com.example.universalplayer.langLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class Opt(val id: String, val label: String, val selected: Boolean)

data class UiState(
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val ended: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val isLive: Boolean = false,
    val liveOffsetMs: Long = -1,
    val hasSubs: Boolean = false,
    val subsOn: Boolean = false,
    val muted: Boolean = false,
    val speed: Float = 1f,
    val hasPrev: Boolean = false,
    val hasNext: Boolean = false
)

sealed interface LoadState {
    data object Idle : LoadState
    data object Loading : LoadState
    data object Ready : LoadState
    data class Error(val message: String) : LoadState
}

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    val player: ExoPlayer = ExoPlayer.Builder(app)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(), true
        )
        .setHandleAudioBecomingNoisy(true)
        .setSeekBackIncrementMs(10_000)
        .setSeekForwardIncrementMs(10_000)
        .build()

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    private val _load = MutableStateFlow<LoadState>(LoadState.Idle)
    val load: StateFlow<LoadState> = _load.asStateFlow()
    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()
    private val _qualities = MutableStateFlow<List<Opt>>(emptyList())
    val qualities: StateFlow<List<Opt>> = _qualities.asStateFlow()
    private val _audios = MutableStateFlow<List<Opt>>(emptyList())
    val audios: StateFlow<List<Opt>> = _audios.asStateFlow()

    private var resolved: Resolved? = null
    private var currentUrl: String? = null
    private var adaptive = false
    private var selHeight = 0
    private var selLang: String = "und"
    private var userSpeed = 1f
    private var job: Job? = null
    private var opts = PlayOptions()
    private var queue: List<HistoryItem> = emptyList()
    private var queueIndex = -1

    /** true = portrait video, false = landscape, null = unknown yet */
    private val _portrait = MutableStateFlow<Boolean?>(null)
    val portrait: StateFlow<Boolean?> = _portrait.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = sync()
        override fun onPlaybackStateChanged(playbackState: Int) {
            sync()
            if (playbackState == Player.STATE_ENDED) skipNext()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            var w = videoSize.width
            var h = videoSize.height
            if (videoSize.unappliedRotationDegrees == 90 || videoSize.unappliedRotationDegrees == 270) {
                val t = w; w = h; h = t
            }
            if (w > 0 && h > 0) _portrait.value = h > w
        }
        override fun onVolumeChanged(volume: Float) = sync()
        override fun onTracksChanged(tracks: Tracks) {
            onTracks(tracks)
            sync()
        }

        override fun onPlayerError(error: PlaybackException) {
            _load.value = LoadState.Error("Playback error: ${error.errorCodeName}")
        }
    }

    init {
        player.addListener(listener)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        viewModelScope.launch {
            while (true) {
                sync()
                delay(250)
            }
        }
    }

    // ---------------------------------------------------------------- state sync
    private fun sync() {
        val p = player
        val dur = if (p.duration == C.TIME_UNSET) 0L else p.duration
        val off = p.currentLiveOffset
        _ui.value = UiState(
            isPlaying = p.playWhenReady && p.playbackState != Player.STATE_ENDED &&
                    p.playbackState != Player.STATE_IDLE,
            buffering = p.playbackState == Player.STATE_BUFFERING,
            ended = p.playbackState == Player.STATE_ENDED,
            positionMs = p.currentPosition.coerceAtLeast(0),
            durationMs = dur,
            bufferedMs = p.bufferedPosition.coerceAtLeast(0),
            isLive = p.isCurrentMediaItemLive,
            liveOffsetMs = if (off == C.TIME_UNSET) -1 else off,
            hasSubs = p.currentTracks.groups.any { it.type == C.TRACK_TYPE_TEXT },
            subsOn = !p.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT),
            muted = p.volume == 0f,
            speed = userSpeed,
            hasPrev = queueIndex > 0,
            hasNext = queueIndex in 0 until queue.size - 1
        )
    }

    // ---------------------------------------------------------------- opening
    fun open(url: String, o: PlayOptions = PlayOptions()) {
        val u = url.trim()
        queue = emptyList()
        queueIndex = -1
        if (u == currentUrl && _load.value is LoadState.Ready && o == opts) return
        opts = o
        startLoad(u)
    }

    fun openQueue(items: List<HistoryItem>, index: Int) {
        if (index !in items.indices) return
        queue = items
        queueIndex = index
        opts = items[index].opts
        startLoad(items[index].url)
    }

    fun skipNext() {
        if (queueIndex in 0 until queue.size - 1) {
            queueIndex++
            opts = queue[queueIndex].opts
            startLoad(queue[queueIndex].url)
        }
    }

    fun skipPrev() {
        if (queueIndex > 0) {
            queueIndex--
            opts = queue[queueIndex].opts
            startLoad(queue[queueIndex].url)
        }
    }

    fun retry() {
        currentUrl?.let { startLoad(it) }
    }

    private fun startLoad(u: String) {
        job?.cancel()
        currentUrl = u
        _load.value = LoadState.Loading
        player.stop()
        player.clearMediaItems()
        _qualities.value = emptyList()
        _audios.value = emptyList()
        _title.value = ""
        _portrait.value = null
        job = viewModelScope.launch {
            try {
                val r = Resolver.resolve(u, opts)
                resolved = r
                adaptive = r.direct
                _title.value = r.title
                if (r.direct) {
                    selHeight = 0
                    val item = MediaItem.Builder().setUri(r.directUrl!!).apply {
                        r.directMime?.let { setMimeType(it) }
                        if (opts.hasDrm) setDrmConfiguration(drmConfig(opts))
                    }.build()
                    player.setMediaSource(directFactory(opts).createMediaSource(item))
                } else {
                    val hs = heights(r)
                    selHeight = hs.firstOrNull { it <= 1080 } ?: hs.firstOrNull() ?: 0
                    selLang = audioLangs(r).firstOrNull() ?: "und"
                    player.setMediaSource(buildSource(r, selHeight, selLang))
                    updateFormatOptions()
                }
                player.playbackParameters = player.playbackParameters.withSpeed(userSpeed)
                player.playWhenReady = true
                player.prepare()
                _load.value = LoadState.Ready
                HistoryStore.add(HistoryItem(u, r.title, r.thumb, System.currentTimeMillis(), opts))
                PlaylistStore.update(u, r.title, r.thumb)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _load.value = LoadState.Error(friendly(e))
            }
        }
    }

    fun close() {
        job?.cancel()
        player.stop()
        player.clearMediaItems()
        currentUrl = null
        resolved = null
        queue = emptyList()
        queueIndex = -1
        _portrait.value = null
        _load.value = LoadState.Idle
        _qualities.value = emptyList()
        _audios.value = emptyList()
    }

    private fun friendly(e: Throwable): String {
        val msg = e.message ?: e.javaClass.simpleName
        val line = msg.lineSequence().firstOrNull { it.contains("ERROR", true) } ?: msg.lineSequence().first()
        return line.removePrefix("ERROR:").trim().take(220).ifBlank { "Could not load this link" }
    }

    // ---------------------------------------------------------------- format mode
    private fun heights(r: Resolved) =
        r.formats.filter { it.vcodec != "none" && it.height > 0 }.map { it.height }.distinct().sortedDescending()

    private fun audioFmts(r: Resolved) = r.formats.filter { it.vcodec == "none" && it.acodec != "none" }

    private fun langKey(f: Fmt) = f.lang ?: "und"

    private fun audioLangs(r: Resolved) =
        audioFmts(r).sortedByDescending { it.langPref }.map { langKey(it) }.distinct()

    private fun codecScore(c: String) = when {
        c.startsWith("avc1") || c.startsWith("h264") -> 3
        c.startsWith("vp09") || c.startsWith("vp9") -> 2
        c.startsWith("av01") -> 1
        else -> 0
    }

    private fun src(f: Fmt): MediaSource {
        val dsf = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(f.headers)
        val item = MediaItem.Builder().setUri(f.url).apply {
            if (f.protocol.contains("m3u8")) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()
        return DefaultMediaSourceFactory(dsf).createMediaSource(item)
    }

    private fun subSource(s: SubTrack): MediaSource {
        val cfg = MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.url))
            .setMimeType(s.mime).setLanguage(s.lang).setLabel(s.label).setSelectionFlags(0).build()
        return SingleSampleMediaSource.Factory(DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true))
            .setTreatLoadErrorsAsEndOfStream(true)
            .createMediaSource(cfg, C.TIME_UNSET)
    }

    private fun buildSource(r: Resolved, h: Int, lang: String): MediaSource {
        val vids = r.formats.filter { it.vcodec != "none" }
        val audios = audioFmts(r)
        fun pickAudio() = audios.filter { langKey(it) == lang }.maxByOrNull { it.tbr } ?: audios.maxByOrNull { it.tbr }!!

        val main: List<MediaSource> = if (vids.isEmpty()) {
            listOf(src(pickAudio()))
        } else {
            val cands = vids.filter { h == 0 || it.height == h }.ifEmpty { vids }
            val videoOnly = cands.filter { it.acodec == "none" }
            val v = if (videoOnly.isNotEmpty() && audios.isNotEmpty()) {
                videoOnly.sortedWith(compareByDescending<Fmt> { codecScore(it.vcodec) }.thenByDescending { it.tbr }).first()
            } else {
                cands.filter { it.acodec != "none" }.maxByOrNull { it.tbr } ?: cands.first()
            }
            if (v.acodec == "none" && audios.isNotEmpty()) listOf(src(v), src(pickAudio())) else listOf(src(v))
        }
        val all = main + r.subs.map { subSource(it) }
        return if (all.size == 1) all[0] else MergingMediaSource(*all.toTypedArray())
    }

    private fun updateFormatOptions() {
        val r = resolved ?: return
        val hs = heights(r)
        _qualities.value = if (hs.size > 1) hs.map { Opt(it.toString(), "${it}p", it == selHeight) } else emptyList()
        val ls = audioLangs(r)
        _audios.value = if (ls.size > 1) ls.map { Opt(it, langLabel(it), it == selLang) } else emptyList()
    }

    private fun rebuild() {
        val r = resolved ?: return
        val pos = player.currentPosition
        val play = player.playWhenReady
        player.setMediaSource(buildSource(r, selHeight, selLang), if (r.isLive) C.TIME_UNSET else pos)
        player.prepare()
        player.playWhenReady = play
        updateFormatOptions()
    }

    // ---------------------------------------------------------------- adaptive mode (direct HLS/DASH)
    private fun onTracks(t: Tracks) {
        // keep subtitles on after a source rebuild
        if (!player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)) {
            val groups = t.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            if (groups.isNotEmpty() && groups.none { it.isSelected }) applyText(groups)
        }
        if (!adaptive) return

        val heights = t.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
            .flatMap { g -> (0 until g.length).map { g.getTrackFormat(it).height } }
            .filter { it > 0 }.distinct().sortedDescending()
        _qualities.value = if (heights.size > 1) {
            val cur = _qualities.value.firstOrNull { it.selected }?.id ?: "0"
            listOf(Opt("0", "Auto", cur == "0")) + heights.map { Opt(it.toString(), "${it}p", cur == it.toString()) }
        } else emptyList()

        val map = linkedMapOf<String, Boolean>()
        t.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.forEach { g ->
            for (i in 0 until g.length) {
                val l = g.getTrackFormat(i).language ?: "und"
                map[l] = (map[l] == true) || g.isTrackSelected(i)
            }
        }
        _audios.value = if (map.size > 1) map.map { Opt(it.key, langLabel(it.key), it.value) } else emptyList()
    }

    // ---------------------------------------------------------------- user actions
    fun selectQuality(id: String) {
        if (adaptive) {
            val h = id.toIntOrNull() ?: 0
            val b = player.trackSelectionParameters.buildUpon()
            if (h == 0) b.clearVideoSizeConstraints() else b.setMinVideoSize(0, h).setMaxVideoSize(Int.MAX_VALUE, h)
            player.trackSelectionParameters = b.build()
            _qualities.value = _qualities.value.map { it.copy(selected = it.id == id) }
        } else {
            selHeight = id.toIntOrNull() ?: 0
            rebuild()
        }
    }

    fun selectAudio(id: String) {
        if (adaptive) {
            player.trackSelectionParameters =
                player.trackSelectionParameters.buildUpon().setPreferredAudioLanguage(id).build()
            _audios.value = _audios.value.map { it.copy(selected = it.id == id) }
        } else {
            selLang = id
            rebuild()
        }
    }

    fun toggleSubs() {
        val on = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        val b = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !on)
        if (!on) b.clearOverridesOfType(C.TRACK_TYPE_TEXT)
        player.trackSelectionParameters = b.build()
        if (on) applyText(player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT })
        sync()
    }

    private fun applyText(groups: List<Tracks.Group>) {
        val pick = groups.firstOrNull { it.getTrackFormat(0).language?.startsWith("en") == true }
            ?: groups.firstOrNull() ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(pick.mediaTrackGroup, 0))
            .build()
    }

    fun togglePlay() {
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0)
            player.play()
        } else if (player.playWhenReady) player.pause() else player.play()
        sync()
    }

    fun toggleMute() {
        player.volume = if (player.volume == 0f) 1f else 0f
        sync()
    }

    fun seekBy(ms: Long) {
        val dur = player.duration
        val target = player.currentPosition + ms
        player.seekTo(if (dur != C.TIME_UNSET && dur > 0) target.coerceIn(0, dur) else target.coerceAtLeast(0))
        sync()
    }

    fun seekToFraction(f: Float) {
        val dur = player.duration
        if (dur != C.TIME_UNSET && dur > 0) player.seekTo((f * dur).toLong())
        sync()
    }

    fun goLive() {
        player.seekToDefaultPosition()
        player.play()
        sync()
    }

    fun setSpeed(s: Float) {
        userSpeed = s
        player.setPlaybackSpeed(s)
        sync()
    }

    fun boost(on: Boolean) {
        player.setPlaybackSpeed(if (on) 2f else userSpeed)
    }

    // ---------------------------------------------------------------- DRM / custom headers
    private fun directFactory(o: PlayOptions): DefaultMediaSourceFactory {
        val dsf = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(o.headers())
        val f = DefaultMediaSourceFactory(dsf)
        if (o.hasDrm) {
            // DefaultDataSource also understands data: URIs (used for ClearKey keys)
            f.setDrmSessionManagerProvider(
                DefaultDrmSessionManagerProvider().apply {
                    setDrmHttpDataSourceFactory(DefaultDataSource.Factory(getApplication()))
                }
            )
        }
        return f
    }

    private fun drmConfig(o: PlayOptions): MediaItem.DrmConfiguration {
        val uuid = when (o.drmScheme) {
            "PlayReady" -> C.PLAYREADY_UUID
            "ClearKey" -> C.CLEARKEY_UUID
            else -> C.WIDEVINE_UUID
        }
        var lic = o.drmLicense.trim()
        if (uuid == C.CLEARKEY_UUID) lic = clearKeyUri(lic)
        return MediaItem.DrmConfiguration.Builder(uuid)
            .setLicenseUri(lic)
            .setLicenseRequestHeaders(o.headers())
            .setMultiSession(true)
            .build()
    }

    /** Accepts "kid:key" (hex, comma separated for many keys) and converts to a ClearKey JSON data URI. */
    private fun clearKeyUri(lic: String): String {
        val pair = Regex("^[0-9a-fA-F]{32}:[0-9a-fA-F]{32}(,[0-9a-fA-F]{32}:[0-9a-fA-F]{32})*$")
        if (!pair.matches(lic.replace(" ", ""))) return lic
        fun b64(hex: String) = Base64.encodeToString(
            hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        val keys = lic.replace(" ", "").split(",").joinToString(",") {
            val (kid, k) = it.split(":")
            """{"kty":"oct","k":"${b64(k)}","kid":"${b64(kid)}"}"""
        }
        val json = """{"keys":[$keys],"type":"temporary"}"""
        return "data:application/json;base64," + Base64.encodeToString(json.toByteArray(), Base64.NO_WRAP)
    }

    fun pause() = player.pause()

    override fun onCleared() {
        player.removeListener(listener)
        player.release()
    }
}
