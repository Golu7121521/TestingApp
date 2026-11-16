package com.example.universalplayer.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

enum class MainTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    ADVANCED("Advanced", Icons.Rounded.Tune),
    HISTORY("History", Icons.Rounded.History),
    PLAYLIST("Playlist", Icons.Rounded.PlaylistPlay)
}

/** Standard Material 3 bottom navigation bar (default height). */
@Composable
fun AppBottomBar(selected: MainTab, onSelect: (MainTab) -> Unit) {
    NavigationBar {
        MainTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) }
            )
        }
    }
}
