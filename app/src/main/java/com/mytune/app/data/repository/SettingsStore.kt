package com.mytune.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.mytune.app.domain.PickedArtist
import com.mytune.app.domain.UserPrefs
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(context: Context, scope: CoroutineScope) {
    private val ds = context.applicationContext.settingsDataStore

    private companion object {
        val STREAM = intPreferencesKey("stream_quality")
        val DOWNLOAD = intPreferencesKey("download_quality")
        val THEME = stringPreferencesKey("theme")
        val ACCENT = stringPreferencesKey("accent")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val COUNTRY = stringPreferencesKey("country")
        val LANGS = stringPreferencesKey("languages")
        val ARTISTS = stringPreferencesKey("artists")
        val TASTE = stringPreferencesKey("taste")
        val PLAYS = longPreferencesKey("plays")
        val EQ = stringPreferencesKey("eq")
    }

    val streamQuality: Flow<Int> = ds.data.map { it[STREAM] ?: 320 }
    val downloadQuality: Flow<Int> = ds.data.map { it[DOWNLOAD] ?: 320 }
    /** "system" (default), "dark" or "light". */
    val theme: Flow<String> = ds.data.map { it[THEME] ?: "system" }

    // Cached copies so non-suspending code (the stream resolver, workers) can read the latest value.
    @Volatile var streamQualityNow: Int = 320
        private set
    @Volatile var downloadQualityNow: Int = 320
        private set

    init {
        scope.launch { streamQuality.collect { streamQualityNow = it } }
        scope.launch { downloadQuality.collect { downloadQualityNow = it } }
    }

    suspend fun setStreamQuality(q: Int) { ds.edit { it[STREAM] = q } }
    suspend fun setDownloadQuality(q: Int) { ds.edit { it[DOWNLOAD] = q } }
    val accent: Flow<String> = ds.data.map { it[ACCENT] ?: "green" }
    suspend fun setAccent(a: String) { ds.edit { it[ACCENT] = a } }
    suspend fun setTheme(t: String) { ds.edit { it[THEME] = t } }

    // ---- Onboarding / personalisation ----
    val onboarded: Flow<Boolean> = ds.data.map { it[ONBOARDED] ?: false }

    val prefs: Flow<UserPrefs> = ds.data.map { p ->
        UserPrefs(
            country = p[COUNTRY] ?: "India",
            languages = p[LANGS].orEmpty().split("|").filter { it.isNotBlank() },
            artists = runCatching {
                val a = JSONArray(p[ARTISTS] ?: "[]")
                (0 until a.length()).map { i -> a.getJSONObject(i).let { PickedArtist(it.getString("id"), it.getString("name"), it.optString("img")) } }
            }.getOrDefault(emptyList()),
        )
    }

    suspend fun prefsNow(): UserPrefs = prefs.first()

    suspend fun saveOnboarding(country: String, languages: List<String>, artists: List<PickedArtist>) {
        ds.edit {
            it[COUNTRY] = country
            it[LANGS] = languages.joinToString("|")
            it[ARTISTS] = JSONArray().apply {
                artists.forEach { a -> put(JSONObject().put("id", a.id).put("name", a.name).put("img", a.image)) }
            }.toString()
            it[ONBOARDED] = true
        }
    }

    /** Adds the artist to "Your Artists", or removes it when already there. */
    suspend fun toggleArtist(a: PickedArtist) {
        ds.edit {
            val arr = runCatching { JSONArray(it[ARTISTS] ?: "[]") }.getOrDefault(JSONArray())
            val out = JSONArray()
            var found = false
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("id") == a.id) found = true else out.put(o)
            }
            if (!found) out.put(JSONObject().put("id", a.id).put("name", a.name).put("img", a.image))
            it[ARTISTS] = out.toString()
        }
    }

    /** Re-opens the onboarding page (keeps the old selection pre-filled). */
    suspend fun resetOnboarding() { ds.edit { it[ONBOARDED] = false } }

    // ---- Listening taste: artist -> weight, decays so recent listening matters most ----
    val playCount: Flow<Long> = ds.data.map { it[PLAYS] ?: 0L }
    @Volatile var playCountNow: Long = 0L
        private set

    init { scope.launch { playCount.collect { playCountNow = it } } }

    suspend fun recordPlay(artistNames: String) {
        val names = artistNames.split(",").map { it.trim() }.filter { it.isNotBlank() }.take(2)
        if (names.isEmpty()) return
        ds.edit { p ->
            val o = runCatching { JSONObject(p[TASTE] ?: "{}") }.getOrDefault(JSONObject())
            val m = HashMap<String, Double>()
            o.keys().forEach { k -> m[k] = o.optDouble(k, 0.0) * 0.97 }
            names.forEach { n -> m[n] = (m[n] ?: 0.0) + 1.0 }
            val top = m.entries.sortedByDescending { it.value }.take(30)
            p[TASTE] = JSONObject().also { j -> top.forEach { j.put(it.key, it.value) } }.toString()
            p[PLAYS] = (p[PLAYS] ?: 0L) + 1
        }
    }

    /** Most played artists, best first. */
    suspend fun topArtists(n: Int = 5): List<String> {
        val o = runCatching { JSONObject(ds.data.first()[TASTE] ?: "{}") }.getOrDefault(JSONObject())
        return o.keys().asSequence().map { it to o.optDouble(it, 0.0) }.sortedByDescending { it.second }.take(n).map { it.first }.toList()
    }

    // ---- Equalizer (JSON blob, see EqualizerManager) ----
    val eq: Flow<String> = ds.data.map { it[EQ].orEmpty() }
    suspend fun saveEq(json: String) { ds.edit { it[EQ] = json } }
}
