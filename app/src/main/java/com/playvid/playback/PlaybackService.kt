package com.playvid.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.playvid.MainActivity
import com.playvid.PlayVidApp

/**
 * Wraps the app's single ExoPlayer instance (owned by [PlaybackController]) in a MediaSession and
 * shows an explicit MediaStyle notification (title, seek bar, play/pause, stop) so that, once
 * started as a foreground service, Android keeps decoding/playing audio reliably while the app is
 * backgrounded. Started from MainActivity.onStop() based on Settings > "Keep Playing in
 * Background"; it never creates or releases the player itself, only the session wrapping it.
 *
 * Stops itself as soon as the player is paused/ends, rather than being told to stop by the
 * Activity: a service that's told to stop (from Activity.onStart()) very soon after being told to
 * start (from Activity.onStop()) — e.g. the user glances at recents and immediately taps back in —
 * can be torn down before it ever calls startForeground(), which crashes the app on Android 12+
 * (ForegroundServiceDidNotStartInTimeException). Deciding to stop from the player's own state
 * instead avoids that race entirely.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var notificationManager: NotificationManager

    private val playerListener = object : Player.Listener {
        // playWhenReady (not isPlaying) is the real "user paused it" signal — isPlaying also
        // goes false during ordinary buffering, which must NOT stop this service (that was
        // cutting playback off after only a few seconds on the first network hiccup).
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady) stopSelf() else updateNotification()
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) stopSelf()
            else updateNotification()
        }
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = updateNotification()
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Background playback", NotificationManager.IMPORTANCE_LOW)
            )
        }

        val player = (application as PlayVidApp).container.playback.player
        if (!player.playWhenReady) {
            // Nothing to keep alive for; bail out before even building a session/notification.
            stopSelf()
            return
        }
        player.addListener(playerListener)
        val sessionIntent = Intent(this, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            this, 0, sessionIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, player).setSessionActivity(contentIntent).build()

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> mediaSession?.player?.let { if (it.isPlaying) it.pause() else it.play() }
            ACTION_STOP -> {
                mediaSession?.player?.pause()
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    private fun updateNotification() {
        if (mediaSession == null) return
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val session = mediaSession
        val player = session?.player
        val title = player?.mediaMetadata?.title?.toString()?.ifBlank { null } ?: "PlayVid"
        val isPlaying = player?.isPlaying == true

        val playPauseIntent = serviceIntent(ACTION_PLAY_PAUSE, 1)
        val stopIntent = serviceIntent(ACTION_STOP, 2)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (isPlaying) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause)
            .setContentTitle(title)
            .setContentText("PlayVid")
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                playPauseIntent
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)

        session?.let {
            builder.setStyle(
                MediaStyle()
                    .setMediaSession(it.sessionCompatToken)
                    .setShowActionsInCompactView(0, 1)
            )
        }
        return builder.build()
    }

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this, requestCode, Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    override fun onTaskRemoved(rootIntent: Intent?) {
        // App swiped away from recents entirely: stop unless something is actively playing.
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // Only detaches the session wrapper; PlaybackController still owns the real player.
        mediaSession?.player?.removeListener(playerListener)
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL_ID = "playback_background"
        const val NOTIFICATION_ID = 7100
        const val ACTION_PLAY_PAUSE = "com.playvid.action.PLAY_PAUSE"
        const val ACTION_STOP = "com.playvid.action.STOP"
    }
}
