package com.playvid.presentation.playlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.SubcomposeAsyncImage
import com.playvid.domain.model.Channel
import com.playvid.domain.model.PlaybackRequest
import com.playvid.presentation.app
import com.playvid.presentation.components.EmptyState

/**
 * Channels of one playlist as a 3-column grid (logo + name). Top bar: back, refresh, favourites
 * filter (star), search. Tap plays a channel; long-press stars / un-stars it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelsScreen(
    playlistId: Long,
    onBack: () -> Unit,
    onPlay: (PlaybackRequest) -> Unit
) {
    val vm: ChannelsViewModel = viewModel(
        key = "channels-$playlistId",
        factory = viewModelFactory {
            initializer {
                val container = app().container
                ChannelsViewModel(app(), container.playlistRepository, container.httpClient, playlistId)
            }
        }
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val visible by vm.visible.collectAsStateWithLifecycle()
    val favorites by vm.favoriteUrls.collectAsStateWithLifecycle()
    val favoritesOnly by vm.favoritesOnly.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize()) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            if (searching) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search channels") },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp)
                )
                IconButton(onClick = { vm.query.value = ""; vm.searching.value = false }) {
                    Icon(Icons.Filled.Close, contentDescription = "Close search")
                }
            } else {
                Text(
                    "Channels",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
                IconButton(onClick = { vm.favoritesOnly.value = !favoritesOnly }) {
                    Icon(
                        if (favoritesOnly) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = "Favourites",
                        tint = if (favoritesOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = { vm.searching.value = true }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
            }
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    state.error.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(Modifier.padding(top = 16.dp)) { Button(onClick = vm::refresh) { Text("Retry") } }
            }
            visible.isEmpty() -> EmptyState(
                if (favoritesOnly) "No favourites yet" else "No matching channels",
                if (favoritesOnly) "Long-press a channel to add it to favourites." else "Try a different search."
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(visible, key = { index, ch -> "$index|${ch.url}" }) { _, channel ->
                    ChannelTile(
                        channel = channel,
                        favorite = channel.url in favorites,
                        onClick = { onPlay(channel.toRequest()) },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.toggleFavorite(channel)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelTile(channel: Channel, favorite: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(14.dp), contentAlignment = Alignment.Center) {
            val initial = remember(channel.name) { channel.name.trim().take(1).uppercase() }
            if (channel.logo == null) {
                LogoPlaceholder(initial)
            } else {
                SubcomposeAsyncImage(
                    model = channel.logo,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    loading = { LogoPlaceholder(initial) },
                    error = { LogoPlaceholder(initial) }
                )
            }
            if (favorite) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Favourite",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).size(18.dp)
                )
            }
        }
        Text(
            channel.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun LogoPlaceholder(initial: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            initial,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
