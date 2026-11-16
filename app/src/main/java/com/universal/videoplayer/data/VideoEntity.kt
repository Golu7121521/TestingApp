package com.universal.videoplayer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val url: String,
    val title: String,
    val source: String,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val lastWatched: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(tableName = "playlist_items", primaryKeys = ["playlistId", "videoUrl"])
data class PlaylistItemEntity(
    val playlistId: Long,
    val videoUrl: String,
    val title: String,
    val position: Int
)
