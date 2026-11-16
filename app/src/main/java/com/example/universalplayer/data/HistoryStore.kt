package com.example.universalplayer.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class HistoryItem(val url: String, val title: String, val thumb: String?, val time: Long)

object HistoryStore {
    private var file: File? = null
    private val _items = MutableStateFlow<List<HistoryItem>>(emptyList())
    val items: StateFlow<List<HistoryItem>> = _items.asStateFlow()

    fun init(ctx: Context) {
        val f = File(ctx.filesDir, "history.json")
        file = f
        runCatching {
            if (f.exists()) {
                val a = JSONArray(f.readText())
                _items.value = (0 until a.length()).map {
                    val o = a.getJSONObject(it)
                    HistoryItem(
                        o.getString("url"),
                        o.optString("title"),
                        if (o.isNull("thumb")) null else o.optString("thumb"),
                        o.optLong("time")
                    )
                }
            }
        }
    }

    @Synchronized
    fun add(item: HistoryItem) {
        _items.value = (listOf(item) + _items.value.filter { it.url != item.url }).take(300)
        save()
    }

    @Synchronized
    fun remove(url: String) {
        _items.value = _items.value.filter { it.url != url }
        save()
    }

    @Synchronized
    fun clear() {
        _items.value = emptyList()
        save()
    }

    private fun save() {
        val f = file ?: return
        runCatching {
            val arr = JSONArray()
            _items.value.forEach {
                arr.put(
                    JSONObject()
                        .put("url", it.url)
                        .put("title", it.title)
                        .put("thumb", it.thumb ?: JSONObject.NULL)
                        .put("time", it.time)
                )
            }
            f.writeText(arr.toString())
        }
    }
}
