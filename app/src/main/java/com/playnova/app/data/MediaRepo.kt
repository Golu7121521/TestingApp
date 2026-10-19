package com.playnova.app.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the device video index through MediaStore. */
class MediaRepo(private val ctx: Context) {

    suspend fun scan(): List<Video> = withContext(Dispatchers.IO) {
        val out = ArrayList<Video>()
        val proj = arrayOf(
            MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION, MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED, MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT, MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE
        )
        try {
            ctx.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj, null, null,
                MediaStore.Video.Media.DATE_ADDED + " DESC"
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(proj[0])
                val iName = c.getColumnIndexOrThrow(proj[1])
                val iDur = c.getColumnIndexOrThrow(proj[2])
                val iSize = c.getColumnIndexOrThrow(proj[3])
                val iDate = c.getColumnIndexOrThrow(proj[4])
                val iW = c.getColumnIndexOrThrow(proj[5])
                val iH = c.getColumnIndexOrThrow(proj[6])
                val iB = c.getColumnIndexOrThrow(proj[7])
                val iM = c.getColumnIndexOrThrow(proj[8])
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    out.add(
                        Video(
                            id = id,
                            uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                            title = c.getString(iName) ?: "video_$id",
                            durationMs = c.getLong(iDur),
                            size = c.getLong(iSize),
                            dateAdded = c.getLong(iDate),
                            width = c.getInt(iW),
                            height = c.getInt(iH),
                            folder = c.getString(iB) ?: "Unknown",
                            mime = c.getString(iM) ?: "video/*"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PlayNova", "scan failed", e)
        }
        out
    }

    companion object {
        /** Build a Video for a file/URL that is not in MediaStore (picker, VIEW intent, stream). */
        fun external(uri: Uri, name: String? = null): Video {
            val title = name ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: uri.host ?: "Video"
            return Video(
                id = -(uri.toString().hashCode().toLong().let { if (it == Long.MIN_VALUE) 1 else Math.abs(it) } + 1),
                uri = uri, title = title, durationMs = 0, size = 0,
                dateAdded = System.currentTimeMillis() / 1000, width = 0, height = 0,
                folder = "External", mime = "video/*", external = true
            )
        }
    }
}
