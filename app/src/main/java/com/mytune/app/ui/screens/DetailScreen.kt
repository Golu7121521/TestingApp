package com.mytune.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.mytune.app.R
import com.mytune.app.di.AppContainer
import com.mytune.app.domain.*
import com.mytune.app.ui.UiState
import com.mytune.app.ui.components.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DetailViewModel(
    private val type: String,
    private val id: String,
    private val name: String,
    private val repo: MusicRepository,
    private val library: com.mytune.app.data.repository.LibraryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<UiState<MediaCollection>>(UiState.Loading)
    val state = _state.asStateFlow()

    /** "More like this": songs similar to the first track, plus artists related to it. */
    val relatedSongs = MutableStateFlow<List<Song>>(emptyList())
    val relatedArtists = MutableStateFlow<List<MediaCollection>>(emptyList())

    init { load() }

    val albums = MutableStateFlow<List<MediaCollection>>(emptyList())

    private fun loadRelated(col: MediaCollection) {
        if (col.type == CollectionType.ARTIST) {
            // Artist page: albums of the artist and similar artists
            albums.value = col.albums
            if (col.similar.isNotEmpty()) relatedArtists.value = col.similar
            val nm = name.ifBlank { col.title }
            viewModelScope.launch {
                if (col.albums.isEmpty() && nm.isNotBlank()) repo.searchAlbums(nm, 12).getOrNull()?.let { albums.value = it }
                if (col.similar.isEmpty()) {
                    val first = col.songs.firstOrNull() ?: return@launch
                    repo.suggestions(first.id, 25).getOrNull()?.let { l ->
                        relatedArtists.value = l.mapNotNull { sg -> sg.artist.split(",").firstOrNull()?.trim()?.takeIf { it.isNotBlank() && !it.equals(nm, true) } }
                            .distinct().take(8).mapNotNull { a -> repo.searchArtists(a, 1).getOrNull()?.firstOrNull() }
                            .filter { it.id != col.id }.distinctBy { it.id }
                    }
                }
            }
            return
        }
        val first = col.songs.firstOrNull() ?: return
        viewModelScope.launch {
            val inList = col.songs.map { it.id }.toSet()
            repo.suggestions(first.id, 25).getOrNull()?.let { l -> relatedSongs.value = l.filter { it.id !in inList } }
        }
        val name = first.artist.split(",").firstOrNull()?.trim().orEmpty()
        if (name.isNotBlank()) viewModelScope.launch {
            repo.searchArtists(name, 12).getOrNull()?.let { l -> relatedArtists.value = l.filter { it.id != col.id } }
        }
    }

    /** Adds the whole collection to the local library as a playlist (or removes it again). */
    fun toggleLibrary(col: MediaCollection, saved: com.mytune.app.data.db.PlaylistInfo?) {
        viewModelScope.launch {
            if (saved != null) {
                library.deletePlaylist(saved.id)
            } else {
                val pid = library.createPlaylist(col.title)
                col.songs.forEach { library.addToPlaylist(pid, it) }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            if (type == CollectionType.USER) {
                // User playlists are local: observe the DB instead of calling the API.
                val pid = id.toLongOrNull() ?: return@launch
                combine(library.playlists, library.playlistSongs(pid)) { lists, songs ->
                    MediaCollection(id, type, lists.firstOrNull { it.id == pid }?.name.orEmpty(), "", songs.firstOrNull()?.artworkUrl.orEmpty(), songs)
                }.collect { _state.value = UiState.Success(it) }
            } else {
                _state.value = UiState.Loading
                val result = when (type) {
                    CollectionType.ALBUM -> repo.album(id)
                    CollectionType.PLAYLIST -> repo.playlist(id)
                    else -> repo.artist(id, name)
                }
                _state.value = result.fold({ UiState.Success(it) }, { UiState.Error(it.message) })
                result.getOrNull()?.let { loadRelated(it) }
            }
        }
    }

    fun removeFromPlaylist(songId: String) {
        val pid = id.toLongOrNull() ?: return
        viewModelScope.launch { library.removeFromPlaylist(pid, songId) }
    }
}

@Composable
fun DetailScreen(c: AppContainer, type: String, id: String, name: String = "", onBack: () -> Unit, onOpen: (String, String) -> Unit) {
    val vm: DetailViewModel = viewModel(
        key = "detail_${type}_$id",
        factory = viewModelFactory { initializer { DetailViewModel(type, id, name, c.repository, c.library) } },
    )
    val state by vm.state.collectAsState()
    val related by vm.relatedSongs.collectAsState()
    val relatedArtists by vm.relatedArtists.collectAsState()
    val artistAlbums by vm.albums.collectAsState()
    val openArtist = LocalOpenArtist.current
    val myPlaylists by c.library.playlists.collectAsState(initial = emptyList<com.mytune.app.data.db.PlaylistInfo>())
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is UiState.Error -> Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 48.dp)) { ErrorView(onRetry = { vm.load() }) }
            is UiState.Success -> {
                val col = s.data
                val saved = myPlaylists.firstOrNull { it.name == col.title }
                val removeAction: ((Song) -> Unit)? =
                    if (col.type == CollectionType.USER) ({ song: Song -> vm.removeFromPlaylist(song.id) }) else null
                SongActionsHost(c) { actions ->
                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            Column(
                                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 32.dp, end = 32.dp, top = 56.dp, bottom = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                // 1. Thumbnail
                                Artwork(
                                    col.artworkUrl,
                                    Modifier.fillMaxWidth().aspectRatio(1f),
                                    if (col.type == CollectionType.ARTIST) CircleShape else RoundedCornerShape(16.dp),
                                )
                                // 2. Name
                                Spacer(Modifier.height(16.dp))
                                Text(col.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (col.subtitle.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(col.subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.height(16.dp))
                                // 3. Actions: add to library, download, shuffle, play
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (col.type != CollectionType.USER) {
                                        FilledTonalButton(onClick = {
                                            vm.toggleLibrary(col, saved)
                                            Toast.makeText(ctx, if (saved != null) "Removed from library" else "Added to library", Toast.LENGTH_SHORT).show()
                                        }, enabled = col.songs.isNotEmpty()) {
                                            Icon(if (saved != null) Icons.Default.Check else Icons.Default.Add, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(if (saved != null) "In Library" else "Add to Library")
                                        }
                                    }
                                    IconButton(onClick = {
                                        scope.launch { col.songs.forEach { c.downloads.enqueue(it, c.settings.downloadQualityNow) } }
                                        Toast.makeText(ctx, ctx.getString(R.string.download_started), Toast.LENGTH_SHORT).show()
                                    }, enabled = col.songs.isNotEmpty()) { Icon(Icons.Default.Download, stringResource(R.string.download_all)) }
                                    IconButton(
                                        onClick = { c.player.play(col.songs, col.songs.indices.random(), shuffle = true) },
                                        enabled = col.songs.isNotEmpty(),
                                    ) { Icon(Icons.Default.Shuffle, stringResource(R.string.shuffle)) }
                                    FilledIconButton(
                                        onClick = { c.player.play(col.songs, 0) },
                                        enabled = col.songs.isNotEmpty(),
                                        modifier = Modifier.size(56.dp),
                                    ) { Icon(Icons.Default.PlayArrow, stringResource(R.string.play), Modifier.size(32.dp)) }
                                }
                            }
                        }
                        if (col.songs.isEmpty()) item { EmptyText(R.string.empty_list) }
                        if (col.type == CollectionType.ARTIST && col.songs.isNotEmpty()) item { SectionHeader("Top Songs") }
                        songItems(col.songs, actions, c, onRemove = removeAction)

                        if (col.type == CollectionType.ARTIST && artistAlbums.isNotEmpty()) {
                            item {
                                Column {
                                    SectionHeader("Albums")
                                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        items(artistAlbums.size) { i ->
                                            val a = artistAlbums[i]
                                            MediaCard(a.title, a.subtitle, a.artworkUrl) { onOpen(a.type, a.id) }
                                        }
                                    }
                                }
                            }
                        }

                        // 4. Related artists
                        if (relatedArtists.isNotEmpty()) {
                            item {
                                Column {
                                    SectionHeader(if (col.type == CollectionType.ARTIST) "Similar Artists" else "Related Artists")
                                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        items(relatedArtists.size) { i ->
                                            val a = relatedArtists[i]
                                            MediaCard(a.title, a.subtitle, a.artworkUrl, circle = true) { openArtist(a.id, a.title) }
                                        }
                                    }
                                }
                            }
                        }
                        // 5. More songs like this (also what keeps playing after the list ends)
                        if (related.isNotEmpty()) {
                            item { SectionHeader("More Like This") }
                            songItems(related, actions, c)
                        }
                        item { Spacer(Modifier.height(LocalBottomInset.current + 16.dp)) }
                    }
                }
            }
        }
        // Back button floating over the thumbnail
        IconButton(
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(8.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), CircleShape),
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
    }
}
