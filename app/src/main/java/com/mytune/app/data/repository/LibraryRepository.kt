package com.mytune.app.data.repository

import com.mytune.app.data.db.FavoriteEntity
import com.mytune.app.data.db.HistoryEntity
import com.mytune.app.data.db.LibraryDao
import com.mytune.app.data.db.PlaylistEntity
import com.mytune.app.data.db.PlaylistSongEntity
import com.mytune.app.data.db.RecentEntity
import com.mytune.app.data.db.SongEntity
import com.mytune.app.domain.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

fun Song.toEntity() = SongEntity(id, title, artist, album, artworkUrl, durationSec)
fun SongEntity.toSong() = Song(id, title, artist, album, artworkUrl, durationSec)

/** Favorites, recents, playlists and search history (all local, Room-backed). */
class LibraryRepository(private val dao: LibraryDao) {
    val favorites: Flow<List<Song>> = dao.favorites().map { l -> l.map { it.toSong() } }
    val recents: Flow<List<Song>> = dao.recents().map { l -> l.map { it.toSong() } }
    val playlists = dao.playlists()
    val history = dao.history()

    fun isFavorite(id: String): Flow<Boolean> = dao.isFavorite(id)
    fun playlistSongs(id: Long): Flow<List<Song>> = dao.playlistSongs(id).map { l -> l.map { it.toSong() } }

    suspend fun toggleFavorite(song: Song) {
        if (dao.isFavoriteNow(song.id)) {
            dao.removeFavorite(song.id)
        } else {
            dao.upsertSong(song.toEntity())
            dao.addFavorite(FavoriteEntity(song.id, System.currentTimeMillis()))
        }
    }

    suspend fun addRecent(song: Song) {
        dao.upsertSong(song.toEntity())
        dao.addRecent(RecentEntity(song.id, System.currentTimeMillis()))
        dao.trimRecents() // keep only the last 50
    }

    suspend fun createPlaylist(name: String): Long = dao.insertPlaylist(PlaylistEntity(name = name.trim()))
    suspend fun renamePlaylist(id: Long, name: String) = dao.renamePlaylist(id, name.trim())
    suspend fun deletePlaylist(id: Long) {
        dao.clearPlaylist(id)
        dao.deletePlaylist(id)
    }

    suspend fun addToPlaylist(playlistId: Long, song: Song) {
        dao.upsertSong(song.toEntity())
        dao.addToPlaylist(PlaylistSongEntity(playlistId, song.id, System.currentTimeMillis()))
    }

    suspend fun removeFromPlaylist(playlistId: Long, songId: String) = dao.removeFromPlaylist(playlistId, songId)

    suspend fun addHistory(text: String) = dao.addHistory(HistoryEntity(text.trim(), System.currentTimeMillis()))
    suspend fun clearHistory() = dao.clearHistory()
}
