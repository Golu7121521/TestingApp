package com.vibemusic.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vibemusic.app.domain.model.Playlist
import com.vibemusic.app.domain.model.Song
import com.vibemusic.app.presentation.VibeViewModel
import com.vibemusic.app.ui.components.AlbumArtwork
import com.vibemusic.app.ui.components.SongItemCard

@Composable
fun SearchScreen(
    viewModel: VibeViewModel,
    onSongMoreClick: (Song) -> Unit,
    onSelectPlaylist: (Playlist) -> Unit
) {
    val songs by viewModel.songs.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    var query by remember { mutableStateOf("") }
    val trimmed = query.trim()

    val matchingSongs = remember(trimmed, songs) {
        if (trimmed.isEmpty()) emptyList()
        else songs.filter {
            it.title.contains(trimmed, ignoreCase = true) ||
                it.artist.contains(trimmed, ignoreCase = true) ||
                it.album.contains(trimmed, ignoreCase = true)
        }
    }
    val matchingArtists = remember(trimmed, artists) {
        if (trimmed.isEmpty()) emptyList()
        else artists.filter { it.name.contains(trimmed, ignoreCase = true) }
    }
    val matchingAlbums = remember(trimmed, albums) {
        if (trimmed.isEmpty()) emptyList()
        else albums.filter {
            it.name.contains(trimmed, ignoreCase = true) ||
                it.artist.contains(trimmed, ignoreCase = true)
        }
    }
    val matchingPlaylists = remember(trimmed, playlists) {
        if (trimmed.isEmpty()) emptyList()
        else playlists.filter { it.name.contains(trimmed, ignoreCase = true) }
    }
    val matchingFolders = remember(trimmed, folders) {
        if (trimmed.isEmpty()) emptyList()
        else folders.filter { it.name.contains(trimmed, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Search",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Songs, artists, albums, playlists, folders...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear Search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (trimmed.isEmpty()) {
            if (recentSearches.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Searches",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.clearSearchHistory() }) {
                        Text("Clear All")
                    }
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 140.dp)) {
                    items(recentSearches, key = { it.query }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { query = item.query }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = item.query,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.deleteSearchQuery(item.query) }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove")
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "Type above to instantly filter your local music collection.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
        } else {
            val totalResults = matchingSongs.size + matchingArtists.size +
                matchingAlbums.size + matchingPlaylists.size + matchingFolders.size

            if (totalResults == 0) {
                Text(
                    text = "No results found for \"$trimmed\"",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (matchingSongs.isNotEmpty()) {
                        item {
                            Text(
                                "Songs (${matchingSongs.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(matchingSongs, key = { "s_${it.id}" }) { song ->
                            SongItemCard(
                                song = song,
                                isPlayingThis = playbackState.currentSong?.id == song.id,
                                onClick = {
                                    viewModel.saveSearchQuery(trimmed)
                                    viewModel.playSong(song, matchingSongs)
                                },
                                onMoreClick = { onSongMoreClick(song) }
                            )
                        }
                    }

                    if (matchingArtists.isNotEmpty()) {
                        item {
                            Text(
                                "Artists (${matchingArtists.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(matchingArtists, key = { "ar_${it.name}" }) { artist ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        viewModel.saveSearchQuery(trimmed)
                                        viewModel.playSongs(artist.songs, 0)
                                    },
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AlbumArtwork(
                                        uri = artist.artworkUri,
                                        contentDescription = artist.name,
                                        modifier = Modifier.size(44.dp),
                                        cornerRadius = 22.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(artist.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${artist.songCount} songs",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (matchingAlbums.isNotEmpty()) {
                        item {
                            Text(
                                "Albums (${matchingAlbums.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(matchingAlbums, key = { "al_${it.id}" }) { album ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        viewModel.saveSearchQuery(trimmed)
                                        viewModel.playSongs(album.songs, 0)
                                    },
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AlbumArtwork(
                                        uri = album.albumArtUri,
                                        contentDescription = album.name,
                                        modifier = Modifier.size(44.dp),
                                        cornerRadius = 10.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(album.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${album.artist} • ${album.songCount} songs",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (matchingPlaylists.isNotEmpty()) {
                        item {
                            Text(
                                "Playlists (${matchingPlaylists.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(matchingPlaylists, key = { "pl_${it.id}" }) { pl ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        viewModel.saveSearchQuery(trimmed)
                                        onSelectPlaylist(pl)
                                    },
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(pl.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    Text("${pl.songCount} tracks", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    if (matchingFolders.isNotEmpty()) {
                        item {
                            Text(
                                "Folders (${matchingFolders.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(matchingFolders, key = { "f_${it.path}" }) { folder ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        viewModel.saveSearchQuery(trimmed)
                                        viewModel.playSongs(folder.songs, 0)
                                    },
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(folder.name, style = MaterialTheme.typography.titleMedium)
                                        Text("${folder.songCount} songs", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
