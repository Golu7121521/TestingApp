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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
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
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SearchResults(
    val songs: List<Song>,
    val albums: List<MediaCollection>,
    val playlists: List<MediaCollection>,
    val artists: List<MediaCollection>,
)

class SearchViewModel(private val repo: MusicRepository, private val library: com.mytune.app.data.repository.LibraryRepository) : ViewModel() {
    val query = MutableStateFlow("")
    val results = MutableStateFlow<UiState<SearchResults>?>(null)
    val history = library.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            // 400 ms debounce, as requested; collectLatest cancels stale searches.
            query.debounce(400).distinctUntilChanged().collectLatest { q ->
                if (q.isBlank()) results.value = null else runSearch(q)
            }
        }
    }

    private suspend fun runSearch(q: String) {
        results.value = UiState.Loading
        kotlinx.coroutines.coroutineScope {
            val s = async { repo.searchSongs(q) }
            val a = async { repo.searchAlbums(q) }
            val p = async { repo.searchPlaylists(q) }
            val r = async { repo.searchArtists(q) }
            val sr = s.await(); val ar = a.await(); val pr = p.await(); val rr = r.await()
            if (sr.isFailure && ar.isFailure && pr.isFailure && rr.isFailure) {
                results.value = UiState.Error(sr.exceptionOrNull()?.message)
            } else {
                results.value = UiState.Success(
                    SearchResults(
                        sr.getOrDefault(emptyList()), ar.getOrDefault(emptyList()),
                        pr.getOrDefault(emptyList()), rr.getOrDefault(emptyList()),
                    )
                )
                library.addHistory(q)
            }
        }
    }

    fun retry() { viewModelScope.launch { runSearch(query.value) } }
    fun clearHistory() { viewModelScope.launch { library.clearHistory() } }
}

@Composable
fun SearchScreen(c: AppContainer, onOpen: (String, String) -> Unit) {
    val vm: SearchViewModel = viewModel(factory = viewModelFactory { initializer { SearchViewModel(c.repository, c.library) } })
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val history by vm.history.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(R.string.tab_songs, R.string.tab_albums, R.string.tab_playlists, R.string.tab_artists)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        OutlinedTextField(
            value = query,
            onValueChange = { vm.query.value = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Default.Close, stringResource(R.string.clear)) }
                }
            },
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )

        when (val r = results) {
            null -> {
                // No query yet: show local search history
                if (history.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.search_history), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.clearHistory() }) { Text(stringResource(R.string.clear)) }
                    }
                    LazyColumn {
                        items(history.size) { i ->
                            Row(
                                Modifier.fillMaxWidth().clickable { vm.query.value = history[i] }.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(12.dp))
                                Text(history[i])
                            }
                        }
                    }
                }
            }
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is UiState.Error -> ErrorView(onRetry = { vm.retry() })
            is UiState.Success -> {
                val data = r.data
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                    tabs.forEachIndexed { i, res -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(res)) }) }
                }
                SongActionsHost(c) { actions ->
                    LazyColumn(Modifier.fillMaxSize()) {
                        when (tab) {
                            0 -> if (data.songs.isEmpty()) item { EmptyText(R.string.no_results) } else songItems(data.songs, actions, c)
                            1 -> collectionItems(data.albums, onOpen)
                            2 -> collectionItems(data.playlists, onOpen)
                            else -> collectionItems(data.artists, onOpen)
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.collectionItems(list: List<MediaCollection>, onOpen: (String, String) -> Unit) {
    if (list.isEmpty()) {
        item { EmptyText(R.string.no_results) }
    } else {
        items(list.size) { i ->
            val item = list[i]
            CollectionRow(item) { onOpen(item.type, item.id) }
        }
    }
}
