package com.example.filerecovery.data

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import java.io.File

/**
 * Normal (non-root) scan. Teen jagah dekhta hai:
 * 1) Android 11+ trash: ".trashed-<expiry>-<naam>"
 * 2) Hidden trash folders (.Trash, .recycle etc.) jo kuch phones/file managers banate hain
 * 3) Thumbnail cache (.thumbnails, .thumbdata*): deleted photos ka chhota preview
 */
object TrashScanner {

    private val trashedRegex = Regex("^\\.trashed-(\\d+)-(.+)$")
    private val hiddenTrashDirs = setOf(".trash", ".trashed", ".recycle", ".recyclebin", ".deleted")

    fun scan(
        root: File = Environment.getExternalStorageDirectory(),
        onProgress: (String) -> Unit = {}
    ): List<RecoverableFile> {
        val results = mutableListOf<RecoverableFile>()
        var lastDir: File? = null

        root.walkTopDown()
            .onFail { _, _ -> }
            .forEach { f ->
                if (!f.isFile) return@forEach

                val parentFile = f.parentFile
                if (parentFile != lastDir) {
                    lastDir = parentFile
                    onProgress(parentFile?.name ?: "/")
                }
                val parent = parentFile?.name?.lowercase() ?: ""

                val trashed = trashedRegex.matchEntire(f.name)
                when {
                    trashed != null -> results += RecoverableFile(
                        file = f,
                        originalName = trashed.groupValues[2],
                        expiresAt = trashed.groupValues[1].toLongOrNull() ?: 0L,
                        sizeBytes = f.length(),
                        category = categorize(trashed.groupValues[2]),
                        source = Source.TRASH
                    )
                    parent in hiddenTrashDirs -> results += RecoverableFile(
                        file = f,
                        originalName = f.name,
                        expiresAt = 0L,
                        sizeBytes = f.length(),
                        category = categorize(f.name),
                        source = Source.TRASH
                    )
                    (parent == ".thumbnails" || f.name.startsWith(".thumbdata")) && isJpeg(f) ->
                        results += RecoverableFile(
                            file = f,
                            originalName = "thumbnail_${f.nameWithoutExtension}.jpg",
                            expiresAt = 0L,
                            sizeBytes = f.length(),
                            category = FileCategory.PHOTOS,
                            source = Source.THUMBNAIL
                        )
                }
            }
        return results.sortedByDescending { it.expiresAt }
    }

    private fun isJpeg(f: File): Boolean = runCatching {
        f.inputStream().use { s ->
            val b = ByteArray(3)
            s.read(b) == 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte()
        }
    }.getOrDefault(false)

    /** Restore: trash wali files apni jagah rename hoti hain, baaki Downloads/FileRecovery/Restored mein copy. */
    fun restore(context: Context, items: List<RecoverableFile>): List<String> {
        val done = mutableListOf<String>()
        val outDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "FileRecovery/Restored"
        ).apply { mkdirs() }

        for (item in items) {
            val ok = runCatching {
                if (item.restoreInPlace) {
                    val target = uniqueIn(item.file.parentFile!!, item.originalName)
                    if (!item.file.renameTo(target)) error("rename failed")
                    rescan(context, target)
                } else {
                    val target = uniqueIn(outDir, item.originalName)
                    item.file.copyTo(target)
                    if (item.source != Source.THUMBNAIL) item.file.delete()
                    rescan(context, target)
                }
                true
            }.getOrDefault(false)
            if (ok) done += item.id
        }
        return done
    }

    private fun rescan(context: Context, f: File) {
        MediaScannerConnection.scanFile(context, arrayOf(f.absolutePath), null, null)
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
