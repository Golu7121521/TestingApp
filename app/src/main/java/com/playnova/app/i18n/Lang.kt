package com.playnova.app.i18n

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Tiny in-app translation layer. All UI text is written in English in code, tr("English text")
 * looks it up in the selected language dictionary and falls back to English.
 */
object Lang {
    /** code -> name shown in its own language */
    val options: List<Pair<String, String>> = listOf(
        "system" to "System default", "en" to "English", "hi" to "हिन्दी", "es" to "Español",
        "fr" to "Français", "pt" to "Português", "id" to "Bahasa Indonesia", "ar" to "العربية", "ru" to "Русский"
    )

    @Volatile var code: String = "system"
        private set
    @Volatile private var dict: Map<String, String> = emptyMap()

    fun saved(ctx: Context): String = ctx.getSharedPreferences("playnova", Context.MODE_PRIVATE).getString("lang", "system") ?: "system"

    private fun dictFor(lang: String): Map<String, String> = when (lang) {
        "hi" -> TR_HI; "es" -> TR_ES; "fr" -> TR_FR; "pt" -> TR_PT
        "id", "in" -> TR_ID; "ar" -> TR_AR; "ru" -> TR_RU
        else -> emptyMap()
    }

    fun init(c: String) {
        code = c
        val effective = if (c == "system") Locale.getDefault().language else c
        dict = dictFor(effective)
    }

    /** Wrap an Activity base context so layout direction and number formats follow the chosen language. */
    fun wrap(base: Context): Context {
        init(saved(base))
        if (code == "system") return base
        val loc = Locale(code)
        Locale.setDefault(loc)
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(loc)
        cfg.setLayoutDirection(loc)
        return base.createConfigurationContext(cfg)
    }

    fun tr(s: String): String = dict[s] ?: s
}

fun tr(s: String): String = Lang.tr(s)

fun tr(s: String, vararg a: Any?): String =
    try { String.format(Lang.tr(s), *a) } catch (e: Exception) { String.format(s, *a) }
