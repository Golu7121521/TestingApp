package com.vibemusic.app.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.vibemusic.app.data.preferences.PreferencesRepository
import com.vibemusic.app.data.scanner.MediaStoreScanner
import com.vibemusic.app.database.ExcludedItemEntity
import com.vibemusic.app.database.PlaylistEntity
import com.vibemusic.app.database.PlaylistSongCrossRef
import com.vibemusic.app.database.RecentSearchEntity
import com.vibemusic.app.database.SongStatsEntity
import com.vibemusic.app.database.VibeDao
import com.vibemusic.app.domain.model.Album
import com.vibemusic.app.domain.model.Artist
import com.vibemusic.app.domain.model.FolderItem
import com.vibemusic.app.domain.model.LyricLine
import com.vibemusic.app.domain.model.Playlist
import com.vibemusic.app.domain.model.Song
import com.vibemusic.app.domain.model.SortOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MusicRepository(
    private val context: Context,
    private val scanner: MediaStoreScanner,
    private val dao: VibeDao,
    private val prefsRepo: PreferencesRepository
) {
    private val _rawSongs = MutableStateFlow<List<Song>>(emptyList())
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    val songsFlow: Flow<List<Song>> = combine(
        _rawSongs,
        dao.observeAllSongStats(),
        prefsRepo.preferencesFlow
    ) { raw, statsList, prefs ->
        val statsMap = statsList.associateBy { it.songId }
        val enriched = raw.map { song ->
            val st = statsMap[song.id]
            song.copy(
                isFavorite = st?.isFavorite ?: false,
                playCount = st?.playCount ?: 0,
                lastPlayed = st?.lastPlayedTimestamp ?: 0L
            )
        }
        sortSongs(enriched, prefs.sortOption)
    }

    val albumsFlow: Flow<List<Album>> = songsFlow.map { songs ->
        songs.groupBy { it.albumId }
            .map { (albumId, albumSongs) ->
                val first = albumSongs.first()
                Album(
                    id = albumId,
                    name = first.album,
                    artist = first.artist,
                    albumArtUri = first.albumArtUri,
                    songCount = albumSongs.size,
                    songs = albumSongs.sortedBy { it.trackNumber }
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    val artistsFlow: Flow<List<Artist>> = songsFlow.map { songs ->
        songs.groupBy { it.artist }
            .map { (artistName, artistSongs) ->
                Artist(
                    name = artistName,
                    albumCount = artistSongs.map { it.albumId }.distinct().size,
                    songCount = artistSongs.size,
                    artworkUri = artistSongs.firstOrNull()?.albumArtUri,
                    songs = artistSongs
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    val foldersFlow: Flow<List<FolderItem>> = songsFlow.map { songs ->
        songs.groupBy { it.folderPath }
            .map { (path, folderSongs) ->
                FolderItem(
                    path = path,
                    name = folderSongs.firstOrNull()?.folderName ?: File(path).name,
                    songCount = folderSongs.size,
                    songs = folderSongs
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    val genresFlow: Flow<Map<String, List<Song>>> = songsFlow.map { songs ->
        songs.filter { it.genre.isNotBlank() }
            .groupBy { it.genre.trim() }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }

    val playlistsFlow: Flow<List<Playlist>> = combine(
        songsFlow,
        dao.observePlaylists(),
        dao.observeAllPlaylistSongs()
    ) { allSongs, customEntities, refs ->
        val songMap = allSongs.associateBy { it.id }

        val recentlyPlayed = allSongs.filter { it.lastPlayed > 0L }
            .sortedByDescending { it.lastPlayed }
            .take(30)
        val recentlyAdded = allSongs.sortedByDescending { it.dateAdded }.take(30)
        val mostPlayed = allSongs.filter { it.playCount > 0 }
            .sortedByDescending { it.playCount }
            .take(30)
        val favorites = allSongs.filter { it.isFavorite }

        val smartPlaylists = listOf(
            Playlist(
                id = -1L,
                name = "Favorites",
                isSmart = true,
                songCount = favorites.size,
                artworkUri = favorites.firstOrNull()?.albumArtUri,
                songs = favorites
            ),
            Playlist(
                id = -2L,
                name = "Recently Played",
                isSmart = true,
                songCount = recentlyPlayed.size,
                artworkUri = recentlyPlayed.firstOrNull()?.albumArtUri,
                songs = recentlyPlayed
            ),
            Playlist(
                id = -3L,
                name = "Most Played",
                isSmart = true,
                songCount = mostPlayed.size,
                artworkUri = mostPlayed.firstOrNull()?.albumArtUri,
                songs = mostPlayed
            ),
            Playlist(
                id = -4L,
                name = "Recently Added",
                isSmart = true,
                songCount = recentlyAdded.size,
                artworkUri = recentlyAdded.firstOrNull()?.albumArtUri,
                songs = recentlyAdded
            )
        )

        val refsByPlaylist = refs.groupBy { it.playlistId }
        val customPlaylists = customEntities.map { entity ->
            val pSongs = refsByPlaylist[entity.id].orEmpty()
                .sortedBy { it.position }
                .mapNotNull { songMap[it.songId] }
            Playlist(
                id = entity.id,
                name = entity.name,
                isSmart = false,
                songCount = pSongs.size,
                artworkUri = pSongs.firstOrNull()?.albumArtUri,
                songs = pSongs
            )
        }

        smartPlaylists + customPlaylists
    }

    val recentSearchesFlow: Flow<List<RecentSearchEntity>> = dao.observeRecentSearches()
    val excludedItemsFlow: Flow<List<ExcludedItemEntity>> = dao.observeExcludedItems()

    fun hasPermission(): Boolean = scanner.hasAudioPermission()

    suspend fun refreshLibrary() {
        _isScanning.value = true
        try {
            val excluded = dao.getExcludedItemsOnce().map { it.pathOrId }.toSet()
            val scanned = scanner.scanDeviceAudio(excluded)
            _rawSongs.value = scanned
        } finally {
            _isScanning.value = false
        }
    }

    fun sortSongs(songs: List<Song>, option: SortOption): List<Song> = when (option) {
        SortOption.TITLE_ASC -> songs.sortedBy { it.title.lowercase() }
        SortOption.DATE_ADDED_DESC -> songs.sortedByDescending { it.dateAdded }
        SortOption.DURATION_DESC -> songs.sortedByDescending { it.duration }
        SortOption.ARTIST_ASC -> songs.sortedBy { it.artist.lowercase() }
        SortOption.ALBUM_ASC -> songs.sortedBy { it.album.lowercase() }
    }

    suspend fun toggleFavorite(songId: Long) = dao.toggleFavorite(songId)
    suspend fun recordSongPlayed(songId: Long) = dao.recordSongPlayed(songId)

    suspend fun createPlaylist(name: String): Long {
        return dao.insertPlaylist(PlaylistEntity(name = name.trim()))
    }

    suspend fun renamePlaylist(id: Long, newName: String) {
        if (id > 0) dao.renamePlaylist(id, newName.trim())
    }

    suspend fun deletePlaylist(id: Long) {
        if (id > 0) {
            dao.clearPlaylistSongs(id)
            dao.deletePlaylist(id)
        }
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
        if (playlistId <= 0) return
        val existing = dao.getSongsForPlaylist(playlistId)
        if (existing.any { it.songId == songId }) return
        val nextPos = (existing.maxOfOrNull { it.position } ?: -1) + 1
        dao.insertPlaylistSong(PlaylistSongCrossRef(playlistId, songId, nextPos))
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        if (playlistId > 0) dao.removeSongFromPlaylist(playlistId, songId)
    }

    suspend fun reorderPlaylistSongs(playlistId: Long, orderedSongIds: List<Long>) {
        if (playlistId <= 0) return
        dao.clearPlaylistSongs(playlistId)
        val refs = orderedSongIds.mapIndexed { idx, sId ->
            PlaylistSongCrossRef(playlistId, sId, idx)
        }
        dao.insertPlaylistSongs(refs)
    }

    suspend fun saveSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) {
            dao.insertRecentSearch(RecentSearchEntity(query = trimmed))
        }
    }

    suspend fun deleteSearchQuery(query: String) = dao.deleteRecentSearch(query)
    suspend fun clearSearchHistory() = dao.clearRecentSearches()

    suspend fun excludePathOrSong(pathOrId: String, isFolder: Boolean, label: String) {
        dao.insertExcludedItem(ExcludedItemEntity(pathOrId, isFolder, label))
        refreshLibrary()
    }

    suspend fun removeExclusion(pathOrId: String) {
        dao.removeExcludedItem(pathOrId)
        refreshLibrary()
    }

    suspend fun loadLyricsForSong(song: Song): List<LyricLine> = withContext(Dispatchers.IO) {
        val audioFile = File(song.filePath)
        val lrcFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
        if (lrcFile.exists() && lrcFile.canRead()) {
            val parsed = parseLrcText(lrcFile.readText())
            if (parsed.isNotEmpty()) return@withContext parsed
        }
        val txtFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.txt")
        if (txtFile.exists() && txtFile.canRead()) {
            val lines = txtFile.readLines().filter { it.isNotBlank() }
                .map { LyricLine(0L, it.trim()) }
            if (lines.isNotEmpty()) return@withContext lines
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, song.uri)
            val embedded = runCatching {
                retriever.extractMetadata(1000)
            }.getOrNull()
            if (!embedded.isNullOrBlank()) {
                val parsed = parseLrcText(embedded)
                return@withContext parsed.ifEmpty {
                    embedded.lines().filter { it.isNotBlank() }.map { LyricLine(0L, it.trim()) }
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { retriever.release() }
        }

        emptyList()
    }

    private fun parseLrcText(raw: String): List<LyricLine> {
        val regex = Regex("""\[(\d{2}):(\d{2})(?:\.(\d{2,3}))?](.*)""")
        val result = mutableListOf<LyricLine>()
        for (line in raw.lines()) {
            val match = regex.find(line.trim())
            if (match != null) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val msPart = match.groupValues[3]
                val ms = when (msPart.length) {
                    2 -> (msPart.toLongOrNull() ?: 0L) * 10L
                    3 -> msPart.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val text = match.groupValues[4].trim()
                if (text.isNotEmpty()) {
                    result.add(LyricLine(min * 60_000L + sec * 1000L + ms, text))
                }
            }
        }
        return result.sortedBy { it.timestampMs }
    }

    suspend fun exportBackupJson(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val playlists = dao.getAllPlaylistsOnce()
            val stats = dao.observeAllSongStats().first()
            val root = JSONObject()
            val plArray = JSONArray()
            for (pl in playlists) {
                val obj = JSONObject()
                obj.put("name", pl.name)
                val songs = dao.getSongsForPlaylist(pl.id)
                val sArr = JSONArray()
                songs.forEach { sArr.put(it.songId) }
                obj.put("songIds", sArr)
                plArray.put(obj)
            }
            val favArray = JSONArray()
            stats.filter { it.isFavorite }.forEach { favArray.put(it.songId) }

            root.put("playlists", plArray)
            root.put("favorites", favArray)

            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(root.toString(2).toByteArray())
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importBackupJson(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonStr = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                ?: return@withContext false
            val root = JSONObject(jsonStr)
            val plArray = root.optJSONArray("playlists") ?: JSONArray()
            for (i in 0 until plArray.length()) {
                val obj = plArray.getJSONObject(i)
                val name = obj.optString("name", "Restored Playlist")
                val plId = dao.insertPlaylist(PlaylistEntity(name = name))
                val sArr = obj.optJSONArray("songIds") ?: JSONArray()
                val refs = (0 until sArr.length()).map { idx ->
                    PlaylistSongCrossRef(plId, sArr.getLong(idx), idx)
                }
                dao.insertPlaylistSongs(refs)
            }
            val favArray = root.optJSONArray("favorites") ?: JSONArray()
            for (i in 0 until favArray.length()) {
                val sId = favArray.getLong(i)
                val existing = dao.getSongStats(sId)
                dao.upsertSongStats(
                    existing?.copy(isFavorite = true) ?: SongStatsEntity(songId = sId, isFavorite = true)
                )
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
