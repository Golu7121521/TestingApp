package com.mytune.app.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.mytune.app.data.repository.LibraryRepository
import com.mytune.app.domain.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class QueueItem(val id: String, val title: String, val artist: String, val artworkUrl: String)

data class PlayerState(
    val hasMedia: Boolean = false,
    val mediaId: String? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUrl: String = "",
    val artistId: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<QueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val error: Boolean = false,
)

private const val EXTRA_DURATION = "dur"
private const val EXTRA_ARTIST_ID = "artistId"

fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri("${StreamResolver.SCHEME}://song/$id") // resolved lazily by StreamResolver
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(if (artworkUrl.isBlank()) null else Uri.parse(artworkUrl))
            .setExtras(Bundle().apply { putLong(EXTRA_DURATION, durationSec); putString(EXTRA_ARTIST_ID, artistId) })
            .build()
    )
    .build()

fun MediaItem.toSong() = Song(
    id = mediaId,
    title = mediaMetadata.title?.toString().orEmpty(),
    artist = mediaMetadata.artist?.toString().orEmpty(),
    album = mediaMetadata.albumTitle?.toString().orEmpty(),
    artworkUrl = mediaMetadata.artworkUri?.toString().orEmpty(),
    durationSec = mediaMetadata.extras?.getLong(EXTRA_DURATION) ?: 0L,
    artistId = mediaMetadata.extras?.getString(EXTRA_ARTIST_ID).orEmpty(),
)

/** UI-side handle to the PlaybackService (via MediaController). Must be used from the main thread. */
class PlayerController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val library: LibraryRepository,
) {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /** Epoch millis when the sleep timer fires, or null. */
    private val _sleepEnd = MutableStateFlow<Long?>(null)
    val sleepEnd: StateFlow<Long?> = _sleepEnd.asStateFlow()

    /** Fires whenever the user starts playback of a list; AppRoot opens the Now Playing screen. */
    private val _openPlayer = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openPlayer: kotlinx.coroutines.flow.SharedFlow<Unit> = _openPlayer

    /** Set by AppContainer: called for every song that starts (feeds the personalised home feed). */
    var onSongStarted: ((Song) -> Unit)? = null

    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private val pending = mutableListOf<(MediaController) -> Unit>()
    private var errorFlag = false
    private var sleepJob: Job? = null

    /** Set by AppContainer: returns songs similar to a song id. Used for endless autoplay. */
    var suggestionProvider: (suspend (String) -> List<Song>)? = null
    private val autoplayDone = mutableSetOf<String>()
    private var autoplayJob: Job? = null

    /** When the queue is about to run out, append similar songs so music keeps playing. */
    private fun maybeAutoplay() {
        val c = controller ?: return
        val provider = suggestionProvider ?: return
        val count = c.mediaItemCount
        if (count == 0 || c.currentMediaItemIndex < count - 2) return
        val last = c.getMediaItemAt(count - 1).mediaId
        if (!autoplayDone.add(last)) return
        autoplayJob?.cancel()
        autoplayJob = scope.launch {
            val more = provider(last)
            val ctl = controller ?: return@launch
            val existing = (0 until ctl.mediaItemCount).map { ctl.getMediaItemAt(it).mediaId }.toSet()
            val fresh = more.filter { it.id !in existing }
            if (fresh.isNotEmpty()) ctl.addMediaItems(fresh.map { it.toMediaItem() })
        }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh(player)

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            errorFlag = false
            maybeAutoplay()
            mediaItem?.let {
                val song = it.toSong()
                scope.launch(Dispatchers.IO) { library.addRecent(song) }
                onSongStarted?.invoke(song)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            errorFlag = true
            controller?.let { refresh(it) }
        }
    }

    init {
        // Position ticker: only touches state while something is playing.
        scope.launch {
            while (true) {
                delay(500)
                val c = controller
                if (c != null && c.isPlaying) _state.value = _state.value.copy(positionMs = c.currentPosition)
            }
        }
    }

    /** Connects to the service (idempotent). */
    fun connect() = withController { }

    private fun withController(block: (MediaController) -> Unit) {
        controller?.let { block(it); return }
        pending += block
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            try {
                val c = f.get()
                controller = c
                c.addListener(listener)
                refresh(c)
                pending.toList().forEach { it(c) }
            } catch (e: Exception) {
                future = null
            } finally {
                pending.clear()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun refresh(p: Player) {
        val cur = p.currentMediaItem
        val md = cur?.mediaMetadata
        if (p.isPlaying) errorFlag = false
        val dur = p.duration.let {
            if (it == C.TIME_UNSET || it < 0) (md?.extras?.getLong(EXTRA_DURATION) ?: 0L) * 1000 else it
        }
        val queue = (0 until p.mediaItemCount).map { i ->
            val m = p.getMediaItemAt(i)
            QueueItem(
                m.mediaId,
                m.mediaMetadata.title?.toString().orEmpty(),
                m.mediaMetadata.artist?.toString().orEmpty(),
                m.mediaMetadata.artworkUri?.toString().orEmpty(),
            )
        }
        _state.value = PlayerState(
            hasMedia = p.mediaItemCount > 0,
            mediaId = cur?.mediaId,
            title = md?.title?.toString().orEmpty(),
            artist = md?.artist?.toString().orEmpty(),
            album = md?.albumTitle?.toString().orEmpty(),
            artworkUrl = md?.artworkUri?.toString().orEmpty(),
            artistId = md?.extras?.getString(EXTRA_ARTIST_ID).orEmpty(),
            isPlaying = p.isPlaying,
            isBuffering = p.playbackState == Player.STATE_BUFFERING,
            positionMs = p.currentPosition,
            durationMs = dur,
            shuffle = p.shuffleModeEnabled,
            repeatMode = p.repeatMode,
            queue = queue,
            currentIndex = p.currentMediaItemIndex,
            error = errorFlag,
        )
    }

    fun play(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        _openPlayer.tryEmit(Unit)
        withController { c ->
            c.shuffleModeEnabled = shuffle
            c.setMediaItems(songs.map { it.toMediaItem() }, startIndex.coerceIn(0, songs.lastIndex), 0L)
            c.prepare()
            c.play()
            maybeAutoplay()
        }
    }

    fun setSpeed(speed: Float) = withController { it.setPlaybackSpeed(speed) }

    fun playNext(song: Song) = withController { c ->
        if (c.mediaItemCount == 0) play(listOf(song)) else c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
    }

    fun addToQueue(song: Song) = withController { c ->
        if (c.mediaItemCount == 0) play(listOf(song)) else c.addMediaItem(song.toMediaItem())
    }

    fun togglePlay() = withController { c ->
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun retry() = withController { c ->
        errorFlag = false
        c.prepare()
        c.play()
    }

    fun seekTo(ms: Long) = withController { it.seekTo(ms) }
    fun next() = withController { it.seekToNext() }
    fun previous() = withController { it.seekToPrevious() }
    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun skipTo(index: Int) = withController { it.seekToDefaultPosition(index); it.play() }
    fun moveQueueItem(from: Int, to: Int) = withController {
        if (from in 0 until it.mediaItemCount && to in 0 until it.mediaItemCount) it.moveMediaItem(from, to)
    }
    fun removeQueueItem(index: Int) = withController {
        if (index in 0 until it.mediaItemCount) it.removeMediaItem(index)
    }

    /** Pauses playback after [minutes]; pass null to cancel. */
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null) {
            _sleepEnd.value = null
            return
        }
        _sleepEnd.value = System.currentTimeMillis() + minutes * 60_000L
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            withController { it.pause() }
            _sleepEnd.value = null
        }
    }
}
