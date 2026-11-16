package com.example.universalplayer.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class HistoryItem(
    val url: String,
    val title: String,
    val thumb: String?,
    val time: Long,
    val opts: PlayOptions = PlayOptions()
)

/** Small JSON-file backed list used for both History (newest first) and Playlist (in order added). */
class ItemStore(private val fileName: String, private val newestFirst: Boolean, private val max: Int) {
    private var file: File? = null
    private val _items = MutableStateFlow<List<HistoryItem>>(emptyList())
    val items: StateFlow<List<HistoryItem>> = _items.asStateFlow()

    fun init(ctx: Context) {
        val f = File(ctx.filesDir, fileName)
        file = f
        runCatching {
            if (f.exists()) {
                val a = JSONArray(f.readText())
                _items.value = (0 until a.length()).map {
                    val o = a.getJSONObject(it)
                    HistoryItem(
                        o.getString("url"), o.optString("title"),
                        if (o.isNull("thumb")) null else o.optString("thumb"),
                        o.optLong("time"), PlayOptions.fromJson(o.optJSONObject("opts"))
                    )
                }
            }
        }
    }

    @Synchronized
    fun add(item: HistoryItem) {
        _items.value = if (newestFirst) {
            (listOf(item) + _items.value.filter { it.url != item.url }).take(max)
        } else {
            if (_items.value.any { it.url == item.url }) _items.value else (_items.value + item).takeLast(max)
        }
        save()
    }

    @Synchronized
    fun update(url: String, title: String, thumb: String?) {
        if (_items.value.none { it.url == url }) return
        _items.value = _items.value.map { if (it.url == url) it.copy(title = title, thumb = thumb) else it }
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
                    JSONObject().put("url", it.url).put("title", it.title)
                        .put("thumb", it.thumb ?: JSONObject.NULL).put("time", it.time)
                        .put("opts", it.opts.toJson())
                )
            }
            f.writeText(arr.toString())
        }
    }
}

val HistoryStore = ItemStore("history.json", true, 300)
val PlaylistStore = ItemStore("playlist.json", false, 500)
