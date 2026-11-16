package com.universal.videoplayer.resolver

import android.net.Uri
import androidx.media3.common.MimeTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

enum class DrmScheme(val label: String) {
    NONE("None"), WIDEVINE("Widevine"), PLAYREADY("PlayReady"), CLEARKEY("ClearKey")
}

enum class UaOption(val label: String, val value: String?) {
    ANDROID_CHROME("Android Chrome", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"),
    CHROME_DESKTOP("Chrome Desktop", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"),
    FIREFOX_DESKTOP("Firefox Desktop", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:127.0) Gecko/20100101 Firefox/127.0"),
    FIREFOX_ANDROID("Firefox Android", "Mozilla/5.0 (Android 14; Mobile; rv:127.0) Gecko/127.0 Firefox/127.0"),
    CUSTOM("Custom User-Agent", null),
}

data class PlaybackRequest(
    val url: String,
    val userAgent: String? = null,
    val cookie: String = "",
    val referer: String = "",
    val origin: String = "",
    val drmScheme: DrmScheme = DrmScheme.NONE,
    val drmLicense: String = "",
    val title: String? = null,
) {
    fun headers(): Map<String, String> = buildMap {
        if (cookie.isNotBlank()) put("Cookie", cookie.trim())
        if (referer.isNotBlank()) put("Referer", referer.trim())
        if (origin.isNotBlank()) put("Origin", origin.trim())
    }
}

enum class SourceKind { HLS, DASH, SMOOTH, PROGRESSIVE }

sealed class ResolveResult {
    data class Success(val uri: String, val mime: String?, val kind: SourceKind) : ResolveResult()
    data class Unsupported(val reason: String) : ResolveResult()
}

/** Add new resolvers to [Resolvers.all] (first match wins). */
interface SourceResolver {
    fun canHandle(uri: Uri): Boolean
    suspend fun resolve(req: PlaybackRequest): ResolveResult
}

/** Platform pages do not expose direct media URLs; we never bypass their protections. */
object ProtectedPlatformResolver : SourceResolver {
    private val hosts = mapOf(
        "youtube.com" to "YouTube", "youtu.be" to "YouTube", "instagram.com" to "Instagram",
        "tiktok.com" to "TikTok", "netflix.com" to "Netflix", "primevideo.com" to "Prime Video",
        "disneyplus.com" to "Disney+", "hotstar.com" to "Hotstar", "hulu.com" to "Hulu",
        "facebook.com" to "Facebook", "fb.watch" to "Facebook", "twitter.com" to "X",
        "x.com" to "X", "dailymotion.com" to "Dailymotion", "twitch.tv" to "Twitch",
        "vimeo.com" to "Vimeo", "spotify.com" to "Spotify",
    )

    private fun match(uri: Uri): String? {
        val h = uri.host?.lowercase() ?: return null
        return hosts.entries.firstOrNull { h == it.key || h.endsWith("." + it.key) }?.value
    }

    override fun canHandle(uri: Uri) = match(uri) != null

    override suspend fun resolve(req: PlaybackRequest): ResolveResult {
        val name = match(Uri.parse(req.url)) ?: "This platform"
        return ResolveResult.Unsupported(
            "$name page links can't be played directly. The platform does not expose a plain media URL " +
                "and its playback is protected. Paste a direct video link (MP4, HLS .m3u8, DASH .mpd) " +
                "that you are authorized to play."
        )
    }
}

object DirectMediaResolver : SourceResolver {
    private val progressiveExt = setOf(
        "mp4", "m4v", "mkv", "webm", "mov", "3gp", "ts", "mp3", "m4a", "aac", "ogg", "opus", "flac", "wav",
    )

    override fun canHandle(uri: Uri) = true

    private fun byExtension(path: String): SourceKind? = when {
        path.endsWith(".m3u8") -> SourceKind.HLS
        path.endsWith(".mpd") -> SourceKind.DASH
        path.contains(".ism") -> SourceKind.SMOOTH
        progressiveExt.contains(path.substringAfterLast('.', "")) -> SourceKind.PROGRESSIVE
        else -> null
    }

    private fun result(url: String, kind: SourceKind) = ResolveResult.Success(
        url,
        when (kind) {
            SourceKind.HLS -> MimeTypes.APPLICATION_M3U8
            SourceKind.DASH -> MimeTypes.APPLICATION_MPD
            SourceKind.SMOOTH -> MimeTypes.APPLICATION_SS
            SourceKind.PROGRESSIVE -> null
        },
        kind,
    )

    override suspend fun resolve(req: PlaybackRequest): ResolveResult {
        val path = Uri.parse(req.url).path.orEmpty().lowercase()
        byExtension(path)?.let { return result(req.url, it) }
        val ct = probeContentType(req) ?: return result(req.url, SourceKind.PROGRESSIVE)
        return when {
            ct.contains("mpegurl") -> result(req.url, SourceKind.HLS)
            ct.contains("dash+xml") -> result(req.url, SourceKind.DASH)
            ct.contains("vnd.ms-sstr") -> result(req.url, SourceKind.SMOOTH)
            ct.startsWith("text/html") -> ResolveResult.Unsupported(
                "This link opens a web page, not a video file or stream. Paste a direct media link."
            )
            ct.startsWith("text/") || ct.contains("json") || ct.startsWith("image/") -> ResolveResult.Unsupported(
                "The server returned \"$ct\", which is not a playable media type."
            )
            else -> result(req.url, SourceKind.PROGRESSIVE)
        }
    }

    private suspend fun probeContentType(req: PlaybackRequest): String? = withContext(Dispatchers.IO) {
        try {
            val c = URL(req.url).openConnection() as HttpURLConnection
            c.requestMethod = "HEAD"
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", req.userAgent ?: "UniversalVideoPlayer")
            req.headers().forEach { (k, v) -> c.setRequestProperty(k, v) }
            val code = c.responseCode
            val type = c.contentType?.lowercase()?.substringBefore(';')?.trim()
            c.disconnect()
            if (code in 200..299) type else null
        } catch (e: Exception) {
            null
        }
    }
}

object Resolvers {
    private val all: List<SourceResolver> = listOf(ProtectedPlatformResolver, DirectMediaResolver)

    suspend fun resolve(req: PlaybackRequest): ResolveResult {
        val uri = Uri.parse(req.url)
        return all.first { it.canHandle(uri) }.resolve(req)
    }
}
