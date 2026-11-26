package com.mytune.app.data.api

import com.mytune.app.domain.CollectionType
import com.mytune.app.domain.HomeData
import com.mytune.app.domain.MediaCollection
import com.mytune.app.domain.MusicRepository
import com.mytune.app.domain.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Talks to JioSaavn's own web API (www.jiosaavn.com/api.php) directly, so the app keeps working when the
 * free third-party wrapper APIs are down or blocked. Responses are converted into the same DTOs the
 * wrapper API uses, so all mapping (stream URLs, artwork, ...) is shared.
 */
class JioSaavnDirect(private val http: OkHttpClient) : MusicRepository {
    private val parser = Json { ignoreUnknownKeys = true; isLenient = true }

    private suspend fun call(call: String, vararg params: Pair<String, String>): JsonElement = withContext(Dispatchers.IO) {
        val url = "https://www.jiosaavn.com/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("__call", call)
            .addQueryParameter("_format", "json")
            .addQueryParameter("_marker", "0")
            .addQueryParameter("api_version", "4")
            .addQueryParameter("ctx", "web6dot0")
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .header("Accept", "application/json")
            .header("Cookie", "L=hindi; gdpr_acceptance=true; DL=english")
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            parser.parseToJsonElement(r.body?.string().orEmpty())
        }
    }

    private suspend fun <T> safe(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun JsonElement?.obj() = this as? JsonObject
    private fun JsonElement?.arr() = this as? JsonArray
    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun img(o: JsonObject): List<ImageDto> =
        listOfNotNull(o.str("image")?.replace("150x150", "500x500")?.replace("50x50", "500x500")).map { ImageDto(url = it) }

    private fun songDto(o: JsonObject): SongDto? {
        val id = o.str("id") ?: return null
        val more = o["more_info"].obj() ?: JsonObject(emptyMap())
        val map = more["artistMap"].obj()
        var primary = map?.get("primary_artists").arr()?.takeIf { it.isNotEmpty() } ?: map?.get("artists").arr()
        if (primary.isNullOrEmpty()) {
            val names = more.str("music") ?: o.str("subtitle")?.substringBefore(" - ")
            primary = JsonArray(listOfNotNull(names).map { JsonObject(mapOf("name" to JsonPrimitive(it))) })
        }
        return SongDto(
            id = id,
            name = o.str("title") ?: o.str("song"),
            duration = JsonPrimitive(more.str("duration") ?: o.str("duration")),
            image = img(o),
            artists = JsonObject(mapOf("primary" to (primary ?: JsonArray(emptyList())))),
            album = AlbumRef(more.str("album_id"), more.str("album")),
            encryptedMediaUrl = more.str("encrypted_media_url") ?: o.str("encrypted_media_url"),
        )
    }

    private fun songs(el: JsonElement?): List<Song> = el.arr().orEmpty().mapNotNull { it.obj()?.let(::songDto)?.toSong() }

    private fun collection(o: JsonObject, type: String): MediaCollection? {
        val sub = o.str("subtitle") ?: o.str("description")
        val tracks = songs(o["list"] ?: o["topSongs"] ?: o["songs"])
        val dto = CollectionDto(
            id = o.str("id") ?: o.str("artistId"),
            name = o.str("title") ?: o.str("name"),
            description = sub,
            image = img(o),
            role = sub,
        )
        return dto.toCollection(type)?.copy(songs = tracks)
    }

    private fun results(root: JsonElement, type: String): List<MediaCollection> =
        (root.obj()?.get("results") ?: root).arr().orEmpty().mapNotNull { it.obj()?.let { o -> collection(o, type) } }

    override suspend fun searchSongs(query: String, limit: Int) = safe {
        val root = call("search.getResults", "q" to query, "p" to "1", "n" to limit.toString())
        songs(root.obj()?.get("results") ?: root)
    }

    override suspend fun searchAlbums(query: String, limit: Int) = safe {
        results(call("search.getAlbumResults", "q" to query, "p" to "1", "n" to limit.toString()), CollectionType.ALBUM)
    }

    override suspend fun searchPlaylists(query: String, limit: Int) = safe {
        results(call("search.getPlaylistResults", "q" to query, "p" to "1", "n" to limit.toString()), CollectionType.PLAYLIST)
    }

    override suspend fun searchArtists(query: String, limit: Int) = safe {
        results(call("search.getArtistResults", "q" to query, "p" to "1", "n" to limit.toString()), CollectionType.ARTIST)
    }

    override suspend fun song(id: String) = safe {
        val root = call("song.getDetails", "pids" to id)
        val o = root.obj()
        val first = o?.get("songs").arr()?.firstOrNull()?.obj() ?: o?.get(id).obj() ?: o
        first?.let(::songDto)?.toSong() ?: throw IOException("Song not found")
    }

    override suspend fun album(id: String) = safe {
        call("content.getAlbumDetails", "albumid" to id).obj()?.let { collection(it, CollectionType.ALBUM) } ?: throw IOException("Album not found")
    }

    override suspend fun playlist(id: String) = safe {
        call("playlist.getDetails", "listid" to id, "n" to "100", "p" to "1").obj()?.let { collection(it, CollectionType.PLAYLIST) } ?: throw IOException("Playlist not found")
    }

    override suspend fun artist(id: String) = safe {
        call("artist.getArtistPageDetails", "artistId" to id, "n_song" to "30", "n_album" to "10").obj()?.let { collection(it, CollectionType.ARTIST) } ?: throw IOException("Artist not found")
    }

    override suspend fun suggestions(id: String, limit: Int) = safe {
        val root = call("reco.getreco", "pid" to id)
        songs(root.obj()?.get("data") ?: root.obj()?.get("songs") ?: root).take(limit)
    }

    override suspend fun home(): Result<HomeData> = buildHome()
}
