package com.example.universalplayer.data

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import java.util.UUID

enum class DrmScheme(val label: String, val uuid: UUID) {
    WIDEVINE("Widevine", C.WIDEVINE_UUID),
    PLAYREADY("PlayReady", C.PLAYREADY_UUID),
    CLEARKEY("ClearKey", C.CLEARKEY_UUID)
}

enum class UserAgentPreset(val label: String, val value: String?) {
    DEFAULT("Default (app)", null),
    ANDROID_CHROME(
        "Android Chrome",
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Mobile Safari/537.36"
    ),
    CHROME_PC(
        "Chrome (PC)",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Safari/537.36"
    ),
    FIREFOX_PC(
        "Firefox (PC)",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    ),
    EDGE_PC(
        "Edge (PC)",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0"
    ),
    SAFARI_IPHONE(
        "Safari (iPhone)",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"
    )
}

/**
 * Values from the Advanced tab. They are applied to every video that is played
 * while they are filled in (empty fields are ignored).
 */
data class AdvancedSettings(
    val link: String = "",
    val cookie: String = "",
    val referer: String = "",
    val origin: String = "",
    val userAgent: UserAgentPreset = UserAgentPreset.DEFAULT,
    val drmLicenseUri: String = "",
    val drmScheme: DrmScheme = DrmScheme.WIDEVINE
) {
    /** Extra HTTP headers (only non-empty ones). */
    val headers: Map<String, String>
        get() = buildMap {
            cookie.trim().takeIf { it.isNotEmpty() }?.let { put("Cookie", it) }
            referer.trim().takeIf { it.isNotEmpty() }?.let { put("Referer", it) }
            origin.trim().takeIf { it.isNotEmpty() }?.let { put("Origin", it) }
            userAgent.value?.let { put("User-Agent", it) }
        }

    /** DRM configuration for the media item, or null when no license URI is set. */
    fun drmConfig(): MediaItem.DrmConfiguration? {
        val uri = drmLicenseUri.trim()
        if (uri.isEmpty()) return null
        return MediaItem.DrmConfiguration.Builder(drmScheme.uuid)
            .setLicenseUri(uri)
            .setLicenseRequestHeaders(headers)
            .build()
    }
}
