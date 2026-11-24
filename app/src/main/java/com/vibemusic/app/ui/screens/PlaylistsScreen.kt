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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vibemusic.app.domain.model.Playlist
import com.vibemusic.app.domain.model.Song
import com.vibemusic.app.presentation.VibeViewModel
import com.vibemusic.app.ui.components.AlbumArtwork
import com.vibemusic.app.ui.components.SongItemCard

@Composable
fun PlaylistsScreen(
    viewModel: VibeViewModel,
    initialSelectedPlaylistId: Long? = null,
    onClearInitialSelection: () -> Unit = {},
    onSongMoreClick: (Song) -> Unit
) {
    val playlists by viewModel.playlists.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    var selectedPlaylistId by remember(initialSelectedPlaylistId) {
        mutableStateOf(initialSelectedPlaylistId)
    }
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var newPlaylistName by remember { mutableStateOf("") }

    val activePlaylist = playlists.find { it.id == selectedPlaylistId }

    if (activePlaylist != null) {
        PlaylistDetailScreen(
            playlist = activePlaylist,
            currentSongId = playbackState.currentSong?.id,
            onBack = {
                selectedPlaylistId = null
                onClearInitialSelection()
            },
            onPlayAll = { viewModel.playSongs(activePlaylist.songs, 0, shuffle = false) },
            onShuffleAll = { viewModel.playSongs(activePlaylist.songs, 0, shuffle = true) },
            onSongClick = { song -> viewModel.playSong(song, activePlaylist.songs) },
            onSongMoreClick = onSongMoreClick,
            onRemoveSong = { song -> viewModel.removeSongFromPlaylist(activePlaylist.id, song.id) },
            onMoveSong = { from, to ->
                viewModel.movePlaylistSong(activePlaylist.id, activePlaylist.songs, from, to)
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Playlists",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    newPlaylistName = ""
                    showCreateDialog = true
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Playlist")
            }
        }

        val smartPlaylists = playlists.filter { it.isSmart }
        val customPlaylists = playlists.filter { !it.isSmart }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = "Smart Playlists",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            items(smartPlaylists, key = { it.id }) { pl ->
                PlaylistCardItem(
                    playlist = pl,
                    onClick = { selectedPlaylistId = pl.id },
                    onPlay = { viewModel.playSongs(pl.songs, 0) },
                    onRename = null,
                    onDelete = null
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Your Playlists (${customPlaylists.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
            }

            if (customPlaylists.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = "Tap 'New Playlist' above to organize your favorite tracks.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            } else {
                items(customPlaylists, key = { it.id }) { pl ->
                    PlaylistCardItem(
                        playlist = pl,
                        onClick = { selectedPlaylistId = pl.id },
                        onPlay = { viewModel.playSongs(pl.songs, 0) },
                        onRename = {
                            newPlaylistName = pl.name
                            renameTarget = pl
                        },
                        onDelete = { viewModel.deletePlaylist(pl.id) }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("Playlist name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createPlaylist(newPlaylistName)
                        showCreateDialog = false
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renamePlaylist(target.id, newPlaylistName)
                        renameTarget = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PlaylistCardItem(
    playlist: Playlist,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArtwork(
                uri = playlist.artworkUri,
                contentDescription = playlist.name,
                modifier = Modifier.size(54.dp),
                cornerRadius = 12.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.songCount} tracks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (playlist.songs.isNotEmpty()) {
                IconButton(onClick = onPlay) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play Playlist")
                }
            }
            if (onRename != null) {
                IconButton(onClick = onRename) {
                    Icon(Icons.Default.Edit, contentDescription = "Rename")
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetailScreen(
    playlist: Playlist,
    currentSongId: Long?,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onSongClick: (Song) -> Unit,
    onSongMoreClick: (Song) -> Unit,
    onRemoveSong: (Song) -> Unit,
    onMoveSong: (Int, Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(playlist.name, style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "${playlist.songCount} songs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (playlist.songs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onPlayAll, shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play All")
                        }
                        FilledTonalButton(onClick = onShuffleAll, shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Default.Shuffle, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Shuffle")
                        }
                    }
                }
            }
        }

        if (playlist.songs.isEmpty()) {
            item {
                Text(
                    text = "This playlist is empty. Tap the three dots on any song to add it here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            itemsIndexed(playlist.songs, key = { idx, s -> "${playlist.id}_${s.id}_$idx" }) { index, song ->
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    SongItemCard(
                        song = song,
                        isPlayingThis = currentSongId == song.id,
                        onClick = { onSongClick(song) },
                        onMoreClick = { onSongMoreClick(song) }
                    )
                    if (!playlist.isSmart) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (index > 0) {
                                IconButton(onClick = { onMoveSong(index, index - 1) }) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up")
                                }
                            }
                            if (index < playlist.songs.lastIndex) {
                                IconButton(onClick = { onMoveSong(index, index + 1) }) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down")
                                }
                            }
                            IconButton(onClick = { onRemoveSong(song) }) {
                                Icon(
                                    Icons.Default.RemoveCircleOutline,
                                    contentDescription = "Remove from Playlist",
                                    tint = Color(0xFFEF4444)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
