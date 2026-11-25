package com.mytune.app.data.api

import com.mytune.app.domain.CollectionType
import com.mytune.app.domain.HomeData
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

    override suspend fun searchSongs(query: String): Result<List<Song>> =
        safe { api.searchSongs(query).data?.results.orEmpty().mapNotNull { it.toSong() } }

    override suspend fun searchAlbums(query: String): Result<List<MediaCollection>> =
        safe { api.searchAlbums(query).data?.results.orEmpty().mapNotNull { it.toCollection(CollectionType.ALBUM) } }

    override suspend fun searchPlaylists(query: String): Result<List<MediaCollection>> =
        safe { api.searchPlaylists(query).data?.results.orEmpty().mapNotNull { it.toCollection(CollectionType.PLAYLIST) } }

    override suspend fun searchArtists(query: String): Result<List<MediaCollection>> =
        safe { api.searchArtists(query).data?.results.orEmpty().mapNotNull { it.toCollection(CollectionType.ARTIST) } }

    override suspend fun song(id: String): Result<Song> =
        safe { api.song(id).data?.firstOrNull()?.toSong() ?: throw IOException("Song not found") }

    override suspend fun album(id: String): Result<MediaCollection> =
        safe { api.album(id).data?.toCollection(CollectionType.ALBUM) ?: throw IOException("Album not found") }

    override suspend fun playlist(id: String): Result<MediaCollection> =
        safe { api.playlist(id).data?.toCollection(CollectionType.PLAYLIST) ?: throw IOException("Playlist not found") }

    override suspend fun artist(id: String): Result<MediaCollection> =
        safe { api.artist(id).data?.toCollection(CollectionType.ARTIST) ?: throw IOException("Artist not found") }

    override suspend fun home(): Result<HomeData> = safe {
        coroutineScope {
            val t = async { searchSongs(ApiConfig.HOME_TRENDING_QUERY) }
            val n = async { searchAlbums(ApiConfig.HOME_NEW_QUERY) }
            val p = async { searchPlaylists(ApiConfig.HOME_PLAYLIST_QUERY) }
            val tr = t.await()
            val nr = n.await()
            val pr = p.await()
            // Only fail when every row failed; otherwise show what we have.
            if (tr.isFailure && nr.isFailure && pr.isFailure) {
                throw tr.exceptionOrNull() ?: IOException("Network error")
            }
            HomeData(tr.getOrDefault(emptyList()), nr.getOrDefault(emptyList()), pr.getOrDefault(emptyList()))
        }
    }
}
