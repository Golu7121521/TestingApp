package com.playnova.app.player

import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import com.playnova.app.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Wired / Bluetooth headset button support.
 *  1 press  = play / pause
 *  2 presses = skip forward, 3 presses = skip back (skip interval from settings)
 * Only active while the app is on screen and the setting is on.
 */
class HeadsetController(ctx: Context, private val holder: PlayerHolder, private val settings: SettingsStore) {
    private val session = MediaSession(ctx, "PlayNovaHeadset")
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var clicks = 0
    private var foreground = false
    private var playing = false
    private var wanted = false
    private val flush = Runnable { val n = clicks; clicks = 0; act(n) }

    init {
        @Suppress("DEPRECATION")
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        session.setCallback(object : MediaSession.Callback() {
            override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                if (!settings.state.value.headset) return false
                val ev: KeyEvent? = if (Build.VERSION.SDK_INT >= 33)
                    mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                else @Suppress("DEPRECATION") mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                if (ev == null) return false
                val down = ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0
                return when (ev.keyCode) {
                    KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { if (down) click(); true }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> { if (down) holder.player.play(); true }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> { if (down) holder.player.pause(); true }
                    KeyEvent.KEYCODE_MEDIA_NEXT -> { if (down) holder.player.seekToNextMediaItem(); true }
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { if (down) holder.player.seekToPreviousMediaItem(); true }
                    else -> false
                }
            }
        })
        scope.launch {
            combine(holder.playing, holder.queue, settings.state) { p, q, s -> Triple(p, q.isNotEmpty(), s.headset) }
                .collect { (p, has, on) -> playing = p; wanted = has && on; refresh() }
        }
    }

    fun setForeground(f: Boolean) { foreground = f; refresh() }

    private fun refresh() {
        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP
        val st = PlaybackState.Builder().setActions(actions)
            .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, if (playing) 1f else 0f)
            .build()
        session.setPlaybackState(st)
        session.isActive = wanted && foreground
    }

    private fun click() {
        clicks++
        handler.removeCallbacks(flush)
        if (clicks >= 3) flush.run() else handler.postDelayed(flush, 380)
    }

    private fun act(n: Int) {
        when (n) {
            1 -> holder.toggle()
            2 -> skip(1)
            else -> skip(-1)
        }
    }

    private fun skip(sign: Int) {
        val p = holder.player
        val ms = settings.state.value.skipSec * 1000L * sign
        p.seekTo((p.currentPosition + ms).coerceAtLeast(0L))
    }
}
