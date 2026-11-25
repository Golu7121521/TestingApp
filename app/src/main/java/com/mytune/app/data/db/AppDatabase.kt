package com.mytune.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SongEntity::class, FavoriteEntity::class, RecentEntity::class, PlaylistEntity::class,
        PlaylistSongEntity::class, HistoryEntity::class, DownloadEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): LibraryDao
}
