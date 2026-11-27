package com.mytune.app.data.api

import com.mytune.app.domain.CollectionType
import com.mytune.app.domain.MediaCollection
import com.mytune.app.domain.Song
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// Every field is nullable with a default so missing/odd data never crashes parsing.
@Serializable data class ApiResponse<T>(val success: Boolean? = null, val data: T? = null)
@Serializable data class SearchData<T>(val total: Int? = null, val results: List<T>? = null)
@Serializable data class ImageDto(val quality: String? = null, val url: String? = null)
@Serializable data class AlbumRef(val id: String? = null, val name: String? = null)

@Serializable
data class SongDto(
    val id: String? = null,
    val name: String? = null,
    val duration: JsonPrimitive? = null, // sometimes a number, sometimes a string
    val image: List<ImageDto>? = null,
    val downloadUrl: List<ImageDto>? = null,
    val artists: JsonElement? = null, // shape varies between API versions, parsed by hand
    val album: AlbumRef? = null,
    @SerialName("encrypted_media_url") val encryptedMediaUrl: String? = null,
)

@Serializable
data class CollectionDto(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val image: List<ImageDto>? = null,
    val artists: JsonElement? = null,
    val songs: List<SongDto>? = null,
    val topSongs: List<SongDto>? = null,
    val topAlbums: List<CollectionDto>? = null,
    val similarArtists: List<CollectionDto>? = null,
    val role: String? = null,
)

private fun String?.clean(): String = (this ?: "")
    .replace("&quot;", "\"").replace("&amp;", "&").replace("&#039;", "'")
    .replace("&lt;", "<").replace("&gt;", ">")

private fun String.https(): String = if (startsWith("http://")) "https://" + removePrefix("http://") else this

private fun List<ImageDto>?.best(): String =
    orEmpty().lastOrNull { !it.url.isNullOrBlank() }?.url.orEmpty().https()

private fun artistNames(el: JsonElement?): String {
    val arr = ((el as? JsonObject)?.get("primary") as? JsonArray)
        ?: ((el as? JsonObject)?.get("all") as? JsonArray)
        ?: (el as? JsonArray)
        ?: return ""
    return arr.mapNotNull { ((it as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull }
        .joinToString(", ") { it.clean() }
}

private fun primaryArtistId(el: JsonElement?): String {
    val arr = ((el as? JsonObject)?.get("primary") as? JsonArray)
        ?: ((el as? JsonObject)?.get("all") as? JsonArray)
        ?: (el as? JsonArray)
        ?: return ""
    return arr.firstNotNullOfOrNull { ((it as? JsonObject)?.get("id") as? JsonPrimitive)?.contentOrNull?.takeIf { v -> v.isNotBlank() } }.orEmpty()
}

internal fun SongDto.toSong(): Song? {
    val sid = id ?: return null
    val urls = mutableMapOf<Int, String>()
    downloadUrl.orEmpty().forEach { d ->
        val q = d.quality?.filter(Char::isDigit)?.toIntOrNull()
        val u = d.url
        if (q != null && !u.isNullOrBlank()) urls[q] = u.https()
    }
    if (urls.isEmpty() && !encryptedMediaUrl.isNullOrBlank()) {
        // Raw JioSaavn responses only carry an encrypted 96kbps URL; higher bitrates swap the suffix.
        SaavnCrypto.decryptUrl(encryptedMediaUrl)?.let { base ->
            listOf(96, 160, 320).forEach { q -> urls[q] = base.https().replace("_96.", "_$q.") }
        }
    }
    return Song(
        id = sid,
        title = name.clean(),
        artist = artistNames(artists),
        album = album?.name.clean(),
        artworkUrl = image.best(),
        durationSec = duration?.contentOrNull?.toLongOrNull() ?: 0L,
        streamUrls = urls,
        artistId = primaryArtistId(artists),
    )
}

internal fun CollectionDto.toCollection(type: String): MediaCollection? {
    val cid = id ?: return null
    val subtitle = when (type) {
        CollectionType.ARTIST -> role.clean()
        else -> artistNames(artists).ifBlank { description.clean() }
    }
    val tracks = (songs ?: topSongs).orEmpty().mapNotNull { it.toSong() }
    return MediaCollection(
        cid, type, name.clean(), subtitle, image.best(), tracks,
        albums = topAlbums.orEmpty().mapNotNull { it.toCollection(CollectionType.ALBUM) },
        similar = similarArtists.orEmpty().mapNotNull { it.toCollection(CollectionType.ARTIST) },
    )
}
