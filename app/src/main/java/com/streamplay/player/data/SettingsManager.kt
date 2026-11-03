package com.streamplay.player.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "player_settings")

data class GestureSettings(
    val swipeVolumeBrightness: Boolean = true,
    val pinchToZoom: Boolean = true,
    val doubleTapSeek: Boolean = true,
    val holdFor2xSpeed: Boolean = true,
    val autoPip: Boolean = true
)

class SettingsManager(private val context: Context) {
    companion object {
        val KEY_SWIPE = booleanPreferencesKey("swipe_vol_bright")
        val KEY_PINCH = booleanPreferencesKey("pinch_zoom")
        val KEY_DOUBLE_TAP = booleanPreferencesKey("double_tap_seek")
        val KEY_HOLD_2X = booleanPreferencesKey("hold_2x_speed")
        val KEY_AUTO_PIP = booleanPreferencesKey("auto_pip")
    }

    val settingsFlow: Flow<GestureSettings> = context.dataStore.data.map { prefs ->
        GestureSettings(
            swipeVolumeBrightness = prefs[KEY_SWIPE] ?: true,
            pinchToZoom = prefs[KEY_PINCH] ?: true,
            doubleTapSeek = prefs[KEY_DOUBLE_TAP] ?: true,
            holdFor2xSpeed = prefs[KEY_HOLD_2X] ?: true,
            autoPip = prefs[KEY_AUTO_PIP] ?: true
        )
    }

    suspend fun updateSetting(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { it[key] = value }
    }
}
