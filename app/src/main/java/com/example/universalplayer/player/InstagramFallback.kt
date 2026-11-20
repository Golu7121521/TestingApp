package com.example.universalplayer.player

import com.example.universalplayer.data.CookieVault
import com.example.universalplayer.data.PlayOptions
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Best-effort backup for Instagram: when yt-dlp is rejected (HTTP 400 etc.), read the public embed page
 * of the reel and pick the video address from it. Does not work for private / age-restricted reels.
 */
object InstagramFallback {
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    fun resolve(url: String, o: PlayOptions): Resolved? {
        val code = Regex("instagram\\.com/(?:[\\w.]+/)?(?:reels?|p|tv)/([A-Za-z0-9_-]+)").find(url)
            ?.groupValues?.get(1) ?: return null
        val cookie = o.cookie.ifBlank { CookieVault.forUrl(url).orEmpty() }.takeIf { !it.contains('\t') }.orEmpty()

        for (path in listOf("p/$code/embed/captioned/", "reel/$code/embed/", "p/$code/embed/")) {
            val html = runCatching { get("https://www.instagram.com/$path", cookie) }.getOrNull() ?: continue
            val clean = html.replace("\\\"", "\"")
            val video = grab(clean, "video_url") ?: continue
            val thumb = grab(clean, "display_url") ?: grab(clean, "thumbnail_src")
            return Resolved(
                title = "Instagram reel $code",
                thumb = thumb,
                isLive = false,
                formats = listOf(
                    Fmt(
                        url = video, height = 0, vcodec = "unknown", acodec = "unknown", protocol = "https",
                        tbr = 0.0, lang = null, langPref = 0,
                        headers = mapOf("User-Agent" to UA, "Referer" to "https://www.instagram.com/")
                    )
                ),
                subs = emptyList(), direct = false, directUrl = null, directMime = null, webUrl = url
            )
        }
        return null
    }

    private fun grab(text: String, key: String): String? {
        val raw = Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1) ?: return null
        val decoded = runCatching { JSONObject("{\"v\":\"$raw\"}").getString("v") }.getOrDefault(raw)
        return decoded.replace("\\/", "/").replace("\\", "").takeIf { it.startsWith("http") }
    }

    private fun get(u: String, cookie: String): String? {
        val c = (URL(u).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            if (cookie.isNotBlank()) setRequestProperty("Cookie", cookie)
        }
        return try {
            if (c.responseCode !in 200..299) null else c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
