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

@Composable
fun HomeScreen(c: AppContainer, onOpen: (String, String) -> Unit) {
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
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp),
                )
            }
            when (val s = state) {
                is UiState.Loading -> {
                    items(3) {
                        Column {
                            Spacer(Modifier.height(24.dp))
                            ShimmerRow()
                        }
                    }
                }
                is UiState.Error -> item { ErrorView(onRetry = { vm.load() }) }
                is UiState.Success -> {
                    val data = s.data
                    if (data.trending.isNotEmpty()) {
                        item { SectionTitle(R.string.trending) }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                itemsIndexed(data.trending) { i, song ->
                                    MediaCard(song.title, song.artist, song.artworkUrl) { c.player.play(data.trending, i) }
                                }
                            }
                        }
                    }
                    if (data.newReleases.isNotEmpty()) {
                        item { SectionTitle(R.string.new_releases) }
                        item { CollectionRowList(data.newReleases, onOpen) }
                    }
                    if (recents.isNotEmpty()) {
                        item { SectionTitle(R.string.recently_played) }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                itemsIndexed(recents) { i, song ->
                                    MediaCard(song.title, song.artist, song.artworkUrl) { c.player.play(recents, i) }
                                }
                            }
                        }
                    }
                    if (data.playlists.isNotEmpty()) {
                        item { SectionTitle(R.string.top_playlists) }
                        item { CollectionRowList(data.playlists, onOpen) }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun CollectionRowList(items: List<MediaCollection>, onOpen: (String, String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items.size) { i ->
            val item = items[i]
            MediaCard(item.title, item.subtitle, item.artworkUrl) { onOpen(item.type, item.id) }
        }
    }
}
