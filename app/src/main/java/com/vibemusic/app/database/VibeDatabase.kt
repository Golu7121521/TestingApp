package com.vibemusic.app.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        SongStatsEntity::class,
        RecentSearchEntity::class,
        ExcludedItemEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class VibeDatabase : RoomDatabase() {
    abstract fun vibeDao(): VibeDao

    companion object {
        @Volatile
        private var INSTANCE: VibeDatabase? = null

        fun getInstance(context: Context): VibeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VibeDatabase::class.java,
                    "vibe_music_db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
