package com.business.musicapp

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val category: String,
    val durationLabel: String,
    val sourceUrl: String,
    val isLocal: Boolean = false
)
