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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.lerp
import com.mytune.app.ui.screens.SearchScreen
import com.mytune.app.ui.screens.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val DOWNLOADS = "downloads"
    const val DETAIL = "detail/{type}/{id}?name={name}"
    fun detail(type: String, id: String, name: String = "") = "detail/$type/${Uri.encode(id)}?name=${Uri.encode(name)}"
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
    var miniPx by remember { mutableIntStateOf(0) }
    val bottomInset = with(density) { bottomBarPx.toDp() }
    // Opening a page from the full player first collapses it, so the page is visible
    val open: (String, String) -> Unit = { type, id ->
        scope.launch { expand.animateTo(0f, tween(250)) }
        nav.navigate(Routes.detail(type, id))
    }

    val openArtist: (String, String) -> Unit = { id, name ->
        scope.launch { expand.animateTo(0f, tween(250)) }
        nav.navigate(Routes.detail(CollectionType.ARTIST, id, name))
    }

    LaunchedEffect(player.hasMedia) { if (!player.hasMedia) expand.snapTo(0f) }
    // Tapping any song starts playback and opens the Now Playing screen.
    LaunchedEffect(Unit) { c.player.openPlayer.collect { expand.animateTo(1f, tween(320)) } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullHeight = maxHeight
        val heightPx = with(density) { fullHeight.toPx() }
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
            scope.launch { expand.animateTo(to, spring(dampingRatio = 0.9f, stiffness = 420f)) }
        }
        val dragModifier = Modifier.draggable(dragState, Orientation.Vertical, onDragStopped = { settle(it) })

        CompositionLocalProvider(LocalBottomInset provides bottomInset, LocalOpenCollection provides open, LocalOpenArtist provides openArtist) {
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
                        navArgument("name") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    DetailScreen(
                        c,
                        type = entry.arguments?.getString("type").orEmpty(),
                        id = entry.arguments?.getString("id").orEmpty(),
                        name = entry.arguments?.getString("name").orEmpty(),
                        onBack = { nav.popBackStack() },
                        onOpen = open,
                    )
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
                    modifier = dragModifier.onSizeChanged { miniPx = it.height },
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

        // Expanding player panel (like YouTube Music): a bottom sheet whose height follows the finger,
        // growing from the mini player to full screen. The mini row cross-fades into the full player.
        if (expand.value > 0f && player.hasMedia) {
            val e = expand.value
            val vPad = with(density) { 4.dp.toPx() }
            val miniSurfacePx = (miniPx - 2 * vPad).coerceAtLeast(with(density) { 56.dp.toPx() })
            val navPx = (bottomBarPx - miniPx).coerceAtLeast(0).toFloat()
            val panelH = miniSurfacePx + (heightPx - miniSurfacePx) * e
            val bottomOffset = (navPx + vPad) * (1f - e)
            val sidePad = 8.dp * (1f - e)
            val corner = 14.dp * (1f - e)
            val panelColor = lerp(MaterialTheme.colorScheme.surfaceContainerHigh, Color(0xFF0B0B0D), e.coerceIn(0f, 1f))

            // Stationary touch layer: the drag deltas must not be measured on the moving panel.
            Box(Modifier.fillMaxSize().then(dragModifier)) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .offset { IntOffset(0, -bottomOffset.toInt()) }
                        .padding(horizontal = sidePad)
                        .fillMaxWidth()
                        .height(with(density) { panelH.toDp() })
                        .clip(RoundedCornerShape(corner))
                        .background(panelColor),
                ) {
                    // Full player: laid out at full size, revealed by the growing panel
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .requiredHeight(fullHeight)
                            .graphicsLayer { alpha = ((e - 0.15f) / 0.6f).coerceIn(0f, 1f) },
                    ) {
                        PlayerScreen(c, onBack = { scope.launch { expand.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 420f)) } })
                    }
                    // Mini row fades out as the panel grows
                    // Only composed while the panel is (almost) collapsed. A transparent mini row left on top of the
                    // full player used to swallow taps (its Next button sat under the 3-dot menu and skipped songs).
                    if (e < 0.3f) {
                        MiniPlayerContent(
                            player,
                            onToggle = { c.player.togglePlay() },
                            onNext = { c.player.next() },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .graphicsLayer { alpha = (1f - e / 0.25f).coerceIn(0f, 1f) }
                                .clickable(enabled = e < 0.1f) { scope.launch { expand.animateTo(1f, tween(320)) } },
                        )
                    }
                }
            }
        }

        // Registered last so it wins over the NavHost's own back handling: one back press collapses the player.
        BackHandler(enabled = expand.value > 0f) {
            scope.launch { expand.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 420f)) }
        }
        }
    }
}
