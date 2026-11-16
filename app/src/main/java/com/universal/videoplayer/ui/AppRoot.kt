package com.universal.videoplayer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.universal.videoplayer.data.ThemeMode
import com.universal.videoplayer.player.PlayerViewModel
import com.universal.videoplayer.resolver.PlaybackRequest
import com.universal.videoplayer.theme.VideoPlayerTheme

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Filled.Home),
    Tab("advance", "Advance", Icons.Filled.Tune),
    Tab("history", "History", Icons.Filled.History),
    Tab("playlist", "Playlist", Icons.Filled.PlaylistPlay),
)

@Composable
fun AppRoot(app: AppViewModel, player: PlayerViewModel, onPip: () -> Unit) {
    val settings by app.settings.collectAsStateWithLifecycle()
    val ui by player.ui.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.theme) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val actions = Actions(
        play = { r: PlaybackRequest, resume: Boolean -> player.play(r, resume) },
        playQueue = { q, i ->
            if (q.isNotEmpty()) player.play(PlaybackRequest(url = q[i].url, title = q[i].title), false, q, i)
        },
    )

    VideoPlayerTheme(dark) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                bottomBar = {
                    if (!ui.active) {
                        NavigationBar {
                            tabs.forEach { t ->
                                NavigationBarItem(
                                    selected = entry?.destination?.hierarchy?.any { it.route == t.route } == true,
                                    onClick = {
                                        nav.navigate(t.route) {
                                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(t.icon, t.label) },
                                    label = { Text(t.label) },
                                )
                            }
                        }
                    }
                },
            ) { pad ->
                NavHost(nav, startDestination = "home", modifier = Modifier.padding(bottom = pad.calculateBottomPadding())) {
                    composable("home") { HomeScreen(app, actions) { nav.navigate("settings") } }
                    composable("advance") { AdvanceScreen(app, actions) }
                    composable("history") { HistoryScreen(app, actions) }
                    composable("playlist") { PlaylistScreen(app, actions) }
                    composable("settings") { SettingsScreen(app) { nav.popBackStack() } }
                }
            }

            if (ui.active) PlayerScreen(player, settings, onPip)

            app.clipPrompt?.let { url ->
                AlertDialog(
                    onDismissRequest = { app.clipPrompt = null },
                    title = { Text("Link detected") },
                    text = { Text("Video link detected in clipboard. Would you like to play it?\n\n$url") },
                    confirmButton = {
                        TextButton({ app.clipPrompt = null; app.homeUrl = url; actions.play(PlaybackRequest(url = url), true) }) { Text("Play") }
                    },
                    dismissButton = {
                        TextButton({ app.clipPrompt = null }) { Text("Dismiss") }
                        TextButton({
                            app.clipPrompt = null; app.homeUrl = url
                            nav.navigate("home") { popUpTo(nav.graph.findStartDestination().id); launchSingleTop = true }
                        }) { Text("Paste") }
                    },
                )
            }
        }
    }
}
