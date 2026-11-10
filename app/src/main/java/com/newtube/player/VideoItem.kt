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
    val url: String
) {
    /** Channel ki unique pehchaan: URL ho to URL, warna naam */
    val key: String
        get() = if (url.isNotBlank()) url else name
}

data class PlayedVideoStream(
    val title: String,
    val uploaderName: String,
    val uploaderUrl: String = "",
    val viewsAndDate: String,
    val videoStreamUrl: String,
    val audioStreamUrl: String? = null,
    val isHls: Boolean = false,
    val isDashManifest: Boolean = false,
    val thumbnailUrl: String,
    val relatedItems: List<VideoItem>
)

sealed class StreamExtractionResult {
    data class Success(val data: PlayedVideoStream) : StreamExtractionResult()
    data class Error(val message: String) : StreamExtractionResult()
}
