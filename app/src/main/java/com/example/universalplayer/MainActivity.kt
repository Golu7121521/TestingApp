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
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.universalplayer.player.PlayerViewModel
import com.example.universalplayer.ui.HistoryScreen
import com.example.universalplayer.ui.HomeScreen
import com.example.universalplayer.ui.PlayerScreen
import com.example.universalplayer.ui.UniversalPlayerTheme

enum class Screen { Home, History, Player }

data class Paste(val url: String, val id: Long)

class MainActivity : ComponentActivity() {
    private val vm: PlayerViewModel by viewModels()
    private var screen by mutableStateOf(Screen.Home)
    private var returnTo = Screen.Home
    private var inPip by mutableStateOf(false)
    private var paste by mutableStateOf<Paste?>(null)
    private var lastClip: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            UniversalPlayerTheme {
                Surface(Modifier.fillMaxSize()) {
                    when (screen) {
                        Screen.Home -> HomeScreen(
                            paste = paste,
                            onPlay = { play(it) },
                            onHistory = { screen = Screen.History }
                        )
                        Screen.History -> {
                            BackHandler { screen = Screen.Home }
                            HistoryScreen(onBack = { screen = Screen.Home }, onPlay = { play(it) })
                        }
                        Screen.Player -> PlayerScreen(
                            vm = vm,
                            inPip = inPip,
                            onBack = { vm.close(); screen = returnTo },
                            onEnterPip = { enterPip() }
                        )
                    }
                }
            }
        }
    }

    private fun play(url: String) {
        if (screen != Screen.Player) returnTo = screen
        vm.open(url)
        screen = Screen.Player
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
        if (screen != Screen.Home) return
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
        if (screen == Screen.Player && vm.ui.value.isPlaying) enterPip()
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
