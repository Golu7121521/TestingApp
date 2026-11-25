package com.mytune.app.data.api

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Retrofit definition for the unofficial JioSaavn API (saavn.dev-style routes). */
interface JioSaavnService {
    @GET("api/search/songs")
    suspend fun searchSongs(@Query("query") query: String, @Query("limit") limit: Int = 30): ApiResponse<SearchData<SongDto>>

    @GET("api/search/albums")
    suspend fun searchAlbums(@Query("query") query: String, @Query("limit") limit: Int = 30): ApiResponse<SearchData<CollectionDto>>

    @GET("api/search/playlists")
    suspend fun searchPlaylists(@Query("query") query: String, @Query("limit") limit: Int = 30): ApiResponse<SearchData<CollectionDto>>

    @GET("api/search/artists")
    suspend fun searchArtists(@Query("query") query: String, @Query("limit") limit: Int = 30): ApiResponse<SearchData<CollectionDto>>

    @GET("api/songs/{id}")
    suspend fun song(@Path("id") id: String): ApiResponse<List<SongDto>>

    @GET("api/albums")
    suspend fun album(@Query("id") id: String): ApiResponse<CollectionDto>

    @GET("api/playlists")
    suspend fun playlist(@Query("id") id: String, @Query("limit") limit: Int = 100): ApiResponse<CollectionDto>

    @GET("api/artists")
    suspend fun artist(@Query("id") id: String): ApiResponse<CollectionDto>
}
