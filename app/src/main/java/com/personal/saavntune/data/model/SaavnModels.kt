package com.personal.saavntune.data.model

import com.google.gson.annotations.SerializedName

data class SaavnSearchResponse(
    @SerializedName("total") val total: Int = 0,
    @SerializedName("start") val start: Int = 1,
    @SerializedName("results") val results: List<SaavnSongItem> = emptyList()
)

data class SaavnSongDetailsResponse(
    @SerializedName("songs") val songs: List<SaavnSongItem> = emptyList()
)

data class SaavnSongItem(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("subtitle") val subtitle: String = "",
    @SerializedName("image") val image: String = "",
    @SerializedName("more_info") val moreInfo: SaavnMoreInfo? = null
)

data class SaavnMoreInfo(
    @SerializedName("album") val album: String? = null,
    @SerializedName("duration") val duration: String? = null,
    @SerializedName("encrypted_media_url") val encryptedMediaUrl: String? = null,
    @SerializedName("artistMap") val artistMap: SaavnArtistMap? = null
)

data class SaavnArtistMap(
    @SerializedName("primary_artists") val primaryArtists: List<SaavnArtist>? = null
)

data class SaavnArtist(
    @SerializedName("name") val name: String = ""
)

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String,
    val streamUrl: String,
    val durationText: String,
    val isLocal: Boolean = false,
    val localPath: String? = null
)
