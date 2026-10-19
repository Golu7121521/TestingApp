package com.playnova.app.data

import android.net.Uri

data class Video(
    val id: Long,
    val uri: Uri,
    val title: String,
    val durationMs: Long,
    val size: Long,
    val dateAdded: Long,
    val width: Int,
    val height: Int,
    val folder: String,
    val mime: String,
    val external: Boolean = false
) {
    val ext: String get() = title.substringAfterLast('.', "").lowercase()
    val resolution: String get() = if (width > 0 && height > 0) "${width}x$height" else "-"
}

data class Folder(val name: String, val count: Int, val thumb: Video)

fun fmtTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

fun fmtSize(b: Long): String {
    if (b <= 0) return "-"
    val mb = b / 1048576.0
    return if (mb >= 1024) "%.2f GB".format(mb / 1024) else "%.1f MB".format(mb)
}
