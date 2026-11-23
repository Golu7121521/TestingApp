package com.example.universalplayer.player

import android.content.Context
import android.content.SharedPreferences

/** Small persistent settings (Advanced tab). */
object PlayerSettings {
    private var prefs: SharedPreferences? = null

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("player_settings", Context.MODE_PRIVATE)
    }

    /** Reconnect automatically after a network drop. */
    var autoReconnect: Boolean
        get() = prefs?.getBoolean("auto_reconnect", true) ?: true
        set(v) { prefs?.edit()?.putBoolean("auto_reconnect", v)?.apply() }

    /** 0 = fast start (small buffer), 1 = balanced, 2 = stable (big buffer). Applied at next app start. */
    var bufferLevel: Int
        get() = prefs?.getInt("buffer_level", 1) ?: 1
        set(v) { prefs?.edit()?.putInt("buffer_level", v)?.apply() }

    /** Playlist screen shows a grid (true) or a list (false). Remembered across screens and app restarts. */
    var playlistGrid: Boolean
        get() = prefs?.getBoolean("playlist_grid", false) ?: false
        set(v) { prefs?.edit()?.putBoolean("playlist_grid", v)?.apply() }
}
