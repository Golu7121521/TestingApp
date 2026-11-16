package com.example.universalplayer

import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import java.util.Locale

private val urlRegex = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)

fun extractUrl(text: String?): String? = text?.let { urlRegex.find(it)?.value }

fun normalizeUrl(input: String): String? {
    val t = input.trim()
    extractUrl(t)?.let { return it }
    if (!t.contains(' ') && t.contains('.') && t.length > 3) return "https://$t"
    return null
}

fun langLabel(tag: String): String =
    if (tag == "und") "Default"
    else Locale.forLanguageTag(tag).getDisplayName(Locale.ENGLISH).ifBlank { tag }

fun formatTime(ms: Long): String {
    val s = (ms.coerceAtLeast(0)) / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
