package com.linkplayer.app

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {

    lateinit var settings: AppSettings
    lateinit var history: HistoryStore

    var inPip by mutableStateOf(false)
    var pipActive = false
    var pipPlaying = false
    var pipRatio: Rational = Rational(16, 9)

    private var urlText by mutableStateOf("")
    private var headersText by mutableStateOf("")
    private var request by mutableStateOf<PlayRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        settings = AppSettings(getSharedPreferences("settings", MODE_PRIVATE))
        history = HistoryStore(getSharedPreferences("history", MODE_PRIVATE))
        handleIntent(intent)
        setContent { AppTheme { Root() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i?.action == Intent.ACTION_VIEW && i.data != null) {
            play(i.data.toString(), "")
        }
    }

    fun play(u: String, h: String) {
        val url = normalizeUrl(u)
        if (url.isBlank()) return
        urlText = url
        headersText = h
        history.add(url, h.trim())
        request = PlayRequest(url, parseHeaders(h))
    }

    @Composable
    private fun Root() {
        val req = request
        if (req == null) {
            HomeScreen(
                url = urlText,
                onUrl = { urlText = it },
                headers = headersText,
                onHeaders = { headersText = it },
                settings = settings,
                history = history,
                onPlay = { u, h -> play(u, h) }
            )
        } else {
            PlayerScreen(req, settings) { request = null }
        }
    }

    // ---------- Picture in Picture ----------
    val supportsPip: Boolean
        get() = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    fun updatePipParams() {
        if (!supportsPip) return
        try {
            val b = PictureInPictureParams.Builder().setAspectRatio(pipRatio)
            if (Build.VERSION.SDK_INT >= 31) {
                b.setAutoEnterEnabled(pipActive && pipPlaying && settings[K.AUTO_PIP])
            }
            setPictureInPictureParams(b.build())
        } catch (_: Exception) {
        }
    }

    fun enterPip() {
        if (!supportsPip) return
        try {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(pipRatio).build())
        } catch (_: Exception) {
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < 31 && pipActive && pipPlaying && settings[K.AUTO_PIP]) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }
}
