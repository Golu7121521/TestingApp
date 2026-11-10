package com.playvid.data.local

import com.playvid.domain.model.Playlist
import com.playvid.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PlaylistRepositoryImpl(private val dao: PlaylistDao) : PlaylistRepository {

    override fun observePlaylists(): Flow<List<Playlist>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun get(id: Long): Playlist? = dao.get(id)?.toDomain()

    override suspend fun add(name: String, source: String, isFile: Boolean): Long =
        dao.insert(PlaylistEntity(name = name, source = source, isFile = isFile, addedAt = System.currentTimeMillis()))

    override suspend fun update(playlist: Playlist) {
        val existing = dao.get(playlist.id) ?: return
        dao.update(existing.copy(name = playlist.name, source = playlist.source, isFile = playlist.isFile))
    }

    override suspend fun delete(id: Long) = dao.delete(id)

    override fun observeFavorites(): Flow<Set<String>> = dao.observeFavoriteUrls().map { it.toSet() }

    override suspend fun setFavorite(url: String, favorite: Boolean) {
        if (favorite) dao.addFavorite(FavoriteChannelEntity(url)) else dao.removeFavorite(url)
    }

    private fun PlaylistEntity.toDomain() = Playlist(id, name, source, isFile)
}
