package com.vibemusic.app.presentation

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vibemusic.app.VibeMusicApp
import com.vibemusic.app.database.ExcludedItemEntity
import com.vibemusic.app.database.RecentSearchEntity
import com.vibemusic.app.domain.model.Album
import com.vibemusic.app.domain.model.AppPreferences
import com.vibemusic.app.domain.model.Artist
import com.vibemusic.app.domain.model.FolderItem
import com.vibemusic.app.domain.model.LyricLine
import com.vibemusic.app.domain.model.Playlist
import com.vibemusic.app.domain.model.RepeatModeOption
import com.vibemusic.app.domain.model.Song
import com.vibemusic.app.domain.model.SortOption
import com.vibemusic.app.domain.model.ThemeModeOption
import com.vibemusic.app.player.HardwareAudioEffectsState
import com.vibemusic.app.player.PlaybackUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VibeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VibeMusicApp
    val repository = app.musicRepository
    val prefsRepo = app.preferencesRepository
    val player = app.playerController

    val preferences: StateFlow<AppPreferences> = prefsRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferences())

    val songs: StateFlow<List<Song>> = repository.songsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val albums: StateFlow<List<Album>> = repository.albumsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val artists: StateFlow<List<Artist>> = repository.artistsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val folders: StateFlow<List<FolderItem>> = repository.foldersFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val genres: StateFlow<Map<String, List<Song>>> = repository.genresFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val playlists: StateFlow<List<Playlist>> = repository.playlistsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recentSearches: StateFlow<List<RecentSearchEntity>> = repository.recentSearchesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val excludedItems: StateFlow<List<ExcludedItemEntity>> = repository.excludedItemsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isScanning: StateFlow<Boolean> = repository.isScanning
    val playbackState: StateFlow<PlaybackUiState> = player.playbackState
    val audioEffectsState: StateFlow<HardwareAudioEffectsState> = player.effectsManager.state

    private val _hasPermission = MutableStateFlow(repository.hasPermission())
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val _currentLyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val currentLyrics: StateFlow<List<LyricLine>> = _currentLyrics.asStateFlow()

    private var loadedLyricsSongId: Long = -1L

    init {
        if (_hasPermission.value) {
            scanLibrary()
        }
        viewModelScope.launch {
            playbackState.collect { state ->
                val song = state.currentSong
                if (song != null && song.id != loadedLyricsSongId) {
                    loadedLyricsSongId = song.id
                    _currentLyrics.value = repository.loadLyricsForSong(song)
                }
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _hasPermission.value = granted || repository.hasPermission()
        if (_hasPermission.value) {
            scanLibrary()
        }
    }

    fun scanLibrary() {
        viewModelScope.launch {
            repository.refreshLibrary()
        }
    }

    fun playSong(song: Song, queue: List<Song> = songs.value) {
        val list = queue.ifEmpty { listOf(song) }
        val index = list.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        player.playSongs(list, index, shuffle = false)
    }

    fun playSongs(list: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        player.playSongs(list, startIndex, shuffle)
    }

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch { repository.toggleFavorite(songId) }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.createPlaylist(name) }
    }

    fun renamePlaylist(id: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch { repository.renamePlaylist(id, newName) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { repository.deletePlaylist(id) }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch { repository.addSongToPlaylist(playlistId, songId) }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch { repository.removeSongFromPlaylist(playlistId, songId) }
    }

    fun movePlaylistSong(playlistId: Long, songsList: List<Song>, fromIndex: Int, toIndex: Int) {
        if (fromIndex !in songsList.indices || toIndex !in songsList.indices) return
        val mutable = songsList.map { it.id }.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        viewModelScope.launch {
            repository.reorderPlaylistSongs(playlistId, mutable)
        }
    }

    fun saveSearchQuery(query: String) {
        viewModelScope.launch { repository.saveSearchQuery(query) }
    }

    fun deleteSearchQuery(query: String) {
        viewModelScope.launch { repository.deleteSearchQuery(query) }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { repository.clearSearchHistory() }
    }

    fun excludeSong(song: Song) {
        viewModelScope.launch {
            repository.excludePathOrSong(song.id.toString(), isFolder = false, label = song.title)
        }
    }

    fun excludeFolder(folderPath: String, label: String) {
        viewModelScope.launch {
            repository.excludePathOrSong(folderPath, isFolder = true, label = label)
        }
    }

    fun removeExclusion(pathOrId: String) {
        viewModelScope.launch { repository.removeExclusion(pathOrId) }
    }

    fun toggleEqualizer(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setEqEnabled(enabled)
            val p = preferences.value
            player.effectsManager.applySettings(
                enabled = enabled,
                presetIndex = p.eqPresetIndex,
                savedBandLevels = p.eqBandLevels,
                bassStrength = p.bassBoostStrength,
                virtualizerStrength = p.virtualizerStrength
            )
        }
    }

    fun selectEqPreset(index: Int) {
        val updatedLevels = player.effectsManager.selectPreset(index)
        viewModelScope.launch {
            prefsRepo.setEqPresetAndBands(index, updatedLevels)
        }
    }

    fun setEqBandLevel(bandIndex: Int, levelMillibels: Int) {
        val updatedLevels = player.effectsManager.setBandLevel(bandIndex, levelMillibels)
        val customIdx = (audioEffectsState.value.presets.size - 1).coerceAtLeast(0)
        viewModelScope.launch {
            prefsRepo.setEqPresetAndBands(customIdx, updatedLevels)
        }
    }

    fun setBassBoost(strength: Int) {
        viewModelScope.launch {
            prefsRepo.setBassBoostStrength(strength)
            val p = preferences.value
            player.effectsManager.applySettings(
                enabled = p.eqEnabled,
                presetIndex = p.eqPresetIndex,
                savedBandLevels = p.eqBandLevels,
                bassStrength = strength,
                virtualizerStrength = p.virtualizerStrength
            )
        }
    }

    fun setVirtualizer(strength: Int) {
        viewModelScope.launch {
            prefsRepo.setVirtualizerStrength(strength)
            val p = preferences.value
            player.effectsManager.applySettings(
                enabled = p.eqEnabled,
                presetIndex = p.eqPresetIndex,
                savedBandLevels = p.eqBandLevels,
                bassStrength = p.bassBoostStrength,
                virtualizerStrength = strength
            )
        }
    }

    fun setThemeMode(mode: ThemeModeOption) {
        viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    }

    fun setAccentColor(hex: Long) {
        viewModelScope.launch { prefsRepo.setAccentColor(hex) }
    }

    fun setSortOption(option: SortOption) {
        viewModelScope.launch { prefsRepo.setSortOption(option) }
    }

    fun setGaplessPlayback(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setGaplessPlayback(enabled) }
    }

    fun setResumePlayback(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setResumePlayback(enabled) }
    }

    fun setAutoPlay(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoPlay(enabled) }
    }

    fun setCrossfadeSeconds(seconds: Int) {
        viewModelScope.launch { prefsRepo.setCrossfadeSeconds(seconds) }
    }

    fun setDefaultRepeatMode(mode: RepeatModeOption) {
        viewModelScope.launch { prefsRepo.setDefaultRepeatMode(mode) }
    }

    fun setAlbumArtAnimation(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAlbumArtAnimation(enabled) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setKeepScreenOn(enabled) }
    }

    fun exportBackup(uri: Uri, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repository.exportBackupJson(uri)
            onComplete(ok)
        }
    }

    fun importBackup(uri: Uri, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repository.importBackupJson(uri)
            onComplete(ok)
        }
    }
}
