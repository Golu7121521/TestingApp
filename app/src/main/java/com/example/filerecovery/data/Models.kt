package com.example.filerecovery.data

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FileCategory(val label: String) {
    ALL("Sab"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCS("Documents"),
    OTHER("Other")
}

enum class Source(val label: String) {
    TRASH("Trash"),
    THUMBNAIL("Thumbnail cache"),
    CARVED("Deep scan (root)")
}

data class RecoverableFile(
    val file: File,
    val originalName: String,
    val expiresAt: Long,        // epoch seconds, sirf Android 11+ trash ke liye
    val sizeBytes: Long,
    val category: FileCategory,
    val source: Source
) {
    val id: String get() = file.absolutePath

    /** true = file ko usi folder mein rename karke restore kar sakte hain */
    val restoreInPlace: Boolean
        get() = source == Source.TRASH && file.name.startsWith(".trashed-")
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "%.1f KB".format(bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}

fun formatExpiry(epochSeconds: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(epochSeconds * 1000))

fun categorize(name: String): FileCategory =
    when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp" -> FileCategory.PHOTOS
        "mp4", "mkv", "3gp", "webm", "mov", "avi" -> FileCategory.VIDEOS
        "mp3", "m4a", "wav", "ogg", "flac", "aac", "opus" -> FileCategory.AUDIO
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt", "csv" ->
            FileCategory.DOCS
        else -> FileCategory.OTHER
    }
