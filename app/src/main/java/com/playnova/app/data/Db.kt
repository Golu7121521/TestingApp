package com.playnova.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val videoId: Long,
    val uri: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastPlayed: Long,
    val speed: Float = 1f,
    val inHistory: Boolean = true
)

@Entity(tableName = "favorites")
data class FavoriteEntity(@PrimaryKey val videoId: Long, val addedAt: Long)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long
)

@Entity(tableName = "playlist_items")
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val videoId: Long,
    val position: Int
)

@Entity(tableName = "searches")
data class SearchEntity(@PrimaryKey val text: String, val time: Long)

@Dao
abstract class AppDao {
    // history / progress
    @Query("SELECT * FROM history WHERE inHistory = 1 ORDER BY lastPlayed DESC")
    abstract fun history(): Flow<List<HistoryEntity>>
    @Query("SELECT * FROM history")
    abstract fun allProgress(): Flow<List<HistoryEntity>>
    @Query("SELECT * FROM history WHERE videoId = :id")
    abstract suspend fun progress(id: Long): HistoryEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertHistory(h: HistoryEntity)
    @Query("UPDATE history SET inHistory = 0 WHERE videoId = :id")
    abstract suspend fun hideFromHistory(id: Long)
    @Query("UPDATE history SET inHistory = 0")
    abstract suspend fun clearHistory()
    @Query("UPDATE history SET positionMs = 0")
    abstract suspend fun resetPositions()

    // favorites
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    abstract fun favorites(): Flow<List<FavoriteEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun addFavorite(f: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE videoId = :id")
    abstract suspend fun removeFavorite(id: Long)

    // playlists
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    abstract fun playlists(): Flow<List<PlaylistEntity>>
    @Query("SELECT * FROM playlist_items ORDER BY position ASC")
    abstract fun allPlaylistItems(): Flow<List<PlaylistItemEntity>>
    @Insert
    abstract suspend fun insertPlaylist(p: PlaylistEntity): Long
    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    abstract suspend fun renamePlaylist(id: Long, name: String)
    @Query("DELETE FROM playlists WHERE id = :id")
    abstract suspend fun deletePlaylist(id: Long)
    @Query("DELETE FROM playlist_items WHERE playlistId = :id")
    abstract suspend fun deletePlaylistItems(id: Long)
    @Insert
    abstract suspend fun insertItem(i: PlaylistItemEntity)
    @Query("DELETE FROM playlist_items WHERE id = :id")
    abstract suspend fun deleteItem(id: Long)
    @Query("SELECT * FROM playlist_items WHERE playlistId = :pid ORDER BY position ASC")
    abstract suspend fun itemsOf(pid: Long): List<PlaylistItemEntity>
    @Query("UPDATE playlist_items SET position = :pos WHERE id = :id")
    abstract suspend fun setItemPosition(id: Long, pos: Int)

    // searches
    @Query("SELECT * FROM searches ORDER BY time DESC LIMIT 12")
    abstract fun searches(): Flow<List<SearchEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun addSearch(s: SearchEntity)
    @Query("DELETE FROM searches")
    abstract suspend fun clearSearches()

    @Transaction
    open suspend fun addToPlaylist(pid: Long, videoId: Long) {
        val items = itemsOf(pid)
        if (items.any { it.videoId == videoId }) return
        insertItem(PlaylistItemEntity(playlistId = pid, videoId = videoId, position = items.size))
    }

    @Transaction
    open suspend fun swapItems(pid: Long, from: Int, to: Int) {
        val items = itemsOf(pid).toMutableList()
        if (from !in items.indices || to !in items.indices) return
        val moved = items.removeAt(from)
        items.add(to, moved)
        items.forEachIndexed { i, it -> setItemPosition(it.id, i) }
    }

    @Transaction
    open suspend fun removePlaylistWithItems(pid: Long) {
        deletePlaylistItems(pid)
        deletePlaylist(pid)
    }
}

@Database(
    entities = [HistoryEntity::class, FavoriteEntity::class, PlaylistEntity::class,
        PlaylistItemEntity::class, SearchEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "playnova.db")
                .fallbackToDestructiveMigration()
                .build().also { inst = it }
        }
    }
}
