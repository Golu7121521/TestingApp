package com.universal.streamplayer.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.Serializable
import java.util.UUID

data class StreamRequest(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Stream Video",
    val url: String,
    val cookie: String = "",
    val referer: String = "",
    val origin: String = "",
    val drmLicenseUrl: String = "",
    val userAgent: String = UserAgents.ANDROID_CHROME.ua,
    val drmScheme: String = DrmSchemes.NONE.label,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

enum class UserAgents(val label: String, val ua: String) {
    ANDROID_CHROME("Android Chrome", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"),
    CHROME_PC("Chrome PC (Windows)", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"),
    FIREFOX("Firefox PC", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:123.0) Gecko/20100101 Firefox/123.0"),
    SAFARI_IOS("iPhone Safari", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.3 Mobile/15E148 Safari/604.1"),
    EXOPLAYER_DEFAULT("ExoPlayer Default", "ExoPlayerLib/2.19.1 (Linux; Android 14)")
}

enum class DrmSchemes(val label: String) {
    NONE("None"),
    WIDEVINE("Widevine"),
    PLAYREADY("PlayReady"),
    CLEARKEY("ClearKey")
}

class StorageRepository(context: Context) {
    private val prefs = context.getSharedPreferences("stream_player_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getHistory(): List<StreamRequest> {
        val json = prefs.getString("history_list", "[]") ?: "[]"
        val type = object : TypeToken<List<StreamRequest>>() {}.type
        return gson.fromJson(json, type)
    }

    fun addToHistory(item: StreamRequest) {
        val current = getHistory().toMutableList()
        current.removeAll { it.url == item.url }
        current.add(0, item.copy(timestamp = System.currentTimeMillis()))
        prefs.edit().putString("history_list", gson.toJson(current.take(100))).apply()
    }

    fun clearHistory() {
        prefs.edit().remove("history_list").apply()
    }

    fun getPlaylists(): List<StreamRequest> {
        val json = prefs.getString("playlist_items", "[]") ?: "[]"
        val type = object : TypeToken<List<StreamRequest>>() {}.type
        return gson.fromJson(json, type)
    }

    fun addToPlaylist(item: StreamRequest) {
        val current = getPlaylists().toMutableList()
        current.removeAll { it.url == item.url }
        current.add(0, item)
        prefs.edit().putString("playlist_items", gson.toJson(current)).apply()
    }

    fun removeFromPlaylist(id: String) {
        val current = getPlaylists().toMutableList()
        current.removeAll { it.id == id }
        prefs.edit().putString("playlist_items", gson.toJson(current)).apply()
    }
}
