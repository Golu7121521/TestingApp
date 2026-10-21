package com.advancedpdf.toolkit

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import org.json.JSONArray
import org.json.JSONObject

/** Small SharedPreferences-backed store. Nothing here leaves the device. */
object Store {
    data class Entry(val uri: String, val name: String, val time: Long)

    private fun p(c: Context): SharedPreferences =
        c.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    // ---- Theme / defaults ----
    fun themeKey(c: Context): String = p(c).getString("theme", "system") ?: "system"
    fun setTheme(c: Context, key: String) {
        p(c).edit().putString("theme", key).apply()
        AppCompatDelegate.setDefaultNightMode(themeMode(c))
    }

    fun themeMode(c: Context): Int = when (themeKey(c)) {
        "light" -> AppCompatDelegate.MODE_NIGHT_NO
        "dark" -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    /** One of: name, date, size, opened */
    fun sort(c: Context): String = p(c).getString("sort", "date") ?: "date"
    fun setSort(c: Context, v: String) = p(c).edit().putString("sort", v).apply()

    /** 0 = low, 1 = medium, 2 = high */
    fun compression(c: Context): Int = p(c).getInt("compression", 1)
    fun setCompression(c: Context, v: Int) = p(c).edit().putInt("compression", v).apply()

    fun readerDark(c: Context): Boolean = p(c).getBoolean("reader_dark", false)
    fun setReaderDark(c: Context, v: Boolean) = p(c).edit().putBoolean("reader_dark", v).apply()

    // ---- Reading position ----
    fun lastPage(c: Context, uri: String): Int = p(c).getInt("pos_" + uri.hashCode(), 0)
    fun setLastPage(c: Context, uri: String, page: Int) =
        p(c).edit().putInt("pos_" + uri.hashCode(), page).apply()

    // ---- Recent files ----
    private fun load(c: Context, key: String): MutableList<Entry> {
        val out = ArrayList<Entry>()
        try {
            val a = JSONArray(p(c).getString(key, "[]") ?: "[]")
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Entry(o.getString("u"), o.getString("n"), o.optLong("t", 0L)))
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun save(c: Context, key: String, list: List<Entry>) {
        val a = JSONArray()
        for (e in list) {
            a.put(JSONObject().put("u", e.uri).put("n", e.name).put("t", e.time))
        }
        p(c).edit().putString(key, a.toString()).apply()
    }

    fun recents(c: Context): List<Entry> = load(c, "recents")

    fun addRecent(c: Context, uri: String, name: String) {
        val list = load(c, "recents")
        list.removeAll { it.uri == uri }
        list.add(0, Entry(uri, name, System.currentTimeMillis()))
        save(c, "recents", list.take(100))
    }

    fun removeRecent(c: Context, uri: String) {
        val list = load(c, "recents")
        list.removeAll { it.uri == uri }
        save(c, "recents", list)
    }

    fun clearRecents(c: Context) = p(c).edit().remove("recents").apply()

    // ---- Starred ----
    fun starred(c: Context): List<Entry> = load(c, "starred")
    fun isStarred(c: Context, uri: String): Boolean = starred(c).any { it.uri == uri }

    fun toggleStar(c: Context, uri: String, name: String) {
        val list = load(c, "starred")
        if (list.any { it.uri == uri }) list.removeAll { it.uri == uri }
        else list.add(0, Entry(uri, name, System.currentTimeMillis()))
        save(c, "starred", list)
    }

    fun removeStar(c: Context, uri: String) {
        val list = load(c, "starred")
        list.removeAll { it.uri == uri }
        save(c, "starred", list)
    }

    fun clearStarred(c: Context) = p(c).edit().remove("starred").apply()

    fun resetAll(c: Context) {
        p(c).edit().clear().apply()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }
}
