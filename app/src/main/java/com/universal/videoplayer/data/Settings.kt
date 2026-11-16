package com.universal.videoplayer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class Scaling(val label: String) {
    FIT("Fit"), FILL("Fill"), ZOOM("Zoom"), ORIGINAL("Original")
}

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val speed: Float = 1f,
    val scaling: Scaling = Scaling.FIT,
    val autoResume: Boolean = true,
    val autoHide: Boolean = true,
    val clipDetect: Boolean = true,
    val clipAsk: Boolean = true,
    val historyEnabled: Boolean = true,
    val saveProgress: Boolean = true,
    val retryOnError: Boolean = true,
    val errorDetails: Boolean = false,
)

object Keys {
    val THEME = stringPreferencesKey("theme")
    val SPEED = floatPreferencesKey("speed")
    val SCALING = stringPreferencesKey("scaling")
    val AUTO_RESUME = booleanPreferencesKey("auto_resume")
    val AUTO_HIDE = booleanPreferencesKey("auto_hide")
    val CLIP_DETECT = booleanPreferencesKey("clip_detect")
    val CLIP_ASK = booleanPreferencesKey("clip_ask")
    val HISTORY = booleanPreferencesKey("history")
    val SAVE_PROGRESS = booleanPreferencesKey("save_progress")
    val RETRY = booleanPreferencesKey("retry")
    val ERROR_DETAILS = booleanPreferencesKey("error_details")
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            theme = runCatching { ThemeMode.valueOf(p[Keys.THEME] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            speed = p[Keys.SPEED] ?: 1f,
            scaling = runCatching { Scaling.valueOf(p[Keys.SCALING] ?: "FIT") }.getOrDefault(Scaling.FIT),
            autoResume = p[Keys.AUTO_RESUME] ?: true,
            autoHide = p[Keys.AUTO_HIDE] ?: true,
            clipDetect = p[Keys.CLIP_DETECT] ?: true,
            clipAsk = p[Keys.CLIP_ASK] ?: true,
            historyEnabled = p[Keys.HISTORY] ?: true,
            saveProgress = p[Keys.SAVE_PROGRESS] ?: true,
            retryOnError = p[Keys.RETRY] ?: true,
            errorDetails = p[Keys.ERROR_DETAILS] ?: false,
        )
    }

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}
