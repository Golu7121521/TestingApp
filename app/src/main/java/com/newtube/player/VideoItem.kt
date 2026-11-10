package com.newtube.player

data class VideoItem(
    val title: String,
    val uploaderName: String,
    val viewCountText: String,
    val durationText: String,
    val thumbnailUrl: String,
    val videoUrl: String,
    val isShort: Boolean = false,
    val uploaderUrl: String = ""
)

data class Subscription(
    val name: String,
    val url: String,
    val avatarUrl: String = ""
) {
    /** Channel ki pehchaan: URL ho to URL, warna naam */
    val key: String
        get() = if (url.isNotBlank()) url else name
}

/** Ek quality option. audioUrl != null matlab video-only + audio merge hoga */
data class QualityOption(
    val label: String,
    val height: Int,
    val videoUrl: String,
    val audioUrl: String?,
    val isProgressive: Boolean
)

data class SubtitleOption(
    val label: String,
    val url: String,
    val mimeType: String,
    val languageTag: String
)

data class PlayedVideoStream(
    val title: String,
    val uploaderName: String,
    val uploaderUrl: String,
    val uploaderAvatarUrl: String,
    val viewsAndDate: String,
    val dashUrl: String?,
    val hlsUrl: String?,
    val qualities: List<QualityOption>,
    val subtitles: List<SubtitleOption>,
    val thumbnailUrl: String,
    val relatedItems: List<VideoItem>
)

sealed class StreamExtractionResult {
    data class Success(val data: PlayedVideoStream) : StreamExtractionResult()
    data class Error(val message: String) : StreamExtractionResult()
}
