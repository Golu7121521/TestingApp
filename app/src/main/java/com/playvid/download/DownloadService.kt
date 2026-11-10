package com.playvid.download

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.playvid.playback.HlsPlaylistUtil
import com.playvid.playback.YouTubeHeadersInterceptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Downloads a video or audio track in the background and saves it to Movies/PlayVid or
 * Music/PlayVid. A direct link is streamed straight to a file. A YouTube video (separate
 * video-only and audio-only streams) is merged into one playable MP4 with Media3 Transformer.
 * Progress, pause/resume and cancel are all coordinated through [DownloadRepository]; one
 * notification per job is kept in sync alongside it.
 */
@UnstableApi
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val activeJobs = AtomicInteger(0)
    private val httpClient by lazy { OkHttpClient.Builder().addInterceptor(YouTubeHeadersInterceptor()).build() }
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
            )
        }
        // Keeps each job's notification (title/progress/pause-resume label) in sync no matter
        // whether pause/resume/cancel was tapped in the notification itself or in the app's
        // Downloads tab — both go through the same DownloadRepository state.
        scope.launch {
            DownloadRepository.jobs.collect { list ->
                list.forEach { job ->
                    if (job.status == DownloadStatus.DOWNLOADING || job.status == DownloadStatus.PAUSED) {
                        notificationManager.notify(
                            job.id,
                            buildProgressNotification(job.id, job.title, job.progress, paused = job.status == DownloadStatus.PAUSED)
                        )
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> { DownloadRepository.pause(intent.getIntExtra(EXTRA_ID, -1)); return START_NOT_STICKY }
            ACTION_RESUME -> { DownloadRepository.resumeJob(intent.getIntExtra(EXTRA_ID, -1)); return START_NOT_STICKY }
            ACTION_CANCEL -> { DownloadRepository.cancel(intent.getIntExtra(EXTRA_ID, -1)); return START_NOT_STICKY }
        }
        val id = intent?.getIntExtra(EXTRA_ID, -1) ?: -1
        val title = intent?.getStringExtra(EXTRA_TITLE)?.ifBlank { null } ?: "video"
        val kind = DownloadKind.valueOf(intent?.getStringExtra(EXTRA_KIND) ?: DownloadKind.VIDEO.name)
        val primaryUrl = intent?.getStringExtra(EXTRA_PRIMARY_URL)
        val audioUrl = intent?.getStringExtra(EXTRA_AUDIO_URL)
        if (id < 0 || primaryUrl == null) return START_NOT_STICKY

        val control = DownloadRepository.controlFor(id)
        val isFirst = activeJobs.getAndIncrement() == 0
        val startNotification = buildProgressNotification(id, title, 0, paused = false)
        if (isFirst) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(this, id, startNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(id, startNotification)
            }
        } else {
            notificationManager.notify(id, startNotification)
        }

        scope.launch {
            DownloadRepository.update(id, progress = 0, status = DownloadStatus.DOWNLOADING)
            val onProgress: (Int) -> Unit = { p ->
                DownloadRepository.update(id, progress = p)
                notificationManager.notify(id, buildProgressNotification(id, title, p, paused = false))
            }
            try {
                val temp = when {
                    kind == DownloadKind.AUDIO -> downloadToTempFile(primaryUrl, control, "m4a", onProgress)
                    audioUrl != null -> mergeToTempFile(id, primaryUrl, audioUrl, control, onProgress)
                    primaryUrl.lowercase().contains(".m3u8") -> downloadHlsToTempFile(primaryUrl, control, onProgress)
                    else -> downloadToTempFile(primaryUrl, control, "mp4", onProgress)
                }
                val savedUri = if (kind == DownloadKind.AUDIO) saveAudioFile(title, temp) else saveToMovies(title, temp)
                temp.delete()
                notificationManager.notify(id, buildDoneNotification(title, savedUri, kind))
                DownloadRepository.remove(id)
            } catch (c: CancellationException) {
                notificationManager.cancel(id)
                DownloadRepository.remove(id)
            } catch (e: Exception) {
                notificationManager.notify(id, buildErrorNotification(title, e))
                DownloadRepository.update(id, status = DownloadStatus.FAILED, error = e.message ?: "Unknown error")
            } finally {
                if (activeJobs.decrementAndGet() == 0) {
                    ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_DETACH)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    // ---- The two download paths -----------------------------------------------------------

    /**
     * Plain link: stream to a temp file using chunked Range requests (~5MB each) instead of one
     * continuous GET. googlevideo (YouTube's CDN) throttles a single long-lived connection down to
     * tens of KB/s; ranged chunk-by-chunk requests, like the official app and yt-dlp use, avoid that.
     * Checks [control] between chunks so the job can be paused (waits in place) or canceled
     * (deletes the partial file and aborts).
     */
    private suspend fun downloadToTempFile(
        url: String,
        control: DownloadControl,
        defaultExt: String,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val ext = url.substringAfterLast('.').substringBefore('?').lowercase()
            .let { if (it.length in 2..4) it else defaultExt }
        val outFile = File(cacheDir, "dl_${System.currentTimeMillis()}.$ext")
        val chunkSize = 5L * 1024 * 1024
        try {
            var total = -1L
            var start = 0L
            var lastNotify = 0L
            outFile.outputStream().use { out ->
                while (total < 0 || start < total) {
                    while (control.paused.get() && !control.canceled.get()) delay(300)
                    if (control.canceled.get()) throw CancellationException("canceled")

                    val end = start + chunkSize - 1
                    val req = Request.Builder().url(url).header("Range", "bytes=$start-$end").build()
                    httpClient.newCall(req).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                        val body = response.body ?: throw IOException("Empty response")
                        if (total < 0) {
                            total = response.header("Content-Range")
                                ?.substringAfterLast('/')?.toLongOrNull()
                                ?: -1L
                        }
                        val bytes = body.bytes()
                        if (bytes.isEmpty()) {
                            total = start // nothing more to read
                            return@use
                        }
                        out.write(bytes)
                        start += bytes.size
                        if (total < 0 && bytes.size.toLong() < chunkSize) total = start // server ignored Range: this was everything

                        val now = System.currentTimeMillis()
                        if (total > 0 && now - lastNotify > 400) {
                            onProgress(((start * 100) / total).toInt())
                            lastNotify = now
                        }
                    }
                }
            }
            onProgress(100)
            outFile
        } catch (t: Throwable) {
            outFile.delete()
            throw t
        }
    }

    /**
     * A downloadable (VOD) HLS stream: fetch the manifest, then fetch every segment (and the
     * init segment for fragmented-MP4 HLS) in order and append them to one file. Both .ts and
     * fragmented-MP4 HLS are valid to just concatenate this way — no transcoding needed.
     */
    private suspend fun downloadHlsToTempFile(
        playlistUrl: String,
        control: DownloadControl,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val plan = HlsPlaylistUtil.resolvePlan(httpClient, playlistUrl)
            ?: throw IOException("Couldn't read the stream's playlist")
        if (plan.segmentUrls.isEmpty()) throw IOException("This stream has no downloadable segments")

        val ext = if (plan.initSegmentUrl != null) "mp4" else "ts"
        val outFile = File(cacheDir, "dl_${System.currentTimeMillis()}.$ext")
        try {
            outFile.outputStream().use { out ->
                plan.initSegmentUrl?.let { out.write(fetchBytes(it)) }
                val total = plan.segmentUrls.size
                plan.segmentUrls.forEachIndexed { index, segUrl ->
                    while (control.paused.get() && !control.canceled.get()) delay(300)
                    if (control.canceled.get()) throw CancellationException("canceled")
                    out.write(fetchBytes(segUrl))
                    onProgress(((index + 1) * 100) / total)
                }
            }
            outFile
        } catch (t: Throwable) {
            outFile.delete()
            throw t
        }
    }

    private fun fetchBytes(url: String): ByteArray {
        httpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            return resp.body?.bytes() ?: throw IOException("Empty response")
        }
    }

    /** YouTube video-only + audio-only: download both, then merge into one MP4 with Media3 Transformer. */
    private suspend fun mergeToTempFile(
        id: Int,
        videoUrl: String,
        audioUrl: String,
        control: DownloadControl,
        onProgress: (Int) -> Unit
    ): File {
        val videoFile = downloadToTempFile(videoUrl, control, "mp4") { p -> onProgress((p * 0.4).toInt()) }
        val audioFile = downloadToTempFile(audioUrl, control, "m4a") { p -> onProgress(40 + (p * 0.2).toInt()) }
        if (control.canceled.get()) {
            videoFile.delete(); audioFile.delete()
            throw CancellationException("canceled")
        }
        DownloadRepository.update(id, status = DownloadStatus.MERGING)

        fun buildComposition(transmux: Boolean): Composition {
            val videoItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(videoFile))).setRemoveAudio(true).build()
            val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(audioFile))).setRemoveVideo(true).build()
            return Composition.Builder(
                listOf(
                    EditedMediaItemSequence.Builder().addItem(videoItem).build(),
                    EditedMediaItemSequence.Builder().addItem(audioItem).build()
                )
            ).setTransmuxVideo(transmux).setTransmuxAudio(transmux).build()
        }

        try {
            // Try a fast stream-copy (no re-encode) first; only transcode if the source codecs
            // turn out not to be compatible with the MP4 container.
            return try {
                runExport(buildComposition(transmux = true), control, onProgress)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                runExport(buildComposition(transmux = false), control, onProgress)
            }
        } finally {
            videoFile.delete()
            audioFile.delete()
        }
    }

    private suspend fun runExport(composition: Composition, control: DownloadControl, onProgress: (Int) -> Unit): File {
        val outFile = File(cacheDir, "dl_${System.currentTimeMillis()}.mp4")
        try {
            return suspendCancellableCoroutine { cont ->
                val transformer = Transformer.Builder(applicationContext)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resume(outFile, null)
                        }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            outFile.delete()
                            if (cont.isActive) cont.resumeWithException(exportException)
                        }
                    })
                    .build()
                transformer.start(composition, outFile.absolutePath)

                val progressJob = scope.launch {
                    val holder = ProgressHolder()
                    while (isActive) {
                        if (control.canceled.get()) {
                            transformer.cancel()
                            if (cont.isActive) cont.resumeWithException(CancellationException("canceled"))
                            break
                        }
                        val state = transformer.getProgress(holder)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(60 + (holder.progress * 0.4).toInt())
                        if (state == Transformer.PROGRESS_STATE_NOT_STARTED) break
                        delay(500)
                    }
                }
                cont.invokeOnCancellation {
                    transformer.cancel()
                    progressJob.cancel()
                }
            }
        } catch (t: Throwable) {
            outFile.delete()
            throw t
        }
    }

    // ---- Saving the finished file -----------------------------------------------------------

    private fun saveToMovies(title: String, file: File): Uri {
        val safeName = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(80).ifBlank { "video" }
        val ext = file.extension.ifBlank { "mp4" }
        val fileName = "$safeName.$ext"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, if (ext == "mp4") "video/mp4" else "video/*")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/PlayVid")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Couldn't create a media entry")
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: throw IOException("Couldn't open output stream")
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        }

        // Android 9 and below: no scoped storage, needs WRITE_EXTERNAL_STORAGE (declared, max SDK 28).
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Storage permission is needed. Grant it in Settings > Apps > PlayVid > Permissions.")
        }
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "PlayVid")
        if (!dir.exists()) dir.mkdirs()
        val outFile = File(dir, fileName)
        file.inputStream().use { input -> outFile.outputStream().use { input.copyTo(it) } }
        MediaScannerConnection.scanFile(this, arrayOf(outFile.absolutePath), null, null)
        return Uri.fromFile(outFile)
    }

    private fun saveAudioFile(title: String, file: File): Uri {
        val safeName = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(80).ifBlank { "audio" }
        val ext = file.extension.ifBlank { "m4a" }
        val fileName = "$safeName.$ext"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.MIME_TYPE, if (ext == "m4a") "audio/mp4" else "audio/*")
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/PlayVid")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Couldn't create a media entry")
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: throw IOException("Couldn't open output stream")
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Storage permission is needed. Grant it in Settings > Apps > PlayVid > Permissions.")
        }
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "PlayVid")
        if (!dir.exists()) dir.mkdirs()
        val outFile = File(dir, fileName)
        file.inputStream().use { input -> outFile.outputStream().use { input.copyTo(it) } }
        MediaScannerConnection.scanFile(this, arrayOf(outFile.absolutePath), null, null)
        return Uri.fromFile(outFile)
    }

    // ---- Notifications ------------------------------------------------------------------

    private fun buildProgressNotification(id: Int, title: String, progress: Int, paused: Boolean): Notification {
        val label = if (paused) "Paused" else "Downloading"
        val pauseResumeIntent = controlIntent(if (paused) ACTION_RESUME else ACTION_PAUSE, id, id * 10 + 1)
        val cancelIntent = controlIntent(ACTION_CANCEL, id, id * 10 + 2)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(label)
            .setContentText(title)
            .setOngoing(!paused)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceIn(0, 100), progress <= 0)
            .addAction(
                if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (paused) "Resume" else "Pause",
                pauseResumeIntent
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
            .build()
    }

    private fun controlIntent(action: String, id: Int, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this, requestCode,
            Intent(this, DownloadService::class.java).setAction(action).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun buildDoneNotification(title: String, uri: Uri, kind: DownloadKind): Notification {
        val mime = if (kind == DownloadKind.AUDIO) "audio/*" else "video/*"
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pending = PendingIntent.getActivity(
            this, uri.hashCode(), viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download complete")
            .setContentText(title)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
    }

    private fun buildErrorNotification(title: String, e: Exception): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download failed")
            .setContentText("$title \u2014 ${e.message ?: "Unknown error"}")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title \u2014 ${e.message ?: "Unknown error"}"))
            .setAutoCancel(true)
            .build()

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val EXTRA_ID = "id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_PRIMARY_URL = "primary_url"
        private const val EXTRA_AUDIO_URL = "audio_url"
        private const val ACTION_PAUSE = "com.playvid.download.action.PAUSE"
        private const val ACTION_RESUME = "com.playvid.download.action.RESUME"
        private const val ACTION_CANCEL = "com.playvid.download.action.CANCEL"

        /**
         * [id] must come from [DownloadRepository.nextId], already registered with an initial
         * QUEUED job so the UI shows it immediately. For [DownloadKind.VIDEO], [audioUrl] null
         * means [primaryUrl] is already a complete file; non-null means merge the two. For
         * [DownloadKind.AUDIO], [primaryUrl] is the audio stream and [audioUrl] is ignored.
         */
        fun enqueue(context: Context, id: Int, title: String, kind: DownloadKind, primaryUrl: String, audioUrl: String?) {
            val intent = Intent(context, DownloadService::class.java)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_KIND, kind.name)
                .putExtra(EXTRA_PRIMARY_URL, primaryUrl)
                .putExtra(EXTRA_AUDIO_URL, audioUrl)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
