package com.playvid.presentation.local

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One video found on the device, as returned by [LocalVideoScanner]. */
data class LocalVideo(
    val uri: String,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val folderId: Long,
    val folderName: String
)

/** A folder (MediaStore "bucket") that contains at least one video. */
data class LocalFolder(val id: Long, val name: String, val videoCount: Int)

/** Scans the device's MediaStore video index — no filesystem traversal, works the same on every API level this app supports. */
object LocalVideoScanner {

    suspend fun scanFolders(context: Context): List<LocalFolder> = withContext(Dispatchers.IO) {
        val counts = LinkedHashMap<Long, Pair<String, Int>>()
        val projection = arrayOf(
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, null, null,
            "${MediaStore.Video.Media.BUCKET_DISPLAY_NAME} COLLATE NOCASE ASC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val name = c.getString(nameCol) ?: "Unknown"
                val existing = counts[id]
                counts[id] = name to ((existing?.second ?: 0) + 1)
            }
        }
        counts.map { (id, pair) -> LocalFolder(id = id, name = pair.first, videoCount = pair.second) }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun scanFolder(context: Context, folderId: Long): List<LocalVideo> = withContext(Dispatchers.IO) {
        val out = mutableListOf<LocalVideo>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection,
            "${MediaStore.Video.Media.BUCKET_ID} = ?", arrayOf(folderId.toString()),
            "${MediaStore.Video.Media.DISPLAY_NAME} COLLATE NOCASE ASC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val bucketIdCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
            val bucketNameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            while (c.moveToNext()) {
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, c.getLong(idCol))
                out += LocalVideo(
                    uri = uri.toString(),
                    name = c.getString(nameCol) ?: "video",
                    durationMs = c.getLong(durationCol),
                    sizeBytes = c.getLong(sizeCol),
                    folderId = c.getLong(bucketIdCol),
                    folderName = c.getString(bucketNameCol) ?: "Unknown"
                )
            }
        }
        out
    }
}
