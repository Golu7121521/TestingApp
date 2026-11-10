package com.streamx.app.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.streamx.app.feature.home.HomeScreen
import com.streamx.app.feature.library.LibraryScreen
import com.streamx.app.feature.profile.ProfileScreen
import com.streamx.app.feature.shorts.ShortsScreen
import com.streamx.app.feature.subscriptions.SubscriptionsScreen

/**
 * Root composable: scaffold with the five-tab bottom navigation.
 * Each tab keeps its own back stack via saveState/restoreState, so switching
 * tabs preserves position (spec section 13).
 */
@Composable
fun StreamXApp() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination

                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = destination.label,
                            )
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.HOME.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(TopLevelDestination.HOME.route) { HomeScreen() }
            composable(TopLevelDestination.SHORTS.route) { ShortsScreen() }
            composable(TopLevelDestination.SUBSCRIPTIONS.route) { SubscriptionsScreen() }
            composable(TopLevelDestination.LIBRARY.route) { LibraryScreen() }
            composable(TopLevelDestination.PROFILE.route) { ProfileScreen() }
        }
    }
}
