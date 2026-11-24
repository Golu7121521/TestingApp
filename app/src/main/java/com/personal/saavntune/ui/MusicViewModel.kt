package com.personal.saavntune.ui

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.personal.saavntune.data.model.Song
import com.personal.saavntune.data.repository.MusicRepository
import com.personal.saavntune.player.PlaybackService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MusicUiState(
    val searchQuery: String = "",
    val onlineSongs: List<Song> = emptyList(),
    val downloadedSongs: List<Song> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val toastMessage: String? = null,
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val selectedTab: Int = 0
)

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MusicRepository(application)

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    private var mediaController: MediaController? = null
    private var currentQueue: List<Song> = emptyList()
    private var currentIndex: Int = -1

    init {
        connectToPlaybackService()
        searchSongs("Top Hindi Hits")
        refreshDownloads()
        startProgressUpdater()
    }

    private fun connectToPlaybackService() {
        val context = getApplication<Application>()
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                mediaController = controller
                controller.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            playNext()
                        }
                        _uiState.value = _uiState.value.copy(
                            durationMs = controller.duration.coerceAtLeast(0L)
                        )
                    }
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun startProgressUpdater() {
        viewModelScope.launch {
            while (true) {
                mediaController?.let { controller ->
                    if (controller.isPlaying) {
                        _uiState.value = _uiState.value.copy(
                            currentPositionMs = controller.currentPosition.coerceAtLeast(0L),
                            durationMs = controller.duration.coerceAtLeast(0L)
                        )
                    }
                }
                delay(500L)
            }
        }
    }

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index)
        if (index == 1) {
            refreshDownloads()
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun searchSongs(customQuery: String? = null) {
        val q = (customQuery ?: _uiState.value.searchQuery).trim()
        if (q.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = repository.searchSongs(q)
            result.fold(
                onSuccess = { list ->
                    _uiState.value = _uiState.value.copy(
                        onlineSongs = list,
                        isLoading = false,
                        errorMessage = if (list.isEmpty()) "No songs found for "$q"" else null
                    )
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Network error: ${err.localizedMessage ?: "Unable to fetch songs"}"
                    )
                }
            )
        }
    }

    fun playSong(song: Song, queue: List<Song>) {
        viewModelScope.launch {
            val playableUrl = repository.resolvePlayableUrl(song)
            if (playableUrl.isNullOrBlank()) {
                showToast("Stream URL unavailable for this track")
                return@launch
            }

            currentQueue = queue
            currentIndex = queue.indexOfFirst { it.id == song.id }

            val metadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(playableUrl)
                .setMediaId(song.id)
                .setMediaMetadata(metadata)
                .build()

            mediaController?.apply {
                setMediaItem(mediaItem)
                prepare()
                play()
            }

            _uiState.value = _uiState.value.copy(
                currentSong = song,
                isPlaying = true,
                currentPositionMs = 0L
            )
        }
    }

    fun togglePlayPause() {
        mediaController?.let { controller ->
            if (controller.isPlaying) {
                controller.pause()
            } else {
                controller.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _uiState.value = _uiState.value.copy(currentPositionMs = positionMs)
    }

    fun playNext() {
        if (currentQueue.isEmpty()) return
        val nextIndex = if (currentIndex + 1 < currentQueue.size) currentIndex + 1 else 0
        playSong(currentQueue[nextIndex], currentQueue)
    }

    fun playPrevious() {
        if (currentQueue.isEmpty()) return
        val prevIndex = if (currentIndex - 1 >= 0) currentIndex - 1 else currentQueue.lastIndex
        playSong(currentQueue[prevIndex], currentQueue)
    }

    fun downloadSong(song: Song) {
        viewModelScope.launch {
            val result = repository.enqueueDownload(song)
            result.fold(
                onSuccess = { msg ->
                    showToast(msg)
                    delay(1500L)
                    refreshDownloads()
                },
                onFailure = { err ->
                    showToast("Download failed: ${err.localizedMessage ?: "Unknown error"}")
                }
            )
        }
    }

    fun refreshDownloads() {
        viewModelScope.launch {
            val list = repository.getDownloadedSongs()
            _uiState.value = _uiState.value.copy(downloadedSongs = list)
        }
    }

    fun deleteDownload(song: Song) {
        viewModelScope.launch {
            val deleted = repository.deleteDownloadedSong(song)
            if (deleted) {
                showToast("Deleted ${song.title}")
                refreshDownloads()
            } else {
                showToast("Unable to delete file")
            }
        }
    }

    fun showToast(message: String) {
        _uiState.value = _uiState.value.copy(toastMessage = message)
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    override fun onCleared() {
        mediaController?.release()
        super.onCleared()
    }
}
