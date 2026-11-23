package com.example.universalplayer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Parsed playlist: channels + the channel-guide (EPG) link found in the #EXTM3U header, if any. */
data class M3uResult(val items: List<HistoryItem>, val epgUrl: String = "")

object M3u {
    private val logoRegex = Regex("""tvg-logo="([^"]*)"""", RegexOption.IGNORE_CASE)
    private val groupRegex = Regex("""group-title="([^"]*)"""", RegexOption.IGNORE_CASE)
    private val tvgIdRegex = Regex("""tvg-id="([^"]*)"""", RegexOption.IGNORE_CASE)
    private val epgRegex = Regex("""(?:url-tvg|x-tvg-url|tvg-url)="([^"]*)"""", RegexOption.IGNORE_CASE)

    suspend fun fetchFull(url: String): M3uResult = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36")
        }
        try {
            if (conn.responseCode !in 200..299) throw IOException("Server returned ${conn.responseCode}")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val res = parseFull(text)
            if (res.items.isEmpty()) throw IOException("No videos found in this playlist")
            res
        } finally {
            conn.disconnect()
        }
    }

    suspend fun fetch(url: String): List<HistoryItem> = fetchFull(url).items

    fun parse(text: String): List<HistoryItem> = parseFull(text).items

    fun parseFull(text: String): M3uResult {
        val out = LinkedHashMap<String, HistoryItem>()
        var title: String? = null
        var logo: String? = null
        var group = ""
        var tvgId = ""
        var referer = ""
        var origin = ""
        var cookie = ""
        var ua = ""
        var kScheme = ""
        var kKey = ""
        var epg = ""
        val now = System.currentTimeMillis()

        fun reset() {
            title = null; logo = null; group = ""; tvgId = ""
            referer = ""; origin = ""; cookie = ""; ua = ""; kScheme = ""; kKey = ""
        }

        for (raw in text.lineSequence()) {
            val line = raw.trim().removePrefix("\uFEFF")
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTM3U", true)) {
                epgRegex.find(line)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }?.let {
                    epg = it.split(',').first().trim()
                }
            } else if (line.startsWith("#EXTINF", true)) {
                reset()
                title = line.substringAfterLast(',', "").trim().ifBlank { null }
                logo = logoRegex.find(line)?.groupValues?.get(1)?.ifBlank { null }
                group = groupRegex.find(line)?.groupValues?.get(1)?.trim().orEmpty()
                tvgId = tvgIdRegex.find(line)?.groupValues?.get(1)?.trim().orEmpty()
            } else if (line.startsWith("#EXTGRP:", true)) {
                if (group.isBlank()) group = line.substringAfter(':').trim()
            } else if (line.startsWith("#KODIPROP:", true)) {
                val kv = line.substringAfter(':')
                val k = kv.substringBefore('=').lowercase()
                val v = kv.substringAfter('=', "").trim()
                if (k.endsWith("license_type")) kScheme = normalizeDrmScheme(v)
                if (k.endsWith("license_key")) kKey = v
            } else if (line.startsWith("#EXTVLCOPT:", true)) {
                val kv = line.substringAfter(':')
                val k = kv.substringBefore('=').lowercase()
                val v = kv.substringAfter('=', "")
                when (k) {
                    "http-referrer", "http-referer" -> referer = v
                    "http-user-agent" -> ua = v
                    "http-origin" -> origin = v
                    "http-cookie" -> cookie = v
                }
            } else if (line.startsWith("#EXTHTTP:", true)) {
                // {"cookie":"...", "Origin":"...", "Referer":"...", "User-Agent":"..."}
                runCatching {
                    val j = JSONObject(line.substringAfter(':'))
                    for (key in j.keys()) {
                        val v = j.optString(key)
                        when (key.lowercase()) {
                            "cookie" -> cookie = v
                            "origin" -> origin = v
                            "referer", "referrer" -> referer = v
                            "user-agent" -> ua = v
                        }
                    }
                }
            } else if (line.startsWith("#")) {
                continue
            } else {
                if (line.startsWith("http", true) && line !in out) {
                    val base = PlayOptions(
                        cookie = cookie, referer = referer, origin = origin, userAgent = ua,
                        drmLicense = kKey, drmScheme = kScheme.ifBlank { "Widevine" }
                    )
                    val (clean, po) = splitLinkOptions(line, base)
                    out[line] = HistoryItem(
                        clean, title ?: clean, logo, now, po,
                        group = group, tvgId = tvgId
                    )
                }
                reset()
            }
        }
        return M3uResult(out.values.toList(), epg)
    }
}
