package com.mytune.app.data.api

import com.mytune.app.domain.CollectionType
import com.mytune.app.domain.HomeData
import com.mytune.app.domain.HomeSection
import com.mytune.app.domain.MediaCollection
import com.mytune.app.domain.MusicRepository
import com.mytune.app.domain.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.IOException

class JioSaavnApi(private val api: JioSaavnService) : MusicRepository {

    /** Wraps a call so network/parse errors become Result.failure instead of crashes. */
    private suspend fun <T> safe(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun searchSongs(query: String, limit: Int): Result<List<Song>> =
        safe { api.searchSongs(query, limit).data?.results.orEmpty().mapNotNull { it.toSong() } }

    override suspend fun searchAlbums(query: String, limit: Int): Result<List<MediaCollection>> =
        safe { api.searchAlbums(query, limit).data?.results.orEmpty().mapNotNull { it.toCollection(CollectionType.ALBUM) } }

    override suspend fun searchPlaylists(query: String, limit: Int): Result<List<MediaCollection>> =
        safe { api.searchPlaylists(query, limit).data?.results.orEmpty().mapNotNull { it.toCollection(CollectionType.PLAYLIST) } }

    override suspend fun searchArtists(query: String, limit: Int): Result<List<MediaCollection>> =
        safe { api.searchArtists(query, limit).data?.results.orEmpty().mapNotNull { it.toCollection(CollectionType.ARTIST) } }

    override suspend fun song(id: String): Result<Song> =
        safe { api.song(id).data?.firstOrNull()?.toSong() ?: throw IOException("Song not found") }

    override suspend fun album(id: String): Result<MediaCollection> =
        safe { api.album(id).data?.toCollection(CollectionType.ALBUM) ?: throw IOException("Album not found") }

    override suspend fun playlist(id: String): Result<MediaCollection> =
        safe { api.playlist(id).data?.toCollection(CollectionType.PLAYLIST) ?: throw IOException("Playlist not found") }

    override suspend fun artist(id: String, name: String): Result<MediaCollection> = safe {
        val col = api.artist(id).data?.toCollection(CollectionType.ARTIST) ?: throw IOException("Artist not found")
        if (col.songs.isNotEmpty() || name.isBlank()) col
        else col.copy(songs = api.searchSongs(name, 30).data?.results.orEmpty().mapNotNull { it.toSong() })
    }

    override suspend fun suggestions(id: String, limit: Int): Result<List<Song>> =
        safe { api.suggestions(id, limit).data.orEmpty().mapNotNull { it.toSong() } }

    override suspend fun home(): Result<HomeData> = buildHome()
}
