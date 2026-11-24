package com.business.musicapp

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import java.util.Locale
import java.util.concurrent.TimeUnit

object TrackRepository {

    /**
     * Default curated royalty-free business background tracks (streaming URLs).
     * Replace or add your own business audio stream URLs here anytime.
     */
    fun getBusinessTracks(): List<Track> = listOf(
        Track(
            id = "biz_1",
            title = "Executive Lounge",
            artist = "Studio Ambient",
            category = "Lobby & Reception",
            durationLabel = "02:13",
            sourceUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
        ),
        Track(
            id = "biz_2",
            title = "Modern Focus Flow",
            artist = "Workspace Beats",
            category = "Productivity",
            durationLabel = "02:45",
            sourceUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"
        ),
        Track(
            id = "biz_3",
            title = "Morning Espresso",
            artist = "Cafe Acoustic",
            category = "Retail & Cafe",
            durationLabel = "03:12",
            sourceUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3"
        ),
        Track(
            id = "biz_4",
            title = "Uplift & Inspire",
            artist = "Brand Pulse",
            category = "Showroom",
            durationLabel = "02:58",
            sourceUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3"
        ),
        Track(
            id = "biz_5",
            title = "Evening Chillout",
            artist = "Velvet Horizon",
            category = "Lounge",
            durationLabel = "03:30",
            sourceUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3"
        )
    )

    /**
     * Scans local audio files stored on the user's Android device.
     */
    fun getDeviceAudioTracks(context: Context): List<Track> {
        val localTracks = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

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
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Unknown Track"
                    val artist = cursor.getString(artistCol)?.takeIf { it != "<unknown>" } ?: "Local Audio"
                    val durationMs = cursor.getLong(durationCol)
                    if (durationMs < 5000) continue

                    val contentUri = ContentUris.withAppendedId(collection, id).toString()
                    localTracks.add(
                        Track(
                            id = "local_$id",
                            title = title,
                            artist = artist,
                            category = "Device Storage",
                            durationLabel = formatDuration(durationMs),
                            sourceUrl = contentUri,
                            isLocal = true
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Permission denied or storage unavailable
        }
        return localTracks
    }

    fun formatDuration(ms: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
