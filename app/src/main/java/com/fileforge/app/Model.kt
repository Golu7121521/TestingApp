package com.fileforge.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FileItem(
    val path: String,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
)

enum class SortMode(val label: String) {
    NAME("Name"), SIZE("Size"), DATE("Date");

    fun next(): SortMode = entries[(ordinal + 1) % entries.size]
}

class EditorFile(val path: String, val text: String)

fun fmtSize(b: Long): String {
    if (b < 1024) return "$b B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = b / 1024.0
    var i = 0
    while (v >= 1024 && i < units.size - 1) {
        v /= 1024
        i++
    }
    return String.format(Locale.US, "%.1f %s", v, units[i])
}

private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

fun fmtDate(ms: Long): String = if (ms <= 0) "" else dateFmt.format(Date(ms))
