package com.mytune.app.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Switches the launcher icon to the colour of the chosen accent (one activity-alias per accent). */
object IconManager {
    fun apply(ctx: Context, accentKey: String) {
        val pm = ctx.packageManager
        val target = Accents.byKey(accentKey).key
        // Enable the new alias first, then disable the others, so the app never has "no launcher icon".
        Accents.all.sortedBy { it.key != target }.forEach { a ->
            val cn = ComponentName(ctx.packageName, "com.mytune.app.Icon${a.key.replaceFirstChar { it.uppercase() }}")
            val state = if (a.key == target) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            if (pm.getComponentEnabledSetting(cn) != state) {
                pm.setComponentEnabledSetting(cn, state, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
