package com.mytune.app.data.api

import com.mytune.app.domain.HomeData
import com.mytune.app.domain.MusicRepository

/** Tries [primary]; if it fails or returns nothing useful, tries [secondary]. */
class FallbackRepository(private val primary: MusicRepository, private val secondary: MusicRepository) : MusicRepository {
    private suspend fun <T> either(useful: (T) -> Boolean = { true }, f: suspend (MusicRepository) -> Result<T>): Result<T> {
        val a = f(primary)
        if (a.isSuccess && useful(a.getOrThrow())) return a
        val b = f(secondary)
        return if (b.isSuccess) b else if (a.isSuccess) a else b
    }

    override suspend fun searchSongs(query: String, limit: Int) = either<List<com.mytune.app.domain.Song>>({ it.isNotEmpty() }) { it.searchSongs(query, limit) }
    override suspend fun searchAlbums(query: String, limit: Int) = either<List<com.mytune.app.domain.MediaCollection>>({ it.isNotEmpty() }) { it.searchAlbums(query, limit) }
    override suspend fun searchPlaylists(query: String, limit: Int) = either<List<com.mytune.app.domain.MediaCollection>>({ it.isNotEmpty() }) { it.searchPlaylists(query, limit) }
    override suspend fun searchArtists(query: String, limit: Int) = either<List<com.mytune.app.domain.MediaCollection>>({ it.isNotEmpty() }) { it.searchArtists(query, limit) }
    override suspend fun song(id: String) = either<com.mytune.app.domain.Song>({ it.streamUrls.isNotEmpty() }) { it.song(id) }
    override suspend fun album(id: String) = either<com.mytune.app.domain.MediaCollection>({ it.songs.isNotEmpty() }) { it.album(id) }
    override suspend fun playlist(id: String) = either<com.mytune.app.domain.MediaCollection>({ it.songs.isNotEmpty() }) { it.playlist(id) }
    override suspend fun artist(id: String, name: String) = either<com.mytune.app.domain.MediaCollection>({ it.songs.isNotEmpty() }) { it.artist(id, name) }
    override suspend fun suggestions(id: String, limit: Int) = either<List<com.mytune.app.domain.Song>>({ it.isNotEmpty() }) { it.suggestions(id, limit) }
    override suspend fun home(): Result<HomeData> = buildHome()
}
