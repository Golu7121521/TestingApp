@file:OptIn(UnstableApi::class)

package com.netplayer.app

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

class PlayerActivity : ComponentActivity() {
    lateinit var player: ExoPlayer
    private lateinit var req: StreamRequest
    val inPip = mutableStateOf(false)
    private var attempts: List<String?> = emptyList()
    private var attempt = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        val r = StreamRequest.from(intent)
        if (r == null) { finish(); return }
        req = r

        if (Build.VERSION.SDK_INT >= 28) {
            val a = window.attributes
            a.layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30)
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = a
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        hideBars()

        HistoryStore(this).add(req)
        val guess = PlayerFactory.guessMime(req.url)
        attempts = (listOfNotNull(guess) + listOf(null, MimeTypes.APPLICATION_M3U8, MimeTypes.APPLICATION_MPD)).distinct()
        player = PlayerFactory.build(this, req)
        load(0)

        val title = Uri.parse(req.url).host ?: req.url
        setContent { AppTheme { PlayerScreen(this@PlayerActivity, player, title) } }
    }

    private fun load(i: Int) {
        player.setMediaItem(PlayerFactory.mediaItem(req, attempts[i]))
        player.prepare()
        player.playWhenReady = true
    }

    /** true = error handled (retrying with another format / live edge) */
    fun handleError(e: PlaybackException): Boolean {
        if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            player.seekToDefaultPosition(); player.prepare(); return true
        }
        val parse = e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
            e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
            e.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ||
            e.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED
        if (parse && attempt + 1 < attempts.size) { attempt++; load(attempt); return true }
        return false
    }

    fun retry() { attempt = 0; load(0) }

    fun hideBars() {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    fun toggleRotation() {
        requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    fun currentBrightness(): Float {
        val b = window.attributes.screenBrightness
        if (b >= 0f) return b
        return try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        } catch (e: Exception) { 0.5f }
    }

    fun setBrightness(v: Float) {
        val a = window.attributes
        a.screenBrightness = v.coerceIn(0.01f, 1f)
        window.attributes = a
    }

    fun enterPip() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        val vs = player.videoSize
        var f = if (vs.width > 0 && vs.height > 0) vs.width.toFloat() / vs.height else 16f / 9f
        f = f.coerceIn(0.45f, 2.3f)
        try {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational((f * 1000).toInt(), 1000)).build()
            )
        } catch (e: Exception) { }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (player.isPlaying) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
        if (!isInPictureInPictureMode) hideBars()
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode) player.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::player.isInitialized) player.release()
    }
}
