package com.mytune.app.domain

/** A playable track. [streamUrls] maps bitrate (kbps) -> URL; empty for tracks loaded from the local DB. */
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val artworkUrl: String = "",
    val durationSec: Long = 0,
    val streamUrls: Map<Int, String> = emptyMap(),
)

/** Album, playlist, artist or user playlist (see [CollectionType]). */
data class MediaCollection(
    val id: String,
    val type: String,
    val title: String,
    val subtitle: String = "",
    val artworkUrl: String = "",
    val songs: List<Song> = emptyList(),
)

data class HomeData(
    val trending: List<Song>,
    val newReleases: List<MediaCollection>,
    val playlists: List<MediaCollection>,
)

object CollectionType {
    const val ALBUM = "album"
    const val PLAYLIST = "playlist"
    const val ARTIST = "artist"
    const val USER = "user"
}

/** Picks the highest bitrate <= preferred (320 -> 160 -> 96 fallback); if none, the lowest available. */
fun Map<Int, String>.pickQuality(preferred: Int): Int? =
    keys.filter { it <= preferred }.maxOrNull() ?: keys.minOrNull()
