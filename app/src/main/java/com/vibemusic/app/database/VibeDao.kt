package com.vibemusic.app.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface VibeDao {

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists")
    suspend fun getAllPlaylistsOnce(): List<PlaylistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :newName WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, newName: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylistSongs(playlistId: Long)

    @Query("SELECT * FROM playlist_songs ORDER BY position ASC")
    fun observeAllPlaylistSongs(): Flow<List<PlaylistSongCrossRef>>

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getSongsForPlaylist(playlistId: Long): List<PlaylistSongCrossRef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSong(ref: PlaylistSongCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSongs(refs: List<PlaylistSongCrossRef>)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)

    @Query("SELECT * FROM song_stats")
    fun observeAllSongStats(): Flow<List<SongStatsEntity>>

    @Query("SELECT * FROM song_stats WHERE songId = :songId")
    suspend fun getSongStats(songId: Long): SongStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSongStats(stats: SongStatsEntity)

    @Transaction
    suspend fun toggleFavorite(songId: Long) {
        val current = getSongStats(songId)
        if (current == null) {
            upsertSongStats(SongStatsEntity(songId = songId, isFavorite = true))
        } else {
            upsertSongStats(current.copy(isFavorite = !current.isFavorite))
        }
    }

    @Transaction
    suspend fun recordSongPlayed(songId: Long) {
        val current = getSongStats(songId)
        val now = System.currentTimeMillis()
        if (current == null) {
            upsertSongStats(SongStatsEntity(songId = songId, playCount = 1, lastPlayedTimestamp = now))
        } else {
            upsertSongStats(current.copy(playCount = current.playCount + 1, lastPlayedTimestamp = now))
        }
    }

    @Query("SELECT * FROM recent_searches ORDER BY timestamp DESC LIMIT 12")
    fun observeRecentSearches(): Flow<List<RecentSearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentSearch(entity: RecentSearchEntity)

    @Query("DELETE FROM recent_searches WHERE query = :query")
    suspend fun deleteRecentSearch(query: String)

    @Query("DELETE FROM recent_searches")
    suspend fun clearRecentSearches()

    @Query("SELECT * FROM excluded_items")
    fun observeExcludedItems(): Flow<List<ExcludedItemEntity>>

    @Query("SELECT * FROM excluded_items")
    suspend fun getExcludedItemsOnce(): List<ExcludedItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExcludedItem(item: ExcludedItemEntity)

    @Query("DELETE FROM excluded_items WHERE pathOrId = :pathOrId")
    suspend fun removeExcludedItem(pathOrId: String)
}
