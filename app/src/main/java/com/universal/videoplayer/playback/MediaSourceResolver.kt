package com.universal.videoplayer.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import java.net.URL

data class PlaybackRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val userAgent: String? = null
)

sealed interface ResolveResult {
    data class Success(val mediaItem: MediaItem) : ResolveResult
    data class Failure(val message: String) : ResolveResult
}

object MediaSourceResolver {
    fun resolve(request: PlaybackRequest): ResolveResult {
        val raw = request.url.trim()
        val uri = runCatching { Uri.parse(raw) }.getOrNull()
            ?: return ResolveResult.Failure("Enter a valid video URL.")
        if (uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank()) {
            return ResolveResult.Failure("Only valid HTTP or HTTPS media URLs are supported.")
        }
        val path = uri.lastPathSegment.orEmpty().lowercase()
        val mime = when {
            path.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
            path.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
            path.endsWith(".ism") || path.endsWith(".isml") -> MimeTypes.APPLICATION_SS
            path.endsWith(".mp4") || path.endsWith(".m4v") -> MimeTypes.VIDEO_MP4
            path.endsWith(".webm") -> MimeTypes.VIDEO_WEBM
            path.endsWith(".mp3") -> MimeTypes.AUDIO_MPEG
            path.endsWith(".mkv") -> MimeTypes.VIDEO_MATROSKA
            else -> null
        }
        val item = MediaItem.Builder().setUri(uri).apply {
            if (mime != null) setMimeType(mime)
        }.build()
        return ResolveResult.Success(item)
    }

    fun sourceDomain(url: String): String =
        runCatching { URL(url).host }.getOrDefault("Unknown source")
}
