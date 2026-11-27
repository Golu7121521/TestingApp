package com.mytune.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mytune.app.R
import com.mytune.app.data.api.HomeFeed
import com.mytune.app.data.api.TasteProfile
import com.mytune.app.di.AppContainer
import com.mytune.app.domain.*
import com.mytune.app.ui.UiState
import com.mytune.app.ui.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val feed = HomeFeed(c.repository)

    private val _music = MutableStateFlow<UiState<HomeData>>(UiState.Loading)
    val music = _music.asStateFlow()
    private val _podcasts = MutableStateFlow<UiState<HomeData>>(UiState.Loading)
    val podcasts = _podcasts.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    private var playsAtBuild = -1L
    private var builtAt = 0L
    private var podcastsLoaded = false

    init { loadMusic() }

    /** [silent] keeps the current feed on screen while the new one loads (no spinner). */
    fun loadMusic(refresh: Boolean = false, silent: Boolean = false) {
        viewModelScope.launch {
            if (refresh) _refreshing.value = true else if (!silent) _music.value = UiState.Loading
            val prefs = c.settings.prefsNow()
            val taste = TasteProfile(
                topArtists = c.settings.topArtists(5),
                lastSong = c.library.recents.first().firstOrNull(),
            )
            feed.music(prefs, taste).fold(
                onSuccess = {
                    _music.value = UiState.Success(it)
                    playsAtBuild = c.settings.playCountNow
                    builtAt = System.currentTimeMillis()
                },
                onFailure = { if (_music.value !is UiState.Success) _music.value = UiState.Error(it.message) },
            )
            _refreshing.value = false
        }
    }

    fun loadPodcasts(refresh: Boolean = false) {
        viewModelScope.launch {
            if (refresh) _refreshing.value = true else _podcasts.value = UiState.Loading
            feed.podcasts(c.settings.prefsNow()).fold(
                onSuccess = { _podcasts.value = UiState.Success(it); podcastsLoaded = true },
                onFailure = { if (_podcasts.value !is UiState.Success) _podcasts.value = UiState.Error(it.message) },
            )
            _refreshing.value = false
        }
    }

    fun ensurePodcasts() { if (!podcastsLoaded) loadPodcasts() }

    /** The feed follows the listening: rebuild after a few new plays, or when it is getting old. */
    fun refreshIfNeeded() {
        if (_music.value !is UiState.Success) return
        val newPlays = c.settings.playCountNow - playsAtBuild
        val age = System.currentTimeMillis() - builtAt
        if (newPlays >= 3 || age > 20 * 60_000L) loadMusic(silent = true)
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Good night"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(c: AppContainer, onOpen: (String, String) -> Unit, onSearch: () -> Unit) {
    val vm: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(c) } })
    val music by vm.music.collectAsState()
    val podcasts by vm.podcasts.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val recents by c.library.recents.collectAsState(initial = emptyList())
    val prefs by c.settings.prefs.collectAsState(initial = UserPrefs())
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 = Music, 1 = Podcasts
    val openArtist = LocalOpenArtist.current

    // Coming back to Home (and every 10 minutes) the feed refreshes itself if the listening has changed.
    LaunchedEffect(Unit) {
        vm.refreshIfNeeded()
        while (true) { delay(10 * 60_000L); vm.refreshIfNeeded() }
    }
    LaunchedEffect(mode) { if (mode == 1) vm.ensurePodcasts() }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { if (mode == 0) vm.loadMusic(refresh = true) else vm.loadPodcasts(refresh = true) },
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary))),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        Text(greeting(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier.height(30.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        listOf("Music", "Podcasts").forEachIndexed { i, label ->
                            val sel = mode == i
                            Box(
                                Modifier.fillMaxHeight().clip(RoundedCornerShape(50))
                                    .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { mode = i }.padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    IconButton(onClick = onSearch) { Icon(Icons.Default.Search, stringResource(R.string.nav_search)) }
                }
            }

            val state = if (mode == 0) music else podcasts
            when (val s = state) {
                is UiState.Loading -> items(4) { Column { Spacer(Modifier.height(24.dp)); ShimmerRow() } }
                is UiState.Error -> item {
                    if (mode == 1) {
                        Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Podcasts could not be loaded right now.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { vm.loadPodcasts() }) { Text(stringResource(R.string.retry)) }
                        }
                    } else ErrorView(onRetry = { vm.loadMusic() })
                }
                is UiState.Success -> {
                    val sections = s.data.sections
                    var personalAdded = false
                    fun personalRows(scope: androidx.compose.foundation.lazy.LazyListScope) {
                        if (mode != 0 || personalAdded) return
                        personalAdded = true
                        if (recents.isNotEmpty()) scope.item(key = "last_played") {
                            Column {
                                FeedHeader(stringResource(R.string.last_played), "Pick up where you left off")
                                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    itemsIndexed(recents.take(15)) { i, song -> CompactSongCard(song.title, song.artist, song.artworkUrl) { c.player.play(recents, i) } }
                                }
                            }
                        }
                        if (prefs.artists.isNotEmpty()) scope.item(key = "your_artists") {
                            Column {
                                FeedHeader("Your Artists")
                                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    itemsIndexed(prefs.artists) { _, a -> ArtistBubble(a.name, a.image) { openArtist(a.id, a.name) } }
                                }
                            }
                        }
                    }
                    if (sections.isEmpty()) item { EmptyText(R.string.empty_list) }
                    if (mode == 0 && sections.firstOrNull()?.style != SectionStyle.HERO) personalRows(this)
                    sections.forEachIndexed { index, section ->
                        item(key = "sec_${mode}_${index}_${section.title}") { FeedSection(c, section, onOpen) }
                        if (section.style == SectionStyle.HERO) personalRows(this)
                    }
                    if (mode == 0 && !personalAdded) personalRows(this)
                }
            }
            item { Spacer(Modifier.height(LocalBottomInset.current + 16.dp)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedSection(c: AppContainer, section: HomeSection, onOpen: (String, String) -> Unit) {
    val openArtist = LocalOpenArtist.current
    val gap = Arrangement.spacedBy(12.dp)
    val pad = PaddingValues(horizontal = 16.dp)
    Column {
        FeedHeader(
            section.title, section.subtitle,
            onPlayAll = if (section.songs.isNotEmpty() && section.style != SectionStyle.DEFAULT) ({ c.player.play(section.songs, 0) }) else null,
        )
        when {
            section.songs.isNotEmpty() -> when (section.style) {
                SectionStyle.HERO -> {
                    val st = rememberLazyListState()
                    LazyRow(state = st, flingBehavior = rememberSnapFlingBehavior(st), contentPadding = pad, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        itemsIndexed(section.songs) { i, s -> HeroSongCard(s.title, s.artist, s.artworkUrl) { c.player.play(section.songs, i) } }
                    }
                }
                SectionStyle.RANKED -> LazyRow(contentPadding = pad, horizontalArrangement = gap) {
                    itemsIndexed(section.songs) { i, s -> RankedSongCard(i + 1, s.title, s.artist, s.artworkUrl) { c.player.play(section.songs, i) } }
                }
                else -> LazyRow(contentPadding = pad, horizontalArrangement = gap) {
                    itemsIndexed(section.songs) { i, s -> MediaCard(s.title, s.artist, s.artworkUrl) { c.player.play(section.songs, i) } }
                }
            }
            else -> LazyRow(contentPadding = pad, horizontalArrangement = gap) {
                itemsIndexed(section.collections) { _, item ->
                    val click = { if (item.type == CollectionType.ARTIST) openArtist(item.id, item.title) else onOpen(item.type, item.id) }
                    when {
                        section.circle -> ArtistBubble(item.title, item.artworkUrl, size = 120, onClick = click)
                        section.style == SectionStyle.TILES -> PlaylistTile(item.title, item.subtitle, item.artworkUrl, click)
                        else -> MediaCard(item.title, item.subtitle, item.artworkUrl, onClick = click)
                    }
                }
            }
        }
    }
}
