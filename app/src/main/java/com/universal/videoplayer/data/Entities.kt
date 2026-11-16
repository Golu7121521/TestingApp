package com.universal.videoplayer.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** History never stores headers, cookies or DRM details - only the URL and metadata. */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val url: String,
    val title: String,
    val domain: String,
    val durationMs: Long,
    val positionMs: Long,
    val lastWatched: Long,
    val thumbPath: String?,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "playlist_items",
    foreignKeys = [ForeignKey(
        entity = PlaylistEntity::class,
        parentColumns = ["id"],
        childColumns = ["playlistId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("playlistId")],
)
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val url: String,
    val title: String,
    val domain: String,
    val durationMs: Long,
    val thumbPath: String?,
    val sortOrder: Int,
)

/** Non-sensitive playback preset. Cookies are intentionally NOT stored. */
@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val uaKey: String,
    val customUa: String,
    val referer: String,
    val origin: String,
    val drmScheme: String,
    val drmLicense: String,
)

data class MediaInfo(
    val url: String,
    val title: String,
    val domain: String,
    val durationMs: Long,
    val thumbPath: String?,
)
