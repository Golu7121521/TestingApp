package com.universal.videoplayer

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.universal.videoplayer.player.PlayerViewModel
import com.universal.videoplayer.ui.AppRoot
import com.universal.videoplayer.ui.AppViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val appVm by viewModels<AppViewModel>()
    private val playerVm by viewModels<PlayerViewModel>()

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_PIP_TOGGLE) playerVm.togglePlay()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ContextCompat.registerReceiver(this, pipReceiver, IntentFilter(ACTION_PIP_TOGGLE), ContextCompat.RECEIVER_NOT_EXPORTED)
        setContent { AppRoot(appVm, playerVm, onPip = ::enterPip) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerVm.ui.map { it.playing }.distinctUntilChanged().collect {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && playerVm.inPip) setPictureInPictureParams(pipParams())
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Clipboard is checked only when the window gains focus (open / resume), never continuously.
        if (hasFocus) appVm.onWindowFocus(getSystemService(ClipboardManager::class.java))
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (playerVm.ui.value.active && playerVm.ui.value.playing) enterPip()
    }

    override fun onStop() {
        super.onStop()
        if (!playerVm.inPip) playerVm.pause()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playerVm.inPip = isInPictureInPictureMode
    }

    override fun onDestroy() {
        unregisterReceiver(pipReceiver)
        super.onDestroy()
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        if (!playerVm.ui.value.active) return
        runCatching { enterPictureInPictureMode(pipParams()) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun pipParams(): PictureInPictureParams {
        val ui = playerVm.ui.value
        val b = PictureInPictureParams.Builder()
        if (ui.videoWidth > 0 && ui.videoHeight > 0) {
            val r = (ui.videoWidth.toFloat() / ui.videoHeight).coerceIn(0.45f, 2.3f)
            b.setAspectRatio(Rational((r * 1000).toInt(), 1000))
        }
        val icon = Icon.createWithResource(
            this, if (ui.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
        )
        val pi = PendingIntent.getBroadcast(
            this, 0, Intent(ACTION_PIP_TOGGLE).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        b.setActions(listOf(RemoteAction(icon, "Play or pause", "Play or pause", pi)))
        return b.build()
    }

    companion object {
        private const val ACTION_PIP_TOGGLE = "com.universal.videoplayer.PIP_TOGGLE"
    }
}
