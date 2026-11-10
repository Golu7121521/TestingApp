package com.example.tubeclone.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.tubeclone.data.Repo
import com.example.tubeclone.data.VideoItem

@Composable
fun YtApp() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF0F0F0F),
            surface = Color(0xFF0F0F0F),
            onBackground = Color.White,
            onSurface = Color.White,
            primary = Color(0xFFFF0000)
        )
    ) {
        val ctx = LocalContext.current
        val player = remember { ExoPlayer.Builder(ctx).build() }
        DisposableEffect(Unit) {
            onDispose { player.release() }
        }

        var tab by rememberSaveable { mutableStateOf(0) }
        var current by remember { mutableStateOf<VideoItem?>(null) }
        var expanded by remember { mutableStateOf(false) }
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var isPlaying by remember { mutableStateOf(false) }

        DisposableEffect(player) {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
            }
            player.addListener(listener)
            onDispose { player.removeListener(listener) }
        }

        // Jab naya video select ho, uska stream URL nikal kar play karo
        LaunchedEffect(current?.url) {
            val v = current ?: return@LaunchedEffect
            loading = true
            error = null
            runCatching { Repo.resolve(v.url) }
                .onSuccess { streamUrl ->
                    player.setMediaItem(MediaItem.fromUri(streamUrl))
                    player.prepare()
                    player.play()
                }
                .onFailure { error = it.message ?: "Video load nahi hua" }
            loading = false
        }

        val onPlay: (VideoItem) -> Unit = { v ->
            if (current?.url != v.url) current = v
            expanded = true
            Repo.addHistory(v)
        }

        // Back dabaye to full player se mini player par wapas
        BackHandler(enabled = expanded) { expanded = false }

        Box(Modifier.fillMaxSize().background(Color(0xFF0F0F0F))) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        0 -> HomeScreen(onPlay)
                        1 -> ShortsScreen(onPlay)
                        2 -> SubsScreen(onPlay)
                        else -> LibraryScreen(onPlay)
                    }
                }

                val cur = current
                if (cur != null && !expanded) {
                    MiniPlayer(
                        video = cur,
                        player = player,
                        isPlaying = isPlaying,
                        onExpand = { expanded = true },
                        onClose = {
                            player.stop()
                            player.clearMediaItems()
                            current = null
                        }
                    )
                }

                NavigationBar(containerColor = Color(0xFF0F0F0F)) {
                    val items = listOf(
                        Triple("Home", Icons.Filled.Home, 0),
                        Triple("Shorts", Icons.Filled.Movie, 1),
                        Triple("Subscriptions", Icons.Filled.Subscriptions, 2),
                        Triple("Library", Icons.Filled.VideoLibrary, 3)
                    )
                    items.forEach { (label, icon, idx) ->
                        NavigationBarItem(
                            selected = tab == idx,
                            onClick = { tab = idx },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 10.sp, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color.White,
                                unselectedIconColor = Color(0xFFAAAAAA),
                                unselectedTextColor = Color(0xFFAAAAAA),
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }

            // Full player overlay
            val cur = current
            if (cur != null && expanded) {
                FullPlayer(
                    video = cur,
                    player = player,
                    loading = loading,
                    error = error,
                    onCollapse = { expanded = false }
                )
            }
        }
    }
}
