package com.litetube.app.player

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer

/**
 * Ek hi ExoPlayer poore app mein. Mini player aur full player dono isi ko use karte hain,
 * isliye video band kiye bina screen badal sakte hain.
 */
object PlayerHolder {
    private var exo: ExoPlayer? = null

    var currentWatchUrl: String? = null
    var currentTitle: String = ""
    var currentChannel: String = ""

    fun get(ctx: Context): ExoPlayer =
        exo ?: ExoPlayer.Builder(ctx.applicationContext).build().also { exo = it }

    fun stopAndClear() {
        exo?.run {
            stop()
            clearMediaItems()
        }
        currentWatchUrl = null
        currentTitle = ""
        currentChannel = ""
    }
}
