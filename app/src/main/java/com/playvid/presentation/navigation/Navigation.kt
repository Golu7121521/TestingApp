package com.playvid.presentation.navigation

import android.net.Uri
import android.util.Base64
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Cloud // Server tab ke liye naya icon
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.playvid.PlayVidApp
import com.playvid.domain.model.PlaybackRequest
import com.playvid.domain.usecase.UrlValidation
import com.playvid.presentation.components.LocalNotifier
import com.playvid.presentation.components.MiniPlayer
import com.playvid.presentation.components.Notifier
import com.playvid.presentation.local.LocalFoldersScreen
import com.playvid.presentation.local.LocalFolderScreen
import com.playvid.presentation.home.HomeScreen
import com.playvid.presentation.player.PlayerScreen
import com.playvid.presentation.playlist.ChannelsScreen
import com.playvid.presentation.playlist.PlaylistScreen
import com.playvid.presentation.settings.SettingsScreen
import com.playvid.presentation.server.ServerScreen // Naya Import

object Routes {
    const val HOME = "home"
    const val SERVER = "server" // FILES ki jagah SERVER
    const val LOCAL = "local"
    const val LOCAL_FOLDER = "local_folder/{id}/{name}"
    const val PLAYLIST = "playlist"
    const val CHANNELS = "channels/{id}"
    const val SETTINGS = "settings"
    const val PLAYER = "player/{payload}"

    fun player(request: PlaybackRequest) = "player/${PlaybackPayload.encode(request)}"
}

object PlaybackPayload {
    private const val SEP = '\u0000'
    private const val FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

    fun encode(r: PlaybackRequest): String {
        val text = "${if (r.isLocal) "1" else "0"}$SEP${r.title}$SEP${r.source}"
        return Base64.encodeToString(text.toByteArray(Charsets.UTF_8), FLAGS)
    }

    fun decode(payload: String): PlaybackRequest? = try {
        val parts = String(Base64.decode(payload, FLAGS), Charsets.UTF_8).split(SEP, limit = 3)
        if (parts.size < 3 || parts[2].isBlank()) null
        else PlaybackRequest(source = parts[2], title = parts[1], isLocal = parts[0] == "1")
    } catch (e: IllegalArgumentException) {
        null
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Filled.Home),
    Tab(Routes.SERVER, "Server", Icons.Filled.Cloud), // Bottom bar mein Server
    Tab(Routes.LOCAL, "Local", Icons.Filled.VideoLibrary),
    Tab(Routes.PLAYLIST, "Playlist", Icons.AutoMirrored.Filled.PlaylistPlay),
    Tab(Routes.SETTINGS, "Settings", Icons.Filled.Settings)
)

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun PlayVidNavHost(sharedText: String? = null, onSharedTextConsumed: () -> Unit = {}) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val inPlayer = route?.startsWith("player") == true

    val context = LocalContext.current
    val controller = remember { (context.applicationContext as PlayVidApp).container.playback }
    val validateUrl = remember { (context.applicationContext as PlayVidApp).container.validateUrl }

    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notifier = remember { Notifier(snackbarHost, scope) }

    val openPlayer: (PlaybackRequest) -> Unit = { nav.navigate(Routes.player(it)) }

    LaunchedEffect(sharedText) {
        val text = sharedText
        if (text != null) {
            val url = Regex("""https?://\S+""").find(text)?.value
            if (url != null) {
                val result = validateUrl(url)
                if (result is UrlValidation.Valid) {
                    openPlayer(PlaybackRequest(source = result.url, title = result.title, isLocal = false))
                } else {
                    notifier.show("That shared link isn't a supported video URL.")
                }
            }
            onSharedTextConsumed()
        }
    }

    CompositionLocalProvider(LocalNotifier provides notifier) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHost) },
            bottomBar = {
                AnimatedVisibility(
                    visible = !inPlayer,
                    enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 2 },
                    exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 2 }
                ) {
                    Column {
                        MiniPlayer(controller = controller, onOpen = openPlayer)
                        ThinBottomBar(currentRoute = route, onSelect = { nav.navigateToTab(it) })
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = Routes.HOME,
                modifier = Modifier.padding(if (inPlayer) PaddingValues() else padding),
                enterTransition = { fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 12 } },
                exitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 12 } },
                popExitTransition = { fadeOut(tween(180)) }
            ) {
                composable(Routes.HOME) { HomeScreen(onPlay = openPlayer) }
                composable(Routes.SERVER) { ServerScreen(onPlay = openPlayer) } // Naya Route
                composable(Routes.LOCAL) {
                    LocalFoldersScreen(onOpenFolder = { id, name ->
                        nav.navigate("local_folder/$id/${Uri.encode(name)}")
                    })
                }
                composable(
                    Routes.LOCAL_FOLDER,
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType },
                        navArgument("name") { type = NavType.StringType }
                    )
                ) { entry ->
                    LocalFolderScreen(
                        folderId = entry.arguments?.getLong("id") ?: 0L,
                        folderName = Uri.decode(entry.arguments?.getString("name") ?: ""),
                        onBack = { nav.popBackStack() },
                        onPlay = openPlayer
                    )
                }
                composable(Routes.PLAYLIST) {
                    PlaylistScreen(onOpenPlaylist = { nav.navigate("channels/$it") })
                }
                composable(
                    Routes.CHANNELS,
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) { entry ->
                    ChannelsScreen(
                        playlistId = entry.arguments?.getLong("id") ?: 0L,
                        onBack = { nav.popBackStack() },
                        onPlay = openPlayer
                    )
                }
                composable(Routes.SETTINGS) { SettingsScreen() }
                composable(
                    route = Routes.PLAYER,
                    arguments = listOf(navArgument("payload") { type = NavType.StringType })
                ) { entry ->
                    val payload = entry.arguments?.getString("payload").orEmpty()
                    PlayerScreen(payload = payload, onBack = { nav.popBackStack() })
                }
            }
        }
    }
}

@Composable
private fun ThinBottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .height(52.dp)
                    .selectableGroup()
            ) {
                tabs.forEach { tab ->
                    val selected = currentRoute == tab.route ||
                        (tab.route == Routes.PLAYLIST && currentRoute?.startsWith("channels/") == true) ||
                        (tab.route == Routes.LOCAL && currentRoute?.startsWith("local_folder/") == true)
                    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab.route) }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                        Text(
                            text = tab.label,
                            color = tint,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
