@file:OptIn(ExperimentalMaterial3Api::class)

package com.playnova.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.playnova.app.AppViewModel
import com.playnova.app.data.MediaRepo
import com.playnova.app.data.Video
import com.playnova.app.i18n.tr
import com.playnova.app.player.LinkKind
import com.playnova.app.player.UrlResolver
import kotlinx.coroutines.launch

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
        val ctx = LocalContext.current
        val view = LocalView.current
        val scope = rememberCoroutineScope()
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

        /** Any link the user types or pastes: http(s), HLS, DASH, RTSP, dynamic/redirecting URLs, bare domains. */
        fun playUrl(raw: String) {
            val uri = UrlResolver.normalize(raw)
            if (uri == null) { Toast.makeText(ctx, tr("This is not a valid link."), Toast.LENGTH_SHORT).show(); return }
            if (!UrlResolver.isRemote(uri)) { playExternal(uri); return }
            Toast.makeText(ctx, tr("Opening link..."), Toast.LENGTH_SHORT).show()
            scope.launch {
                when (val kind = UrlResolver.detect(uri)) {
                    LinkKind.WEBPAGE -> Toast.makeText(ctx, tr("This link is a web page, not a direct video link."), Toast.LENGTH_LONG).show()
                    else -> play(listOf(MediaRepo.external(uri).copy(mime = UrlResolver.mimeFor(kind) ?: "video/*")), 0)
                }
            }
        }

        LaunchedEffect(externalUri) {
            if (externalUri != null) {
                if (UrlResolver.isRemote(externalUri)) playUrl(externalUri.toString()) else playExternal(externalUri)
                onExternalHandled()
            }
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
                    if (isTab) SlimBottomBar(route) { r ->
                        nav.navigate(r) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true; restoreState = true
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
                        nav.navigate(if (hasVideoPermission(ctx)) "home" else "permission") { popUpTo("onboarding") { inclusive = true } }
                    }
                }
                composable("permission") {
                    PermissionScreen(vm) { nav.navigate("home") { popUpTo("permission") { inclusive = true } } }
                }
                composable("home") {
                    HomeScreen(vm, onNav = { r -> nav.navigate(r) { launchSingleTop = true } }, onPlay = { l, i -> play(l, i) },
                        onPlayExternal = { playExternal(it) }, onPlayUrl = { playUrl(it) })
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

/** Slim bottom navigation: 52dp tall, no pill indicator, the active tab uses the accent color. */
@Composable
private fun SlimBottomBar(route: String, onSelect: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(52.dp)) {
            tabs.forEach { t ->
                val sel = route == t.route
                val tint = if (sel) MaterialTheme.colorScheme.primary else Muted
                Column(
                    Modifier.weight(1f).fillMaxHeight().selectable(selected = sel, role = Role.Tab, onClick = { onSelect(t.route) }),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                ) {
                    Icon(t.icon, null, Modifier.size(22.dp), tint = tint)
                    Text(tr(t.label), fontSize = 10.sp, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
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
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onOpen).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Thumb(v, Modifier.width(64.dp).aspectRatio(16f / 9f))
        Text(v.title, Modifier.weight(1f).padding(horizontal = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        IconButton(onClick = { holder.toggle() }) { Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (playing) tr("Pause") else tr("Play")) }
        IconButton(onClick = { holder.close() }) { Icon(Icons.Outlined.Close, tr("Close mini player")) }
    }
}
