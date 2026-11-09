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

data class RecoverableFile(
    val file: File,
    val originalName: String,
    val expiresAt: Long,        // epoch seconds (Android trash expiry)
    val sizeBytes: Long,
    val category: FileCategory
) {
    val id: String get() = file.absolutePath
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "%.1f KB".format(bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}

fun formatExpiry(epochSeconds: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(epochSeconds * 1000))
