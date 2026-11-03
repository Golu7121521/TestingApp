package com.linkplayer.app

import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONArray
import org.json.JSONObject

data class PlayRequest(val url: String, val headers: Map<String, String>)

data class HistoryItem(val url: String, val headers: String, val time: Long)

object K {
    const val BRIGHTNESS = "g_brightness"
    const val VOLUME = "g_volume"
    const val SEEK = "g_seek"
    const val DOUBLE_TAP = "g_double_tap"
    const val LONG_PRESS = "g_long_press"
    const val ZOOM = "g_zoom"
    const val AUTO_LANDSCAPE = "auto_landscape"
    const val AUTO_PIP = "auto_pip"
}

/** key, title, subtitle */
val SETTING_OPTIONS = listOf(
    Triple(K.BRIGHTNESS, "Brightness gesture", "Left half par upar/neeche swipe"),
    Triple(K.VOLUME, "Volume gesture", "Right half par upar/neeche swipe"),
    Triple(K.SEEK, "Swipe to seek", "Left/right swipe karke aage-peeche karo"),
    Triple(K.DOUBLE_TAP, "Double tap seek", "Left/right double tap = 10s, beech mein = play/pause"),
    Triple(K.LONG_PRESS, "Press & hold 2x", "Dabaye rakho to 2x speed, chhodo to normal"),
    Triple(K.ZOOM, "Pinch in / out zoom", "Do ungli se video zoom in/out"),
    Triple(K.AUTO_LANDSCAPE, "Auto rotate to video", "Video ke hisaab se landscape/portrait"),
    Triple(K.AUTO_PIP, "Auto Picture-in-Picture", "Home dabane par video PiP mein chalu rahe"),
)

class AppSettings(private val sp: SharedPreferences) {
    private val map = mutableStateMapOf<String, Boolean>()

    init {
        SETTING_OPTIONS.forEach { map[it.first] = sp.getBoolean(it.first, true) }
    }

    operator fun get(key: String): Boolean = map[key] ?: true

    fun set(key: String, value: Boolean) {
        map[key] = value
        sp.edit().putBoolean(key, value).apply()
    }
}

class HistoryStore(private val sp: SharedPreferences) {
    val items = mutableStateListOf<HistoryItem>()

    init {
        try {
            val arr = JSONArray(sp.getString("h", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items.add(HistoryItem(o.getString("u"), o.optString("h"), o.optLong("t")))
            }
        } catch (_: Exception) {
        }
    }

    private fun save() {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("u", it.url).put("h", it.headers).put("t", it.time)) }
        sp.edit().putString("h", arr.toString()).apply()
    }

    fun add(url: String, headers: String) {
        items.removeAll { it.url == url && it.headers == headers }
        items.add(0, HistoryItem(url, headers, System.currentTimeMillis()))
        while (items.size > 100) items.removeAt(items.lastIndex)
        save()
    }

    fun remove(item: HistoryItem) {
        items.remove(item)
        save()
    }

    fun clear() {
        items.clear()
        save()
    }
}

fun parseHeaders(text: String): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    text.lines().forEach { line ->
        val i = line.indexOf(':')
        if (i > 0) {
            val k = line.substring(0, i).trim()
            val v = line.substring(i + 1).trim()
            if (k.isNotEmpty()) out[k] = v
        }
    }
    return out
}

fun normalizeUrl(raw: String): String {
    val t = raw.trim()
    if (t.isEmpty()) return t
    return if (t.contains("://") || t.startsWith("content:") || t.startsWith("file:")) t else "https://$t"
}
