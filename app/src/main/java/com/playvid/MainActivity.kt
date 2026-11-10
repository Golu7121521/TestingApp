package com.playvid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.playvid.domain.model.AppSettings
import com.playvid.domain.model.ThemeMode
import com.playvid.playback.PlaybackService
import com.playvid.presentation.navigation.PlayVidNavHost
import com.playvid.presentation.player.LocalPipController
import com.playvid.presentation.player.PipController
import com.playvid.presentation.theme.PlayVidTheme

class MainActivity : ComponentActivity() {

    private lateinit var pip: PipController
    private val playback by lazy { (application as PlayVidApp).container.playback }

    /** Kept in sync from Settings so the plain (non-Compose) onStop()/onStart() can read it. */
    private var backgroundPlaybackEnabled = true

    /** Text from an incoming "Share" intent (from another app), exposed to Compose. */
    private var sharedText by mutableStateOf<String?>(null)

    // Setup multiple permissions request for Storage and Notifications
    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // You can handle granted/denied states here if needed
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pip = PipController(this)
        addOnPictureInPictureModeChangedListener { info -> pip.inPip = info.isInPictureInPictureMode }
        consumeShareIntent(intent)

        // Determine which permissions we need to request based on Android version
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ requires POST_NOTIFICATIONS and READ_MEDIA_VIDEO
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        } else {
            // Android 12 and below requires READ_EXTERNAL_STORAGE (Important for Android 10)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        // Launch permission request if there are any missing permissions
        if (permissionsToRequest.isNotEmpty()) {
            requestMultiplePermissions.launch(permissionsToRequest.toTypedArray())
        }

        val container = (application as PlayVidApp).container

        setContent {
            val settings by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            // Keep status/navigation bar icon colours readable for the chosen theme.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                )
                onDispose { }
            }

            LaunchedEffect(settings.pipEnabled) { pip.autoEnabled = settings.pipEnabled }
            LaunchedEffect(settings.backgroundPlayback) { backgroundPlaybackEnabled = settings.backgroundPlayback }

            // The player screen hides the status/nav bars itself too, but on a slow cold start
            // (old/low-end devices especially) the window can still be settling well past a
            // normal warm-launch timeframe, so that first hide() is sometimes dropped. Driving
            // the retries from here — the Activity's own window, not a timing-sensitive Compose
            // view deep in the player screen — catches that reliably on the very first video.
            LaunchedEffect(pip.enabled) {
                if (pip.enabled) {
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    val delaysMs = longArrayOf(0, 100, 200, 350, 600, 1000, 1600, 2400, 3500)
                    delaysMs.forEach { d ->
                        if (d > 0) kotlinx.coroutines.delay(d)
                        if (pip.enabled) insetsController.hide(WindowInsetsCompat.Type.systemBars())
                    }
                }
            }

            CompositionLocalProvider(LocalPipController provides pip) {
                PlayVidTheme(darkTheme = dark) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        PlayVidNavHost(sharedText = sharedText, onSharedTextConsumed = { sharedText = null })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeShareIntent(intent)
    }

    /** Another app shared a video link with PlayVid ("Share" sheet): grab the text, Compose plays it. */
    private fun consumeShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText = it }
        }
    }

    /** Home button / app switch while a video is on screen: keep playing in a PiP window. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (pip.enabled && pip.autoEnabled) pip.enter()
    }

    /**
     * Whenever the window regains focus (after the file picker, a system dialog, a swipe-down
     * of the status bar...) hide the bars again if the player is open.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && pip.enabled && !pip.inPip) {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    /**
     * App went to the background (not into PiP). With Settings > "Keep Playing in Background"
     * on, hand the existing player over to a foreground MediaSession service so audio keeps
     * playing reliably instead of pausing; otherwise pause as before.
     */
    override fun onStop() {
        super.onStop()
        if (isInPictureInPictureMode) return
        playback.saveProgress()
        if (backgroundPlaybackEnabled && playback.current.value != null && playback.state.value.isPlaying) {
            ContextCompat.startForegroundService(this, Intent(this, PlaybackService::class.java))
        } else {
            playback.pause()
        }
    }

    /** Nothing to do here now: PlaybackService stops itself once the player pauses/ends, so a
     *  quick "recents -> back in" doesn't race a start against a stop and crash the service. */
    override fun onStart() {
        super.onStart()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) playback.release()
    }
}
