package com.playvid.playback

import com.playvid.domain.model.Channel

/**
 * Parses an M3U/M3U8 *channel list*: `#EXTINF` (name, tvg-logo, group-title) + stream URL, plus the
 * per-channel extras IPTV lists use — `#EXTVLCOPT` (user-agent / referrer), `#EXTHTTP` (cookie) and
 * `#KODIPROP` ClearKey licenses. Those extras are folded into the URL as a
 * `|user-agent=..&referer=..&cookie=..&drmScheme=clearkey&drmLicense=kid:key` suffix, which the
 * player already understands. Not for HLS media/master manifests — see [looksLikeHlsManifest].
 */
object M3uParser {

    private val ATTR = Regex("""([\w-]+)="([^"]*)"""")
    private val COOKIE_JSON = Regex(""""cookie"\s*:\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
    private val UA_JSON = Regex(""""user-agent"\s*:\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
    private val HEX_PAIR = Regex("""^[0-9a-fA-F]+:[0-9a-fA-F]+$""")

    fun parseChannels(text: String): List<Channel> {
        val out = mutableListOf<Channel>()
        var name: String? = null
        var logo: String? = null
        var group: String? = null
        var ua: String? = null
        var referer: String? = null
        var origin: String? = null
        var cookie: String? = null
        var drmScheme: String? = null
        var drmLicense: String? = null

        fun reset() {
            name = null; logo = null; group = null; ua = null; referer = null
            origin = null; cookie = null; drmScheme = null; drmLicense = null
        }

        text.lineSequence().forEach { raw ->
            val line = raw.trim().removePrefix("\uFEFF")
            when {
                line.isEmpty() -> {}
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val body = line.substringAfter(':', "")
                    ATTR.findAll(body).forEach { m ->
                        when (m.groupValues[1].lowercase()) {
                            "tvg-logo" -> logo = m.groupValues[2].ifBlank { null }
                            "group-title" -> group = m.groupValues[2].ifBlank { null }
                        }
                    }
                    name = displayName(body)
                }
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = line.substringAfter(':')
                    val key = opt.substringBefore('=').trim().lowercase()
                    val value = opt.substringAfter('=', "").trim()
                    when (key) {
                        "http-user-agent" -> ua = value
                        "http-referrer", "http-referer" -> referer = value
                        "http-origin" -> origin = value
                    }
                }
                line.startsWith("#EXTHTTP:", ignoreCase = true) -> {
                    COOKIE_JSON.find(line)?.groupValues?.get(1)?.let { cookie = it }
                    UA_JSON.find(line)?.groupValues?.get(1)?.let { ua = it }
                }
                line.startsWith("#KODIPROP:", ignoreCase = true) -> {
                    val prop = line.substringAfter(':')
                    val key = prop.substringBefore('=').trim().lowercase()
                    val value = prop.substringAfter('=', "").trim()
                    when (key) {
                        "inputstream.adaptive.license_type" -> drmScheme = value.lowercase()
                        "inputstream.adaptive.license_key" -> drmLicense = value
                    }
                }
                line.startsWith("#") -> {}
                line.startsWith("http://", ignoreCase = true) || line.startsWith("https://", ignoreCase = true) -> {
                    val params = mutableListOf<String>()
                    ua?.takeIf { it.isNotBlank() && !line.contains("user-agent=", true) }
                        ?.let { params += "user-agent=$it" }
                    referer?.takeIf { it.isNotBlank() }?.let { params += "referer=$it" }
                    origin?.takeIf { it.isNotBlank() }?.let { params += "origin=$it" }
                    cookie?.takeIf { it.isNotBlank() }?.let { params += "cookie=$it" }
                    val license = drmLicense
                    if (drmScheme?.contains("clearkey") == true && license != null && HEX_PAIR.matches(license)) {
                        params += "drmScheme=clearkey"
                        params += "drmLicense=$license"
                    }
                    val url = when {
                        params.isEmpty() -> line
                        line.contains('|') -> line + "&" + params.joinToString("&")
                        else -> line + "|" + params.joinToString("&")
                    }
                    val title = name ?: line.substringBefore('|').substringBefore('?')
                        .substringAfterLast('/').ifBlank { line }
                    out += Channel(name = title, url = url, logo = logo, group = group)
                    reset()
                }
                else -> {} // rtmp://, local paths, junk: not playable here
            }
        }
        return out
    }

    /** The text after the first comma that is *outside* quotes (attribute values may contain commas). */
    private fun displayName(extinfBody: String): String? {
        var inQuotes = false
        extinfBody.forEachIndexed { i, c ->
            if (c == '"') inQuotes = !inQuotes
            else if (c == ',' && !inQuotes) return extinfBody.substring(i + 1).trim().ifBlank { null }
        }
        return null
    }

    /** True if this is an actual HLS stream manifest (one playable video), not an IPTV channel list. */
    fun looksLikeHlsManifest(text: String): Boolean =
        text.contains("#EXT-X-STREAM-INF") || text.contains("#EXT-X-TARGETDURATION") || text.contains("#EXT-X-VERSION")
}
