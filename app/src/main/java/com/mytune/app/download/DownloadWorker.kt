package com.mytune.app.download

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.mytune.app.MyTuneApp
import com.mytune.app.R
import com.mytune.app.data.db.DownloadStatus
import com.mytune.app.domain.pickQuality
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Streams one song to app-specific storage with a progress notification; resumes via HTTP Range. */
class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    private val notifId get() = (inputData.getString(KEY_ID) ?: "").hashCode()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val container = (applicationContext as MyTuneApp).container
        val dao = container.db.dao()
        val id = inputData.getString(KEY_ID) ?: return@withContext Result.failure()
        val entry = dao.download(id) ?: return@withContext Result.failure()
        val title = dao.song(id)?.title ?: id

        // May be refused on Android 12+ when the app is in the background; the download still runs.
        runCatching { setForeground(foregroundInfo(title, 0, true)) }
        dao.setDownloadStatus(id, DownloadStatus.RUNNING)

        try {
            val song = container.repository.song(id).getOrThrow()
            val q = song.streamUrls.pickQuality(entry.quality) ?: throw IOException("No download URL")
            val url = song.streamUrls.getValue(q)
            val ext = if (url.substringBefore('?').endsWith(".mp3")) "mp3" else "m4a"
            val file = File(container.musicDir(), "$id.$ext")

            var have = if (file.exists()) file.length() else 0L
            val request = Request.Builder().url(url).apply { if (have > 0) header("Range", "bytes=$have-") }.build()

            container.okHttp.newCall(request).execute().use { resp ->
                if (resp.code != 416) { // 416 = partial file already complete
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    val body = resp.body ?: throw IOException("Empty body")
                    val append = resp.code == 206
                    if (!append) have = 0L
                    val length = body.contentLength()
                    val total = if (length > 0) length + have else 0L

                    FileOutputStream(file, append).use { out ->
                        val buf = ByteArray(16 * 1024)
                        var done = have
                        var lastUpdate = 0L
                        body.byteStream().use { input ->
                            while (true) {
                                ensureActive()
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                val now = System.currentTimeMillis()
                                if (now - lastUpdate > 500) { // throttle DB + notification updates
                                    lastUpdate = now
                                    dao.setProgress(id, done, total, file.absolutePath)
                                    val pct = if (total > 0) (done * 100 / total).toInt() else 0
                                    runCatching { setForeground(foregroundInfo(title, pct, total <= 0)) }
                                }
                            }
                        }
                    }
                }
            }

            val size = file.length()
            dao.upsertDownload(
                entry.copy(status = DownloadStatus.DONE, quality = q, filePath = file.absolutePath, totalBytes = size, downloadedBytes = size)
            )
            notifyDone(title, true)
            Result.success()
        } catch (e: CancellationException) {
            withContext(NonCancellable) { dao.setDownloadStatus(id, DownloadStatus.PAUSED) }
            throw e
        } catch (e: Exception) {
            dao.setDownloadStatus(id, DownloadStatus.FAILED)
            notifyDone(title, false)
            Result.failure()
        }
    }

    private fun buildNotification(title: String, progress: Int, indeterminate: Boolean): Notification =
        NotificationCompat.Builder(applicationContext, MyTuneApp.DOWNLOAD_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(applicationContext.getString(R.string.downloading))
            .setProgress(100, progress, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun foregroundInfo(title: String, progress: Int, indeterminate: Boolean): ForegroundInfo {
        val n = buildNotification(title, progress, indeterminate)
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(notifId, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notifId, n)
        }
    }

    private fun notifyDone(title: String, success: Boolean) {
        val text = applicationContext.getString(if (success) R.string.download_complete else R.string.download_failed)
        val n = NotificationCompat.Builder(applicationContext, MyTuneApp.DOWNLOAD_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java).notify(notifId xor 0x5A5A, n)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo("", 0, true)

    companion object {
        const val KEY_ID = "id"
    }
}
