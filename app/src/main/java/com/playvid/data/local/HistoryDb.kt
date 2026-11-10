package com.playvid.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** Unique index on [source]: playing the same video again replaces its old row. */
@Entity(tableName = "history", indices = [Index(value = ["source"], unique = true)])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val source: String,
    val isLocal: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val lastPlayedAt: Long
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY lastPlayedAt DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE source = :source LIMIT 1")
    suspend fun getBySource(source: String): HistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HistoryEntity)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM history")
    suspend fun clear()
}

/** A saved playlist (online link or local file copy) shown in the Playlist tab. */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val source: String,
    val isFile: Boolean,
    val addedAt: Long
)

/** A channel the user starred; keyed by stream URL so it survives playlist refreshes. */
@Entity(tableName = "favorite_channels")
data class FavoriteChannelEntity(@PrimaryKey val url: String)

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY addedAt ASC, id ASC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun get(id: Long): PlaylistEntity?

    @Insert
    suspend fun insert(entity: PlaylistEntity): Long

    @Update
    suspend fun update(entity: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT url FROM favorite_channels")
    fun observeFavoriteUrls(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(entity: FavoriteChannelEntity)

    @Query("DELETE FROM favorite_channels WHERE url = :url")
    suspend fun removeFavorite(url: String)
}

@Database(
    entities = [HistoryEntity::class, PlaylistEntity::class, FavoriteChannelEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun playlistDao(): PlaylistDao
}

/** v2 had a flat "playlist" table of entries; v3 replaced it with named playlists + favorites; v4 drops the
 *  per-playlist user-agent picker (every request now just uses the app's own default headers). History is kept. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `playlist`")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `source` TEXT NOT NULL, `isFile` INTEGER NOT NULL, " +
                "`userAgent` TEXT NOT NULL, `addedAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS `favorite_channels` (`url` TEXT NOT NULL, PRIMARY KEY(`url`))")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlists_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `source` TEXT NOT NULL, `isFile` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO `playlists_new` (id, name, source, isFile, addedAt) " +
                "SELECT id, name, source, isFile, addedAt FROM `playlists`"
        )
        db.execSQL("DROP TABLE `playlists`")
        db.execSQL("ALTER TABLE `playlists_new` RENAME TO `playlists`")
    }
}
