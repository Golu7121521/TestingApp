package com.example.offlineplayer

data class VideoItem(
    val id: Long,
    val title: String,
    val uriString: String,
    val durationMs: Long
)
