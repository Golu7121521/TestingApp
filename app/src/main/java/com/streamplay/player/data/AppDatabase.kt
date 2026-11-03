package com.streamplay.player.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "stream_history")
data class StreamHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val userAgent: String,
    val referer: String,
    val rawHeaders: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM stream_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<StreamHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: StreamHistoryEntity)

    @Delete
    suspend fun delete(item: StreamHistoryEntity)

    @Query("DELETE FROM stream_history")
    suspend fun clearAll()
}

@Database(entities = [StreamHistoryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "streamplay_history.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
