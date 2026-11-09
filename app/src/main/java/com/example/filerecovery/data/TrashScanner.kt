package com.example.filerecovery.data

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import java.io.File

/**
 * Android 11+ jab file ko Trash/Recently Deleted mein bhejta hai,
 * to file ka naam ".trashed-<expiryEpoch>-<originalName>" ho jaata hai
 * aur wo usi folder mein rehti hai. All-files access ho to hum use dhund sakte hain.
 */
object TrashScanner {

    private val trashRegex = Regex("^\\.trashed-(\\d+)-(.+)$")

    fun scan(
        root: File = Environment.getExternalStorageDirectory(),
        onProgress: (String) -> Unit = {}
    ): List<RecoverableFile> {
        val results = mutableListOf<RecoverableFile>()
        root.walkTopDown()
            .onFail { _, _ -> /* permission nahi mila, skip karo */ }
            .forEach { f ->
                if (!f.isFile) return@forEach
                val match = trashRegex.matchEntire(f.name) ?: return@forEach
                val expiry = match.groupValues[1].toLongOrNull() ?: 0L
                val original = match.groupValues[2]
                onProgress(f.parentFile?.name ?: "/")
                results += RecoverableFile(
                    file = f,
                    originalName = original,
                    expiresAt = expiry,
                    sizeBytes = f.length(),
                    category = categorize(original)
                )
            }
        return results.sortedByDescending { it.expiresAt }
    }

    /** Returns ids of files jo successfully restore hue. */
    fun restore(context: Context, items: List<RecoverableFile>): List<String> {
        val done = mutableListOf<String>()
        val fallbackDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Recovered"
        ).apply { mkdirs() }

        for (item in items) {
            val dir = item.file.parentFile ?: continue
            // Pehle original folder mein rename (sabse sahi restore)
            val target = uniqueIn(dir, item.originalName)
            val ok = item.file.renameTo(target) || runCatching {
                // Fallback: Downloads/Recovered mein copy
                val out = uniqueIn(fallbackDir, item.originalName)
                item.file.copyTo(out)
                item.file.delete()
                MediaScannerConnection.scanFile(context, arrayOf(out.absolutePath), null, null)
                true
            }.getOrDefault(false)

            if (ok) {
                if (target.exists()) {
                    MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null, null)
                }
                done += item.id
            }
        }
        return done
    }

    fun categorize(name: String): FileCategory =
        when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp" -> FileCategory.PHOTOS
            "mp4", "mkv", "3gp", "webm", "mov", "avi" -> FileCategory.VIDEOS
            "mp3", "m4a", "wav", "ogg", "flac", "aac", "opus" -> FileCategory.AUDIO
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt", "csv" ->
                FileCategory.DOCS
            else -> FileCategory.OTHER
        }

    private fun uniqueIn(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.', name)
        val ext = if ('.' in name) "." + name.substringAfterLast('.') else ""
        var i = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($i)$ext")
            i++
        }
        return candidate
    }
}
