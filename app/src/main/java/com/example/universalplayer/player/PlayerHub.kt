@file:OptIn(UnstableApi::class)

package com.example.universalplayer.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer

/** Creates the single player used by the app. */
object PlayerHub {
    @Volatile
    private var player: ExoPlayer? = null

    fun get(ctx: Context): ExoPlayer {
        player?.let { return it }
        synchronized(this) {
            player?.let { return it }
            val p = ExoPlayer.Builder(ctx.applicationContext)
                .setLoadControl(loadControl(PlayerSettings.bufferLevel))
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(), true
                )
                .setHandleAudioBecomingNoisy(true)
                .setSeekBackIncrementMs(10_000)
                .setSeekForwardIncrementMs(10_000)
                .build()
            player = p
            return p
        }
    }

    fun release() {
        synchronized(this) {
            player?.release()
            player = null
        }
    }

    private fun loadControl(level: Int): DefaultLoadControl {
        // min, max, start-playback, restart-after-stall (ms). Balanced = ExoPlayer defaults.
        val (min, max, start, again) = when (level) {
            0 -> listOf(5_000, 20_000, 1_000, 2_000)
            2 -> listOf(30_000, 120_000, 5_000, 10_000)
            else -> listOf(50_000, 50_000, 2_500, 5_000)
        }
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(min, max, start, again)
            .build()
    }
}
