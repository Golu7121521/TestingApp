package com.playnova.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Simple SharedPreferences backed settings exposed as one immutable state object. */
data class Prefs(
    val theme: Int = 0,               // 0 dark, 1 light, 2 system
    val accent: Int = 0,
    val gridDefault: Boolean = true,
    val defaultSpeed: Float = 1f,
    val resume: Boolean = true,
    val autoNext: Boolean = true,
    val orientation: Int = 0,         // 0 auto, 1 landscape, 2 portrait
    val aspect: Int = 0,              // 0 fit, 1 stretch, 2 crop, 3 fill width, 4 fill height
    val skipSec: Int = 10,
    val autoPip: Boolean = true,
    val gBrightness: Boolean = true,
    val gVolume: Boolean = true,
    val gSeek: Boolean = true,
    val gDoubleTap: Boolean = true,
    val gLongPress: Boolean = true,
    val longPressSpeed: Float = 2f,
    val sensitivity: Float = 1f,
    val subSize: Float = 1f,
    val subColor: Int = 0,            // 0 white, 1 yellow, 2 cyan
    val subBg: Float = 0.4f,
    val onboarded: Boolean = false
)

class SettingsStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("playnova", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Prefs> = _state

    private fun load(): Prefs {
        val d = Prefs()
        return Prefs(
            sp.getInt("theme", d.theme), sp.getInt("accent", d.accent),
            sp.getBoolean("grid", d.gridDefault), sp.getFloat("speed", d.defaultSpeed),
            sp.getBoolean("resume", d.resume), sp.getBoolean("autoNext", d.autoNext),
            sp.getInt("orient", d.orientation), sp.getInt("aspect", d.aspect),
            sp.getInt("skip", d.skipSec), sp.getBoolean("autoPip", d.autoPip),
            sp.getBoolean("gB", d.gBrightness), sp.getBoolean("gV", d.gVolume),
            sp.getBoolean("gS", d.gSeek), sp.getBoolean("gD", d.gDoubleTap),
            sp.getBoolean("gL", d.gLongPress), sp.getFloat("lpSpeed", d.longPressSpeed),
            sp.getFloat("sens", d.sensitivity), sp.getFloat("subSize", d.subSize),
            sp.getInt("subColor", d.subColor), sp.getFloat("subBg", d.subBg),
            sp.getBoolean("onboarded", d.onboarded)
        )
    }

    fun update(f: (Prefs) -> Prefs) {
        val p = f(_state.value)
        _state.value = p
        sp.edit()
            .putInt("theme", p.theme).putInt("accent", p.accent).putBoolean("grid", p.gridDefault)
            .putFloat("speed", p.defaultSpeed).putBoolean("resume", p.resume)
            .putBoolean("autoNext", p.autoNext).putInt("orient", p.orientation)
            .putInt("aspect", p.aspect).putInt("skip", p.skipSec).putBoolean("autoPip", p.autoPip)
            .putBoolean("gB", p.gBrightness).putBoolean("gV", p.gVolume).putBoolean("gS", p.gSeek)
            .putBoolean("gD", p.gDoubleTap).putBoolean("gL", p.gLongPress)
            .putFloat("lpSpeed", p.longPressSpeed).putFloat("sens", p.sensitivity)
            .putFloat("subSize", p.subSize).putInt("subColor", p.subColor).putFloat("subBg", p.subBg)
            .putBoolean("onboarded", p.onboarded).apply()
    }

    // IDs the user removed from the library view (file itself is untouched)
    private val _hidden = MutableStateFlow(sp.getStringSet("hidden", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet())
    val hidden: StateFlow<Set<Long>> = _hidden
    fun hide(ids: Collection<Long>) {
        val n = _hidden.value + ids
        _hidden.value = n
        sp.edit().putStringSet("hidden", n.map { it.toString() }.toSet()).apply()
    }
    fun unhideAll() {
        _hidden.value = emptySet()
        sp.edit().remove("hidden").apply()
    }
}
