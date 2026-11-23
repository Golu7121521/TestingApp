package com.example.universalplayer

import android.content.Context
import android.net.Uri
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

private val mediaExt = setOf("mp4", "webm", "mkv", "mov", "m4v", "3gp", "mp3", "m4a", "aac", "ogg", "opus", "flac", "wav", "m3u8", "mpd", "ts")

private val supportedHosts = listOf(
    "youtube.com", "youtu.be", "youtube-nocookie.com", "vimeo.com", "dailymotion.com", "dai.ly",
    "twitch.tv", "twitter.com", "x.com", "facebook.com", "fb.watch", "instagram.com", "tiktok.com",
    "reddit.com", "redd.it", "streamable.com", "bilibili.com", "b23.tv", "soundcloud.com", "ok.ru",
    "vk.com", "rumble.com", "odysee.com", "bitchute.com", "imgur.com", "9gag.com", "tumblr.com",
    "linkedin.com", "pinterest.com", "pin.it", "likee.video", "snapchat.com", "kick.com", "nicovideo.jp",
    "archive.org", "ted.com", "coub.com", "mixcloud.com", "bandcamp.com", "vevo.com", "espn.com",
    "cnn.com", "bbc.com", "bbc.co.uk", "nbcnews.com", "mxplayer.in", "arte.tv", "rtve.es", "vidio.com",
    "dumpert.nl", "gfycat.com", "flickr.com", "metacafe.com", "veoh.com", "weibo.com", "youku.com"
)

/** Only links this app can really play are auto-pasted (direct media files or known video sites). */
fun isSupportedLink(url: String): Boolean {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return false
    val host = uri.host?.lowercase()?.removePrefix("www.") ?: return false
    val path = (uri.path ?: "").lowercase()
    val ext = path.substringAfterLast('.', "")
    if (ext in mediaExt || url.contains(".m3u8", true) || url.contains(".mpd", true)) return true
    val hasPath = path.length > 1 || !uri.query.isNullOrEmpty()
    return hasPath && supportedHosts.any { host == it || host.endsWith(".$it") }
}
