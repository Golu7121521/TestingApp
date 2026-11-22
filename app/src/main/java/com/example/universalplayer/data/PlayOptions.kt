package com.example.universalplayer.data

import org.json.JSONObject

data class PlayOptions(
    val cookie: String = "",
    val referer: String = "",
    val origin: String = "",
    val drmLicense: String = "",
    val userAgent: String = "",
    val drmScheme: String = "Widevine"
) {
    val hasDrm get() = drmLicense.isNotBlank()

    fun headers(): Map<String, String> = buildMap {
        if (userAgent.isNotBlank()) put("User-Agent", userAgent)
        if (referer.isNotBlank()) put("Referer", referer)
        if (origin.isNotBlank()) put("Origin", origin)
        if (cookie.isNotBlank()) put("Cookie", cookie)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("cookie", cookie).put("referer", referer).put("origin", origin)
        .put("drm", drmLicense).put("ua", userAgent).put("scheme", drmScheme)

    companion object {
        fun fromJson(o: JSONObject?): PlayOptions =
            if (o == null) PlayOptions() else PlayOptions(
                o.optString("cookie"), o.optString("referer"), o.optString("origin"),
                o.optString("drm"), o.optString("ua"), o.optString("scheme", "Widevine")
            )
    }
}

val USER_AGENTS = listOf(
    "Default" to "",
    "Android Chrome" to "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36",
    "Chrome PC (Windows)" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
    "Chrome PC (macOS)" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
    "Firefox PC" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:127.0) Gecko/20100101 Firefox/127.0",
    "Firefox Android" to "Mozilla/5.0 (Android 14; Mobile; rv:127.0) Gecko/127.0 Firefox/127.0",
    "Safari iPhone" to "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
    "Edge PC" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0"
)

val DRM_SCHEMES = listOf("Widevine", "PlayReady", "ClearKey")

private val INLINE_KEY_SPLIT = Regex(
    "[&|](?=(?:drmScheme|drmLicense|drm_scheme|drm_license|drmType|license_type|license_key|licenseKey|" +
        "user-agent|useragent|referer|referrer|origin|cookie)=)",
    RegexOption.IGNORE_CASE
)
private val KID_KEY = Regex("^[0-9a-fA-F]{32}:[0-9a-fA-F]{32}(,[0-9a-fA-F]{32}:[0-9a-fA-F]{32})*$")

fun normalizeDrmScheme(s: String): String = when {
    s.contains("clear", true) -> "ClearKey"
    s.contains("play", true) -> "PlayReady"
    s.contains("wide", true) -> "Widevine"
    else -> s
}

/**
 * Supports the common IPTV link style:
 *   https://host/stream.mpd?|drmScheme=clearkey&drmLicense=KID:KEY
 *   https://host/stream.mpd|User-Agent=xyz&Referer=https://site
 * Returns the clean URL plus the options found after the "|" merged over [base].
 */
fun splitLinkOptions(raw: String, base: PlayOptions = PlayOptions()): Pair<String, PlayOptions> {
    val i = raw.indexOf('|')
    if (i < 0) return raw to base
    val url = raw.substring(0, i).trim().trimEnd('?', '&')
    var o = base
    for (part in raw.substring(i + 1).split(INLINE_KEY_SPLIT)) {
        val eq = part.indexOf('=')
        if (eq <= 0) continue
        val k = part.substring(0, eq).trim().lowercase()
        var v = part.substring(eq + 1).trim()
        if (v.contains('%')) v = runCatching { java.net.URLDecoder.decode(v, "UTF-8") }.getOrDefault(v)
        if (v.isEmpty()) continue
        o = when (k) {
            "drmscheme", "drm_scheme", "drmtype", "license_type" -> o.copy(drmScheme = normalizeDrmScheme(v))
            "drmlicense", "drm_license", "license_key", "licensekey" -> o.copy(drmLicense = v)
            "user-agent", "useragent" -> o.copy(userAgent = v)
            "referer", "referrer" -> o.copy(referer = v)
            "origin" -> o.copy(origin = v)
            "cookie" -> o.copy(cookie = v)
            else -> o
        }
    }
    // a bare kid:key license is always ClearKey
    if (o.drmLicense.isNotBlank() && KID_KEY.matches(o.drmLicense.replace(" ", "")) && o.drmScheme != "ClearKey") {
        o = o.copy(drmScheme = "ClearKey")
    }
    return url to o
}
