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

    override suspend fun artist(id: String): Result<MediaCollection> =
        safe { api.artist(id).data?.toCollection(CollectionType.ARTIST) ?: throw IOException("Artist not found") }

    override suspend fun suggestions(id: String, limit: Int): Result<List<Song>> =
        safe { api.suggestions(id, limit).data.orEmpty().mapNotNull { it.toSong() } }

    /**
     * The unofficial API has no "home" endpoint, so the JioSaavn-style home is assembled from many
     * searches that run in parallel. Failed / empty rows are simply dropped.
     */
    override suspend fun home(): Result<HomeData> = safe {
        coroutineScope {
            val songRows = ApiConfig.HOME_SONG_ROWS.map { (title, q) -> title to async { searchSongs(q, 25) } }
            val playlistRows = ApiConfig.HOME_PLAYLIST_ROWS.map { (title, q) -> title to async { searchPlaylists(q, 20) } }
            val albumRows = ApiConfig.HOME_ALBUM_ROWS.map { (title, q) -> title to async { searchAlbums(q, 20) } }
            val podcastRows = ApiConfig.HOME_PODCAST_ROWS.map { (title, q) -> title to async { searchPlaylists(q, 20) } }
            val moods = ApiConfig.HOME_MOODS.map { async { searchPlaylists(it, 3) } }
            val artists = ApiConfig.HOME_ARTISTS.map { async { searchArtists(it, 1) } }

            val sections = mutableListOf<HomeSection>()
            songRows.take(1).forEach { (t, d) -> d.await().getOrNull()?.let { if (it.isNotEmpty()) sections += HomeSection(t, songs = it) } }
            playlistRows.take(1).forEach { (t, d) -> d.await().getOrNull()?.let { if (it.isNotEmpty()) sections += HomeSection(t, collections = it) } }
            songRows.drop(1).take(1).forEach { (t, d) -> d.await().getOrNull()?.let { if (it.isNotEmpty()) sections += HomeSection(t, songs = it) } }
            val moodList = moods.flatMap { it.await().getOrNull().orEmpty() }.distinctBy { it.id }
            if (moodList.isNotEmpty()) sections += HomeSection("Made for Your Moods", collections = moodList)
            albumRows.forEach { (t, d) -> d.await().getOrNull()?.let { if (it.isNotEmpty()) sections += HomeSection(t, collections = it) } }
            val artistList = artists.mapNotNull { it.await().getOrNull()?.firstOrNull() }
            if (artistList.isNotEmpty()) sections += HomeSection("Popular Artists", collections = artistList, circle = true)
            songRows.drop(2).forEach { (t, d) -> d.await().getOrNull()?.let { if (it.isNotEmpty()) sections += HomeSection(t, songs = it) } }
            playlistRows.drop(1).forEach { (t, d) -> d.await().getOrNull()?.let { if (it.isNotEmpty()) sections += HomeSection(t, collections = it) } }
            podcastRows.forEach { (t, d) -> d.await().getOrNull()?.let { if (it.isNotEmpty()) sections += HomeSection(t, collections = it) } }

            if (sections.isEmpty()) throw IOException("Network error")
            HomeData(sections)
        }
    }
}
