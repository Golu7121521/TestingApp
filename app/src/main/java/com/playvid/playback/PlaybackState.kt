package com.playvid.playback

/** One selectable track: a video quality, an audio language or a subtitle. */
data class TrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val selected: Boolean,
    val height: Int = 0,
    val bitrate: Int = 0
)

/**
 * One quality a video can be downloaded in.
 * [audioUrl] null means [videoUrl] is already a complete file (direct MP4 link, or a YouTube
 * video with no separate audio-only stream) — nothing to merge, just save it as-is.
 */
data class DownloadOption(val label: String, val videoUrl: String, val audioUrl: String? = null)

/** One audio-only quality a YouTube video can be downloaded in (no video track). */
data class AudioDownloadOption(val label: String, val url: String)

/** Rarely-changing player state. Position lives in [PlayerProgress] so ticking doesn't rebuild the screen. */
data class PlayerUiState(
    val title: String = "",
    val isLocal: Boolean = false,
    val isPlaying: Boolean = false,
    /** True until playback is ready for the first time. */
    val isLoading: Boolean = true,
    /** True when rebuffering after playback already started. */
    val isBuffering: Boolean = false,
    /** Short hint shown under "Can't play this video". null = no error. */
    val error: String? = null,
    val speed: Float = 1f,
    val isMuted: Boolean = false,
    /** false = Fit (default, never crops), true = Fill (crops to fill the screen). */
    val resizeFill: Boolean = false,
    /** Saved position offered in the "Continue from ...?" prompt. */
    val resumePrompt: Long? = null,
    val qualities: List<TrackOption> = emptyList(),
    val isAutoQuality: Boolean = true,
    val audioTracks: List<TrackOption> = emptyList(),
    val textTracks: List<TrackOption> = emptyList(),
    val textEnabled: Boolean = false,
    /** Empty when this source can't be downloaded (local file already on-device, or a live/adaptive stream). */
    val downloadOptions: List<DownloadOption> = emptyList(),
    val audioDownloadOptions: List<AudioDownloadOption> = emptyList()
)

/** Updated twice a second; only the seek bar and mini player observe it. */
data class PlayerProgress(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isLive: Boolean = false,
    /** How far behind the live edge, in ms. 0 while at the edge or not live. */
    val liveOffsetMs: Long = 0L
)
