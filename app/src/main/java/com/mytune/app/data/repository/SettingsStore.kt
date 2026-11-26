package com.mytune.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
}
