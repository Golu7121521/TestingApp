package com.mytune.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSong(song: SongEntity)
    @Query("SELECT * FROM songs WHERE id = :id") suspend fun song(id: String): SongEntity?

    // Favorites
    @Query("SELECT s.* FROM songs s INNER JOIN favorites f ON s.id = f.songId ORDER BY f.addedAt DESC")
    fun favorites(): Flow<List<SongEntity>>
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :id)") fun isFavorite(id: String): Flow<Boolean>
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :id)") suspend fun isFavoriteNow(id: String): Boolean
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addFavorite(f: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE songId = :id") suspend fun removeFavorite(id: String)

    // Recently played (capped at 50)
    @Query("SELECT s.* FROM songs s INNER JOIN recents r ON s.id = r.songId ORDER BY r.playedAt DESC LIMIT 50")
    fun recents(): Flow<List<SongEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addRecent(r: RecentEntity)
    @Query("DELETE FROM recents WHERE songId NOT IN (SELECT songId FROM recents ORDER BY playedAt DESC LIMIT 50)")
    suspend fun trimRecents()

    // Playlists
    @Query("SELECT p.id AS id, p.name AS name, (SELECT COUNT(*) FROM playlist_songs ps WHERE ps.playlistId = p.id) AS songCount FROM playlists p ORDER BY p.id DESC")
    fun playlists(): Flow<List<PlaylistInfo>>
    @Insert suspend fun insertPlaylist(p: PlaylistEntity): Long
    @Query("UPDATE playlists SET name = :name WHERE id = :id") suspend fun renamePlaylist(id: Long, name: String)
    @Query("DELETE FROM playlists WHERE id = :id") suspend fun deletePlaylist(id: Long)
    @Query("DELETE FROM playlist_songs WHERE playlistId = :id") suspend fun clearPlaylist(id: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addToPlaylist(e: PlaylistSongEntity)
    @Query("DELETE FROM playlist_songs WHERE playlistId = :pid AND songId = :sid") suspend fun removeFromPlaylist(pid: Long, sid: String)
    @Query("SELECT s.* FROM songs s INNER JOIN playlist_songs ps ON s.id = ps.songId WHERE ps.playlistId = :pid ORDER BY ps.addedAt")
    fun playlistSongs(pid: Long): Flow<List<SongEntity>>

    // Search history
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addHistory(h: HistoryEntity)
    @Query("SELECT text FROM search_history ORDER BY ts DESC LIMIT 15") fun history(): Flow<List<String>>
    @Query("DELETE FROM search_history") suspend fun clearHistory()

    // Downloads
    @Transaction @Query("SELECT * FROM downloads ORDER BY createdAt DESC") fun downloads(): Flow<List<DownloadItem>>
    @Query("SELECT * FROM downloads WHERE songId = :id") fun downloadFlow(id: String): Flow<DownloadEntity?>
    @Query("SELECT * FROM downloads WHERE songId = :id") suspend fun download(id: String): DownloadEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertDownload(d: DownloadEntity)
    @Query("UPDATE downloads SET status = :status WHERE songId = :id") suspend fun setDownloadStatus(id: String, status: String)
    @Query("UPDATE downloads SET downloadedBytes = :done, totalBytes = :total, filePath = :path WHERE songId = :id")
    suspend fun setProgress(id: String, done: Long, total: Long, path: String)
    @Query("DELETE FROM downloads WHERE songId = :id") suspend fun deleteDownload(id: String)
    @Query("DELETE FROM downloads") suspend fun deleteAllDownloads()
}
