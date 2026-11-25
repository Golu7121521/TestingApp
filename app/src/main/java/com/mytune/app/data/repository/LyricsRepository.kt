package com.mytune.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class LyricLine(val timeMs: Long, val text: String)

/** Lyrics; [synced] is non-empty when time-stamped lines are available. */
data class Lyrics(val plain: String, val synced: List<LyricLine>)

/** Fetches lyrics from the free LRCLIB service (https://lrclib.net). */
class LyricsRepository(private val http: OkHttpClient) {
    private val cache = HashMap<String, Lyrics?>()

    suspend fun get(title: String, artist: String, durationSec: Long): Lyrics? = withContext(Dispatchers.IO) {
        val key = "$title|$artist"
        if (cache.containsKey(key)) return@withContext cache[key]
        val t = title.replace(Regex("\\s*[\\(\\[].*?[\\)\\]]"), "").trim()
        val a = artist.split(",").firstOrNull().orEmpty().trim()
        val result = try {
            byGet(t, a, durationSec) ?: bySearch("$t $a") ?: bySearch(t)
        } catch (e: Exception) {
            null
        }
        if (result != null) cache[key] = result
        result
    }

    private fun call(url: String): String? {
        val req = Request.Builder().url(url).header("User-Agent", "MyTune/1.0").build()
        http.newCall(req).execute().use { r -> return if (r.isSuccessful) r.body?.string() else null }
    }

    private fun byGet(title: String, artist: String, dur: Long): Lyrics? {
        val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .apply { if (dur > 0) addQueryParameter("duration", dur.toString()) }
            .build().toString()
        return call(url)?.let { parse(JSONObject(it)) }
    }

    private fun bySearch(q: String): Lyrics? {
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder().addQueryParameter("q", q).build().toString()
        val arr = JSONArray(call(url) ?: return null)
        for (i in 0 until arr.length()) parse(arr.getJSONObject(i))?.let { return it }
        return null
    }

    private fun parse(o: JSONObject): Lyrics? {
        val plain = o.optString("plainLyrics", "").takeIf { it != "null" }.orEmpty()
        val syncedRaw = o.optString("syncedLyrics", "").takeIf { it != "null" }.orEmpty()
        val synced = Regex("\\[(\\d+):(\\d+(?:\\.\\d+)?)](.*)").findAll(syncedRaw).mapNotNull { m ->
            val text = m.groupValues[3].trim()
            val ms = ((m.groupValues[1].toLong() * 60 + m.groupValues[2].toDouble()) * 1000).toLong()
            LyricLine(ms, text)
        }.toList()
        if (plain.isBlank() && synced.isEmpty()) return null
        return Lyrics(plain.ifBlank { synced.joinToString("\n") { it.text } }, synced)
    }
}
