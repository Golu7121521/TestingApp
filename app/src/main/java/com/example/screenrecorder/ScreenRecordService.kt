package com.example.screenrecorder

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenRecordService : Service() {

    companion object {
        const val ACTION_START = "com.example.screenrecorder.ACTION_START"
        const val ACTION_STOP = "com.example.screenrecorder.ACTION_STOP"
        const val ACTION_STATE_CHANGED = "com.example.screenrecorder.ACTION_STATE_CHANGED"

        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        const val EXTRA_RECORD_AUDIO = "EXTRA_RECORD_AUDIO"
        const val EXTRA_FPS = "EXTRA_FPS"
        const val EXTRA_IS_RECORDING = "EXTRA_IS_RECORDING"
        const val EXTRA_SAVED_MESSAGE = "EXTRA_SAVED_MESSAGE"

        private const val CHANNEL_ID = "screen_recorder_channel"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        var isRecording: Boolean = false

        @Volatile
        var recordingStartElapsedMs: Long = 0L
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var outputPfd: ParcelFileDescriptor? = null
    private var outputUri: Uri? = null
    private var outputFileName: String = ""

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopRecordingAndCleanUp("Screen capture ended by system")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!isRecording) {
                    val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                    val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(EXTRA_RESULT_DATA)
                    }
                    val recordAudio = intent.getBooleanExtra(EXTRA_RECORD_AUDIO, false)
                    val fps = intent.getIntExtra(EXTRA_FPS, 30)

                    if (resultCode == Activity.RESULT_OK && resultData != null) {
                        startForegroundNotification()
                        startRecording(resultCode, resultData, recordAudio, fps)
                    } else {
                        stopSelf()
                    }
                }
            }
            ACTION_STOP -> {
                stopRecordingAndCleanUp("Saved to Movies/ScreenRecorder/$outputFileName")
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        createNotificationChannel()

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ScreenRecordService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Recording Active")
            .setContentText("Tap Stop to finish and save your video")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop Recording", stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startRecording(resultCode: Int, resultData: Intent, recordAudio: Boolean, fps: Int) {
        try {
            val metrics = getScreenMetrics()
            val (width, height) = getScaledResolution(metrics.widthPixels, metrics.heightPixels)
            val density = metrics.densityDpi

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            outputFileName = "REC_$timestamp.mp4"

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            if (recordAudio) {
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            }
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, outputFileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/ScreenRecorder")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Failed to create MediaStore entry")
                outputUri = uri
                outputPfd = contentResolver.openFileDescriptor(uri, "w")
                recorder.setOutputFile(outputPfd!!.fileDescriptor)
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "ScreenRecorder"
                )
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, outputFileName)
                recorder.setOutputFile(file.absolutePath)
            }

            recorder.setVideoSize(width, height)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (recordAudio) {
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioEncodingBitRate(128_000)
                recorder.setAudioSamplingRate(44_100)
            }
            val bitRate = if (fps >= 60) 10_000_000 else 6_000_000
            recorder.setVideoEncodingBitRate(bitRate)
            recorder.setVideoFrameRate(fps)
            recorder.prepare()

            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = projectionManager.getMediaProjection(resultCode, resultData)
                ?: throw IllegalStateException("MediaProjection returned null")
            projection.registerCallback(projectionCallback, null)

            val display = projection.createVirtualDisplay(
                "ScreenRecorderDisplay",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder.surface,
                null,
                null
            )

            recorder.start()

            mediaRecorder = recorder
            mediaProjection = projection
            virtualDisplay = display
            isRecording = true
            recordingStartElapsedMs = SystemClock.elapsedRealtime()

            broadcastState(true, null)
        } catch (e: Exception) {
            e.printStackTrace()
            stopRecordingAndCleanUp("Recording failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private fun stopRecordingAndCleanUp(message: String?) {
        if (!isRecording && mediaRecorder == null && mediaProjection == null) {
            stopSelf()
            return
        }

        isRecording = false
        recordingStartElapsedMs = 0L

        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {
        }

        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (_: Exception) {
        }
        mediaRecorder = null

        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        virtualDisplay = null

        try {
            mediaProjection?.unregisterCallback(projectionCallback)
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        mediaProjection = null

        try {
            outputPfd?.close()
        } catch (_: Exception) {
        }
        outputPfd = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && outputUri != null) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                contentResolver.update(outputUri!!, values, null, null)
            } catch (_: Exception) {
            }
            outputUri = null
        }

        broadcastState(false, message)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun broadcastState(recording: Boolean, message: String?) {
        val intent = Intent(ACTION_STATE_CHANGED).apply {
            setPackage(packageName)
            putExtra(EXTRA_IS_RECORDING, recording)
            if (message != null) {
                putExtra(EXTRA_SAVED_MESSAGE, message)
            }
        }
        sendBroadcast(intent)
    }

    private fun getScreenMetrics(): DisplayMetrics {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    private fun getScaledResolution(rawWidth: Int, rawHeight: Int): Pair<Int, Int> {
        val maxDimension = 1920
        val largest = maxOf(rawWidth, rawHeight)
        val scale = if (largest > maxDimension) maxDimension.toFloat() / largest.toFloat() else 1f
        // Ensure dimensions are even numbers as required by H.264 hardware encoders
        val w = ((rawWidth * scale).toInt() / 2) * 2
        val h = ((rawHeight * scale).toInt() / 2) * 2
        return Pair(maxOf(w, 2), maxOf(h, 2))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ongoing screen recording controls"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopRecordingAndCleanUp(null)
        super.onDestroy()
    }
}
