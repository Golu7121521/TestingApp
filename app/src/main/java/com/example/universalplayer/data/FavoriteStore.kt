package com.example.universalplayer.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.io.File

/** Favorite channels / videos, stored by link. */
object FavoriteStore {
    private var file: File? = null
    private val _urls = MutableStateFlow<Set<String>>(emptySet())
    val urls: StateFlow<Set<String>> = _urls.asStateFlow()

    fun init(ctx: Context) {
        val f = File(ctx.filesDir, "favorites.json")
        file = f
        runCatching {
            if (f.exists()) {
                val a = JSONArray(f.readText())
                _urls.value = (0 until a.length()).map { a.getString(it) }.toSet()
            }
        }
    }

    @Synchronized
    fun toggle(url: String) {
        _urls.value = if (url in _urls.value) _urls.value - url else _urls.value + url
        val f = file ?: return
        runCatching {
            val a = JSONArray()
            _urls.value.forEach { a.put(it) }
            f.writeText(a.toString())
        }
    }
}
