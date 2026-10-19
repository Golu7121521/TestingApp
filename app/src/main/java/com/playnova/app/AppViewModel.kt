package com.playnova.app

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.playnova.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    private val a = app as PlayNovaApp
    private val dao = a.db.dao()
    val settings = a.settings
    val holder = a.holder

    private val _all = MutableStateFlow<List<Video>>(emptyList())
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading
    private val _scanned = MutableStateFlow(false)
    val scanned: StateFlow<Boolean> = _scanned

    /** Library without the videos the user removed from the library. */
    val videos: StateFlow<List<Video>> = combine(_all, settings.hidden) { v, h -> v.filter { it.id !in h } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val progress: StateFlow<Map<Long, HistoryEntity>> = dao.allProgress().map { l -> l.associateBy { it.videoId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val history: StateFlow<List<HistoryEntity>> = dao.history()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val favoriteIds: StateFlow<List<Long>> = dao.favorites().map { l -> l.map { it.videoId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playlists: StateFlow<List<PlaylistEntity>> = dao.playlists()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playlistItems: StateFlow<List<PlaylistItemEntity>> = dao.allPlaylistItems()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val searches: StateFlow<List<SearchEntity>> = dao.searches()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val folders: StateFlow<List<Folder>> = videos.map { list ->
        list.groupBy { it.folder }.map { (n, l) -> Folder(n, l.size, l.first()) }.sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _all.value = a.repo.scan()
            _loading.value = false
            _scanned.value = true
        }
    }

    fun videoById(id: Long): Video? = _all.value.firstOrNull { it.id == id }

    /** History rows -> playable videos. Deleted library files are skipped. */
    fun historyVideos(h: List<HistoryEntity>): List<Pair<HistoryEntity, Video>> = h.mapNotNull { e ->
        val v = if (e.videoId >= 0) videoById(e.videoId) else MediaRepo.external(Uri.parse(e.uri), e.title)
        if (v == null || v.id in settings.hidden.value) null else e to v
    }

    fun toggleFavorite(v: Video) = viewModelScope.launch(Dispatchers.IO) {
        if (v.id in favoriteIds.value) dao.removeFavorite(v.id)
        else dao.addFavorite(FavoriteEntity(v.id, System.currentTimeMillis()))
    }

    fun favoriteAll(list: List<Video>) = viewModelScope.launch(Dispatchers.IO) {
        list.forEach { dao.addFavorite(FavoriteEntity(it.id, System.currentTimeMillis())) }
    }

    fun createPlaylist(name: String, thenAdd: List<Video> = emptyList()) = viewModelScope.launch(Dispatchers.IO) {
        val id = dao.insertPlaylist(PlaylistEntity(name = name.trim(), createdAt = System.currentTimeMillis()))
        thenAdd.forEach { dao.addToPlaylist(id, it.id) }
    }
    fun renamePlaylist(id: Long, name: String) = viewModelScope.launch(Dispatchers.IO) { dao.renamePlaylist(id, name.trim()) }
    fun deletePlaylist(id: Long) = viewModelScope.launch(Dispatchers.IO) { dao.removePlaylistWithItems(id) }
    fun addToPlaylist(pid: Long, list: List<Video>) = viewModelScope.launch(Dispatchers.IO) { list.forEach { dao.addToPlaylist(pid, it.id) } }
    fun removePlaylistItem(itemId: Long) = viewModelScope.launch(Dispatchers.IO) { dao.deleteItem(itemId) }
    fun movePlaylistItem(pid: Long, from: Int, to: Int) = viewModelScope.launch(Dispatchers.IO) { dao.swapItems(pid, from, to) }

    fun removeHistory(id: Long) = viewModelScope.launch(Dispatchers.IO) { dao.hideFromHistory(id) }
    fun clearHistory() = viewModelScope.launch(Dispatchers.IO) { dao.clearHistory() }
    fun resetPositions() = viewModelScope.launch(Dispatchers.IO) { dao.resetPositions() }

    fun addSearch(q: String) {
        if (q.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) { dao.addSearch(SearchEntity(q.trim(), System.currentTimeMillis())) }
    }
    fun clearSearches() = viewModelScope.launch(Dispatchers.IO) { dao.clearSearches() }

    fun removeFromLibrary(list: List<Video>) = settings.hide(list.map { it.id })

    /** Android 11+: system confirmation dialog for deleting real files. */
    fun deleteRequest(list: List<Video>): IntentSender? {
        val real = list.filter { !it.external }
        if (real.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return MediaStore.createDeleteRequest(app.contentResolver, real.map { it.uri }).intentSender
    }

    /** Android 10 and below: direct delete (user already confirmed in our dialog). */
    fun deleteLegacy(list: List<Video>): Boolean {
        var ok = true
        list.filter { !it.external }.forEach {
            try { if (app.contentResolver.delete(it.uri, null, null) <= 0) ok = false } catch (e: Exception) { ok = false }
        }
        refresh()
        return ok
    }
}
