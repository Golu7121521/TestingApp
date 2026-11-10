package com.playvid.presentation.playlist

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.playvid.domain.model.Playlist
import com.playvid.domain.repository.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The Playlist tab: the list of saved playlists (add from URL / file, edit, delete). */
class PlaylistsViewModel(
    private val app: Application,
    private val repo: PlaylistRepository
) : AndroidViewModel(app) {

    val playlists: StateFlow<List<Playlist>> =
        repo.observePlaylists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun dismissMessage() { _message.value = null }

    fun addUrl(name: String, url: String) {
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://", true) && !cleanUrl.startsWith("https://", true)) {
            _message.value = "Enter a valid http(s) playlist URL."
            return
        }
        viewModelScope.launch {
            repo.add(name.trim().ifBlank { nameFromUrl(cleanUrl) }, cleanUrl, false)
        }
    }

    /** Copies the picked .m3u into app storage so the playlist keeps working after the picker's access ends. */
    fun addFile(name: String, uri: Uri) {
        viewModelScope.launch {
            val text = try {
                withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }
            } catch (e: Exception) {
                null
            }
            if (text.isNullOrBlank()) {
                _message.value = "Couldn't read that file."
                return@launch
            }
            val file = withContext(Dispatchers.IO) {
                File(app.filesDir, "playlist_${System.currentTimeMillis()}.m3u").also { it.writeText(text) }
            }
            repo.add(name.trim().ifBlank { "Playlist" }, file.absolutePath, true)
        }
    }

    fun update(playlist: Playlist, name: String, url: String) {
        viewModelScope.launch {
            val newSource = if (playlist.isFile) playlist.source else url.trim().ifBlank { playlist.source }
            repo.update(playlist.copy(name = name.trim().ifBlank { playlist.name }, source = newSource))
        }
    }

    fun delete(playlist: Playlist) {
        viewModelScope.launch {
            repo.delete(playlist.id)
            if (playlist.isFile) withContext(Dispatchers.IO) { File(playlist.source).delete() }
        }
    }

    private fun nameFromUrl(url: String): String =
        url.substringBefore('?').trimEnd('/').substringAfterLast('/').ifBlank { url }
}
