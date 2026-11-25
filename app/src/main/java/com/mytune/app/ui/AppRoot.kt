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
import com.mytune.app.ui.screens.SearchScreen
import com.mytune.app.ui.screens.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val DOWNLOADS = "downloads"
    const val PLAYER = "player"
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

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (route != Routes.PLAYER) {
                Column(Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 0.97f))) {
                    // Floating mini player sits directly above the tab bar
                    if (player.hasMedia) {
                        MiniPlayer(player, onOpen = { nav.navigate(Routes.PLAYER) }, onToggle = { c.player.togglePlay() }, onNext = { c.player.next() })
                    }
                    if (showNav) {
                        Box(Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)))
                        // iOS-style tab bar: icon + label, accent colour for the selected tab
                        Row(
                            Modifier.fillMaxWidth().navigationBarsPadding().height(58.dp).padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            items.forEach { item ->
                                val sel = route == item.route
                                val tint = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable(
                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                            indication = null,
                                        ) {
                                            nav.navigate(item.route) {
                                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        stringResource(item.label),
                                        fontSize = 11.sp,
                                        color = tint,
                                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.navigationBarsPadding())
                    }
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(pad)) {
            composable(Routes.HOME) { HomeScreen(c, open) }
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
                )
            }
            composable(
                Routes.PLAYER,
                enterTransition = { slideInVertically(tween(300)) { it } },
                exitTransition = { slideOutVertically(tween(300)) { it } },
                popEnterTransition = { slideInVertically(tween(300)) { it } },
                popExitTransition = { slideOutVertically(tween(300)) { it } },
            ) { PlayerScreen(c, onBack = { nav.popBackStack() }) }
        }
    }
}
