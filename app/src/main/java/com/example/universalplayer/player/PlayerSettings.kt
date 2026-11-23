package com.example.universalplayer.player

import android.content.Context
import android.content.SharedPreferences

/** Small persistent settings (Advanced tab). */
object PlayerSettings {
    private var prefs: SharedPreferences? = null

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("player_settings", Context.MODE_PRIVATE)
    }

    /** Keep playing audio when the app is in the background / screen is off. */
    var backgroundAudio: Boolean
        get() = prefs?.getBoolean("bg_audio", true) ?: true
        set(v) { prefs?.edit()?.putBoolean("bg_audio", v)?.apply() }

    /** Reconnect automatically after a network drop. */
    var autoReconnect: Boolean
        get() = prefs?.getBoolean("auto_reconnect", true) ?: true
        set(v) { prefs?.edit()?.putBoolean("auto_reconnect", v)?.apply() }

    /** 0 = fast start (small buffer), 1 = balanced, 2 = stable (big buffer). Applied at next app start. */
    var bufferLevel: Int
        get() = prefs?.getInt("buffer_level", 1) ?: 1
        set(v) { prefs?.edit()?.putInt("buffer_level", v)?.apply() }
}
