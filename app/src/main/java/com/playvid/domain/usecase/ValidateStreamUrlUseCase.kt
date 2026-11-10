package com.playvid.domain.usecase

import java.net.URI
import java.net.URISyntaxException

sealed interface UrlValidation {
    data class Valid(val url: String, val isHls: Boolean, val title: String) : UrlValidation
    data class Invalid(val message: String) : UrlValidation
}

/**
 * Checks a typed/pasted link before it reaches the player so bad input gives a
 * short readable message instead of a playback failure.
 */
class ValidateStreamUrlUseCase {

    operator fun invoke(raw: String): UrlValidation {
        val url = raw.trim()
        if (url.isEmpty() || url.length > MAX_LENGTH || url.any { it.isWhitespace() }) return INVALID

        // A `|drmScheme=...&drmLicense=keyId:key` suffix (ClearKey DASH links) isn't valid URI
        // syntax on its own, so only the manifest URL before it is checked; the full string
        // (DRM part included) is still what gets played.
        val barIndex = url.indexOf('|')
        val checkUrl = if (barIndex >= 0) url.substring(0, barIndex) else url

        val uri = try {
            URI(checkUrl)
        } catch (e: URISyntaxException) {
            return INVALID
        }

        val scheme = uri.scheme?.lowercase() ?: return INVALID
        if (uri.host.isNullOrBlank()) return INVALID
        // rtsp://, ftp://, etc. are well-formed but not playable here.
        if (scheme != "http" && scheme != "https") return UNSUPPORTED

        val path = uri.path.orEmpty()
        val fileName = path.substringAfterLast('/')
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val hasExtension = extension.length in 1..5 && extension.all { it.isLetterOrDigit() }
        // Reject only extensions that are clearly not video (images, documents, archives, ...).
        // Proxy/relay links (e.g. stream_proxy.php?url=...m3u8...) have a script extension on the
        // outer URL with the real media type buried in a query param, so those must stay allowed;
        // the real type is sniffed from the response while loading either way.
        if (hasExtension && extension in BLOCKED_EXTENSIONS) return UNSUPPORTED

        return UrlValidation.Valid(
            url = url,
            isHls = url.lowercase().contains(".m3u8"),
            title = if (isYouTubeHost(uri.host)) "YouTube video" else fileName.takeIf { it.isNotBlank() } ?: uri.host
        )
    }

    private fun isYouTubeHost(host: String): Boolean {
        val h = host.lowercase()
        return h == "youtu.be" || h == "youtube.com" || h.endsWith(".youtube.com")
    }

    private companion object {
        const val MAX_LENGTH = 2048
        val INVALID = UrlValidation.Invalid("Please enter a valid video URL")
        val UNSUPPORTED = UrlValidation.Invalid("Unsupported video format")
        val BLOCKED_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "ico",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "json", "xml",
            "zip", "rar", "7z", "tar", "gz", "apk", "exe", "dmg", "iso",
            "mp3", "wav", "aac", "flac", "wma", // audio-only files, not this player's job
            "html", "htm", "css", "js"
        )
    }
}
