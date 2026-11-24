package com.personal.saavntune.data.repository

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.personal.saavntune.data.api.SaavnApiService
import com.personal.saavntune.data.model.SaavnSongItem
import com.personal.saavntune.data.model.Song
import com.personal.saavntune.util.SaavnDecryptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MusicRepository(
    private val context: Context,
    private val apiService: SaavnApiService = SaavnApiService.create()
) {

    suspend fun searchSongs(query: String): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.searchSongs(query = query)
            val songs = response.results.mapNotNull { mapToSong(it) }
            Result.success(songs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resolvePlayableUrl(song: Song): String? = withContext(Dispatchers.IO) {
        if (song.isLocal && !song.localPath.isNullOrBlank()) {
            return@withContext Uri.fromFile(File(song.localPath)).toString()
        }
        if (song.streamUrl.isNotBlank()) {
            return@withContext song.streamUrl
        }
        return@withContext try {
            val details = apiService.getSongDetails(songIds = song.id)
            val encUrl = details.songs.firstOrNull()?.moreInfo?.encryptedMediaUrl
            SaavnDecryptor.decryptMediaUrl(encUrl, highQuality = true)
        } catch (e: Exception) {
            null
        }
    }

    private fun mapToSong(item: SaavnSongItem): Song? {
        val decryptedUrl = SaavnDecryptor.decryptMediaUrl(
            item.moreInfo?.encryptedMediaUrl,
            highQuality = true
        ) ?: ""

        val artists = item.moreInfo?.artistMap?.primaryArtists
            ?.map { SaavnDecryptor.cleanHtml(it.name) }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.joinToString(", ")
            ?.takeIf { it.isNotBlank() }
            ?: SaavnDecryptor.cleanHtml(item.subtitle)

        return Song(
            id = item.id,
            title = SaavnDecryptor.cleanHtml(item.title),
            artist = artists.ifBlank { "Unknown Artist" },
            album = SaavnDecryptor.cleanHtml(item.moreInfo?.album),
            artworkUrl = SaavnDecryptor.getHighResArtwork(item.image),
            streamUrl = decryptedUrl,
            durationText = SaavnDecryptor.formatDuration(item.moreInfo?.duration),
            isLocal = false
        )
    }

    suspend fun enqueueDownload(song: Song): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = resolvePlayableUrl(song)
                ?: return@withContext Result.failure(Exception("Could not get audio download link"))

            val safeTitle = SaavnDecryptor.sanitizeFileName(song.title)
            val safeArtist = SaavnDecryptor.sanitizeFileName(song.artist)
            val fileName = "$safeTitle - $safeArtist.m4a"

            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(song.title)
                .setDescription("Downloading by ${song.artist}")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_MUSIC,
                    "SaavnTune/$fileName"
                )

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Result.success("Downloading: $fileName")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDownloadedSongs(): List<Song> = withContext(Dispatchers.IO) {
        val musicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "SaavnTune"
        )
        if (!musicDir.exists() || !musicDir.isDirectory) {
            return@withContext emptyList()
        }

        val files = musicDir.listFiles { file ->
            file.isFile && (file.extension.equals("m4a", true) ||
                    file.extension.equals("mp4", true) ||
                    file.extension.equals("mp3", true))
        } ?: return@withContext emptyList()

        files.sortedByDescending { it.lastModified() }.map { file ->
            val baseName = file.nameWithoutExtension
            val parts = baseName.split(" - ", limit = 2)
            val title = parts.getOrNull(0)?.trim().orEmpty().ifBlank { baseName }
            val artist = parts.getOrNull(1)?.trim().orEmpty().ifBlank { "Downloaded Audio" }

            Song(
                id = file.absolutePath,
                title = title,
                artist = artist,
                album = "Offline Downloads",
                artworkUrl = "",
                streamUrl = Uri.fromFile(file).toString(),
                durationText = "Offline",
                isLocal = true,
                localPath = file.absolutePath
            )
        }
    }

    suspend fun deleteDownloadedSong(song: Song): Boolean = withContext(Dispatchers.IO) {
        val path = song.localPath ?: return@withContext false
        val file = File(path)
        if (file.exists()) file.delete() else false
    }
}
