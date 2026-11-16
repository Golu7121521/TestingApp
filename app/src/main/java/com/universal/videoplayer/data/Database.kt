package com.universal.videoplayer.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY lastWatched DESC")
    fun observe(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE url = :url")
    suspend fun get(url: String): HistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: HistoryEntity)

    @Query("DELETE FROM history WHERE url = :url")
    suspend fun delete(url: String)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY id")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist_items ORDER BY playlistId, sortOrder")
    fun observeItems(): Flow<List<PlaylistItemEntity>>

    @Insert
    suspend fun insertPlaylist(p: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Insert
    suspend fun insertItem(i: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM playlist_items WHERE playlistId = :pid")
    suspend fun maxOrder(pid: Long): Int

    @Query("UPDATE playlist_items SET sortOrder = :order WHERE id = :id")
    suspend fun setOrder(id: Long, order: Int)
}

@Dao
interface PresetDao {
    @Query("SELECT * FROM presets ORDER BY id DESC")
    fun observe(): Flow<List<PresetEntity>>

    @Insert
    suspend fun insert(p: PresetEntity)

    @Query("DELETE FROM presets WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(
    entities = [HistoryEntity::class, PlaylistEntity::class, PlaylistItemEntity::class, PresetEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun history(): HistoryDao
    abstract fun playlists(): PlaylistDao
    abstract fun presets(): PresetDao
}
