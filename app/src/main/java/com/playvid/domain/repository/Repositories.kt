package com.playvid.domain.repository

import com.playvid.domain.model.AppSettings
import com.playvid.domain.model.HistoryItem
import com.playvid.domain.model.Playlist
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun observeAll(): Flow<List<HistoryItem>>
    suspend fun get(source: String): HistoryItem?
    suspend fun saveProgress(source: String, title: String, isLocal: Boolean, positionMs: Long, durationMs: Long)
    suspend fun delete(id: Long)
    suspend fun clear()
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

interface PlaylistRepository {
    fun observePlaylists(): Flow<List<Playlist>>
    suspend fun get(id: Long): Playlist?
    suspend fun add(name: String, source: String, isFile: Boolean): Long
    suspend fun update(playlist: Playlist)
    suspend fun delete(id: Long)

    /** URLs of channels the user starred (across all playlists). */
    fun observeFavorites(): Flow<Set<String>>
    suspend fun setFavorite(url: String, favorite: Boolean)
}
