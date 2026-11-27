package com.mytune.app.domain

/** Abstraction over any music backend. Swap the implementation in AppContainer. */
interface MusicRepository {
    suspend fun searchSongs(query: String, limit: Int = 30): Result<List<Song>>
    suspend fun searchAlbums(query: String, limit: Int = 30): Result<List<MediaCollection>>
    suspend fun searchPlaylists(query: String, limit: Int = 30): Result<List<MediaCollection>>
    suspend fun searchArtists(query: String, limit: Int = 30): Result<List<MediaCollection>>
    suspend fun song(id: String): Result<Song>
    suspend fun album(id: String): Result<MediaCollection>
    suspend fun playlist(id: String): Result<MediaCollection>
    /** [name] lets a backend fall back to a song search when the artist page itself has no songs. */
    suspend fun artist(id: String, name: String = ""): Result<MediaCollection>
    /** Songs similar to [id]; used for "related songs" and endless autoplay. */
    suspend fun suggestions(id: String, limit: Int = 20): Result<List<Song>>
    suspend fun home(): Result<HomeData>
}
