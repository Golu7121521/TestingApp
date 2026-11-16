package com.universal.videoplayer.data

import android.content.Context
import androidx.room.Room
import com.universal.videoplayer.utils.domainOf

class Repository(context: Context) {
    private val db = Room.databaseBuilder(context, AppDatabase::class.java, "uvp.db").build()
    val settings = SettingsStore(context)

    val history = db.history().observe()
    val playlists = db.playlists().observePlaylists()
    val playlistItems = db.playlists().observeItems()
    val presets = db.presets().observe()

    suspend fun getHistory(url: String) = db.history().get(url)

    suspend fun recordProgress(
        url: String, title: String, durationMs: Long, positionMs: Long, thumb: String?,
    ) {
        val old = db.history().get(url)
        db.history().upsert(
            HistoryEntity(
                url = url,
                title = title,
                domain = domainOf(url),
                durationMs = if (durationMs > 0) durationMs else old?.durationMs ?: 0,
                positionMs = positionMs,
                lastWatched = System.currentTimeMillis(),
                thumbPath = thumb ?: old?.thumbPath,
            )
        )
    }

    suspend fun removeHistory(url: String) = db.history().delete(url)
    suspend fun clearHistory() = db.history().clear()

    suspend fun createPlaylist(name: String): Long = db.playlists().insertPlaylist(PlaylistEntity(name = name))
    suspend fun renamePlaylist(id: Long, name: String) = db.playlists().rename(id, name)
    suspend fun deletePlaylist(id: Long) = db.playlists().deletePlaylist(id)

    suspend fun addToPlaylist(playlistId: Long, m: MediaInfo) {
        val next = db.playlists().maxOrder(playlistId) + 1
        db.playlists().insertItem(
            PlaylistItemEntity(
                playlistId = playlistId, url = m.url, title = m.title, domain = m.domain,
                durationMs = m.durationMs, thumbPath = m.thumbPath, sortOrder = next,
            )
        )
    }

    suspend fun removeItem(id: Long) = db.playlists().deleteItem(id)

    suspend fun swapOrder(a: PlaylistItemEntity, b: PlaylistItemEntity) {
        db.playlists().setOrder(a.id, b.sortOrder)
        db.playlists().setOrder(b.id, a.sortOrder)
    }

    suspend fun savePreset(p: PresetEntity) = db.presets().insert(p)
    suspend fun deletePreset(id: Long) = db.presets().delete(id)
}
