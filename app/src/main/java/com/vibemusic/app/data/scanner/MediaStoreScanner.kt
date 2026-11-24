package com.vibemusic.app.data.scanner

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.vibemusic.app.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaStoreScanner(private val context: Context) {

    companion object {
        val SUPPORTED_EXTENSIONS = setOf(
            "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma"
        )
    }

    fun hasAudioPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun scanDeviceAudio(
        excludedPathsOrIds: Set<String> = emptySet()
    ): List<Song> = withContext(Dispatchers.IO) {
        if (!hasAudioPermission()) return@withContext emptyList()

        val songs = mutableListOf<Song>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.DATA)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.SIZE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.Audio.Media.BITRATE)
                add(MediaStore.Audio.Media.GENRE)
            }
        }.toTypedArray()

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        val albumArtBaseUri = Uri.parse("content://media/external/audio/albumart")

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val bitrateCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.BITRATE)
                } else -1
                val genreCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val filePath = cursor.getString(dataCol) ?: continue
                    val file = File(filePath)
                    val ext = file.extension.lowercase()
                    if (ext.isNotEmpty() && ext !in SUPPORTED_EXTENSIONS) continue

                    val duration = cursor.getLong(durationCol)
                    if (duration < 1000L) continue

                    val folderPath = file.parent ?: "/Internal Storage"
                    if (excludedPathsOrIds.contains(id.toString()) ||
                        excludedPathsOrIds.any { folderPath.startsWith(it) }
                    ) {
                        continue
                    }

                    val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() }
                        ?: file.nameWithoutExtension
                    val rawArtist = cursor.getString(artistCol)
                    val artist = if (rawArtist.isNullOrBlank() || rawArtist == "<unknown>") {
                        "Unknown Artist"
                    } else rawArtist
                    val rawAlbum = cursor.getString(albumCol)
                    val album = if (rawAlbum.isNullOrBlank() || rawAlbum == "<unknown>") {
                        "Unknown Album"
                    } else rawAlbum

                    val albumId = cursor.getLong(albumIdCol)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    val artUri = ContentUris.withAppendedId(albumArtBaseUri, albumId)

                    val trackNum = cursor.getInt(trackCol) % 1000
                    val dateAdded = cursor.getLong(dateCol)
                    val mimeType = cursor.getString(mimeCol) ?: "audio/*"
                    val size = cursor.getLong(sizeCol)
                    val bitrate = if (bitrateCol >= 0) cursor.getInt(bitrateCol) else 0
                    val genre = if (genreCol >= 0) cursor.getString(genreCol).orEmpty() else ""

                    songs.add(
                        Song(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            albumId = albumId,
                            duration = duration,
                            uri = contentUri,
                            albumArtUri = artUri,
                            trackNumber = trackNum,
                            dateAdded = dateAdded,
                            folderPath = folderPath,
                            folderName = file.parentFile?.name ?: "Music",
                            filePath = filePath,
                            mimeType = mimeType,
                            size = size,
                            bitrate = bitrate,
                            genre = genre
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        songs
    }
}
