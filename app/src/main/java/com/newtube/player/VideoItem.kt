package com.newtube.player

data class VideoItem(
    val title: String,
    val uploaderName: String,
    val viewCountText: String,
    val durationText: String,
    val thumbnailUrl: String,
    val videoUrl: String,
    val isShort: Boolean = false
)

data class PlayedVideoStream(
    val title: String,
    val uploaderName: String,
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
