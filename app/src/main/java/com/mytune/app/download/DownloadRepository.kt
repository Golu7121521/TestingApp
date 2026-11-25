package com.mytune.app.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.mytune.app.data.db.DownloadEntity
import com.mytune.app.data.db.DownloadStatus
import com.mytune.app.data.db.LibraryDao
import com.mytune.app.data.repository.toEntity
import com.mytune.app.di.AppContainer
import com.mytune.app.domain.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/** Queues and controls downloads. Progress lives in Room, so the UI just observes [items]. */
class DownloadRepository(
    private val context: Context,
    private val dao: LibraryDao,
    private val container: AppContainer,
) {
    val items = dao.downloads()
    fun statusOf(id: String): Flow<DownloadEntity?> = dao.downloadFlow(id)

    private val wm get() = WorkManager.getInstance(context)

    suspend fun enqueue(song: Song, quality: Int) {
        val existing = dao.download(song.id)
        if (existing?.status == DownloadStatus.DONE) return
        dao.upsertSong(song.toEntity())
        dao.upsertDownload(
            DownloadEntity(
                songId = song.id,
                status = DownloadStatus.QUEUED,
                quality = quality,
                filePath = existing?.filePath.orEmpty(),
                totalBytes = existing?.totalBytes ?: 0L,
                downloadedBytes = existing?.downloadedBytes ?: 0L,
                createdAt = System.currentTimeMillis(),
            )
        )
        schedule(song.id)
    }

    private fun schedule(id: String) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_ID to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniqueWork("dl_$id", ExistingWorkPolicy.REPLACE, request)
    }

    suspend fun pause(id: String) {
        wm.cancelUniqueWork("dl_$id")
        dao.setDownloadStatus(id, DownloadStatus.PAUSED)
    }

    /** Resume a paused download or retry a failed one (continues from the partial file). */
    suspend fun resume(id: String) {
        dao.setDownloadStatus(id, DownloadStatus.QUEUED)
        schedule(id)
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        wm.cancelUniqueWork("dl_$id")
        dao.download(id)?.filePath?.takeIf { it.isNotBlank() }?.let { File(it).delete() }
        dao.deleteDownload(id)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        wm.cancelAllWork()
        container.musicDir().listFiles()?.forEach { it.delete() }
        dao.deleteAllDownloads()
    }
}
