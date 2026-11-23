@file:OptIn(UnstableApi::class)

package com.example.universalplayer.player

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.datasource.HttpDataSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.common.util.concurrent.ListenableFuture
import org.json.JSONArray
import org.json.JSONObject
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
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
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
import com.example.universalplayer.data.normalizeDrmScheme
import com.example.universalplayer.data.splitLinkOptions
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
    data class Error(val message: String, val details: String = "") : LoadState
}

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    val player: ExoPlayer = PlayerHub.get(app)

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
    private var autoRetry = false
    private var lastVideoKey: String? = null
    private var lastKeys: List<String> = emptyList()
    private val rememberedHeight = HashMap<String, Int>()

    // ---- auto reconnect / network
    private var netRetry = 0
    private var retryJob: Job? = null
    private var lastErrorNetwork = false
    private val _reconnect = MutableStateFlow<String?>(null)
    val reconnect: StateFlow<String?> = _reconnect.asStateFlow()
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    // ---- background audio (media session service)
    private var controllerFuture: ListenableFuture<MediaController>? = null

    // ---- Chromecast
    private var casting = false
    private val _castName = MutableStateFlow<String?>(null)
    val castName: StateFlow<String?> = _castName.asStateFlow()
    private val _castNote = MutableStateFlow<String?>(null)
    val castNote: StateFlow<String?> = _castNote.asStateFlow()

    /** true = portrait video, false = landscape, null = unknown yet */
    private val _portrait = MutableStateFlow<Boolean?>(null)
    val portrait: StateFlow<Boolean?> = _portrait.asStateFlow()

    private val _resumedAt = MutableStateFlow(0L)
    val resumedAt: StateFlow<Long> = _resumedAt.asStateFlow()
    private val _subs = MutableStateFlow<List<Opt>>(emptyList())
    val subs: StateFlow<List<Opt>> = _subs.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = sync()
        override fun onPlaybackStateChanged(playbackState: Int) {
            sync()
            if (playbackState == Player.STATE_READY) {
                netRetry = 0
                _reconnect.value = null
            }
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
            val u = currentUrl
            val r = resolved
            if (r != null && r.isShort && !autoRetry && u != null) {
                // cached link may have expired: forget it and resolve again once
                autoRetry = true
                Resolver.forget(u, opts)
                lastKeys.forEach { ShortCache.remove(getApplication(), it) }
                startLoad(u, true)
                return
            }
            if (tryReconnect(error)) return
            lastErrorNetwork = isNetworkError(error)
            _reconnect.value = null
            _load.value = LoadState.Error(
                "Playback error: ${error.errorCodeName}" + (httpStatus(error)?.let { "  (HTTP $it)" } ?: ""),
                diagnose(error)
            )
        }
    }

    init {
        player.addListener(listener)
        PlayerHub.onNext = { skipNext() }
        PlayerHub.onPrev = { skipPrev() }
        PlayerHub.canNext = { queueIndex in 0 until queue.size - 1 }
        PlayerHub.canPrev = { queueIndex > 0 }
        runCatching {
            controllerFuture = MediaController.Builder(
                app, SessionToken(app, ComponentName(app, PlaybackService::class.java))
            ).buildAsync()
        }
        runCatching {
            val cm = app.getSystemService(ConnectivityManager::class.java)
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    viewModelScope.launch { onNetworkBack() }
                }
            }
            cm?.registerDefaultNetworkCallback(cb)
            netCallback = cb
        }
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        viewModelScope.launch {
            var n = 0
            while (true) {
                sync()
                pollCast()
                delay(250)
                if (++n % 40 == 0 && player.isPlaying) saveProgress()
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
        val (u, po) = splitLinkOptions(url.trim(), o)
        queue = emptyList()
        queueIndex = -1
        if (u == currentUrl && _load.value is LoadState.Ready && po == opts) return
        opts = po
        startLoad(u)
    }

    private fun loadQueueItem(it: HistoryItem) {
        val (u, po) = splitLinkOptions(it.url, it.opts)
        opts = po
        startLoad(u)
    }

    fun openQueue(items: List<HistoryItem>, index: Int) {
        if (index !in items.indices) return
        queue = items
        queueIndex = index
        loadQueueItem(items[index])
    }

    fun skipNext() {
        if (queueIndex in 0 until queue.size - 1) {
            queueIndex++
            loadQueueItem(queue[queueIndex])
        }
    }

    fun skipPrev() {
        if (queueIndex > 0) {
            queueIndex--
            loadQueueItem(queue[queueIndex])
        }
    }

    fun retry() {
        currentUrl?.let { startLoad(it) }
    }

    private fun startLoad(u: String, auto: Boolean = false) {
        if (!auto) autoRetry = false
        saveProgress()
        job?.cancel()
        retryJob?.cancel()
        netRetry = 0
        _reconnect.value = null
        currentUrl = u
        _load.value = LoadState.Loading
        player.stop()
        player.clearMediaItems()
        _qualities.value = emptyList()
        _audios.value = emptyList()
        _title.value = ""
        _epgId.value = queue.getOrNull(queueIndex)?.tvgId.orEmpty()
        _portrait.value = null
        _resumedAt.value = 0L
        _subs.value = emptyList()
        job = viewModelScope.launch {
            try {
                val r = Resolver.resolve(u, opts)
                resolved = r
                val saved = HistoryStore.items.value.firstOrNull { it.url == u }
                val startPos = if (!r.isLive && saved != null && saved.position > 5_000) saved.position else 0L
                val startArg = if (startPos > 0) startPos else C.TIME_UNSET
                adaptive = r.direct
                _title.value = queue.getOrNull(queueIndex)?.title?.takeIf { it != u }
                    ?: saved?.title?.takeIf { it.isNotBlank() && it != u } ?: r.title
                if (r.direct) {
                    selHeight = 0
                    val item = MediaItem.Builder().setUri(r.directUrl!!).apply {
                        setMediaMetadata(MediaMetadata.Builder().setTitle(_title.value).build())
                        r.directMime?.let { setMimeType(it) }
                        if (opts.hasDrm) setDrmConfiguration(drmConfig(opts))
                    }.build()
                    player.setMediaSource(directFactory(opts, r.directUrl!!).createMediaSource(item), startArg)
                } else {
                    val hs = heights(r)
                    selHeight = rememberedHeight[r.webUrl]?.takeIf { it in hs } ?: hs.firstOrNull { it <= 1080 } ?: hs.firstOrNull() ?: 0
                    selLang = audioLangs(r).firstOrNull() ?: "und"
                    player.setMediaSource(buildSource(r, selHeight, selLang), startArg)
                    updateFormatOptions()
                }
                player.playbackParameters = player.playbackParameters.withSpeed(userSpeed)
                player.playWhenReady = true
                player.prepare()
                _load.value = LoadState.Ready
                val base = queue.getOrNull(queueIndex)
                val histTitle = saved?.title?.takeIf { it.isNotBlank() && it != u }
                    ?: base?.title?.takeIf { it != u } ?: r.title
                HistoryStore.add(
                    HistoryItem(u, histTitle, r.thumb ?: saved?.thumb ?: base?.thumb, System.currentTimeMillis(), opts, startPos, saved?.duration ?: 0L)
                )
                _resumedAt.value = startPos
                PlaylistStore.update(u, r.title, r.thumb)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _load.value = LoadState.Error(friendly(e), diagnoseResolve(e, u))
            }
        }
    }

    fun close() {
        saveProgress()
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
        if (msg.contains("parse JSON", true) || msg.contains("Bad Request", true)) {
            return "The site did not return the video (login expired, too many requests, or the site changed)." +
                    "\n\nTry again in a few minutes, use Advanced > Site login > Re-login, or Advanced > Update video engine."
        }
        val line = msg.lineSequence().firstOrNull { it.contains("ERROR", true) } ?: msg.lineSequence().first()
        val base = line.removePrefix("ERROR:").trim().take(220).ifBlank { "Could not load this link" }
        val needsLogin = listOf("login", "log in", "age", "cookies", "private", "empty media response", "restricted")
            .any { msg.contains(it, ignoreCase = true) }
        return if (needsLogin) "$base\n\nTip: open Advanced tab > Site login and sign in (age-restricted / private videos)." else base
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

    private fun src(f: Fmt, cacheKey: String? = null): MediaSource {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(f.headers)
        val dsf: DataSource.Factory = if (cacheKey != null) {
            CacheDataSource.Factory()
                .setCache(ShortCache.get(getApplication()))
                .setUpstreamDataSourceFactory(http)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } else http
        val item = MediaItem.Builder().setUri(f.url).apply {
            setMediaMetadata(
                MediaMetadata.Builder().setTitle(_title.value)
                    .setArtworkUri(resolved?.thumb?.let { Uri.parse(it) }).build()
            )
            if (f.protocol.contains("m3u8")) setMimeType(MimeTypes.APPLICATION_M3U8)
            if (cacheKey != null) setCustomCacheKey(cacheKey)
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

        val keys = mutableListOf<String>()
        fun s(f: Fmt, kind: String): MediaSource {
            val k = if (r.isShort && !f.protocol.contains("m3u8"))
                "${r.webUrl}|$kind|${if (kind == "a") langKey(f) else f.height.toString()}" else null
            if (k != null) { keys += k; if (kind == "v") lastVideoKey = k }
            return src(f, k)
        }

        val main: List<MediaSource> = if (vids.isEmpty()) {
            listOf(s(pickAudio(), "a"))
        } else {
            val cands = vids.filter { h == 0 || it.height == h }.ifEmpty { vids }
            val videoOnly = cands.filter { it.acodec == "none" }
            val v = if (videoOnly.isNotEmpty() && audios.isNotEmpty()) {
                videoOnly.sortedWith(compareByDescending<Fmt> { codecScore(it.vcodec) }.thenByDescending { it.tbr }).first()
            } else {
                cands.filter { it.acodec != "none" }.maxByOrNull { it.tbr } ?: cands.first()
            }
            if (v.acodec == "none" && audios.isNotEmpty()) listOf(s(v, "v"), s(pickAudio(), "a")) else listOf(s(v, "v"))
        }
        lastKeys = keys.toList()
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
        val textGroups = t.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        val textOff = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        _subs.value = textGroups.mapIndexed { i, g ->
            val f = g.getTrackFormat(0)
            Opt(i.toString(), f.label ?: langLabel(f.language ?: "und"), !textOff && g.isSelected)
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
            val oldKey = lastVideoKey
            selHeight = id.toIntOrNull() ?: 0
            resolved?.let { if (it.isShort) rememberedHeight[it.webUrl] = selHeight }
            rebuild()
            // new quality replaces the old cached copy
            if (oldKey != null && oldKey != lastVideoKey) ShortCache.remove(getApplication(), oldKey)
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

    // ---------------------------------------------------------------- resume / subtitles
    fun saveProgress() {
        val u = currentUrl ?: return
        if (_load.value !is LoadState.Ready) return
        val p = player
        if (p.isCurrentMediaItemLive) return
        val d = p.duration
        if (d == C.TIME_UNSET || d <= 0) return
        var pos = p.currentPosition
        if (pos > d * 0.95) pos = 0
        HistoryStore.setProgress(u, pos, d)
        PlaylistStore.setProgress(u, pos, d)
    }

    fun startOver() {
        player.seekTo(0)
        _resumedAt.value = 0L
    }

    fun selectSub(id: String) {
        val b = player.trackSelectionParameters.buildUpon()
        if (id == "off") {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).clearOverridesOfType(C.TRACK_TYPE_TEXT)
        } else {
            val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            val g = groups.getOrNull(id.toIntOrNull() ?: -1) ?: return
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0))
        }
        player.trackSelectionParameters = b.build()
        onTracks(player.currentTracks)
        sync()
    }

    // ---------------------------------------------------------------- DRM / custom headers
    private fun directFactory(o: PlayOptions, url: String): DefaultMediaSourceFactory {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(o.headers())
        // keep the signed token (?edge-cache-token=... etc.) on child playlists, segments and keys
        val dsf = androidx.media3.datasource.ResolvingDataSource.Factory(http, TokenPropagation.resolver(url))
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
        val uuid = when (normalizeDrmScheme(o.drmScheme)) {
            "PlayReady" -> C.PLAYREADY_UUID
            "ClearKey" -> C.CLEARKEY_UUID
            else -> C.WIDEVINE_UUID
        }
        var lic = o.drmLicense.trim()
        val hdrs = LinkedHashMap<String, String>()
        hdrs.putAll(o.headers())
        hdrs.putAll(o.drmHeaderMap())
        // Kodi style license: https://server/license|Header=value&Header2=value|R{SSM}|
        if (lic.startsWith("http", true) && lic.contains('|')) {
            val parts = lic.split('|')
            lic = parts[0].trim()
            parts.getOrNull(1)?.split('&')?.forEach { kv ->
                val i = kv.indexOf('=')
                if (i > 0) hdrs[kv.substring(0, i).trim()] = kv.substring(i + 1).trim()
            }
        }
        if (uuid == C.CLEARKEY_UUID) lic = clearKeyUri(lic)
        return MediaItem.DrmConfiguration.Builder(uuid)
            .setLicenseUri(lic)
            .setLicenseRequestHeaders(hdrs)
            .setMultiSession(true)
            .build()
    }

    private val hex32 = Regex("^[0-9a-fA-F]{32}$")
    private val kidKeyPairs = Regex("^[0-9a-fA-F]{32}:[0-9a-fA-F]{32}(,[0-9a-fA-F]{32}:[0-9a-fA-F]{32})*$")

    private fun b64url(bytes: ByteArray) =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    private fun keyPart(v: String): String =
        if (hex32.matches(v)) b64url(v.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
        else v.trimEnd('=').replace('+', '-').replace('/', '_')

    private fun dataUri(json: String) =
        "data:application/json;base64," + Base64.encodeToString(json.toByteArray(), Base64.NO_WRAP)

    /**
     * ClearKey license can be given as: "kid:key" (hex, comma separated), a JSON {"keys":[...]},
     * a flat JSON {"kid":"key"}, a data: URI, or a license server URL.
     */
    private fun clearKeyUri(lic: String): String {
        val t = lic.trim()
        if (t.startsWith("data:", true) || t.startsWith("http", true)) return t
        if (t.startsWith("{")) {
            val json = runCatching {
                val o = JSONObject(t)
                val arr = JSONArray()
                fun add(kid: String, k: String) {
                    arr.put(JSONObject().put("kty", "oct").put("kid", keyPart(kid)).put("k", keyPart(k)))
                }
                val keys = o.optJSONArray("keys")
                if (keys != null) {
                    for (i in 0 until keys.length()) {
                        val e = keys.getJSONObject(i)
                        add(e.getString("kid"), e.getString("k"))
                    }
                } else {
                    for (kid in o.keys()) add(kid, o.getString(kid))
                }
                JSONObject().put("keys", arr).put("type", "temporary").toString()
            }.getOrNull()
            return if (json != null) dataUri(json) else t
        }
        val compact = t.replace(" ", "")
        if (!kidKeyPairs.matches(compact)) return t
        val keys = compact.split(",").joinToString(",") {
            val (kid, k) = it.split(":")
            """{"kty":"oct","k":"${keyPart(k)}","kid":"${keyPart(kid)}"}"""
        }
        return dataUri("""{"keys":[$keys],"type":"temporary"}""")
    }

    // ---------------------------------------------------------------- diagnostics
    private fun httpStatus(e: Throwable): Int? {
        var t: Throwable? = e
        var depth = 0
        while (t != null && depth++ < 10) {
            if (t is HttpDataSource.InvalidResponseCodeException) return t.responseCode
            t = t.cause
        }
        return null
    }

    private fun rootCause(e: Throwable): Throwable {
        var t = e
        var depth = 0
        while (t.cause != null && t.cause !== t && depth++ < 10) t = t.cause!!
        return t
    }

    private fun isNetworkError(e: PlaybackException): Boolean = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> true
        else -> false
    }

    private fun linkType(): String {
        val r = resolved
        return when {
            r == null -> "unknown"
            r.directMime == MimeTypes.APPLICATION_MPD -> "DASH (.mpd)"
            r.directMime == MimeTypes.APPLICATION_M3U8 -> "HLS (.m3u8)"
            r.direct -> "Direct file"
            else -> "Site link via yt-dlp (" + (r.formats.firstOrNull()?.protocol ?: "?") + ")"
        }
    }

    private fun diagnose(e: PlaybackException): String {
        val o = opts
        val u = currentUrl.orEmpty()
        val sb = StringBuilder()
        sb.appendLine("Error: ${e.errorCodeName} (${e.errorCode})")
        val http = httpStatus(e)
        if (http != null) {
            sb.appendLine("HTTP status: $http")
            LinkTester.hint(http).takeIf { it.isNotEmpty() && http !in 200..299 }?.let { sb.appendLine("=> $it") }
        }
        sb.appendLine("Type: " + linkType())
        sb.appendLine("Host: " + (runCatching { Uri.parse(u).host }.getOrNull() ?: "?"))
        sb.appendLine(
            "DRM: " + if (o.hasDrm) {
                val lic = o.drmLicense.trim()
                val kind = when {
                    lic.startsWith("{") -> "JSON keys"
                    kidKeyPairs.matches(lic.replace(" ", "")) -> "kid:key"
                    lic.startsWith("http", true) -> "license URL"
                    else -> "custom"
                }
                normalizeDrmScheme(o.drmScheme) + " ($kind)"
            } else "none"
        )
        val hs = o.headers().keys.toList()
        if (hs.isNotEmpty()) sb.appendLine("Headers sent: " + hs.joinToString(", "))
        val root = rootCause(e)
        sb.appendLine("Cause: ${root.javaClass.simpleName}" + (root.message?.let { ": " + it.take(160) } ?: ""))
        val tip = when {
            e.errorCode in 6000..6999 ->
                "DRM problem: key is wrong/expired or the scheme does not match. Try the other DRM scheme, or check the key."
            http == 403 || http == 401 ->
                "Link blocked or expired: add Referer/Origin/User-Agent in Advanced, or use a fresh link."
            http == 404 || http == 410 -> "Stream not found or expired: get a new link."
            e.errorCode in 2000..2999 && http == null ->
                "Network problem: check internet, or the server is down."
            e.errorCode in 3000..3999 -> "The file/manifest looks broken or unsupported."
            e.errorCode in 4000..4999 -> "This device cannot decode the video format."
            else -> ""
        }
        if (tip.isNotEmpty()) sb.appendLine("=> $tip")
        sb.appendLine("Tip: Advanced > Test link shows more.")
        return sb.toString().trim()
    }

    private fun diagnoseResolve(e: Throwable, u: String): String {
        val sb = StringBuilder()
        sb.appendLine("Type: site link (yt-dlp)")
        sb.appendLine("Host: " + (runCatching { Uri.parse(u).host }.getOrNull() ?: "?"))
        sb.appendLine("Error: " + e.javaClass.simpleName)
        val msg = (e.message ?: "").trim()
        if (msg.isNotEmpty()) sb.appendLine(msg.take(500))
        return sb.toString().trim()
    }

    // ---------------------------------------------------------------- auto reconnect
    private fun tryReconnect(e: PlaybackException): Boolean {
        if (!PlayerSettings.autoReconnect || currentUrl == null) return false
        if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            player.seekToDefaultPosition()
            player.prepare()
            return true
        }
        val http = httpStatus(e)
        val recoverable = when (e.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> true
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                http == null || http >= 500 || http == 408 || http == 429
            else -> false
        }
        if (!recoverable || netRetry >= MAX_RETRY) return false
        netRetry++
        val wait = minOf(1000L shl (netRetry - 1), 15_000L)
        _reconnect.value = "Reconnecting ($netRetry/$MAX_RETRY)..."
        retryJob?.cancel()
        retryJob = viewModelScope.launch {
            delay(wait)
            player.prepare()
        }
        return true
    }

    private fun onNetworkBack() {
        if (!PlayerSettings.autoReconnect || currentUrl == null) return
        if (_load.value is LoadState.Error && lastErrorNetwork) {
            retry()
        } else if (player.playerError != null && _reconnect.value != null) {
            retryJob?.cancel()
            player.prepare()
        }
    }

    // ---------------------------------------------------------------- background audio
    /** App left the screen: keep audio going (notification) or pause, depending on the setting. */
    fun onAppStop() {
        saveProgress()
        if (casting) return
        if (PlayerSettings.backgroundAudio && player.playWhenReady && player.playbackState != Player.STATE_IDLE) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()
        } else {
            player.pause()
        }
    }

    fun onAppStart() {
        if (player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO)) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false).build()
        }
    }

    // ---------------------------------------------------------------- Chromecast
    private fun castSource(): Triple<String, String, Boolean>? {
        val r = resolved ?: return null
        if (r.direct) {
            if (opts.hasDrm || opts.headers().isNotEmpty()) {
                _castNote.value = "Streams that need DRM or special headers cannot be cast."
                return null
            }
            val mime = when (r.directMime) {
                MimeTypes.APPLICATION_MPD -> "application/dash+xml"
                MimeTypes.APPLICATION_M3U8 -> "application/x-mpegURL"
                else -> "video/mp4"
            }
            return Triple(r.directUrl.orEmpty(), mime, r.isLive)
        }
        val f = r.formats.filter { it.vcodec != "none" && it.acodec != "none" && it.protocol.startsWith("http") }
            .maxByOrNull { if (it.height > 0) it.height else 0 }
            ?: r.formats.firstOrNull { it.protocol.contains("m3u8") }
        if (f == null) {
            _castNote.value = "This link has no format that a Chromecast can play."
            return null
        }
        val mime = if (f.protocol.contains("m3u8")) "application/x-mpegURL" else "video/mp4"
        return Triple(f.url, mime, r.isLive)
    }

    private fun pollCast() {
        if (!CastHelper.isReady) return
        val sess = CastHelper.session()
        if (sess != null && !casting) {
            casting = true
            _castName.value = runCatching { sess.castDevice?.friendlyName }.getOrNull() ?: "Chromecast"
            _castNote.value = null
            val src = castSource()
            val client = sess.remoteMediaClient
            if (src == null || client == null || src.first.isBlank()) {
                CastHelper.endSession()
                return
            }
            runCatching {
                val md = com.google.android.gms.cast.MediaMetadata(com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MOVIE)
                md.putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE, _title.value)
                val info = MediaInfo.Builder(src.first)
                    .setStreamType(if (src.third) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED)
                    .setContentType(src.second)
                    .setMetadata(md)
                    .build()
                val req = MediaLoadRequestData.Builder()
                    .setMediaInfo(info)
                    .setAutoplay(true)
                    .setCurrentTime(if (src.third) 0L else player.currentPosition)
                    .build()
                client.load(req)
            }
            player.pause()
        } else if (sess == null && casting) {
            casting = false
            _castName.value = null
            player.play()
        }
    }

    fun castTogglePlay() {
        runCatching { CastHelper.session()?.remoteMediaClient?.togglePlayback() }
    }

    fun castStop() {
        CastHelper.endSession()
    }

    /** The link as the player sees it (used for "open in another app"). */
    fun currentLink(): String? = resolved?.directUrl ?: currentUrl

    private val _epgId = MutableStateFlow("")
    /** tvg-id of the channel that is playing (for the channel guide). */
    val epgId: StateFlow<String> = _epgId.asStateFlow()

    fun clearCastNote() {
        _castNote.value = null
    }

    fun pause() {
        saveProgress()
        player.pause()
    }

    override fun onCleared() {
        player.removeListener(listener)
        retryJob?.cancel()
        runCatching {
            netCallback?.let { getApplication<Application>().getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(it) }
        }
        controllerFuture?.let { runCatching { MediaController.releaseFuture(it) } }
        val appCtx = getApplication<Application>()
        appCtx.stopService(Intent(appCtx, PlaybackService::class.java))
        if (casting) CastHelper.endSession()
        PlayerHub.release()
    }

    private companion object {
        const val MAX_RETRY = 6
    }
}
