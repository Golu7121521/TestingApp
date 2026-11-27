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
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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

private val TRENDING_SEARCHES = listOf("Arijit Singh", "Punjabi hits", "Love songs", "Diljit Dosanjh", "Lofi chill", "Bollywood party", "Sidhu Moose Wala", "Devotional")

private data class Browse(val title: String, val query: String, val color: Color)

private val BROWSE = listOf(
    Browse("Top Charts", "top charts", Color(0xFF15803D)),
    Browse("New Releases", "new releases", Color(0xFF0F766E)),
    Browse("Podcasts", "podcast", Color(0xFFB45309)),
    Browse("Radio", "radio", Color(0xFF1D4ED8)),
    Browse("Hindi", "hindi hits", Color(0xFFBE123C)),
    Browse("English", "english hits", Color(0xFF475569)),
    Browse("Punjabi", "punjabi hits", Color(0xFF9A3412)),
    Browse("Tamil", "tamil hits", Color(0xFF0E7490)),
    Browse("Telugu", "telugu hits", Color(0xFF4D7C0F)),
    Browse("Romance", "romantic songs", Color(0xFFBE185D)),
    Browse("Party", "party songs", Color(0xFFC2410C)),
    Browse("Chill", "chill songs", Color(0xFF0369A1)),
    Browse("Workout", "workout songs", Color(0xFF166534)),
    Browse("Devotional", "devotional songs", Color(0xFFA16207)),
)

@Composable
fun SearchScreen(c: AppContainer, onOpen: (String, String) -> Unit) {
    val vm: SearchViewModel = viewModel(factory = viewModelFactory { initializer { SearchViewModel(c.repository, c.library) } })
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val history by vm.history.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(R.string.tab_songs, R.string.tab_albums, R.string.tab_playlists, R.string.tab_artists)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            stringResource(R.string.nav_search),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
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
                // No query yet: history, trending and browse categories (JioSaavn style)
                LazyColumn(Modifier.fillMaxSize()) {
                    if (history.isNotEmpty()) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.search_history), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                TextButton(onClick = { vm.clearHistory() }) { Text(stringResource(R.string.clear)) }
                            }
                        }
                        items(history.take(5).size) { i ->
                            Row(
                                Modifier.fillMaxWidth().clickable { vm.query.value = history[i] }.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(12.dp))
                                Text(history[i])
                            }
                        }
                    }
                    item {
                        Column {
                            SectionHeader("Trending Searches")
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(TRENDING_SEARCHES.size) { i ->
                                    AssistChip(onClick = { vm.query.value = TRENDING_SEARCHES[i] }, label = { Text(TRENDING_SEARCHES[i]) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.TrendingUp, null, Modifier.size(18.dp)) })
                                }
                            }
                        }
                    }
                    item { SectionHeader("Browse All") }
                    items(BROWSE.chunked(2).size) { row ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BROWSE.chunked(2)[row].forEach { b ->
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(72.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Brush.linearGradient(listOf(b.color, b.color.copy(alpha = 0.6f))))
                                        .clickable { vm.query.value = b.query }
                                        .padding(12.dp),
                                ) {
                                    Text(b.title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(LocalBottomInset.current + 16.dp)) }
                }
            }
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is UiState.Error -> ErrorView(onRetry = { vm.retry() })
            is UiState.Success -> {
                val data = r.data
                val pager = rememberPagerState(initialPage = tab, pageCount = { tabs.size })
                val scope = rememberCoroutineScope()
                LaunchedEffect(pager.currentPage) { tab = pager.currentPage }
                ScrollableTabRow(selectedTabIndex = pager.currentPage, edgePadding = 8.dp) {
                    tabs.forEachIndexed { i, res ->
                        Tab(selected = pager.currentPage == i, onClick = { scope.launch { pager.animateScrollToPage(i) } }, text = { Text(stringResource(res)) })
                    }
                }
                SongActionsHost(c) { actions ->
                    // Swipe left / right between Songs, Albums, Playlists and Artists (pages slide with the finger)
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                        LazyColumn(Modifier.fillMaxSize()) {
                            when (page) {
                                0 -> if (data.songs.isEmpty()) item { EmptyText(R.string.no_results) } else songItems(data.songs, actions, c)
                                1 -> collectionItems(data.albums, onOpen)
                                2 -> collectionItems(data.playlists, onOpen)
                                else -> collectionItems(data.artists, onOpen, artists = true)
                            }
                            item { Spacer(Modifier.height(LocalBottomInset.current + 16.dp)) }
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.collectionItems(list: List<MediaCollection>, onOpen: (String, String) -> Unit, artists: Boolean = false) {
    if (list.isEmpty()) {
        item { EmptyText(R.string.no_results) }
    } else {
        items(list.size) { i ->
            val item = list[i]
            if (artists) {
                val openArtist = LocalOpenArtist.current
                CollectionRow(item) { openArtist(item.id, item.title) }
            } else CollectionRow(item) { onOpen(item.type, item.id) }
        }
    }
}
