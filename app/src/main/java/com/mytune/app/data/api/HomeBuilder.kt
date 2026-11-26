package com.mytune.app.data.api

import com.mytune.app.domain.HomeData
import com.mytune.app.domain.HomeSection
import com.mytune.app.domain.MusicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.IOException

/**
 * No backend has a "home" endpoint we can rely on, so the JioSaavn-style home is assembled from many
 * searches that run in parallel. Failed / empty rows are simply dropped.
 */
suspend fun MusicRepository.buildHome(): Result<HomeData> = try {
    coroutineScope {
        val repo = this@buildHome
        val songRows = ApiConfig.HOME_SONG_ROWS.map { (title, q) -> title to async { repo.searchSongs(q, 25) } }
        val playlistRows = ApiConfig.HOME_PLAYLIST_ROWS.map { (title, q) -> title to async { repo.searchPlaylists(q, 20) } }
        val albumRows = ApiConfig.HOME_ALBUM_ROWS.map { (title, q) -> title to async { repo.searchAlbums(q, 20) } }
        val podcastRows = ApiConfig.HOME_PODCAST_ROWS.map { (title, q) -> title to async { repo.searchPlaylists(q, 20) } }
        val moods = ApiConfig.HOME_MOODS.map { async { repo.searchPlaylists(it, 3) } }
        val artists = ApiConfig.HOME_ARTISTS.map { async { repo.searchArtists(it, 1) } }

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
        // Podcast rows are tagged so the home screen can show them under the "Podcasts" tab.
        podcastRows.forEach { (t, d) -> d.await().getOrNull()?.let { if (it.isNotEmpty()) sections += HomeSection(t, collections = it, podcast = true) } }

        if (sections.isEmpty()) throw IOException("Network error")
        Result.success(HomeData(sections))
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
