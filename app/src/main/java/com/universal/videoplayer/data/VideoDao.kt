package com.universal.videoplayer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY lastWatched DESC")
    fun observeHistory(): Flow<List<VideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(video: VideoEntity)

    @Query("DELETE FROM videos WHERE url = :url")
    suspend fun delete(url: String)

    @Query("DELETE FROM videos")
    suspend fun clear()

    @Query("SELECT * FROM playlists ORDER BY name")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Insert
    suspend fun createPlaylist(item: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addPlaylistItem(item: PlaylistItemEntity)

    @Query("SELECT * FROM playlist_items WHERE playlistId = :id ORDER BY position")
    fun observePlaylistItems(id: Long): Flow<List<PlaylistItemEntity>>

    @Query("DELETE FROM playlist_items WHERE playlistId = :id AND videoUrl = :url")
    suspend fun removePlaylistItem(id: Long, url: String)
}
