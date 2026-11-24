package com.vibemusic.app.navigation

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vibemusic.app.domain.model.Song
import com.vibemusic.app.presentation.VibeViewModel
import com.vibemusic.app.ui.components.AddToPlaylistDialog
import com.vibemusic.app.ui.components.MiniPlayerBar
import com.vibemusic.app.ui.components.SongInfoDialog
import com.vibemusic.app.ui.components.SongOptionsBottomSheet
import com.vibemusic.app.ui.screens.EqualizerScreen
import com.vibemusic.app.ui.screens.HomeScreen
import com.vibemusic.app.ui.screens.LibraryScreen
import com.vibemusic.app.ui.screens.NowPlayingScreen
import com.vibemusic.app.ui.screens.PlaylistsScreen
import com.vibemusic.app.ui.screens.SearchScreen
import com.vibemusic.app.ui.screens.SettingsScreen
import com.vibemusic.app.utils.MediaUtils

enum class BottomNavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    HOME("home", "Home", Icons.Default.Home),
    LIBRARY("library", "Library", Icons.Default.LibraryMusic),
    SEARCH("search", "Search", Icons.Default.Search),
    PLAYLISTS("playlists", "Playlists", Icons.AutoMirrored.Filled.QueueMusic),
    SETTINGS("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun VibeMainScaffold(
    viewModel: VibeViewModel,
    onRequestAudioPermission: () -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: BottomNavDestination.HOME.route

    val playbackState by viewModel.playbackState.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val context = LocalContext.current

    var showNowPlaying by remember { mutableStateOf(false) }
    var selectedSongForOptions by remember { mutableStateOf<Song?>(null) }
    var selectedSongForPlaylist by remember { mutableStateOf<Song?>(null) }
    var selectedSongForInfo by remember { mutableStateOf<Song?>(null) }
    var pendingDeleteSong by remember { mutableStateOf<Song?>(null) }
    var targetPlaylistIdForNav by remember { mutableStateOf<Long?>(null) }

    val deleteMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, "Audio file deleted", Toast.LENGTH_SHORT).show()
            viewModel.scanLibrary()
        }
    }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (currentRoute != "equalizer") {
                    Column {
                        if (playbackState.currentSong != null) {
                            MiniPlayerBar(
                                playbackState = playbackState,
                                onOpenNowPlaying = { showNowPlaying = true },
                                onTogglePlayPause = { viewModel.player.togglePlayPause() },
                                onSkipNext = { viewModel.player.skipToNext() }
                            )
                        }

                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            BottomNavDestination.entries.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToTab(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = dest.icon,
                                            contentDescription = dest.label
                                        )
                                    },
                                    label = { Text(dest.label) }
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = BottomNavDestination.HOME.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
            ) {
                composable(BottomNavDestination.HOME.route) {
                    HomeScreen(
                        viewModel = viewModel,
                        onNavigateSearch = { navigateToTab(BottomNavDestination.SEARCH.route) },
                        onNavigateSettings = { navigateToTab(BottomNavDestination.SETTINGS.route) },
                        onNavigateEqualizer = { navController.navigate("equalizer") },
                        onRequestPermission = onRequestAudioPermission,
                        onSongMoreClick = { selectedSongForOptions = it },
                        onSelectPlaylist = { pl ->
                            targetPlaylistIdForNav = pl.id
                            navigateToTab(BottomNavDestination.PLAYLISTS.route)
                        },
                        onSelectAlbum = { alb ->
                            viewModel.playSongs(alb.songs, 0)
                            showNowPlaying = true
                        },
                        onSelectArtist = { art ->
                            viewModel.playSongs(art.songs, 0)
                            showNowPlaying = true
                        }
                    )
                }

                composable(BottomNavDestination.LIBRARY.route) {
                    LibraryScreen(
                        viewModel = viewModel,
                        onSongMoreClick = { selectedSongForOptions = it },
                        onSelectPlaylist = { pl ->
                            targetPlaylistIdForNav = pl.id
                            navigateToTab(BottomNavDestination.PLAYLISTS.route)
                        }
                    )
                }

                composable(BottomNavDestination.SEARCH.route) {
                    SearchScreen(
                        viewModel = viewModel,
                        onSongMoreClick = { selectedSongForOptions = it },
                        onSelectPlaylist = { pl ->
                            targetPlaylistIdForNav = pl.id
                            navigateToTab(BottomNavDestination.PLAYLISTS.route)
                        }
                    )
                }

                composable(BottomNavDestination.PLAYLISTS.route) {
                    PlaylistsScreen(
                        viewModel = viewModel,
                        initialSelectedPlaylistId = targetPlaylistIdForNav,
                        onClearInitialSelection = { targetPlaylistIdForNav = null },
                        onSongMoreClick = { selectedSongForOptions = it }
                    )
                }

                composable(BottomNavDestination.SETTINGS.route) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onOpenEqualizer = { navController.navigate("equalizer") }
                    )
                }

                composable("equalizer") {
                    EqualizerScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }

        // Full-Screen Now Playing Overlay
        AnimatedVisibility(
            visible = showNowPlaying,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            NowPlayingScreen(
                viewModel = viewModel,
                onClose = { showNowPlaying = false },
                onOpenEqualizer = {
                    showNowPlaying = false
                    navController.navigate("equalizer")
                },
                onSongMoreClick = { selectedSongForOptions = it }
            )
        }
    }

    // Song Options Bottom Sheet
    selectedSongForOptions?.let { song ->
        SongOptionsBottomSheet(
            song = song,
            onDismiss = { selectedSongForOptions = null },
            onPlayNext = {
                viewModel.player.playNextInQueue(song)
                Toast.makeText(context, "Playing next", Toast.LENGTH_SHORT).show()
            },
            onAddToQueue = {
                viewModel.player.addToQueue(song)
                Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
            },
            onToggleFavorite = { viewModel.toggleFavorite(song.id) },
            onAddToPlaylist = { selectedSongForPlaylist = song },
            onShowInfo = { selectedSongForInfo = song },
            onShare = { MediaUtils.shareSong(context, song) },
            onSetRingtone = { MediaUtils.setAsRingtone(context, song) },
            onExclude = {
                viewModel.excludeSong(song)
                Toast.makeText(context, "Excluded from library", Toast.LENGTH_SHORT).show()
            },
            onDelete = {
                val senderReq = MediaUtils.buildDeleteRequest(context, song)
                if (senderReq != null) {
                    deleteMediaLauncher.launch(senderReq)
                } else {
                    pendingDeleteSong = song
                }
            }
        )
    }

    // Add to Playlist Dialog
    selectedSongForPlaylist?.let { song ->
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { selectedSongForPlaylist = null },
            onSelectPlaylist = { plId ->
                viewModel.addSongToPlaylist(plId, song.id)
                Toast.makeText(context, "Added to playlist", Toast.LENGTH_SHORT).show()
            },
            onCreateNewPlaylist = { name ->
                viewModel.createPlaylist(name)
            }
        )
    }

    // Song Metadata Info Dialog
    selectedSongForInfo?.let { song ->
        SongInfoDialog(
            song = song,
            onDismiss = { selectedSongForInfo = null }
        )
    }

    // Legacy Delete Confirmation Dialog (Android < 11)
    pendingDeleteSong?.let { song ->
        AlertDialog(
            onDismissRequest = { pendingDeleteSong = null },
            title = { Text("Delete Audio File") },
            text = { Text("Permanently delete "${song.title}" from your device?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val deleted = MediaUtils.deleteLegacySong(context, song)
                        if (deleted) {
                            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                            viewModel.scanLibrary()
                        }
                        pendingDeleteSong = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSong = null }) { Text("Cancel") }
            }
        )
    }
}
