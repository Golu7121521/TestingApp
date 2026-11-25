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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.scale
import com.mytune.app.R
import com.mytune.app.di.AppContainer
import com.mytune.app.domain.*
import com.mytune.app.ui.UiState
import com.mytune.app.ui.components.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(private val repo: MusicRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<HomeData>>(UiState.Loading)
    val state = _state.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    init { load() }

    fun load(refresh: Boolean = false) {
        viewModelScope.launch {
            if (refresh) _refreshing.value = true else _state.value = UiState.Loading
            repo.home().fold(
                onSuccess = { _state.value = UiState.Success(it) },
                onFailure = { if (_state.value !is UiState.Success) _state.value = UiState.Error(it.message) },
            )
            _refreshing.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(c: AppContainer, onOpen: (String, String) -> Unit, onSearch: () -> Unit) {
    val vm: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(c.repository) } })
    val state by vm.state.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val recents by c.library.recents.collectAsState(initial = emptyList())

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { vm.load(refresh = true) },
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    // App icon (launcher foreground on the launcher background colour)
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(colorResource(R.color.ic_launcher_background))) {
                        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize().scale(1.6f))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onSearch) { Icon(Icons.Default.Search, stringResource(R.string.nav_search)) }
                }
            }
            when (val s = state) {
                is UiState.Loading -> {
                    items(4) {
                        Column {
                            Spacer(Modifier.height(24.dp))
                            ShimmerRow()
                        }
                    }
                }
                is UiState.Error -> item { ErrorView(onRetry = { vm.load() }) }
                is UiState.Success -> {
                    s.data.sections.forEachIndexed { index, section ->
                        item(key = "sec_${index}_${section.title}") {
                            Column {
                                SectionHeader(section.title)
                                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (section.songs.isNotEmpty()) {
                                        itemsIndexed(section.songs) { i, song ->
                                            MediaCard(song.title, song.artist, song.artworkUrl) { c.player.play(section.songs, i) }
                                        }
                                    } else {
                                        items(section.collections.size) { i ->
                                            val item = section.collections[i]
                                            MediaCard(item.title, item.subtitle, item.artworkUrl, circle = section.circle) { onOpen(item.type, item.id) }
                                        }
                                    }
                                }
                            }
                        }
                        // Personal row right after the first two sections
                        if (index == 1 && recents.isNotEmpty()) {
                            item(key = "recents") {
                                Column {
                                    SectionHeader(stringResource(R.string.recently_played))
                                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        itemsIndexed(recents) { i, song ->
                                            MediaCard(song.title, song.artist, song.artworkUrl) { c.player.play(recents, i) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(LocalBottomInset.current + 16.dp)) }
        }
    }
}
