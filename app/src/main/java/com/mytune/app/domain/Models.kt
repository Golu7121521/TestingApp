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
    /** Id of the primary artist when the API provides it (opens the artist page directly). */
    val artistId: String = "",
)

/** Album, playlist, artist or user playlist (see [CollectionType]). */
data class MediaCollection(
    val id: String,
    val type: String,
    val title: String,
    val subtitle: String = "",
    val artworkUrl: String = "",
    val songs: List<Song> = emptyList(),
    /** Artist pages: top albums and similar artists when the API returns them. */
    val albums: List<MediaCollection> = emptyList(),
    val similar: List<MediaCollection> = emptyList(),
)

/** One horizontal row on the home screen: either songs or collections (albums / playlists / artists / podcasts). */
data class HomeSection(
    val title: String,
    val songs: List<Song> = emptyList(),
    val collections: List<MediaCollection> = emptyList(),
    val circle: Boolean = false,
    val podcast: Boolean = false,
    val style: String = SectionStyle.DEFAULT,
    val subtitle: String = "",
)

/** How the home screen draws a section. */
object SectionStyle {
    const val DEFAULT = "default"
    const val HERO = "hero"       // "Just For You": big cards
    const val RANKED = "ranked"   // trending: numbered cards
    const val TILES = "tiles"     // playlists / charts: gradient tiles
}

data class HomeData(val sections: List<HomeSection>)

object CollectionType {
    const val ALBUM = "album"
    const val PLAYLIST = "playlist"
    const val ARTIST = "artist"
    const val USER = "user"
}

/** Picks the highest bitrate <= preferred (320 -> 160 -> 96 fallback); if none, the lowest available. */
fun Map<Int, String>.pickQuality(preferred: Int): Int? =
    keys.filter { it <= preferred }.maxOrNull() ?: keys.minOrNull()
