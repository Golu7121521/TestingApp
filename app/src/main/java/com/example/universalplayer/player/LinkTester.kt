package com.example.universalplayer.player

import android.util.Base64
import com.example.universalplayer.data.PlayOptions
import com.example.universalplayer.data.normalizeDrmScheme
import com.example.universalplayer.data.splitLinkOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Checks a link the way the player would fetch it and explains what is wrong (403, expired token, wrong key ...). */
object LinkTester {
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36"

    fun hint(code: Int): String = when (code) {
        in 200..299 -> "OK"
        401 -> "Unauthorized: needs login / token / Cookie."
        403 -> "Blocked or expired: set Referer, Origin or User-Agent in Advanced, or get a fresh link (token may have expired)."
        404 -> "Not found: the link is wrong or the stream was removed."
        410 -> "Gone: the link has expired."
        429 -> "Too many requests: wait a bit and try again."
        in 500..599 -> "Server problem: try again later."
        else -> ""
    }

    suspend fun test(raw: String, base: PlayOptions): String = withContext(Dispatchers.IO) {
        val (url, o) = splitLinkOptions(raw.trim(), base)
        val sb = StringBuilder()
        sb.appendLine("Link: ${url.take(120)}")
        sb.appendLine("DRM: " + if (o.hasDrm) normalizeDrmScheme(o.drmScheme) else "none")
        val conn = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", o.userAgent.ifBlank { UA })
                o.headers().forEach { (k, v) -> if (!k.equals("User-Agent", true)) setRequestProperty(k, v) }
            }
        } catch (e: Exception) {
            return@withContext sb.appendLine("Invalid link: ${e.message}").toString()
        }
        try {
            val t0 = System.currentTimeMillis()
            val code = conn.responseCode
            sb.appendLine("HTTP: $code ${conn.responseMessage.orEmpty()}  (${System.currentTimeMillis() - t0} ms)")
            val h = hint(code)
            if (h.isNotEmpty() && code !in 200..299) sb.appendLine("=> $h")
            sb.appendLine("Content-Type: ${conn.contentType ?: "unknown"}")
            if (conn.url.toString() != url) sb.appendLine("Redirected to: ${conn.url.host}")
            if (code !in 200..299) return@withContext sb.toString()

            val buf = ByteArray(65_536)
            var n = 0
            conn.inputStream.use { ins ->
                while (n < buf.size) {
                    val r = ins.read(buf, n, buf.size - n)
                    if (r < 0) break
                    n += r
                }
            }
            val text = String(buf, 0, n, Charsets.UTF_8)
            when {
                text.contains("<MPD", true) -> analyseDash(text, o, sb)
                text.trimStart().startsWith("#EXTM3U") -> analyseHls(text, sb)
                else -> sb.appendLine("Type: direct file / unknown (${n} bytes read)")
            }
        } catch (e: Exception) {
            sb.appendLine("Connection failed: ${e.javaClass.simpleName}: ${e.message ?: ""}")
            sb.appendLine("=> Check internet / DNS / the link itself.")
        } finally {
            conn.disconnect()
        }
        sb.toString()
    }

    private fun analyseDash(t: String, o: PlayOptions, sb: StringBuilder) {
        sb.appendLine("Type: DASH manifest (.mpd) - OK")
        val schemes = mutableListOf<String>()
        val lower = t.lowercase()
        if (lower.contains("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")) schemes += "Widevine"
        if (lower.contains("9a04f079-9840-4286-ab92-e65be0885f95")) schemes += "PlayReady"
        if (lower.contains("e2719d58-a985-b3c9-781a-b030af78d30e") || lower.contains("1077efec-c0b2-4d02-ace3-3c1e52e2fb4b")) schemes += "ClearKey"
        val enc = lower.contains("mp4protection")
        if (enc || schemes.isNotEmpty()) {
            sb.appendLine("Encrypted: yes (" + (if (schemes.isEmpty()) "cenc" else schemes.joinToString(", ")) + ")")
        } else {
            sb.appendLine("Encrypted: no")
        }
        val kidRegex = Regex("""default_KID="([0-9a-fA-F-]{32,36})"""", RegexOption.IGNORE_CASE)
        val kids = kidRegex.findAll(t).map { it.groupValues[1].replace("-", "").lowercase() }.distinct().toList()
        if (kids.isNotEmpty()) sb.appendLine("Stream KID: " + kids.joinToString(", "))
        if (enc && !o.hasDrm) sb.appendLine("=> Stream is encrypted but no DRM key / license was given.")
        if (o.hasDrm && kids.isNotEmpty()) {
            val given = Regex("[0-9a-fA-F]{32}(?=:)").findAll(o.drmLicense).map { it.value.lowercase() }.toList()
            if (given.isNotEmpty()) {
                if (given.any { it in kids }) sb.appendLine("Key check: KID matches the stream - good")
                else sb.appendLine("=> Key check: KID in your key does NOT match the stream. Wrong or old key.")
            }
        }
    }

    private fun analyseHls(t: String, sb: StringBuilder) {
        sb.appendLine("Type: HLS playlist (.m3u8) - OK")
        val m = Regex("#EXT-X-KEY:METHOD=([A-Z0-9-]+)").find(t)?.groupValues?.get(1)
        sb.appendLine("Encrypted: " + (if (m == null || m == "NONE") "no" else m))
        if (t.contains("#EXT-X-STREAM-INF")) sb.appendLine("Variants: ${Regex("#EXT-X-STREAM-INF").findAll(t).count()}")
    }
}
