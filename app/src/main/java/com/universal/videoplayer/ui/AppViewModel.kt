package com.universal.videoplayer.ui

import android.app.Application
import android.content.ClipboardManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.universal.videoplayer.App
import com.universal.videoplayer.data.AppSettings
import com.universal.videoplayer.data.HistoryEntity
import com.universal.videoplayer.data.MediaInfo
import com.universal.videoplayer.data.PlaylistEntity
import com.universal.videoplayer.data.PlaylistItemEntity
import com.universal.videoplayer.data.PresetEntity
import com.universal.videoplayer.utils.UrlValidator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as App).repo

    val settings: StateFlow<AppSettings> =
        repo.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val history: StateFlow<List<HistoryEntity>> =
        repo.history.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playlists: StateFlow<List<PlaylistEntity>> =
        repo.playlists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val items: StateFlow<List<PlaylistItemEntity>> =
        repo.playlistItems.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val presets: StateFlow<List<PresetEntity>> =
        repo.presets.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var homeUrl by mutableStateOf("")
    var clipPrompt by mutableStateOf<String?>(null)
    private var lastClip: String? = null

    /** Called once when the window gains focus; never polls the clipboard in the background. */
    fun onWindowFocus(cm: ClipboardManager) {
        val s = settings.value
        if (!s.clipDetect) return
        val text = runCatching {
            cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        }.getOrNull() ?: return
        val url = UrlValidator.normalize(text, requireScheme = true) ?: return
        if (url == lastClip) return
        lastClip = url
        if (s.clipAsk) clipPrompt = url else homeUrl = url
    }

    fun <T> set(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch { repo.settings.set(key, value) }
    }

    fun removeHistory(url: String) = viewModelScope.launch { repo.removeHistory(url) }
    fun clearHistory() = viewModelScope.launch { repo.clearHistory() }

    fun createPlaylist(name: String, thenAdd: MediaInfo? = null) = viewModelScope.launch {
        val id = repo.createPlaylist(name.trim())
        thenAdd?.let { repo.addToPlaylist(id, it) }
    }

    fun renamePlaylist(id: Long, name: String) = viewModelScope.launch { repo.renamePlaylist(id, name.trim()) }
    fun deletePlaylist(id: Long) = viewModelScope.launch { repo.deletePlaylist(id) }
    fun addToPlaylist(id: Long, m: MediaInfo) = viewModelScope.launch { repo.addToPlaylist(id, m) }
    fun removeItem(id: Long) = viewModelScope.launch { repo.removeItem(id) }

    fun move(item: PlaylistItemEntity, up: Boolean) = viewModelScope.launch {
        val list = items.value.filter { it.playlistId == item.playlistId }
        val i = list.indexOfFirst { it.id == item.id }
        val j = if (up) i - 1 else i + 1
        if (i >= 0 && j in list.indices) repo.swapOrder(list[i], list[j])
    }

    fun savePreset(p: PresetEntity) = viewModelScope.launch { repo.savePreset(p) }
    fun deletePreset(id: Long) = viewModelScope.launch { repo.deletePreset(id) }
}
