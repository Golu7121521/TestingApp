package com.playvid.presentation.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.util.Rational
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Bridges the Activity's Picture-in-Picture APIs to Compose. */
@Stable
class PipController(private val activity: Activity) {

    /** True while the player screen is open: leaving the app then enters PiP. */
    var enabled by mutableStateOf(false)

    /** Settings.pipEnabled, kept in sync from Compose. Only gates auto-PiP on leaving the app;
     *  the manual PiP button in the player controls always works via [enter]. */
    var autoEnabled by mutableStateOf(true)

    /** True while the activity is shown in the small PiP window. */
    var inPip by mutableStateOf(false)

    val isSupported: Boolean =
        activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    fun enter() {
        if (!isSupported) return
        try {
            activity.enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            )
        } catch (_: IllegalStateException) {
            // PiP disabled by the user or unavailable right now; ignore.
        }
    }
}

val LocalPipController = staticCompositionLocalOf<PipController> {
    error("PipController not provided")
}
