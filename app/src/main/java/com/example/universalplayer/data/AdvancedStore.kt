package com.example.universalplayer.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Keeps the Advanced tab settings and remembers them between app starts. */
object AdvancedStore {
    private var prefs: SharedPreferences? = null
    private val _settings = MutableStateFlow(AdvancedSettings())
    val settings: StateFlow<AdvancedSettings> = _settings.asStateFlow()

    fun init(ctx: Context) {
        val p = ctx.getSharedPreferences("advanced", Context.MODE_PRIVATE)
        prefs = p
        _settings.value = AdvancedSettings(
            link = p.getString("link", null).orEmpty(),
            cookie = p.getString("cookie", null).orEmpty(),
            referer = p.getString("referer", null).orEmpty(),
            origin = p.getString("origin", null).orEmpty(),
            userAgent = UserAgentPreset.entries.firstOrNull { it.name == p.getString("ua", null) }
                ?: UserAgentPreset.DEFAULT,
            drmLicenseUri = p.getString("drm", null).orEmpty(),
            drmScheme = DrmScheme.entries.firstOrNull { it.name == p.getString("scheme", null) }
                ?: DrmScheme.WIDEVINE
        )
    }

    fun update(s: AdvancedSettings) {
        _settings.value = s
        val e = prefs?.edit() ?: return
        e.putString("link", s.link)
        e.putString("cookie", s.cookie)
        e.putString("referer", s.referer)
        e.putString("origin", s.origin)
        e.putString("ua", s.userAgent.name)
        e.putString("drm", s.drmLicenseUri)
        e.putString("scheme", s.drmScheme.name)
        e.apply()
    }
}
