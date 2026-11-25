package com.mytune.app.domain

/** Abstraction over any music backend. Swap the implementation in AppContainer. */
interface MusicRepository {
    suspend fun searchSongs(query: String): Result<List<Song>>
    suspend fun searchAlbums(query: String): Result<List<MediaCollection>>
    suspend fun searchPlaylists(query: String): Result<List<MediaCollection>>
    suspend fun searchArtists(query: String): Result<List<MediaCollection>>
    suspend fun song(id: String): Result<Song>
    suspend fun album(id: String): Result<MediaCollection>
    suspend fun playlist(id: String): Result<MediaCollection>
    suspend fun artist(id: String): Result<MediaCollection>
    suspend fun home(): Result<HomeData>
}
