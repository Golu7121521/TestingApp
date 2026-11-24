package com.vibemusic.app.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.vibemusic.app.data.preferences.PreferencesRepository
import com.vibemusic.app.data.repository.MusicRepository
import com.vibemusic.app.domain.model.RepeatModeOption
import com.vibemusic.app.domain.model.Song
import com.vibemusic.app.service.VibePlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class PlaybackUiState(
    val currentSong: Song? = null,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatModeOption = RepeatModeOption.OFF,
    val playbackSpeed: Float = 1.0f,
    val sleepTimerRemainingMs: Long = 0L
)

class VibePlayerController(
    private val context: Context,
    private val repository: MusicRepository,
    private val prefsRepo: PreferencesRepository,
    val effectsManager: AudioEffectsManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val _playbackState = MutableStateFlow(PlaybackUiState())
    val playbackState: StateFlow<PlaybackUiState> = _playbackState.asStateFlow()

    private var positionJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var lastRecordedSongId: Long = -1L

    fun initialize() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, VibePlaybackService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching {
                val ctrl = future.get()
                controller = ctrl
                setupPlayerListener(ctrl)
                restorePreferencesAndState(ctrl)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun onAudioSessionIdReady(audioSessionId: Int) {
        scope.launch {
            val prefs = prefsRepo.preferencesFlow.first()
            effectsManager.attachToSession(
                audioSessionId = audioSessionId,
                enabled = prefs.eqEnabled,
                presetIndex = prefs.eqPresetIndex,
                savedBandLevels = prefs.eqBandLevels,
                bassStrength = prefs.bassBoostStrength,
                virtualizerStrength = prefs.virtualizerStrength
            )
        }
    }

    private fun setupPlayerListener(ctrl: MediaController) {
        ctrl.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
                if (isPlaying) {
                    startPositionTicker()
                } else {
                    stopPositionTicker()
                    persistCurrentPosition()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateCurrentSongFromController(ctrl)
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _playbackState.value = _playbackState.value.copy(isShuffleEnabled = shuffleModeEnabled)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                val mapped = when (repeatMode) {
                    Player.REPEAT_MODE_ALL -> RepeatModeOption.ALL
                    Player.REPEAT_MODE_ONE -> RepeatModeOption.ONE
                    else -> RepeatModeOption.OFF
                }
                _playbackState.value = _playbackState.value.copy(repeatMode = mapped)
            }
        })
    }

    private fun restorePreferencesAndState(ctrl: MediaController) {
        scope.launch {
            val prefs = prefsRepo.preferencesFlow.first()
            ctrl.playbackParameters = PlaybackParameters(prefs.playbackSpeed)
            val repeatInt = when (prefs.defaultRepeatMode) {
                RepeatModeOption.OFF -> Player.REPEAT_MODE_OFF
                RepeatModeOption.ALL -> Player.REPEAT_MODE_ALL
                RepeatModeOption.ONE -> Player.REPEAT_MODE_ONE
            }
            ctrl.repeatMode = repeatInt
            _playbackState.value = _playbackState.value.copy(
                playbackSpeed = prefs.playbackSpeed,
                repeatMode = prefs.defaultRepeatMode
            )

            val allSongs = repository.songsFlow.first()
            if (prefs.resumePlayback && prefs.lastPlayedSongId != -1L && allSongs.isNotEmpty()) {
                val idx = allSongs.indexOfFirst { it.id == prefs.lastPlayedSongId }
                if (idx >= 0) {
                    val items = allSongs.map { it.toMediaItem() }
                    ctrl.setMediaItems(items, idx, prefs.lastPlayedPositionMs)
                    ctrl.prepare()
                    _playbackState.value = _playbackState.value.copy(
                        queue = allSongs,
                        currentIndex = idx,
                        currentSong = allSongs[idx],
                        currentPositionMs = prefs.lastPlayedPositionMs,
                        durationMs = allSongs[idx].duration
                    )
                    if (prefs.autoPlay) {
                        ctrl.play()
                    }
                }
            } else if (prefs.autoPlay && allSongs.isNotEmpty()) {
                playSongs(allSongs, 0)
            }
        }
    }

    private fun updateCurrentSongFromController(ctrl: MediaController) {
        val queue = _playbackState.value.queue
        val idx = ctrl.currentMediaItemIndex
        val song = queue.getOrNull(idx)
        val duration = ctrl.duration.takeIf { it > 0 } ?: (song?.duration ?: 0L)
        _playbackState.value = _playbackState.value.copy(
            currentIndex = idx,
            currentSong = song,
            durationMs = duration,
            currentPositionMs = ctrl.currentPosition.coerceAtLeast(0L)
        )
        if (song != null && song.id != lastRecordedSongId) {
            lastRecordedSongId = song.id
            scope.launch {
                repository.recordSongPlayed(song.id)
                prefsRepo.saveLastPlaybackState(song.id, 0L)
            }
        }
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        val ctrl = controller ?: return
        val safeIndex = startIndex.coerceIn(0, songs.lastIndex)
        val items = songs.map { it.toMediaItem() }

        ctrl.shuffleModeEnabled = shuffle
        ctrl.setMediaItems(items, safeIndex, 0L)
        ctrl.prepare()
        ctrl.play()

        val selected = songs[safeIndex]
        _playbackState.value = _playbackState.value.copy(
            queue = songs,
            currentIndex = safeIndex,
            currentSong = selected,
            isPlaying = true,
            isShuffleEnabled = shuffle,
            currentPositionMs = 0L,
            durationMs = selected.duration
        )

        if (selected.id != lastRecordedSongId) {
            lastRecordedSongId = selected.id
            scope.launch {
                repository.recordSongPlayed(selected.id)
                prefsRepo.saveLastPlaybackState(selected.id, 0L)
            }
        }
    }

    fun playNextInQueue(song: Song) {
        val ctrl = controller ?: return
        val currentQueue = _playbackState.value.queue.toMutableList()
        if (currentQueue.isEmpty()) {
            playSongs(listOf(song), 0)
            return
        }
        val insertIndex = (ctrl.currentMediaItemIndex + 1).coerceIn(0, currentQueue.size)
        currentQueue.add(insertIndex, song)
        ctrl.addMediaItem(insertIndex, song.toMediaItem())
        _playbackState.value = _playbackState.value.copy(queue = currentQueue)
    }

    fun addToQueue(song: Song) {
        val ctrl = controller ?: return
        val currentQueue = _playbackState.value.queue.toMutableList()
        if (currentQueue.isEmpty()) {
            playSongs(listOf(song), 0)
            return
        }
        currentQueue.add(song)
        ctrl.addMediaItem(song.toMediaItem())
        _playbackState.value = _playbackState.value.copy(queue = currentQueue)
    }

    fun removeFromQueue(index: Int) {
        val ctrl = controller ?: return
        val currentQueue = _playbackState.value.queue.toMutableList()
        if (index !in currentQueue.indices) return
        currentQueue.removeAt(index)
        ctrl.removeMediaItem(index)
        _playbackState.value = _playbackState.value.copy(
            queue = currentQueue,
            currentIndex = ctrl.currentMediaItemIndex,
            currentSong = currentQueue.getOrNull(ctrl.currentMediaItemIndex)
        )
    }

    fun jumpToQueueIndex(index: Int) {
        val ctrl = controller ?: return
        if (index in _playbackState.value.queue.indices) {
            ctrl.seekToDefaultPosition(index)
            ctrl.play()
        }
    }

    fun togglePlayPause() {
        val ctrl = controller ?: return
        if (ctrl.isPlaying) {
            ctrl.pause()
        } else {
            if (ctrl.mediaItemCount == 0 && _playbackState.value.queue.isNotEmpty()) {
                playSongs(_playbackState.value.queue, 0)
            } else {
                ctrl.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val ctrl = controller ?: return
        ctrl.seekTo(positionMs.coerceAtLeast(0L))
        _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
    }

    fun seekForward(deltaMs: Long = 10_000L) {
        val ctrl = controller ?: return
        val target = (ctrl.currentPosition + deltaMs).coerceAtMost(ctrl.duration.coerceAtLeast(0L))
        seekTo(target)
    }

    fun seekBackward(deltaMs: Long = 10_000L) {
        val ctrl = controller ?: return
        val target = (ctrl.currentPosition - deltaMs).coerceAtLeast(0L)
        seekTo(target)
    }

    fun skipToNext() {
        val ctrl = controller ?: return
        if (ctrl.hasNextMediaItem()) {
            ctrl.seekToNextMediaItem()
            ctrl.play()
        }
    }

    fun skipToPrevious() {
        val ctrl = controller ?: return
        if (ctrl.currentPosition > 3000L) {
            ctrl.seekTo(0L)
        } else if (ctrl.hasPreviousMediaItem()) {
            ctrl.seekToPreviousMediaItem()
            ctrl.play()
        }
    }

    fun toggleShuffle() {
        val ctrl = controller ?: return
        ctrl.shuffleModeEnabled = !ctrl.shuffleModeEnabled
    }

    fun cycleRepeatMode() {
        val ctrl = controller ?: return
        val next = when (ctrl.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        ctrl.repeatMode = next
    }

    fun setPlaybackSpeed(speed: Float) {
        val ctrl = controller ?: return
        val clamped = speed.coerceIn(0.5f, 2.5f)
        ctrl.playbackParameters = PlaybackParameters(clamped)
        _playbackState.value = _playbackState.value.copy(playbackSpeed = clamped)
        scope.launch { prefsRepo.setPlaybackSpeed(clamped) }
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _playbackState.value = _playbackState.value.copy(sleepTimerRemainingMs = 0L)
            return
        }
        val totalMs = minutes * 60_000L
        _playbackState.value = _playbackState.value.copy(sleepTimerRemainingMs = totalMs)
        sleepTimerJob = scope.launch {
            var remaining = totalMs
            while (remaining > 0L) {
                delay(1000L)
                remaining -= 1000L
                _playbackState.value = _playbackState.value.copy(
                    sleepTimerRemainingMs = remaining.coerceAtLeast(0L)
                )
            }
            controller?.pause()
            _playbackState.value = _playbackState.value.copy(sleepTimerRemainingMs = 0L)
        }
    }

    private fun startPositionTicker() {
        positionJob?.cancel()
        positionJob = scope.launch {
            var tickCount = 0
            while (true) {
                val ctrl = controller
                if (ctrl != null && ctrl.isPlaying) {
                    val pos = ctrl.currentPosition.coerceAtLeast(0L)
                    val dur = ctrl.duration.takeIf { it > 0 } ?: _playbackState.value.durationMs
                    _playbackState.value = _playbackState.value.copy(
                        currentPositionMs = pos,
                        durationMs = dur
                    )
                    tickCount++
                    if (tickCount % 10 == 0) {
                        persistCurrentPosition()
                    }
                }
                delay(500L)
            }
        }
    }

    private fun stopPositionTicker() {
        positionJob?.cancel()
    }

    private fun persistCurrentPosition() {
        val song = _playbackState.value.currentSong ?: return
        val pos = _playbackState.value.currentPositionMs
        scope.launch {
            prefsRepo.saveLastPlaybackState(song.id, pos)
        }
    }

    private fun Song.toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(albumArtUri)
            .setIsPlayable(true)
            .build()

        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    fun release() {
        stopPositionTicker()
        sleepTimerJob?.cancel()
        effectsManager.release()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
