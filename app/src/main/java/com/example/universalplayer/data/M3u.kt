package com.example.universalplayer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object M3u {
    private val logoRegex = Regex("""tvg-logo="([^"]*)"""")

    suspend fun fetch(url: String): List<HistoryItem> = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36")
        }
        try {
            if (conn.responseCode !in 200..299) throw IOException("Server returned ${conn.responseCode}")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val items = parse(text)
            if (items.isEmpty()) throw IOException("No videos found in this playlist")
            items
        } finally {
            conn.disconnect()
        }
    }

    fun parse(text: String): List<HistoryItem> {
        val out = LinkedHashMap<String, HistoryItem>()
        var title: String? = null
        var logo: String? = null
        var referer = ""
        var ua = ""
        var kScheme = ""
        var kKey = ""
        val now = System.currentTimeMillis()
        for (raw in text.lineSequence()) {
            val line = raw.trim().removePrefix("\uFEFF")
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF")) {
                title = line.substringAfterLast(',', "").trim().ifBlank { null }
                logo = logoRegex.find(line)?.groupValues?.get(1)?.ifBlank { null }
                referer = ""; ua = ""; kScheme = ""; kKey = ""
            } else if (line.startsWith("#KODIPROP:")) {
                val kv = line.removePrefix("#KODIPROP:")
                val k = kv.substringBefore('=').lowercase()
                val v = kv.substringAfter('=', "").trim()
                if (k.endsWith("license_type")) kScheme = normalizeDrmScheme(v)
                if (k.endsWith("license_key")) kKey = v
            } else if (line.startsWith("#EXTVLCOPT:")) {
                val kv = line.removePrefix("#EXTVLCOPT:")
                val k = kv.substringBefore('=').lowercase()
                val v = kv.substringAfter('=', "")
                if (k == "http-referrer" || k == "http-referer") referer = v
                if (k == "http-user-agent") ua = v
            } else if (line.startsWith("#")) {
                continue
            } else {
                if (line.startsWith("http", true) && line !in out) {
                    val base = PlayOptions(
                        referer = referer, userAgent = ua, drmLicense = kKey,
                        drmScheme = kScheme.ifBlank { "Widevine" }
                    )
                    val (clean, po) = splitLinkOptions(line, base)
                    out[line] = HistoryItem(clean, title ?: clean, logo, now, po)
                }
                title = null; logo = null; referer = ""; ua = ""; kScheme = ""; kKey = ""
            }
        }
        return out.values.toList()
    }
}
