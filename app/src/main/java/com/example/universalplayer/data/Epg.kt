package com.example.universalplayer.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.zip.GZIPInputStream

data class Programme(val start: Long, val stop: Long, val title: String, val desc: String = "")

/** XMLTV channel guide. Only the channels of the open playlist and a +-day window are kept in memory. */
object EpgStore {
    private var cacheDir: File? = null
    private val _data = MutableStateFlow<Map<String, List<Programme>>>(emptyMap())
    val data: StateFlow<Map<String, List<Programme>>> = _data.asStateFlow()
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()
    private var loadedKey: String? = null

    fun init(ctx: Context) {
        cacheDir = ctx.cacheDir
    }

    fun nowNext(id: String, now: Long = System.currentTimeMillis()): Pair<Programme?, Programme?> {
        val list = _data.value[id.lowercase()] ?: return null to null
        val cur = list.firstOrNull { now >= it.start && now < it.stop }
        val next = list.firstOrNull { it.start >= (cur?.stop ?: now) }
        return cur to next
    }

    fun schedule(id: String, now: Long = System.currentTimeMillis()): List<Programme> =
        (_data.value[id.lowercase()] ?: emptyList()).filter { it.stop > now }.take(12)

    /** Loads (or re-uses a < 6 h old cache of) the guide for [wanted] channel ids. */
    suspend fun load(url: String, wanted: Set<String>, force: Boolean = false) {
        if (url.isBlank() || wanted.isEmpty()) return
        val ids = wanted.map { it.lowercase() }.filter { it.isNotBlank() }.toSet()
        if (ids.isEmpty()) return
        val key = url + "#" + ids.size
        if (!force && loadedKey == key && _data.value.isNotEmpty()) return
        _status.value = "Loading channel guide..."
        try {
            val result = withContext(Dispatchers.IO) {
                val cache = File(cacheDir ?: File("/data/local/tmp"), "epg_${key.hashCode()}.json")
                if (!force && cache.exists() && System.currentTimeMillis() - cache.lastModified() < 6 * 3600_000L) {
                    runCatching { readCache(cache) }.getOrNull()?.let { return@withContext it }
                }
                val map = download(url, ids)
                runCatching { writeCache(cache, map) }
                map
            }
            _data.value = result
            loadedKey = key
            _status.value = if (result.isEmpty()) "Guide loaded, but no channel matched (check tvg-id)" else "Guide: ${result.size} channels"
        } catch (e: Throwable) {
            _status.value = "Could not load guide: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    private fun download(url: String, ids: Set<String>): Map<String, List<Programme>> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36")
        }
        try {
            if (conn.responseCode !in 200..299) throw java.io.IOException("Server returned ${conn.responseCode}")
            val buffered = BufferedInputStream(conn.inputStream, 64 * 1024)
            buffered.mark(4)
            val b0 = buffered.read()
            val b1 = buffered.read()
            buffered.reset()
            val stream = if (b0 == 0x1f && b1 == 0x8b) GZIPInputStream(buffered, 64 * 1024) else buffered
            return parse(stream, ids)
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(stream: java.io.InputStream, ids: Set<String>): Map<String, List<Programme>> {
        val now = System.currentTimeMillis()
        val from = now - 3 * 3600_000L
        val to = now + 36 * 3600_000L
        val out = HashMap<String, MutableList<Programme>>()
        val p = XmlPullParserFactory.newInstance().newPullParser()
        p.setInput(stream, null)
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && p.name == "programme") {
                val ch = p.getAttributeValue(null, "channel")?.lowercase().orEmpty()
                val wantIt = ch in ids
                val st = if (wantIt) parseTime(p.getAttributeValue(null, "start")) else 0L
                val sp = if (wantIt) parseTime(p.getAttributeValue(null, "stop")) else 0L
                var title = ""
                var desc = ""
                while (true) {
                    ev = p.next()
                    if (ev == XmlPullParser.END_DOCUMENT) break
                    if (ev == XmlPullParser.END_TAG && p.name == "programme") break
                    if (ev == XmlPullParser.START_TAG && wantIt) {
                        if (p.name == "title" && title.isEmpty()) title = p.nextText().trim()
                        else if (p.name == "desc" && desc.isEmpty()) desc = p.nextText().trim()
                    }
                }
                if (wantIt && st > 0 && sp > st && sp > from && st < to && title.isNotEmpty()) {
                    out.getOrPut(ch) { mutableListOf() }.add(Programme(st, sp, title, desc.take(400)))
                }
            }
            ev = p.next()
        }
        return out.mapValues { (_, v) -> v.sortedBy { it.start } }
    }

    /** XMLTV time: 20261123183000 +0530 (offset optional, then UTC). */
    private fun parseTime(s: String?): Long {
        val t = s?.trim() ?: return 0L
        if (t.length < 14) return 0L
        return try {
            val dt = LocalDateTime.of(
                t.substring(0, 4).toInt(), t.substring(4, 6).toInt(), t.substring(6, 8).toInt(),
                t.substring(8, 10).toInt(), t.substring(10, 12).toInt(), t.substring(12, 14).toInt()
            )
            var off = 0
            val rest = t.substring(14).trim()
            if (rest.length >= 5 && (rest[0] == '+' || rest[0] == '-')) {
                val sign = if (rest[0] == '-') -1 else 1
                off = sign * (rest.substring(1, 3).toInt() * 3600 + rest.substring(3, 5).toInt() * 60)
            }
            dt.toInstant(ZoneOffset.ofTotalSeconds(off)).toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }

    private fun writeCache(f: File, m: Map<String, List<Programme>>) {
        val arr = JSONArray()
        m.forEach { (c, list) ->
            list.forEach {
                arr.put(JSONObject().put("c", c).put("s", it.start).put("e", it.stop).put("t", it.title).put("d", it.desc))
            }
        }
        f.writeText(arr.toString())
    }

    private fun readCache(f: File): Map<String, List<Programme>> {
        val arr = JSONArray(f.readText())
        val out = HashMap<String, MutableList<Programme>>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.getOrPut(o.getString("c")) { mutableListOf() }
                .add(Programme(o.getLong("s"), o.getLong("e"), o.getString("t"), o.optString("d")))
        }
        return out.mapValues { (_, v) -> v.sortedBy { it.start } }
    }
}
