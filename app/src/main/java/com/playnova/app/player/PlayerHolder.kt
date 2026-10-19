package com.playnova.app.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.*
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.playnova.app.data.AppDao
import com.playnova.app.data.HistoryEntity
import com.playnova.app.data.SettingsStore
import com.playnova.app.data.Video
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Single app-wide player. Playback state lives here, independent of any screen,
 * so rotation / navigation / mini-player never restart the video.
 */
class PlayerHolder(
    private val ctx: Context,
    private val dao: AppDao,
    private val settings: SettingsStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val player: ExoPlayer by lazy {
        // Hardware (MediaCodec) decoders first; fall back to another decoder if one fails.
        val factory = DefaultRenderersFactory(ctx)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        ExoPlayer.Builder(ctx, factory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true
            )
            .setHandleAudioBecomingNoisy(true)
            .build().also { it.addListener(listener) }
    }

    private val _queue = MutableStateFlow<List<Video>>(emptyList())
    val queue: StateFlow<List<Video>> = _queue
    private val _index = MutableStateFlow(0)
    val index: StateFlow<Int> = _index
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _tracks = MutableStateFlow(Tracks.EMPTY)
    val tracks: StateFlow<Tracks> = _tracks
    private val _sleepEnd = MutableStateFlow(0L)
    val sleepEnd: StateFlow<Long> = _sleepEnd
    private val _repeat = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeat: StateFlow<Int> = _repeat
    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle
    private val _abA = MutableStateFlow(-1L)
    val abA: StateFlow<Long> = _abA
    private val _abB = MutableStateFlow(-1L)
    val abB: StateFlow<Long> = _abB
    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed
    private val _subUri = MutableStateFlow<Uri?>(null)

    val current: Video? get() = _queue.value.getOrNull(_index.value)
    val hasMedia: Boolean get() = _queue.value.isNotEmpty()

    private var sleepJob: Job? = null
    private var ticker: Job? = null
    private var baseSpeed = 1f

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playing.value = isPlaying
            if (!isPlaying) saveProgress()
        }

        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            val i = player.currentMediaItemIndex
            if (i in _queue.value.indices) _index.value = i
            _error.value = null
            _abA.value = -1; _abB.value = -1
            _subUri.value = null
            val v = current ?: return
            scope.launch {
                val h = withContext(Dispatchers.IO) { dao.progress(v.id) }
                val prefs = settings.state.value
                baseSpeed = h?.speed?.takeIf { it > 0f } ?: prefs.defaultSpeed
                setSpeed(baseSpeed, persist = false)
                if (prefs.resume && h != null && h.positionMs > 5000 &&
                    (h.durationMs <= 0 || h.positionMs < h.durationMs - 5000)
                ) player.seekTo(h.positionMs)
            }
        }

        override fun onTracksChanged(tracks: Tracks) { _tracks.value = tracks }

        override fun onPlayerError(error: PlaybackException) {
            _error.value = explain(error)
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) saveProgress(finished = true)
        }
    }

    init {
        ticker = scope.launch {
            var n = 0
            while (isActive) {
                delay(250)
                if (!_playing.value) continue
                val a = _abA.value; val b = _abB.value
                if (a >= 0 && b > a && player.currentPosition >= b) player.seekTo(a)
                if (++n % 20 == 0) saveProgress()
            }
        }
    }

    fun explain(e: PlaybackException): String = when (e.errorCode) {
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
            "Is phone ka decoder is video ko decode nahi kar pa raha (codec ya resolution zyada heavy hai, jaise AV1 / 4K HEVC 10-bit)."
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
            "Ye video format/codec is device par supported nahi hai."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
            "File corrupt lag rahi hai ya poori download nahi hui."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
            "Is container format ko player samajh nahi paaya."
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            "File nahi mili. Shayad move ya delete ho gayi hai. Library refresh karo."
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
            "File kholne ki permission nahi hai."
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "Network connection fail ho gaya."
        else -> "Playback error: ${e.errorCodeName}"
    }

    fun play(videos: List<Video>, start: Int, shuffle: Boolean = false) {
        if (videos.isEmpty()) return
        saveProgress()
        _queue.value = videos
        _index.value = start.coerceIn(videos.indices)
        _error.value = null
        player.shuffleModeEnabled = shuffle
        _shuffle.value = shuffle
        val items = videos.map {
            MediaItem.Builder().setUri(it.uri).setMediaId(it.id.toString()).build()
        }
        player.setMediaItems(items, _index.value, C.TIME_UNSET)
        player.pauseAtEndOfMediaItems = !settings.state.value.autoNext
        player.repeatMode = _repeat.value
        player.prepare()
        player.playWhenReady = true
    }

    fun retry() { _error.value = null; player.prepare(); player.playWhenReady = true }

    fun toggle() { if (player.isPlaying) player.pause() else { if (player.playbackState == Player.STATE_ENDED) player.seekTo(0); player.play() } }

    fun close() {
        saveProgress()
        player.stop()
        player.clearMediaItems()
        _queue.value = emptyList()
        _playing.value = false
    }

    fun setSpeed(s: Float, persist: Boolean = true) {
        player.setPlaybackSpeed(s)
        _speed.value = s
        if (persist) { baseSpeed = s; saveProgress() }
    }

    fun tempSpeed(s: Float?) {
        player.setPlaybackSpeed(s ?: baseSpeed)
        _speed.value = s ?: baseSpeed
    }

    fun setRepeat(mode: Int) { player.repeatMode = mode; _repeat.value = mode }
    fun setShuffle(on: Boolean) { player.shuffleModeEnabled = on; _shuffle.value = on }

    fun setSleep(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) { _sleepEnd.value = 0; return }
        _sleepEnd.value = System.currentTimeMillis() + minutes * 60_000L
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            player.pause()
            _sleepEnd.value = 0
        }
    }

    fun markA() { _abA.value = player.currentPosition; _abB.value = -1 }
    fun markB() { if (_abA.value >= 0 && player.currentPosition > _abA.value) _abB.value = player.currentPosition }
    fun clearAB() { _abA.value = -1; _abB.value = -1 }

    fun selectAudio(group: Tracks.Group, idx: Int) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, idx)).build()
    }

    fun selectText(group: Tracks.Group?, idx: Int) {
        val b = player.trackSelectionParameters.buildUpon()
        if (group == null) b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        else b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, idx))
        player.trackSelectionParameters = b.build()
    }

    /** Attach an external subtitle (SRT / VTT / SSA) to the current video, keeping position. */
    fun addSubtitle(uri: Uri, name: String) {
        val v = current ?: return
        val lower = name.lowercase()
        val mime = when {
            lower.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            lower.endsWith(".ass") || lower.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        val sub = MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(mime).setLanguage("und").setLabel(name)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
        val item = MediaItem.Builder().setUri(v.uri).setMediaId(v.id.toString())
            .setSubtitleConfigurations(listOf(sub)).build()
        val pos = player.currentPosition
        val i = player.currentMediaItemIndex
        player.replaceMediaItem(i, item)
        player.seekTo(i, pos)
        _subUri.value = uri
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
    }

    fun saveProgress(finished: Boolean = false) {
        val v = current ?: return
        if (player.currentMediaItem == null) return
        val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: v.durationMs
        val pos = if (finished) 0L else player.currentPosition
        val h = HistoryEntity(v.id, v.uri.toString(), v.title, pos, dur,
            System.currentTimeMillis(), baseSpeed, true)
        scope.launch(Dispatchers.IO) { runCatching { dao.upsertHistory(h) } }
    }

    fun release() { saveProgress(); player.release() }
}
