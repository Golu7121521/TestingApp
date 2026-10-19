package com.playnova.app.player

import android.net.Uri
import androidx.media3.common.MimeTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

enum class LinkKind { MEDIA, HLS, DASH, WEBPAGE }

/** Turns whatever the user pastes into something ExoPlayer can open, like a browser address bar would. */
object UrlResolver {
    const val UA = "Mozilla/5.0 (Linux; Android 11; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private val knownMedia = listOf(".mp4", ".mkv", ".webm", ".mov", ".avi", ".3gp", ".m4v", ".ts", ".flv", ".mp3", ".m4a", ".aac", ".ogg", ".opus", ".wav", ".flac")

    /** Trim, strip quotes, add https:// when no scheme is given, encode spaces. Returns null for blank input. */
    fun normalize(raw: String): Uri? {
        var s = raw.trim().trim('"', '\'', '<', '>').replace("\n", "").replace("\r", "")
        if (s.isEmpty()) return null
        s = s.replace(" ", "%20")
        val hasScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(s)
        if (!hasScheme) {
            s = when {
                s.startsWith("//") -> "https:$s"
                s.startsWith("/") -> "file://$s"
                else -> "https://$s"
            }
        }
        return runCatching { Uri.parse(s) }.getOrNull()?.takeIf { !it.host.isNullOrBlank() || it.scheme == "file" || it.scheme == "content" }
    }

    fun isRemote(u: Uri) = u.scheme.let { it != null && it != "file" && it != "content" }

    fun mimeFor(k: LinkKind): String? = when (k) {
        LinkKind.HLS -> MimeTypes.APPLICATION_M3U8
        LinkKind.DASH -> MimeTypes.APPLICATION_MPD
        else -> null
    }

    /**
     * Works out what a link is. Clear cases (".m3u8", ".mp4"...) need no network.
     * Dynamic links without extension (redirects, tokens, php/aspx endpoints) are probed with a tiny ranged GET.
     */
    suspend fun detect(uri: Uri): LinkKind = withContext(Dispatchers.IO) {
        val s = uri.toString().lowercase()
        val path = (uri.path ?: "").lowercase()
        if (!uri.scheme.orEmpty().startsWith("http")) return@withContext LinkKind.MEDIA
        if (path.endsWith(".m3u8") || s.contains(".m3u8")) return@withContext LinkKind.HLS
        if (path.endsWith(".mpd") || s.contains(".mpd")) return@withContext LinkKind.DASH
        if (knownMedia.any { path.endsWith(it) }) return@withContext LinkKind.MEDIA
        probe(uri.toString())
    }

    private fun probe(start: String): LinkKind {
        var url = start
        repeat(8) {
            var c: HttpURLConnection? = null
            try {
                c = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000; readTimeout = 8000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", UA)
                    setRequestProperty("Range", "bytes=0-2047")
                    setRequestProperty("Accept", "*/*")
                }
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: return LinkKind.MEDIA
                    url = URL(URL(url), loc).toString()
                    return@repeat
                }
                val type = (c.contentType ?: "").lowercase()
                if (type.contains("mpegurl")) return LinkKind.HLS
                if (type.contains("dash+xml")) return LinkKind.DASH
                if (type.startsWith("video/") || type.startsWith("audio/") || type.contains("octet-stream")) {
                    if (!type.contains("octet-stream")) return LinkKind.MEDIA
                }
                val head = runCatching { c.inputStream.use { it.readNBytesCompat(512) } }.getOrDefault("")
                return when {
                    head.trimStart().startsWith("#EXTM3U") -> LinkKind.HLS
                    head.contains("<MPD") -> LinkKind.DASH
                    type.contains("text/html") || head.trimStart().startsWith("<!doctype html", true) || head.trimStart().startsWith("<html", true) -> LinkKind.WEBPAGE
                    else -> LinkKind.MEDIA
                }
            } catch (e: Exception) {
                return LinkKind.MEDIA
            } finally {
                c?.disconnect()
            }
        }
        return LinkKind.MEDIA
    }

    private fun java.io.InputStream.readNBytesCompat(n: Int): String {
        val buf = ByteArray(n)
        var off = 0
        while (off < n) { val r = read(buf, off, n - off); if (r <= 0) break; off += r }
        return String(buf, 0, off, Charsets.ISO_8859_1)
    }
}
