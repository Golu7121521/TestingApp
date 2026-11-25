package com.mytune.app.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

/** Metadata cache for every song the user touches; other tables reference it by id. */
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String,
    val durationSec: Long,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(@PrimaryKey val songId: String, val addedAt: Long)

@Entity(tableName = "recents")
data class RecentEntity(@PrimaryKey val songId: String, val playedAt: Long)

@Entity(tableName = "playlists")
data class PlaylistEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songId"])
data class PlaylistSongEntity(val playlistId: Long, val songId: String, val addedAt: Long)

@Entity(tableName = "search_history")
data class HistoryEntity(@PrimaryKey val text: String, val ts: Long)

object DownloadStatus {
    const val QUEUED = "QUEUED"
    const val RUNNING = "RUNNING"
    const val PAUSED = "PAUSED"
    const val FAILED = "FAILED"
    const val DONE = "DONE"
}

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val songId: String,
    val status: String,
    val quality: Int,
    val filePath: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val createdAt: Long,
)

data class DownloadItem(
    @Embedded val download: DownloadEntity,
    @Relation(parentColumn = "songId", entityColumn = "id") val song: SongEntity?,
)

data class PlaylistInfo(val id: Long, val name: String, val songCount: Int)
