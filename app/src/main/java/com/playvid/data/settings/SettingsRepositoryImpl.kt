package com.playvid.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.playvid.domain.model.AppSettings
import com.playvid.domain.model.ThemeMode
import com.playvid.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepositoryImpl(private val context: Context) : SettingsRepository {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val resumePlayback = booleanPreferencesKey("resume_playback")
        val brightnessGesture = booleanPreferencesKey("brightness_gesture")
        val volumeGesture = booleanPreferencesKey("volume_gesture")
        val preferredQuality = intPreferencesKey("preferred_quality")
        val dataSaver = booleanPreferencesKey("data_saver")
        val holdSpeedGesture = booleanPreferencesKey("hold_speed_gesture")
        val holdSpeedMultiplier = intPreferencesKey("hold_speed_multiplier")
        val pipEnabled = booleanPreferencesKey("pip_enabled")
        val backgroundPlayback = booleanPreferencesKey("background_playback")
    }

    override val settings: Flow<AppSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val new = transform(prefs.toSettings())
            prefs[Keys.theme] = new.theme.name
            prefs[Keys.resumePlayback] = new.resumePlayback
            prefs[Keys.brightnessGesture] = new.brightnessGesture
            prefs[Keys.volumeGesture] = new.volumeGesture
            prefs[Keys.preferredQuality] = new.preferredQuality
            prefs[Keys.dataSaver] = new.dataSaver
            prefs[Keys.holdSpeedGesture] = new.holdSpeedGesture
            prefs[Keys.holdSpeedMultiplier] = new.holdSpeedMultiplier
            prefs[Keys.pipEnabled] = new.pipEnabled
            prefs[Keys.backgroundPlayback] = new.backgroundPlayback
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            theme = this[Keys.theme]
                ?.let { name -> runCatching { ThemeMode.valueOf(name) }.getOrNull() } ?: d.theme,
            resumePlayback = this[Keys.resumePlayback] ?: d.resumePlayback,
            brightnessGesture = this[Keys.brightnessGesture] ?: d.brightnessGesture,
            volumeGesture = this[Keys.volumeGesture] ?: d.volumeGesture,
            preferredQuality = this[Keys.preferredQuality] ?: d.preferredQuality,
            dataSaver = this[Keys.dataSaver] ?: d.dataSaver,
            holdSpeedGesture = this[Keys.holdSpeedGesture] ?: d.holdSpeedGesture,
            holdSpeedMultiplier = this[Keys.holdSpeedMultiplier] ?: d.holdSpeedMultiplier,
            pipEnabled = this[Keys.pipEnabled] ?: d.pipEnabled,
            backgroundPlayback = this[Keys.backgroundPlayback] ?: d.backgroundPlayback
        )
    }
}
