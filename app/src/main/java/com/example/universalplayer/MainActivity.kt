package com.example.universalplayer

import android.app.PictureInPictureParams
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.universalplayer.player.PlayerViewModel
import com.example.universalplayer.ui.AdvancedScreen
import com.example.universalplayer.ui.AppBottomBar
import com.example.universalplayer.ui.HistoryScreen
import com.example.universalplayer.ui.HomeScreen
import com.example.universalplayer.ui.MainTab
import com.example.universalplayer.ui.PlayerScreen
import com.example.universalplayer.ui.PlaylistScreen
import com.example.universalplayer.ui.UniversalPlayerTheme
import com.example.universalplayer.data.PlaylistStore

data class Paste(val url: String, val id: Long)

class MainActivity : ComponentActivity() {
    private val vm: PlayerViewModel by viewModels()
    private var tab by mutableStateOf(MainTab.HOME)
    private var playing by mutableStateOf(false)
    private var inPip by mutableStateOf(false)
    private var paste by mutableStateOf<Paste?>(null)
    private var lastClip: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            UniversalPlayerTheme {
                Surface(Modifier.fillMaxSize()) {
                    if (playing) {
                        PlayerScreen(
                            vm = vm,
                            inPip = inPip,
                            onBack = { vm.close(); playing = false },
                            onEnterPip = { enterPip() }
                        )
                    } else {
                        BackHandler(enabled = tab != MainTab.HOME) { tab = MainTab.HOME }
                        Scaffold(
                            bottomBar = { AppBottomBar(selected = tab, onSelect = { tab = it }) }
                        ) { pad ->
                            Surface(Modifier.padding(pad).fillMaxSize()) {
                                when (tab) {
                                    MainTab.HOME -> HomeScreen(
                                        paste = paste,
                                        onPlay = { play(it) },
                                        onAddToPlaylist = { PlaylistStore.add(it) }
                                    )
                                    MainTab.ADVANCED -> AdvancedScreen(onPlay = { play(it) })
                                    MainTab.HISTORY -> HistoryScreen(onPlay = { play(it) })
                                    MainTab.PLAYLIST -> PlaylistScreen(onPlay = { play(it) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun play(url: String) {
        vm.open(url)
        playing = true
    }

    private fun handleIntent(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) {
            extractUrl(i.getStringExtra(Intent.EXTRA_TEXT))?.let { play(it) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    // Auto-paste: clipboard can only be read once the window has focus (Android 10+)
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) readClipboard()
    }

    private fun readClipboard() {
        if (playing || tab != MainTab.HOME) return
        val cm = getSystemService(ClipboardManager::class.java) ?: return
        val clip = cm.primaryClip?.takeIf { it.itemCount > 0 } ?: return
        val text = clip.getItemAt(0)?.coerceToText(this)?.toString()
        val url = extractUrl(text) ?: return
        if (url == lastClip) return
        lastClip = url
        paste = Paste(url, System.nanoTime())
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= 26) {
            runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (playing && vm.ui.value.isPlaying) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        vm.pause()
    }
}
