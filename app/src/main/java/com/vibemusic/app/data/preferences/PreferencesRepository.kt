package com.vibemusic.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vibemusic.app.domain.model.AppPreferences
import com.vibemusic.app.domain.model.RepeatModeOption
import com.vibemusic.app.domain.model.SortOption
import com.vibemusic.app.domain.model.ThemeModeOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vibe_preferences")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_COLOR = longPreferencesKey("accent_color")
        val GAPLESS_PLAYBACK = booleanPreferencesKey("gapless_playback")
        val RESUME_PLAYBACK = booleanPreferencesKey("resume_playback")
        val AUTO_PLAY = booleanPreferencesKey("auto_play")
        val CROSSFADE_SECONDS = intPreferencesKey("crossfade_seconds")
        val DEFAULT_REPEAT_MODE = stringPreferencesKey("default_repeat_mode")
        val ALBUM_ART_ANIMATION = booleanPreferencesKey("album_art_animation")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val SORT_OPTION = stringPreferencesKey("sort_option")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET_INDEX = intPreferencesKey("eq_preset_index")
        val EQ_BAND_LEVELS = stringPreferencesKey("eq_band_levels")
        val BASS_BOOST_STRENGTH = intPreferencesKey("bass_boost_strength")
        val VIRTUALIZER_STRENGTH = intPreferencesKey("virtualizer_strength")
        val LAST_PLAYED_SONG_ID = longPreferencesKey("last_played_song_id")
        val LAST_PLAYED_POSITION_MS = longPreferencesKey("last_played_position_ms")
    }

    val preferencesFlow: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_MODE] ?: ThemeModeOption.AMOLED_DARK.name
        val repeatStr = prefs[Keys.DEFAULT_REPEAT_MODE] ?: RepeatModeOption.OFF.name
        val sortStr = prefs[Keys.SORT_OPTION] ?: SortOption.TITLE_ASC.name
        val bandsStr = prefs[Keys.EQ_BAND_LEVELS] ?: ""
        val bandList = if (bandsStr.isBlank()) emptyList() else {
            bandsStr.split(",").mapNotNull { it.toIntOrNull() }
        }

        AppPreferences(
            themeMode = runCatching { ThemeModeOption.valueOf(themeStr) }.getOrDefault(ThemeModeOption.AMOLED_DARK),
            accentColorHex = prefs[Keys.ACCENT_COLOR] ?: 0xFFA855F7,
            gaplessPlayback = prefs[Keys.GAPLESS_PLAYBACK] ?: true,
            resumePlayback = prefs[Keys.RESUME_PLAYBACK] ?: true,
            autoPlay = prefs[Keys.AUTO_PLAY] ?: false,
            crossfadeSeconds = prefs[Keys.CROSSFADE_SECONDS] ?: 0,
            defaultRepeatMode = runCatching { RepeatModeOption.valueOf(repeatStr) }.getOrDefault(RepeatModeOption.OFF),
            albumArtAnimation = prefs[Keys.ALBUM_ART_ANIMATION] ?: true,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: false,
            playbackSpeed = prefs[Keys.PLAYBACK_SPEED] ?: 1.0f,
            sortOption = runCatching { SortOption.valueOf(sortStr) }.getOrDefault(SortOption.TITLE_ASC),
            eqEnabled = prefs[Keys.EQ_ENABLED] ?: false,
            eqPresetIndex = prefs[Keys.EQ_PRESET_INDEX] ?: 0,
            eqBandLevels = bandList,
            bassBoostStrength = prefs[Keys.BASS_BOOST_STRENGTH] ?: 0,
            virtualizerStrength = prefs[Keys.VIRTUALIZER_STRENGTH] ?: 0,
            lastPlayedSongId = prefs[Keys.LAST_PLAYED_SONG_ID] ?: -1L,
            lastPlayedPositionMs = prefs[Keys.LAST_PLAYED_POSITION_MS] ?: 0L
        )
    }

    suspend fun setThemeMode(mode: ThemeModeOption) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAccentColor(hex: Long) {
        context.dataStore.edit { it[Keys.ACCENT_COLOR] = hex }
    }

    suspend fun setGaplessPlayback(enabled: Boolean) {
        context.dataStore.edit { it[Keys.GAPLESS_PLAYBACK] = enabled }
    }

    suspend fun setResumePlayback(enabled: Boolean) {
        context.dataStore.edit { it[Keys.RESUME_PLAYBACK] = enabled }
    }

    suspend fun setAutoPlay(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_PLAY] = enabled }
    }

    suspend fun setCrossfadeSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.CROSSFADE_SECONDS] = seconds.coerceIn(0, 12) }
    }

    suspend fun setDefaultRepeatMode(mode: RepeatModeOption) {
        context.dataStore.edit { it[Keys.DEFAULT_REPEAT_MODE] = mode.name }
    }

    suspend fun setAlbumArtAnimation(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ALBUM_ART_ANIMATION] = enabled }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.dataStore.edit { it[Keys.PLAYBACK_SPEED] = speed.coerceIn(0.5f, 2.5f) }
    }

    suspend fun setSortOption(sort: SortOption) {
        context.dataStore.edit { it[Keys.SORT_OPTION] = sort.name }
    }

    suspend fun setEqEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.EQ_ENABLED] = enabled }
    }

    suspend fun setEqPresetAndBands(presetIndex: Int, bandLevels: List<Int>) {
        context.dataStore.edit {
            it[Keys.EQ_PRESET_INDEX] = presetIndex
            it[Keys.EQ_BAND_LEVELS] = bandLevels.joinToString(",")
        }
    }

    suspend fun setBassBoostStrength(strength: Int) {
        context.dataStore.edit { it[Keys.BASS_BOOST_STRENGTH] = strength.coerceIn(0, 1000) }
    }

    suspend fun setVirtualizerStrength(strength: Int) {
        context.dataStore.edit { it[Keys.VIRTUALIZER_STRENGTH] = strength.coerceIn(0, 1000) }
    }

    suspend fun saveLastPlaybackState(songId: Long, positionMs: Long) {
        context.dataStore.edit {
            it[Keys.LAST_PLAYED_SONG_ID] = songId
            it[Keys.LAST_PLAYED_POSITION_MS] = positionMs
        }
    }
}
