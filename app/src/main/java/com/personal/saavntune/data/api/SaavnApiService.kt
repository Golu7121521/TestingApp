package com.personal.saavntune.data.api

import com.personal.saavntune.data.model.SaavnSearchResponse
import com.personal.saavntune.data.model.SaavnSongDetailsResponse
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface SaavnApiService {

    @GET("api.php")
    suspend fun searchSongs(
        @Query("__call") call: String = "search.getResults",
        @Query("_format") format: String = "json",
        @Query("_marker") marker: Int = 0,
        @Query("ctx") ctx: String = "web6dot0",
        @Query("api_version") apiVersion: Int = 4,
        @Query("q") query: String,
        @Query("n") limit: Int = 25,
        @Query("p") page: Int = 1
    ): SaavnSearchResponse

    @GET("api.php")
    suspend fun getSongDetails(
        @Query("__call") call: String = "song.getDetails",
        @Query("_format") format: String = "json",
        @Query("_marker") marker: Int = 0,
        @Query("ctx") ctx: String = "web6dot0",
        @Query("api_version") apiVersion: Int = 4,
        @Query("pids") songIds: String
    ): SaavnSongDetailsResponse

    companion object {
        private const val BASE_URL = "https://www.jiosaavn.com/"

        fun create(): SaavnApiService {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                        )
                        .build()
                    chain.proceed(request)
                }
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SaavnApiService::class.java)
        }
    }
}
