package com.mytune.app.ui

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

import android.net.Uri
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mytune.app.ui.screens.DetailScreen
import com.mytune.app.ui.screens.DownloadsScreen
import com.mytune.app.ui.screens.HomeScreen
import com.mytune.app.ui.screens.LibraryScreen
import com.mytune.app.ui.screens.PlayerScreen
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.mytune.app.ui.screens.SearchScreen
import com.mytune.app.ui.screens.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val DOWNLOADS = "downloads"
    const val DETAIL = "detail/{type}/{id}"
    fun detail(type: String, id: String) = "detail/$type/${Uri.encode(id)}"
}

private data class NavItem(val route: String, val label: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun AppRoot(c: AppContainer) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val player by c.player.state.collectAsState()
    val items = listOf(
        NavItem(Routes.HOME, R.string.nav_home, Icons.Default.Home),
        NavItem(Routes.SEARCH, R.string.nav_search, Icons.Default.Search),
        NavItem(Routes.LIBRARY, R.string.nav_library, Icons.Default.LibraryMusic),
        NavItem(Routes.SETTINGS, R.string.nav_settings, Icons.Default.Settings),
    )
    val showNav = route in items.map { it.route }
    val open: (String, String) -> Unit = { type, id -> nav.navigate(Routes.detail(type, id)) }
    val goTab: (String) -> Unit = { r ->
        nav.navigate(r) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val scope = rememberCoroutineScope()
    // 0f = player collapsed (mini player), 1f = full screen. Follows the finger while dragging.
    val expand = remember { Animatable(0f) }
    val density = LocalDensity.current
    var bottomBarPx by remember { mutableIntStateOf(0) }
    val bottomInset = with(density) { bottomBarPx.toDp() }

    LaunchedEffect(player.hasMedia) { if (!player.hasMedia) expand.snapTo(0f) }
    BackHandler(enabled = expand.value > 0f) { scope.launch { expand.animateTo(0f, tween(300)) } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = with(density) { maxHeight.toPx() }
        val dragState = rememberDraggableState { dy ->
            scope.launch { expand.snapTo((expand.value - dy / heightPx).coerceIn(0f, 1f)) }
        }
        fun settle(velocity: Float) {
            val to = when {
                velocity < -900f -> 1f
                velocity > 900f -> 0f
                expand.value > 0.5f -> 1f
                else -> 0f
            }
            scope.launch { expand.animateTo(to, spring(dampingRatio = 0.85f, stiffness = 380f)) }
        }
        val dragModifier = Modifier.draggable(dragState, Orientation.Vertical, onDragStopped = { settle(it) })

        CompositionLocalProvider(LocalBottomInset provides bottomInset) {
            // Screens draw edge to edge, behind the transparent bottom bar.
            NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.fillMaxSize()) {
                composable(Routes.HOME) { HomeScreen(c, open, onSearch = { goTab(Routes.SEARCH) }) }
                composable(Routes.SEARCH) { SearchScreen(c, open) }
                composable(Routes.LIBRARY) { LibraryScreen(c, open, onDownloads = { nav.navigate(Routes.DOWNLOADS) }) }
                composable(Routes.SETTINGS) { SettingsScreen(c) }
                composable(Routes.DOWNLOADS) { DownloadsScreen(c, onBack = { nav.popBackStack() }) }
                composable(
                    Routes.DETAIL,
                    arguments = listOf(
                        navArgument("type") { type = NavType.StringType },
                        navArgument("id") { type = NavType.StringType },
                    ),
                ) { entry ->
                    DetailScreen(
                        c,
                        type = entry.arguments?.getString("type").orEmpty(),
                        id = entry.arguments?.getString("id").orEmpty(),
                        onBack = { nav.popBackStack() },
                        onOpen = open,
                    )
                }
            }
        }

        // Mini player + bottom navigation, floating over the content
        Column(Modifier.align(Alignment.BottomCenter).onSizeChanged { bottomBarPx = it.height }) {
            if (player.hasMedia) {
                MiniPlayer(
                    player,
                    onOpen = { scope.launch { expand.animateTo(1f, tween(320)) } },
                    onToggle = { c.player.togglePlay() },
                    onNext = { c.player.next() },
                    modifier = dragModifier,
                )
            }
            if (showNav) {
                // Fully transparent at the top, fading to dark at the bottom
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(0f to Color.Transparent, 0.55f to Color(0x99000000), 1f to Color(0xF2000000)))
                        .navigationBarsPadding()
                        .height(64.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEach { item ->
                        val sel = route == item.route
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null,
                                ) { goTab(item.route) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            val tint = if (sel) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)
                            Icon(item.icon, null, tint = tint, modifier = Modifier.size(26.dp))
                            Text(stringResource(item.label), color = tint, fontSize = 11.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
            } else {
                Spacer(Modifier.navigationBarsPadding())
            }
        }

        // Full-screen player: slides with the finger and shrinks/fades into the mini player.
        if (expand.value > 0f && player.hasMedia) {
            Box(Modifier.fillMaxSize().then(dragModifier)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val e = expand.value
                            translationY = (1f - e) * size.height
                            val sc = 0.94f + 0.06f * e
                            scaleX = sc
                            scaleY = sc
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                            alpha = (e / 0.55f).coerceIn(0f, 1f)
                            shape = RoundedCornerShape(topStart = 28.dp * (1f - e), topEnd = 28.dp * (1f - e))
                            clip = true
                        },
                ) {
                    PlayerScreen(c, onBack = { scope.launch { expand.animateTo(0f, tween(300)) } })
                }
            }
        }
    }
}
