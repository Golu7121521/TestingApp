package com.vibemusic.app.domain.model

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val uri: Uri,
    val albumArtUri: Uri,
    val trackNumber: Int,
    val dateAdded: Long,
    val folderPath: String,
    val folderName: String,
    val filePath: String,
    val mimeType: String,
    val size: Long,
    val bitrate: Int = 0,
    val genre: String = "",
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayed: Long = 0L
)

data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val albumArtUri: Uri,
    val songCount: Int,
    val songs: List<Song>
)

data class Artist(
    val name: String,
    val albumCount: Int,
    val songCount: Int,
    val artworkUri: Uri?,
    val songs: List<Song>
)

data class FolderItem(
    val path: String,
    val name: String,
    val songCount: Int,
    val songs: List<Song>
)

data class Playlist(
    val id: Long,
    val name: String,
    val isSmart: Boolean = false,
    val songCount: Int,
    val artworkUri: Uri? = null,
    val songs: List<Song> = emptyList()
)

enum class SortOption(val label: String) {
    TITLE_ASC("Title (A-Z)"),
    DATE_ADDED_DESC("Date Added"),
    DURATION_DESC("Duration"),
    ARTIST_ASC("Artist"),
    ALBUM_ASC("Album")
}

enum class RepeatModeOption(val label: String) {
    OFF("Repeat Off"),
    ALL("Repeat All"),
    ONE("Repeat One")
}

enum class ThemeModeOption(val label: String) {
    AMOLED_DARK("AMOLED Dark"),
    LIGHT("Light Mode"),
    SYSTEM("System Default")
}

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

data class AppPreferences(
    val themeMode: ThemeModeOption = ThemeModeOption.AMOLED_DARK,
    val accentColorHex: Long = 0xFFA855F7,
    val gaplessPlayback: Boolean = true,
    val resumePlayback: Boolean = true,
    val autoPlay: Boolean = false,
    val crossfadeSeconds: Int = 0,
    val defaultRepeatMode: RepeatModeOption = RepeatModeOption.OFF,
    val albumArtAnimation: Boolean = true,
    val keepScreenOn: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val sortOption: SortOption = SortOption.TITLE_ASC,
    val eqEnabled: Boolean = false,
    val eqPresetIndex: Int = 0,
    val eqBandLevels: List<Int> = emptyList(),
    val bassBoostStrength: Int = 0,
    val virtualizerStrength: Int = 0,
    val lastPlayedSongId: Long = -1L,
    val lastPlayedPositionMs: Long = 0L
)
