package com.mytune.app.player

import android.app.ActivityManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.mytune.app.MainActivity
import com.mytune.app.MyTuneApp
import com.mytune.app.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Background playback + notification (with Like and Close buttons), lock screen and Bluetooth controls. */
class PlaybackService : MediaSessionService() {
    companion object {
        /** Audio session of the ExoPlayer. */
        @Volatile var audioSessionId: Int = 0
        private const val CMD_LIKE = "com.mytune.app.LIKE"
        private const val CMD_CLOSE = "com.mytune.app.CLOSE"
    }

    private var session: MediaSession? = null
    private var likeJob: Job? = null
    private var pressCount = 0
    private val pressHandler = Handler(Looper.getMainLooper())
    private val likeCmd = SessionCommand(CMD_LIKE, Bundle.EMPTY)
    private val closeCmd = SessionCommand(CMD_CLOSE, Bundle.EMPTY)

    private fun layout(liked: Boolean): ImmutableList<CommandButton> = ImmutableList.of(
        CommandButton.Builder()
            .setDisplayName(if (liked) "Unlike" else "Like")
            .setIconResId(if (liked) R.drawable.ic_notif_liked else R.drawable.ic_notif_like)
            .setSessionCommand(likeCmd)
            .build(),
        CommandButton.Builder()
            .setDisplayName("Close")
            .setIconResId(R.drawable.ic_notif_close)
            .setSessionCommand(closeCmd)
            .build(),
    )

    override fun onCreate() {
        super.onCreate()
        val container = (application as MyTuneApp).container

        val dataSourceFactory = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(this, DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)),
            container.resolver,
        )
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        audioSessionId = player.audioSessionId
        container.equalizer.attach(player.audioSessionId)

        setMediaNotificationProvider(DefaultMediaNotificationProvider(this).apply {
            setSmallIcon(R.drawable.ic_notification)
        })

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val s = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .setCustomLayout(layout(false))
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(mediaSession: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    val cmds = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(likeCmd).add(closeCmd).build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(mediaSession)
                        .setAvailableSessionCommands(cmds)
                        .build()
                }

                // Earphone button: 1 press = play/pause, 2 = next, 3 = previous.
                override fun onMediaButtonEvent(mediaSession: MediaSession, controllerInfo: MediaSession.ControllerInfo, intent: Intent): Boolean {
                    val ev = if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent::class.java)
                    else @Suppress("DEPRECATION") intent.getParcelableExtra<android.view.KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    if (ev == null) return false
                    if (ev.keyCode != android.view.KeyEvent.KEYCODE_HEADSETHOOK && ev.keyCode != android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) return false
                    if (ev.action == android.view.KeyEvent.ACTION_DOWN && ev.repeatCount == 0) {
                        pressCount++
                        pressHandler.removeCallbacksAndMessages(null)
                        pressHandler.postDelayed({
                            val p = mediaSession.player
                            when (pressCount) {
                                1 -> if (p.isPlaying) p.pause() else p.play()
                                2 -> p.seekToNext()
                                else -> p.seekToPrevious()
                            }
                            pressCount = 0
                        }, 400)
                    }
                    return true
                }

                override fun onCustomCommand(
                    mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand, args: Bundle,
                ): ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        CMD_LIKE -> mediaSession.player.currentMediaItem?.let { item ->
                            container.appScope.launch { container.library.toggleFavorite(item.toSong()) }
                        }
                        CMD_CLOSE -> shutDownCompletely()
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                // Controllers send items without a URI; rebuild it from the media id.
                override fun onAddMediaItems(
                    mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> {
                    val resolved = mediaItems.map {
                        it.buildUpon().setUri("${StreamResolver.SCHEME}://song/${it.mediaId}").build()
                    }.toMutableList()
                    return Futures.immediateFuture(resolved)
                }
            })
            .build()
        session = s

        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                PlaybackService.audioSessionId = audioSessionId
                container.equalizer.attach(audioSessionId)
            }

            // Keep the Like button in the notification in sync with the favourite state of the current song.
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                likeJob?.cancel()
                val id = mediaItem?.mediaId ?: return
                likeJob = container.appScope.launch {
                    container.library.isFavorite(id).collectLatest { s.setCustomLayout(layout(it)) }
                }
            }
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Stops playback, removes the notification and the app from recents, then ends the process. */
    private fun shutDownCompletely() {
        runCatching { session?.player?.run { pause(); stop(); clearMediaItems() } }
        runCatching { (getSystemService(ACTIVITY_SERVICE) as ActivityManager).appTasks.forEach { it.finishAndRemoveTask() } }
        stopSelf()
        Handler(Looper.getMainLooper()).postDelayed({ android.os.Process.killProcess(android.os.Process.myPid()) }, 400)
    }

    /** App swiped away from Recents: stop everything and free the RAM. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        shutDownCompletely()
    }

    override fun onDestroy() {
        (application as MyTuneApp).container.equalizer.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
