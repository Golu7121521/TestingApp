package com.playnova.app.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Launcher icon follows the accent color through activity-aliases (one per accent). */
object AppIcon {
    private val names = listOf("Purple", "Blue", "Teal", "Pink", "Orange")

    fun apply(ctx: Context, accent: Int) {
        val sp = ctx.getSharedPreferences("playnova", Context.MODE_PRIVATE)
        val target = accent.coerceIn(names.indices)
        if (sp.getInt("iconIdx", 0) == target) return
        val pm = ctx.packageManager
        runCatching {
            set(pm, ctx, names[target], true)
            names.forEachIndexed { i, n -> if (i != target) set(pm, ctx, n, false) }
            sp.edit().putInt("iconIdx", target).apply()
        }
    }

    private fun set(pm: PackageManager, ctx: Context, name: String, on: Boolean) {
        pm.setComponentEnabledSetting(
            ComponentName(ctx.packageName, "com.playnova.app.Icon$name"),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
