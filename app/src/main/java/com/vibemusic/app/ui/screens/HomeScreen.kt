package com.vibemusic.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vibemusic.app.domain.model.Album
import com.vibemusic.app.domain.model.Artist
import com.vibemusic.app.domain.model.Playlist
import com.vibemusic.app.domain.model.Song
import com.vibemusic.app.presentation.VibeViewModel
import com.vibemusic.app.ui.components.AlbumArtwork
import com.vibemusic.app.ui.components.SongItemCard

@Composable
fun HomeScreen(
    viewModel: VibeViewModel,
    onNavigateSearch: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateEqualizer: () -> Unit,
    onRequestPermission: () -> Unit,
    onSongMoreClick: (Song) -> Unit,
    onSelectPlaylist: (Playlist) -> Unit,
    onSelectAlbum: (Album) -> Unit,
    onSelectArtist: (Artist) -> Unit
) {
    val hasPermission by viewModel.hasPermission.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    val recentlyPlayed = songs.filter { it.lastPlayed > 0L }.sortedByDescending { it.lastPlayed }.take(12)
    val recentlyAdded = songs.sortedByDescending { it.dateAdded }.take(12)
    val mostPlayed = songs.filter { it.playCount > 0 }.sortedByDescending { it.playCount }.take(12)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        // Top Header with VIBE MUSIC Branding & Greeting
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "VIBE MUSIC Logo",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = "VIBE MUSIC",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(onClick = onNavigateEqualizer) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Equalizer",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        IconButton(onClick = onNavigateSearch) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        IconButton(onClick = onNavigateSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Good Music, Good Mood",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (songs.isNotEmpty()) "${songs.size} local tracks ready offline" else "Your offline hi-fi audio sanctuary",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (songs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { viewModel.playSongs(songs, 0, shuffle = false) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Play All")
                            }
                            FilledTonalButton(
                                onClick = { viewModel.playSongs(songs, 0, shuffle = true) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Shuffle, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Shuffle")
                            }
                        }
                    }
                }
            }
        }

        // Permission or Empty State Handling
        if (!hasPermission) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Audio Permission Required",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Allow VIBE MUSIC to access local audio files stored on your device. No internet connection or account is ever needed.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onRequestPermission) {
                            Text("Grant Storage Permission")
                        }
                    }
                }
            }
        } else if (isScanning && songs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Scanning local audio library...")
                    }
                }
            }
        } else if (songs.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No Local Music Found", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Place MP3, FLAC, WAV, M4A, AAC, OGG, or OPUS files on your device and tap Rescan.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(onClick = { viewModel.scanLibrary() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Rescan Device")
                        }
                    }
                }
            }
        }

        // Recently Played Horizontal Section
        if (recentlyPlayed.isNotEmpty()) {
            item {
                HomeSectionHeader("Recently Played")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recentlyPlayed, key = { "rp_${it.id}" }) { song ->
                        HorizontalSquareCard(
                            title = song.title,
                            subtitle = song.artist,
                            artUri = song.albumArtUri,
                            onClick = { viewModel.playSong(song, recentlyPlayed) }
                        )
                    }
                }
            }
        }

        // Recently Added Horizontal Section
        if (recentlyAdded.isNotEmpty()) {
            item {
                HomeSectionHeader("Recently Added")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recentlyAdded, key = { "ra_${it.id}" }) { song ->
                        HorizontalSquareCard(
                            title = song.title,
                            subtitle = song.artist,
                            artUri = song.albumArtUri,
                            onClick = { viewModel.playSong(song, recentlyAdded) }
                        )
                    }
                }
            }
        }

        // Most Played Horizontal Section
        if (mostPlayed.isNotEmpty()) {
            item {
                HomeSectionHeader("Most Played")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(mostPlayed, key = { "mp_${it.id}" }) { song ->
                        HorizontalSquareCard(
                            title = song.title,
                            subtitle = "${song.playCount} plays • ${song.artist}",
                            artUri = song.albumArtUri,
                            onClick = { viewModel.playSong(song, mostPlayed) }
                        )
                    }
                }
            }
        }

        // Your Playlists Horizontal Section
        if (playlists.isNotEmpty()) {
            item {
                HomeSectionHeader("Your Playlists")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(playlists, key = { "pl_${it.id}" }) { pl ->
                        HorizontalSquareCard(
                            title = pl.name,
                            subtitle = "${pl.songCount} tracks",
                            artUri = pl.artworkUri,
                            onClick = { onSelectPlaylist(pl) }
                        )
                    }
                }
            }
        }

        // Albums Horizontal Section
        if (albums.isNotEmpty()) {
            item {
                HomeSectionHeader("Albums")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(albums.take(15), key = { "alb_${it.id}" }) { album ->
                        HorizontalSquareCard(
                            title = album.name,
                            subtitle = album.artist,
                            artUri = album.albumArtUri,
                            onClick = { onSelectAlbum(album) }
                        )
                    }
                }
            }
        }

        // Artists Horizontal Section
        if (artists.isNotEmpty()) {
            item {
                HomeSectionHeader("Artists")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(artists.take(15), key = { "art_${it.name}" }) { artist ->
                        Column(
                            modifier = Modifier
                                .width(105.dp)
                                .clickable { onSelectArtist(artist) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(92.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (artist.artworkUri != null) {
                                    AlbumArtwork(
                                        uri = artist.artworkUri,
                                        contentDescription = artist.name,
                                        modifier = Modifier.fillMaxSize(),
                                        cornerRadius = 46.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = artist.name,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = artist.name,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${artist.songCount} songs",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // All Songs Section
        if (songs.isNotEmpty()) {
            item {
                HomeSectionHeader("All Songs (${songs.size})")
            }
            items(songs, key = { "all_${it.id}" }) { song ->
                SongItemCard(
                    song = song,
                    isPlayingThis = playbackState.currentSong?.id == song.id,
                    onClick = { viewModel.playSong(song, songs) },
                    onMoreClick = { onSongMoreClick(song) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp)
    )
}

@Composable
private fun HorizontalSquareCard(
    title: String,
    subtitle: String,
    artUri: android.net.Uri?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(136.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        AlbumArtwork(
            uri = artUri,
            contentDescription = title,
            modifier = Modifier.size(136.dp),
            cornerRadius = 14.dp,
            iconSize = 42.dp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
