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
    private val repo: MusicRepository,
    private val library: com.mytune.app.data.repository.LibraryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<UiState<MediaCollection>>(UiState.Loading)
    val state = _state.asStateFlow()

    init { load() }

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
                    else -> repo.artist(id)
                }
                _state.value = result.fold({ UiState.Success(it) }, { UiState.Error(it.message) })
            }
        }
    }

    fun removeFromPlaylist(songId: String) {
        val pid = id.toLongOrNull() ?: return
        viewModelScope.launch { library.removeFromPlaylist(pid, songId) }
    }
}

@Composable
fun DetailScreen(c: AppContainer, type: String, id: String, onBack: () -> Unit) {
    val vm: DetailViewModel = viewModel(
        key = "detail_${type}_$id",
        factory = viewModelFactory { initializer { DetailViewModel(type, id, c.repository, c.library) } },
    )
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val title = (state as? UiState.Success)?.data?.title.orEmpty()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            // Collapsing toolbar: the large title shrinks as the list scrolls.
            LargeTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { pad ->
        when (val s = state) {
            is UiState.Loading -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is UiState.Error -> Box(Modifier.fillMaxSize().padding(pad)) { ErrorView(onRetry = { vm.load() }) }
            is UiState.Success -> {
                val col = s.data
                val removeAction: ((Song) -> Unit)? =
                    if (col.type == CollectionType.USER) ({ song: Song -> vm.removeFromPlaylist(song.id) }) else null
                SongActionsHost(c) { actions ->
                    LazyColumn(Modifier.fillMaxSize().padding(pad)) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Artwork(
                                    col.artworkUrl,
                                    Modifier.size(220.dp),
                                    if (col.type == CollectionType.ARTIST) CircleShape else RoundedCornerShape(16.dp),
                                )
                                if (col.subtitle.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(col.subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { c.player.play(col.songs, 0) }, enabled = col.songs.isNotEmpty()) {
                                        Icon(Icons.Default.PlayArrow, null)
                                        Text(stringResource(R.string.play))
                                    }
                                    OutlinedButton(
                                        onClick = { c.player.play(col.songs, col.songs.indices.random(), shuffle = true) },
                                        enabled = col.songs.isNotEmpty(),
                                    ) {
                                        Icon(Icons.Default.Shuffle, null)
                                        Text(stringResource(R.string.shuffle))
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        scope.launch { col.songs.forEach { c.downloads.enqueue(it, c.settings.downloadQualityNow) } }
                                        Toast.makeText(ctx, ctx.getString(R.string.download_started), Toast.LENGTH_SHORT).show()
                                    },
                                    enabled = col.songs.isNotEmpty(),
                                ) {
                                    Icon(Icons.Default.Download, null)
                                    Text(stringResource(R.string.download_all))
                                }
                            }
                        }
                        if (col.songs.isEmpty()) item { EmptyText(R.string.empty_list) }
                        songItems(
                            col.songs, actions, c,
                            onRemove = removeAction,
                        )
                    }
                }
            }
        }
    }
}
