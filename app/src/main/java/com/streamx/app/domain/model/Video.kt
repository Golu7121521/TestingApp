package com.streamx.app.domain.model

/**
 * Domain model for a video shown in feeds and search results.
 * Kept free of Android and NewPipeExtractor types so the UI and domain layers stay independent.
 */
data class Video(
    val id: String,
    val title: String,
    val channelName: String,
    val channelAvatarUrl: String?,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
    val viewCountText: String?,
    val uploadedText: String?,
)
