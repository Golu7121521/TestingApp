package com.streamx.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.ui.graphics.vector.ImageVector

/** Top-level bottom-navigation destinations (five tabs, per the spec). */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    SHORTS("shorts", "Shorts", Icons.Filled.PlayCircle, Icons.Outlined.PlayCircleOutline),
    SUBSCRIPTIONS("subscriptions", "Subscriptions", Icons.Filled.Subscriptions, Icons.Outlined.Subscriptions),
    LIBRARY("library", "Library", Icons.Filled.LibraryBooks, Icons.Outlined.LibraryBooks),
    PROFILE("profile", "Profile", Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle),
}
