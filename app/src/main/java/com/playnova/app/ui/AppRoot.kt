@file:OptIn(ExperimentalMaterial3Api::class)

package com.playnova.app.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playnova.app.AppViewModel
import com.playnova.app.data.MediaRepo
import com.playnova.app.data.Video

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Outlined.Home), Tab("videos", "Videos", Icons.Outlined.VideoLibrary),
    Tab("folders", "Folders", Icons.Outlined.Folder), Tab("playlists", "Playlists", Icons.Outlined.PlaylistPlay),
    Tab("settings", "Settings", Icons.Outlined.Settings)
)

@Composable
fun AppRoot(externalUri: Uri?, onExternalHandled: () -> Unit) {
    val vm: AppViewModel = viewModel()
    val prefs by vm.settings.state.collectAsState()
    PlayNovaTheme(prefs) {
        val view = LocalView.current
        val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
        SideEffect {
            (view.context as? android.app.Activity)?.window?.let { WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = light }
        }
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route ?: "splash"
        val holder = vm.holder
        val queue by holder.queue.collectAsState()

        LaunchedEffect(Unit) { vm.refresh() }

        fun play(list: List<Video>, i: Int, shuffle: Boolean = false) {
            holder.play(list, i, shuffle)
            nav.navigate("player") { launchSingleTop = true }
        }
        fun playExternal(u: Uri) = play(listOf(MediaRepo.external(u)), 0)

        LaunchedEffect(externalUri) {
            if (externalUri != null) { playExternal(externalUri); onExternalHandled() }
        }

        val isTab = tabs.any { it.route == route }
        val showMini = queue.isNotEmpty() && (isTab || route.startsWith("folder") || route.startsWith("playlist/") || route in listOf("search", "history", "favorites", "storage"))
        val isPlayer = route == "player"
        val intro = route == "splash" || route == "onboarding" || route == "permission"

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = if (isPlayer || intro) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
            bottomBar = {
                if (!isPlayer && !intro) Column {
                    if (showMini) MiniPlayer(vm) { nav.navigate("player") { launchSingleTop = true } }
                    if (isTab) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        tabs.forEach { t ->
                            NavigationBarItem(
                                selected = route == t.route,
                                onClick = {
                                    nav.navigate(t.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true; restoreState = true
                                    }
                                },
                                icon = { Icon(t.icon, null) }, label = { Text(t.label, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        ) { inner ->
            NavHost(nav, startDestination = "splash", modifier = Modifier.padding(if (isPlayer || intro) PaddingValues(0.dp) else inner)) {
                composable("splash") {
                    SplashScreen {
                        val next = if (!prefs.onboarded) "onboarding" else "home"
                        nav.navigate(next) { popUpTo("splash") { inclusive = true } }
                    }
                }
                composable("onboarding") {
                    OnboardingScreen {
                        vm.settings.update { it.copy(onboarded = true) }
                        nav.navigate(if (hasVideoPermission(nav.context)) "home" else "permission") { popUpTo("onboarding") { inclusive = true } }
                    }
                }
                composable("permission") {
                    PermissionScreen(vm) { nav.navigate("home") { popUpTo("permission") { inclusive = true } } }
                }
                composable("home") {
                    HomeScreen(vm, onNav = { r -> nav.navigate(r) { launchSingleTop = true } }, onPlay = { l, i -> play(l, i) }, onPlayExternal = { playExternal(it) })
                }
                composable("videos") { LibraryScreen(vm, null, null) { l, i -> play(l, i) } }
                composable("folders") { FoldersScreen(vm) { nav.navigate("folder/" + Uri.encode(it)) } }
                composable("folder/{name}", arguments = listOf(navArgument("name") { type = NavType.StringType })) { e ->
                    val name = e.arguments?.getString("name") ?: ""
                    LibraryScreen(vm, name, { nav.popBackStack() }) { l, i -> play(l, i) }
                }
                composable("playlists") { PlaylistsScreen(vm, { nav.navigate("playlist/$it") }, { nav.navigate("favorites") }) }
                composable("playlist/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    PlaylistDetailScreen(vm, e.arguments?.getLong("id") ?: 0L, { nav.popBackStack() }) { l, i, s -> play(l, i, s) }
                }
                composable("settings") { SettingsScreen(vm) { nav.navigate("storage") } }
                composable("storage") { StorageScreen(vm) { nav.popBackStack() } }
                composable("search") { SearchScreen(vm, { nav.popBackStack() }) { l, i -> play(l, i) } }
                composable("history") { HistoryScreen(vm, { nav.popBackStack() }) { l, i -> play(l, i) } }
                composable("favorites") { FavoritesScreen(vm, { nav.popBackStack() }) { l, i, s -> play(l, i, s) } }
                composable("player") { PlayerScreen(vm) { nav.popBackStack() } }
            }
        }
    }
}

/** In-app mini-player (separate from system picture-in-picture). */
@Composable
private fun MiniPlayer(vm: AppViewModel, onOpen: () -> Unit) {
    val holder = vm.holder
    val queue by holder.queue.collectAsState()
    val idx by holder.index.collectAsState()
    val playing by holder.playing.collectAsState()
    val v = queue.getOrNull(idx) ?: return
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onOpen).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Thumb(v, Modifier.width(72.dp).aspectRatio(16f / 9f))
        Text(v.title, Modifier.weight(1f).padding(horizontal = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        IconButton(onClick = { holder.toggle() }) { Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (playing) "Pause" else "Play") }
        IconButton(onClick = { holder.close() }) { Icon(Icons.Outlined.Close, "Mini player band karo") }
    }
}
