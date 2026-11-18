package com.gamerec.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast

class RecorderService : Service() {

    companion object {
        const val ACTION_START = "com.gamerec.app.START"
        const val ACTION_STOP = "com.gamerec.app.STOP"
        const val EXTRA_RESULT_CODE = "rc"
        const val EXTRA_DATA = "data"
        const val EXTRA_SHORT = "short"
        const val EXTRA_FPS = "fps"
        const val EXTRA_MBPS = "mbps"
        const val EXTRA_HEVC = "hevc"
        const val EXTRA_ORIENT = "orient"
        const val EXTRA_AUDIO = "audio"

        private const val CHANNEL_ID = "rec"
        private const val NOTIF_ID = 1

        @Volatile
        var isRecording = false
    }

    private var projection: MediaProjection? = null
    private var recorder: ScreenRecorder? = null
    private val main = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording(intent)
            ACTION_STOP -> stopRecording()
            else -> if (!isRecording) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startRecording(intent: Intent) {
        if (isRecording) return

        // Android 14+: MediaProjection lene se pehle foreground service chalu honi chahiye
        startForeground(
            NOTIF_ID, buildNotification("Recording shuru ho rahi hai..."),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )

        try {
            val code = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            @Suppress("DEPRECATION")
            val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
                ?: throw IllegalStateException("Permission data nahi mila")

            val mpm = getSystemService(MediaProjectionManager::class.java)
            val mp = mpm.getMediaProjection(code, data)
            mp.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    main.post { stopRecording() }
                }
            }, main)
            projection = mp

            val cfg = RecConfig(
                shortSide = intent.getIntExtra(EXTRA_SHORT, 1080),
                fps = intent.getIntExtra(EXTRA_FPS, 60),
                mbps = intent.getIntExtra(EXTRA_MBPS, 20),
                hevc = intent.getBooleanExtra(EXTRA_HEVC, false),
                orientation = intent.getIntExtra(EXTRA_ORIENT, 0),
                audio = intent.getBooleanExtra(EXTRA_AUDIO, true)
            )
            val r = ScreenRecorder(this, mp, cfg)
            r.start()
            recorder = r
            isRecording = true

            getSystemService(NotificationManager::class.java)
                .notify(NOTIF_ID, buildNotification("Recording: " + r.infoText))
        } catch (e: Exception) {
            Toast.makeText(this, "Recording shuru nahi hui: " + e.message, Toast.LENGTH_LONG).show()
            cleanup()
        }
    }

    private fun stopRecording() {
        val r = recorder
        if (r == null) {
            cleanup()
            return
        }
        recorder = null
        isRecording = false
        // Encoder band karna time leta hai, isliye main thread par nahi
        Thread {
            r.stop()
            main.post {
                Toast.makeText(
                    this,
                    if (r.savedOk) "Saved: Movies/GameRecorder" else "Recording save nahi hui",
                    Toast.LENGTH_LONG
                ).show()
                cleanup()
            }
        }.start()
    }

    private fun cleanup() {
        isRecording = false
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
        recorder = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        recorder?.let { Thread { it.stop() }.start() }
        isRecording = false
        super.onDestroy()
    }

    private fun buildNotification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Screen recording", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stop = PendingIntent.getService(
            this, 0, Intent(this, RecorderService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_rec)
            .setContentTitle("Game Recorder")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }
}
