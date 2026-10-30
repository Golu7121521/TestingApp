package com.example.youtubeclone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.youtubeclone.data.RealVideoItem
import com.example.youtubeclone.ui.components.YouTubeTopBar
import com.example.youtubeclone.ui.screens.RealHomeScreen
import com.example.youtubeclone.ui.screens.RealShortsScreen
import com.example.youtubeclone.ui.screens.RealVideoPlayerScreen
import com.example.youtubeclone.ui.theme.YouTubeCloneTheme

enum class BottomNavTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    SHORTS("Shorts", Icons.Filled.Bolt, Icons.Outlined.Bolt),
    CODING("Coding", Icons.Filled.Code, Icons.Outlined.Code),
    MUSIC("Music", Icons.Filled.MusicNote, Icons.Outlined.MusicNote)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var isDarkTheme by remember { mutableStateOf(true) }
            YouTubeCloneTheme(darkTheme = isDarkTheme) {
                YouTubeNewPipeApp(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = { isDarkTheme = !isDarkTheme }
                )
            }
        }
    }
}

@Composable
fun YouTubeNewPipeApp(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    var currentTab by remember { mutableStateOf(BottomNavTab.HOME) }
    var searchQuery by remember { mutableStateOf("Trending") }
    var activeVideo by remember { mutableStateOf<RealVideoItem?>(null) }

    BackHandler(enabled = activeVideo != null) {
        activeVideo = null
    }

    Scaffold(
        topBar = {
            if (activeVideo == null) {
                YouTubeTopBar(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onSearchSubmit = { query ->
                        searchQuery = query
                        currentTab = BottomNavTab.HOME
                    }
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background
            ) {
                BottomNavTab.entries.forEach { tab ->
                    val selected = currentTab == tab && activeVideo == null
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            activeVideo = null
                            currentTab = tab
                            when (tab) {
                                BottomNavTab.HOME -> searchQuery = "Trending"
                                BottomNavTab.CODING -> searchQuery = "Android Jetpack Compose Tutorial"
                                BottomNavTab.MUSIC -> searchQuery = "LoFi Hindi Music"
                                else -> {}
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (activeVideo != null) {
                RealVideoPlayerScreen(
                    videoItem = activeVideo!!,
                    onClose = { activeVideo = null },
                    onSelectVideo = { activeVideo = it }
                )
            } else {
                when (currentTab) {
                    BottomNavTab.SHORTS -> RealShortsScreen(onVideoSelect = { activeVideo = it })
                    else -> RealHomeScreen(
                        searchQuery = searchQuery,
                        onSearchCategoryChange = { searchQuery = it },
                        onVideoSelect = { activeVideo = it }
                    )
                }
            }
        }
    }
}
