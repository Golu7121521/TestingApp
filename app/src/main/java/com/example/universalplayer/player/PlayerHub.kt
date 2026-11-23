@file:OptIn(UnstableApi::class)

package com.example.universalplayer.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer

/** One process-wide player, shared by the screen (PlayerViewModel) and the background-playback service. */
object PlayerHub {
    @Volatile
    private var player: ExoPlayer? = null

    /** Set by the view model so the notification's next / previous buttons follow the queue. */
    @Volatile var onNext: (() -> Unit)? = null
    @Volatile var onPrev: (() -> Unit)? = null
    @Volatile var canNext: () -> Boolean = { false }
    @Volatile var canPrev: () -> Boolean = { false }

    fun get(ctx: Context): ExoPlayer {
        player?.let { return it }
        synchronized(this) {
            player?.let { return it }
            val app = ctx.applicationContext
            val p = ExoPlayer.Builder(app)
                .setLoadControl(loadControl(PlayerSettings.bufferLevel))
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(), true
                )
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
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
            onNext = null
            onPrev = null
            canNext = { false }
            canPrev = { false }
        }
    }

    private fun loadControl(level: Int): DefaultLoadControl {
        // min, max, start-playback, restart-after-stall (ms)
        val (min, max, start, again) = when (level) {
            0 -> listOf(5_000, 20_000, 1_000, 2_000)
            2 -> listOf(30_000, 120_000, 5_000, 10_000)
            else -> listOf(15_000, 50_000, 2_500, 5_000)
        }
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(min, max, start, again)
            .build()
    }
}

/** Wraps the real player so the media notification can show next / previous for the app's own queue. */
class QueueAwarePlayer(p: Player) : ForwardingPlayer(p) {
    override fun getAvailableCommands(): Player.Commands {
        val b = super.getAvailableCommands().buildUpon()
        if (PlayerHub.canNext()) b.add(Player.COMMAND_SEEK_TO_NEXT)
        if (PlayerHub.canPrev()) b.add(Player.COMMAND_SEEK_TO_PREVIOUS)
        return b.build()
    }

    override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)

    override fun seekToNext() {
        PlayerHub.onNext?.invoke()
    }

    override fun seekToPrevious() {
        PlayerHub.onPrev?.invoke()
    }
}
