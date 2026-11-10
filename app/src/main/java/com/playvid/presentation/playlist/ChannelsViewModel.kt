package com.playvid.presentation.playlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.playvid.domain.model.Channel
import com.playvid.domain.repository.PlaylistRepository
import com.playvid.playback.M3uParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File

data class ChannelsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val channels: List<Channel> = emptyList()
)

/** The channel grid for one playlist: load/refresh, search, favorites. */
class ChannelsViewModel(
    app: Application,
    private val repo: PlaylistRepository,
    private val httpClient: OkHttpClient,
    private val playlistId: Long
) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(ChannelsUiState())
    val state: StateFlow<ChannelsUiState> = _state

    val query = MutableStateFlow("")
    val favoritesOnly = MutableStateFlow(false)
    val searching = MutableStateFlow(false)

    private val favorites: StateFlow<Set<String>> =
        repo.observeFavorites().stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val favoriteUrls: StateFlow<Set<String>> = favorites

    /** What the grid shows: the loaded channels filtered by the search text and the favorites toggle. */
    val visible: StateFlow<List<Channel>> =
        combine(_state, query, favoritesOnly, favorites) { s, q, favOnly, favs ->
            s.channels.filter { ch ->
                (!favOnly || ch.url in favs) && (q.isBlank() || ch.name.contains(q.trim(), ignoreCase = true))
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init { load() }

    fun refresh() = load()

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch { repo.setFavorite(channel.url, channel.url !in favorites.value) }
    }

    private sealed interface Fetched {
        data class Text(val value: String) : Fetched
        object NotText : Fetched
        object Failed : Fetched
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = ChannelsUiState(loading = true)
            val playlist = repo.get(playlistId)
            if (playlist == null) {
                _state.value = ChannelsUiState(loading = false, error = "This playlist no longer exists.")
                return@launch
            }
            val fetched = withContext(Dispatchers.IO) {
                if (playlist.isFile) {
                    try { Fetched.Text(File(playlist.source).readText()) } catch (e: Exception) { Fetched.Failed }
                } else {
                    fetch(playlist.source)
                }
            }
            val single = listOf(Channel(name = playlist.name, url = playlist.source))
            val channels: List<Channel>? = when (fetched) {
                Fetched.Failed -> null
                // A direct video/stream link rather than a channel list: play it as one channel.
                Fetched.NotText -> single
                is Fetched.Text ->
                    if (M3uParser.looksLikeHlsManifest(fetched.value)) single
                    else M3uParser.parseChannels(fetched.value)
            }
            _state.value = when {
                channels == null -> ChannelsUiState(
                    loading = false,
                    error = "Couldn't load this playlist. Check the link and your connection."
                )
                channels.isEmpty() -> ChannelsUiState(loading = false, error = "No channels found in this playlist.")
                else -> ChannelsUiState(loading = false, channels = channels)
            }
        }
    }

    /** Reads a remote playlist as text, capped so a video link can't be pulled down in full. */
    private fun fetch(url: String): Fetched = try {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@use Fetched.Failed
            // A .m3u/.m3u8 URL is always treated as text even if the server mislabels it (GitHub
            // Pages and plenty of IPTV hosts serve these as "audio/x-mpegurl", "video/...", etc.
            // — trust the extension, since a real playlist link is never binary).
            val looksLikePlaylistFile = url.substringBefore('?').lowercase().let { it.endsWith(".m3u") || it.endsWith(".m3u8") }
            if (!looksLikePlaylistFile) {
                val type = resp.header("Content-Type").orEmpty().lowercase()
                if (type.startsWith("video/") || type.startsWith("audio/")) return@use Fetched.NotText
            }
            val body = resp.body ?: return@use Fetched.Failed
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            val limit = 16 * 1024 * 1024
            body.byteStream().use { input ->
                while (out.size() < limit) {
                    val n = input.read(buffer)
                    if (n == -1) break
                    out.write(buffer, 0, n)
                }
            }
            Fetched.Text(out.toString(Charsets.UTF_8.name()))
        }
    } catch (e: Exception) {
        Fetched.Failed
    }
}
