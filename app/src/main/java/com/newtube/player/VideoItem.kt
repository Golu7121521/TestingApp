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
    val streamUrl: String,
    val thumbnailUrl: String,
    val relatedItems: List<VideoItem>
)
