package com.example.devicehealth.system

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("devicehealth", Context.MODE_PRIVATE)

    /** 0 = follow system, 1 = light, 2 = dark */
    var themeMode: Int
        get() = sp.getInt("theme", 0)
        set(v) { sp.edit().putInt("theme", v).apply() }

    var accentIndex: Int
        get() = sp.getInt("accent", 0)
        set(v) { sp.edit().putInt("accent", v).apply() }

    var alertsEnabled: Boolean
        get() = sp.getBoolean("alerts", false)
        set(v) { sp.edit().putBoolean("alerts", v).apply() }

    var onboardingDone: Boolean
        get() = sp.getBoolean("onboarding", false)
        set(v) { sp.edit().putBoolean("onboarding", v).apply() }
}
