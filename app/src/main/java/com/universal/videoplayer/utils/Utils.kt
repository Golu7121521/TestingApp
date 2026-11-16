package com.universal.videoplayer.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

fun domainOf(url: String): String =
    runCatching { Uri.parse(url).host.orEmpty().removePrefix("www.") }.getOrDefault("").ifEmpty { "unknown" }

fun fmtTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun fmtDate(ts: Long): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
        .format(java.util.Date(ts))

object UrlValidator {
    /** Returns a normalized http(s) URL, or null when the text is not a valid link. */
    fun normalize(raw: String, requireScheme: Boolean = false): String? {
        val t = raw.trim()
        if (t.isEmpty() || t.any { it.isWhitespace() }) return null
        if (requireScheme && !t.contains("://")) return null
        val u = if (t.contains("://")) t else "https://$t"
        val uri = runCatching { Uri.parse(u) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        val ok = (uri.scheme == "http" || uri.scheme == "https") && (host.contains('.') || host == "localhost")
        return if (ok) u else null
    }
}
