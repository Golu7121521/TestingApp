package com.playvid.presentation.util

import android.text.format.DateUtils
import com.playvid.domain.model.HistoryItem
import kotlin.math.roundToInt

/** 83_000 ms -> "01:23", 3_723_000 ms -> "1:02:03". */
fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** 1f -> "1x", 1.5f -> "1.5x". */
fun speedLabel(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"

/** Small source label for lists: "M3U8", "MP4", "Local"... */
fun HistoryItem.sourceLabel(): String {
    if (isLocal) return "Local"
    val path = source.substringBefore('?').substringBefore('#').lowercase()
    if (path.contains(".m3u8")) return "M3U8"
    val ext = path.substringAfterLast('.', "")
    return if (ext.length in 2..4 && ext.all { it.isLetterOrDigit() }) ext.uppercase() else "Video"
}

/** "M3U8 • 65% watched • 2 hours ago" */
fun HistoryItem.subtitle(): String {
    val ago = DateUtils.getRelativeTimeSpanString(
        lastPlayedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
    ).toString()
    val watched = (progress * 100).roundToInt()
    return if (watched > 0) "${sourceLabel()} • $watched% watched • $ago" else "${sourceLabel()} • $ago"
}
