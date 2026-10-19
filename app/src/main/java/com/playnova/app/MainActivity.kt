package com.playnova.app

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.content.Context
import com.playnova.app.data.AppIcon
import com.playnova.app.i18n.Lang
import com.playnova.app.player.PipState
import com.playnova.app.ui.AppRoot
import com.playnova.app.ui.enterPip

class MainActivity : ComponentActivity() {
    private var external by mutableStateOf<Uri?>(null)
    private val app get() = application as PlayNovaApp

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        app.headset // create the headset-button session
        if (savedInstanceState == null) takeIntent(intent)
        setContent { AppRoot(external) { external = null } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeIntent(intent)
    }

    private fun takeIntent(i: Intent?) {
        if (i?.action == Intent.ACTION_VIEW && i.data != null) external = i.data
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val h = app.holder
        if (PipState.playerVisible && h.player.isPlaying && app.settings.state.value.autoPip) {
            val vs = h.player.videoSize
            enterPip(this, vs.width, vs.height)
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PipState.inPip = isInPictureInPictureMode
    }

    override fun onStart() {
        super.onStart()
        app.headset.setForeground(true)
    }

    override fun onStop() {
        super.onStop()
        app.headset.setForeground(false)
        AppIcon.apply(this, app.settings.state.value.accent)
        // No background service: pause when the app is no longer visible (also when PiP window is closed).
        if (app.holder.hasMedia) { app.holder.player.pause(); app.holder.saveProgress() }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && app.holder.hasMedia) app.holder.close()
    }
}
