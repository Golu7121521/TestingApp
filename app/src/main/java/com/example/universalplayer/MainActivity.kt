package com.example.universalplayer

import android.app.PictureInPictureParams
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.universalplayer.data.PlayOptions
import com.example.universalplayer.player.PlayerViewModel
import com.example.universalplayer.ui.*

enum class Screen { Main, Player }

class MainActivity : ComponentActivity() {
    private val vm: PlayerViewModel by viewModels()
    private var screen by mutableStateOf(Screen.Main)
    private var tab by mutableStateOf(Tab.Home)
    private var homeText by mutableStateOf("")
    private var lastClip: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            UniversalPlayerTheme {
                val form = remember { AdvancedForm() }
                Surface(Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = screen,
                        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
                        label = "screen"
                    ) { sc ->
                        when (sc) {
                            Screen.Main -> {
                                BackHandler(tab != Tab.Home) { tab = Tab.Home }
                                Scaffold(
                                    bottomBar = { AppBottomBar(tab) { tab = it } },
                                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                                ) { pad ->
                                    Box(Modifier.padding(pad)) {
                                        AnimatedContent(
                                            targetState = tab,
                                            transitionSpec = {
                                                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                                                (slideInHorizontally(tween(260)) { it / 8 * dir } + fadeIn(tween(260))) togetherWith
                                                        (slideOutHorizontally(tween(200)) { -it / 8 * dir } + fadeOut(tween(160)))
                                            },
                                            label = "tab"
                                        ) { t ->
                                            when (t) {
                                                Tab.Home -> HomeScreen(
                                                    text = homeText,
                                                    onText = { homeText = it },
                                                    onPlay = { play(it) },
                                                    onPlayItem = { play(it.url, it.opts) }
                                                )
                                                Tab.Advanced -> AdvancedScreen(form) { url, o -> play(url, o) }
                                                Tab.History -> HistoryScreen { play(it.url, it.opts) }
                                                Tab.Playlist -> PlaylistScreen { items, i ->
                                                    vm.openQueue(items, i)
                                                    screen = Screen.Player
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Screen.Player -> PlayerScreen(
                                vm = vm,
                                inPip = inPip,
                                onBack = { vm.close(); screen = Screen.Main },
                                onEnterPip = { enterPip() }
                            )
                        }
                    }
                }
            }
        }
    }

    private var inPip by mutableStateOf(false)

    private fun play(url: String, opts: PlayOptions = PlayOptions()) {
        vm.open(url, opts)
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
        if (screen != Screen.Main) return
        val cm = getSystemService(ClipboardManager::class.java) ?: return
        val clip = cm.primaryClip?.takeIf { it.itemCount > 0 } ?: return
        val url = extractUrl(clip.getItemAt(0)?.coerceToText(this)?.toString()) ?: return
        if (!isSupportedLink(url)) return
        if (url == lastClip) return
        lastClip = url
        homeText = url
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ratio = if (vm.portrait.value == true) Rational(9, 16) else Rational(16, 9)
            runCatching {
                enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(ratio).build())
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
