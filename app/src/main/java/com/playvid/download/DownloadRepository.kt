package com.playvid.download

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Per-job pause/cancel flags. DownloadService reads these; the UI (via [DownloadRepository]) sets them. */
class DownloadControl {
    val paused = AtomicBoolean(false)
    val canceled = AtomicBoolean(false)
}

private val ACTIVE_STATUSES = setOf(
    DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING, DownloadStatus.MERGING, DownloadStatus.PAUSED
)

/**
 * Process-wide registry of download jobs. [DownloadService] writes progress into it as it works;
 * the Download tab observes [jobs] and calls [pause]/[resumeJob]/[cancel] to control a running job.
 * Jobs are in-memory only (an active download does not survive the app process dying); finished
 * files are discovered separately by scanning MediaStore via [listCompleted].
 */
object DownloadRepository {

    private val idCounter = AtomicInteger(9000)
    fun nextId(): Int = idCounter.getAndIncrement()

    private val _jobs = MutableStateFlow<List<DownloadJob>>(emptyList())
    val jobs: StateFlow<List<DownloadJob>> = _jobs

    private val controls = ConcurrentHashMap<Int, DownloadControl>()
    fun controlFor(id: Int): DownloadControl = controls.getOrPut(id) { DownloadControl() }

    fun upsert(job: DownloadJob) {
        _jobs.update { list -> listOf(job) + list.filterNot { it.id == job.id } }
    }

    fun update(id: Int, progress: Int? = null, status: DownloadStatus? = null, error: String? = null) {
        _jobs.update { list ->
            list.map {
                if (it.id == id) {
                    it.copy(progress = progress ?: it.progress, status = status ?: it.status, error = error ?: it.error)
                } else it
            }
        }
    }

    fun remove(id: Int) {
        controls.remove(id)
        _jobs.update { list -> list.filterNot { it.id == id } }
    }

    fun pause(id: Int) {
        controlFor(id).paused.set(true)
        update(id, status = DownloadStatus.PAUSED)
    }

    fun resumeJob(id: Int) {
        controlFor(id).paused.set(false)
        update(id, status = DownloadStatus.DOWNLOADING)
    }

    /** Aborts an active job, or just dismisses the row if the job already finished/failed. */
    fun cancel(id: Int) {
        val status = _jobs.value.firstOrNull { it.id == id }?.status
        if (status != null && status !in ACTIVE_STATUSES) {
            remove(id)
        } else {
            controlFor(id).paused.set(false)
            controlFor(id).canceled.set(true)
        }
    }

    // ---- Finished downloads, found on disk (not tracked here while the app is running) ----

    suspend fun listCompleted(context: Context): List<CompletedItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<CompletedItem>()
        items += queryCollection(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "Movies/PlayVid", DownloadKind.VIDEO)
        items += queryCollection(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "Music/PlayVid", DownloadKind.AUDIO)
        items.sortedByDescending { it.dateAddedSec }
    }

    private fun queryCollection(context: Context, collection: Uri, relativePathPrefix: String, kind: DownloadKind): List<CompletedItem> {
        val items = mutableListOf<CompletedItem>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return items // no RELATIVE_PATH to filter by pre-Q
        try {
            context.contentResolver.query(
                collection, projection,
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?", arrayOf("$relativePathPrefix%"),
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                while (c.moveToNext()) {
                    val uri = ContentUris.withAppendedId(collection, c.getLong(idCol))
                    items += CompletedItem(
                        uri = uri.toString(),
                        name = c.getString(nameCol) ?: "download",
                        kind = kind,
                        sizeBytes = c.getLong(sizeCol),
                        dateAddedSec = c.getLong(dateCol)
                    )
                }
            }
        } catch (_: Exception) {
            // Column/permission issue: just show nothing extra rather than crash the screen.
        }
        return items
    }
}
