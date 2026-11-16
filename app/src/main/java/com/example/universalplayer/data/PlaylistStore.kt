package com.example.universalplayer.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class PlaylistItem(val url: String, val time: Long)

object PlaylistStore {
    private var file: File? = null
    private val _items = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val items: StateFlow<List<PlaylistItem>> = _items.asStateFlow()

    fun init(ctx: Context) {
        val f = File(ctx.filesDir, "playlist.json")
        file = f
        runCatching {
            if (f.exists()) {
                val a = JSONArray(f.readText())
                _items.value = (0 until a.length()).map {
                    val o = a.getJSONObject(it)
                    PlaylistItem(o.getString("url"), o.optLong("time"))
                }
            }
        }
    }

    @Synchronized
    fun add(url: String) {
        if (_items.value.any { it.url == url }) return
        _items.value = _items.value + PlaylistItem(url, System.currentTimeMillis())
        save()
    }

    @Synchronized
    fun remove(url: String) {
        _items.value = _items.value.filter { it.url != url }
        save()
    }

    private fun save() {
        val f = file ?: return
        runCatching {
            val arr = JSONArray()
            _items.value.forEach {
                arr.put(JSONObject().put("url", it.url).put("time", it.time))
            }
            f.writeText(arr.toString())
        }
    }
}
