package com.example.universalplayer.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Playlist(val id: String, val name: String, val items: List<HistoryItem>)

private fun HistoryItem.toJson(): JSONObject =
    JSONObject().put("url", url).put("title", title).put("thumb", thumb ?: JSONObject.NULL)
        .put("time", time).put("opts", opts.toJson()).put("pos", position).put("dur", duration)

private fun itemFromJson(o: JSONObject) = HistoryItem(
    o.getString("url"), o.optString("title"),
    if (o.isNull("thumb")) null else o.optString("thumb"),
    o.optLong("time"), PlayOptions.fromJson(o.optJSONObject("opts")),
    o.optLong("pos"), o.optLong("dur")
)

object PlaylistStore {
    private var file: File? = null
    private val _lists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _lists.asStateFlow()

    fun init(ctx: Context) {
        val f = File(ctx.filesDir, "playlists.json")
        file = f
        runCatching {
            if (f.exists()) {
                val a = JSONArray(f.readText())
                _lists.value = (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    val arr = o.optJSONArray("items") ?: JSONArray()
                    Playlist(o.getString("id"), o.optString("name"), (0 until arr.length()).map { itemFromJson(arr.getJSONObject(it)) })
                }
            } else {
                // migrate the old single playlist, if any
                val old = File(ctx.filesDir, "playlist.json")
                if (old.exists()) {
                    val arr = JSONArray(old.readText())
                    val items = (0 until arr.length()).map { itemFromJson(arr.getJSONObject(it)) }
                    if (items.isNotEmpty()) {
                        _lists.value = listOf(Playlist(UUID.randomUUID().toString(), "My playlist", items))
                        save()
                    }
                }
            }
        }
    }

    @Synchronized
    fun create(name: String, items: List<HistoryItem> = emptyList()): String {
        val id = UUID.randomUUID().toString()
        _lists.value = _lists.value + Playlist(id, name.trim(), items.distinctBy { it.url })
        save()
        return id
    }

    @Synchronized fun rename(id: String, name: String) = mutate(id) { it.copy(name = name.trim()) }

    @Synchronized
    fun delete(id: String) {
        _lists.value = _lists.value.filter { it.id != id }
        save()
    }

    @Synchronized
    fun restore(p: Playlist) {
        if (_lists.value.none { it.id == p.id }) {
            _lists.value = _lists.value + p
            save()
        }
    }

    @Synchronized
    fun addItems(id: String, items: List<HistoryItem>): Int {
        var added = 0
        mutate(id) { pl ->
            val have = pl.items.map { it.url }.toHashSet()
            val fresh = items.filter { have.add(it.url) }
            added = fresh.size
            pl.copy(items = pl.items + fresh)
        }
        return added
    }

    fun addItem(id: String, item: HistoryItem) = addItems(id, listOf(item))

    @Synchronized
    fun removeItem(id: String, url: String) = mutate(id) { pl -> pl.copy(items = pl.items.filter { it.url != url }) }

    /** Fills a title / thumbnail only when the item still has none (keeps imported channel names). */
    @Synchronized
    fun update(url: String, title: String, thumb: String?) {
        if (_lists.value.none { pl -> pl.items.any { it.url == url } }) return
        _lists.value = _lists.value.map { pl ->
            if (pl.items.none { it.url == url }) pl else pl.copy(items = pl.items.map {
                if (it.url == url) it.copy(
                    title = if (it.title == it.url && title.isNotBlank()) title else it.title,
                    thumb = it.thumb ?: thumb
                ) else it
            })
        }
        save()
    }

    @Synchronized
    fun setProgress(url: String, pos: Long, dur: Long) {
        if (_lists.value.none { pl -> pl.items.any { it.url == url } }) return
        _lists.value = _lists.value.map { pl ->
            if (pl.items.none { it.url == url }) pl
            else pl.copy(items = pl.items.map { if (it.url == url) it.copy(position = pos, duration = dur) else it })
        }
        save()
    }

    private fun mutate(id: String, f: (Playlist) -> Playlist) {
        _lists.value = _lists.value.map { if (it.id == id) f(it) else it }
        save()
    }

    private fun save() {
        val fl = file ?: return
        runCatching {
            val arr = JSONArray()
            _lists.value.forEach { pl ->
                val items = JSONArray()
                pl.items.forEach { items.put(it.toJson()) }
                arr.put(JSONObject().put("id", pl.id).put("name", pl.name).put("items", items))
            }
            fl.writeText(arr.toString())
        }
    }
}
