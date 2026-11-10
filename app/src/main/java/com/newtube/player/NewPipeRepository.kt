package com.newtube.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale

object NewPipeRepository {

    private val youtubeService = ServiceList.YouTube

    /**
     * Fetches Trending or Search videos using NewPipeExtractor.
     */
    suspend fun fetchVideos(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        try {
            val extractor = youtubeService.getSearchExtractor(query)
            extractor.fetchPage()
            val items = extractor.initialPage.items.filterIsInstance<StreamInfoItem>()
            items.map { item ->
                val thumb = item.thumbnails.lastOrNull()?.url ?: ""
                VideoItem(
                    title = item.name ?: "Untitled Video",
                    uploaderName = item.uploaderName ?: "YouTube Channel",
                    viewCountText = formatViews(item.viewCount) + " • " + (item.textualUploadDate ?: "Recently"),
                    durationText = formatDuration(item.duration),
                    thumbnailUrl = thumb,
                    videoUrl = item.url,
                    isShort = item.isShortFormContent || (item.duration in 1..60)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Extracts playable stream(s) with complete fallback chain:
     * 1. Progressive MP4 (Video+Audio combined)
     * 2. Adaptive Video-Only MP4 (360p/480p/720p) + Audio-Only M4A/WebM stream
     * 3. HLS M3U8 stream
     * 4. DASH Manifest
     */
    suspend fun extractStreamDetails(videoUrl: String): StreamExtractionResult = withContext(Dispatchers.IO) {
        try {
            val streamInfo = StreamInfo.getInfo(youtubeService, videoUrl)

            // 1. Check progressive streams (combined video + audio)
            val progressiveMp4 = streamInfo.videoStreams
                .filter { !it.content.isNullOrEmpty() && (it.format?.suffix?.equals("mp4", true) == true) }
                .maxByOrNull { parseResolutionScore(it.resolution) }
                ?: streamInfo.videoStreams.firstOrNull { !it.content.isNullOrEmpty() }

            var videoUrlToPlay: String? = progressiveMp4?.content
            var audioUrlToMerge: String? = null
            var isHlsStream = false
            var isDashStream = false

            // 2. If progressive stream is empty (very common on YouTube now), use Adaptive VideoOnly + Audio stream
            if (videoUrlToPlay.isNullOrEmpty()) {
                val adaptiveVideos = streamInfo.videoOnlyStreams.filter { !it.content.isNullOrEmpty() }
                // Prefer MP4 (avc1) 720p or 480p or 360p for smooth hardware decode on Redmi 9 Prime
                val preferredVideo = adaptiveVideos
                    .filter { it.format?.suffix?.equals("mp4", true) == true }
                    .sortedBy { parseResolutionScore(it.resolution) }
                    .lastOrNull { parseResolutionScore(it.resolution) <= 720 }
                    ?: adaptiveVideos.firstOrNull { it.format?.suffix?.equals("mp4", true) == true }
                    ?: adaptiveVideos.firstOrNull()

                val bestAudio = streamInfo.audioStreams
                    .filter { !it.content.isNullOrEmpty() }
                    .maxByOrNull { it.averageBitrate }

                if (preferredVideo != null && bestAudio != null) {
                    videoUrlToPlay = preferredVideo.content
                    audioUrlToMerge = bestAudio.content
                }
            }

            // 3. Fallback to HLS URL (used by iOS Innertube client & Livestreams)
            if (videoUrlToPlay.isNullOrEmpty() && !streamInfo.hlsUrl.isNullOrEmpty()) {
                videoUrlToPlay = streamInfo.hlsUrl
                isHlsStream = true
            }

            // 4. Fallback to DASH MPD URL
            if (videoUrlToPlay.isNullOrEmpty() && !streamInfo.dashMpdUrl.isNullOrEmpty()) {
                videoUrlToPlay = streamInfo.dashMpdUrl
                isDashStream = true
            }

            // 5. Fallback to Audio-only if no video stream found at all
            if (videoUrlToPlay.isNullOrEmpty()) {
                val audioOnly = streamInfo.audioStreams.firstOrNull { !it.content.isNullOrEmpty() }
                if (audioOnly != null) {
                    videoUrlToPlay = audioOnly.content
                }
            }

            if (videoUrlToPlay.isNullOrEmpty()) {
                return@withContext StreamExtractionResult.Error("No playable stream found for this video")
            }

            val thumb = streamInfo.thumbnails.lastOrNull()?.url ?: ""

            val related = streamInfo.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .take(15)
                .map { item ->
                    VideoItem(
                        title = item.name ?: "Video",
                        uploaderName = item.uploaderName ?: "Channel",
                        viewCountText = formatViews(item.viewCount),
                        durationText = formatDuration(item.duration),
                        thumbnailUrl = item.thumbnails.lastOrNull()?.url ?: "",
                        videoUrl = item.url
                    )
                }

            StreamExtractionResult.Success(
                PlayedVideoStream(
                    title = streamInfo.name ?: "YouTube Video",
                    uploaderName = streamInfo.uploaderName ?: "Channel",
                    viewsAndDate = "${formatViews(streamInfo.viewCount)} • ${streamInfo.textualUploadDate ?: ""}",
                    videoStreamUrl = videoUrlToPlay,
                    audioStreamUrl = audioUrlToMerge,
                    isHls = isHlsStream,
                    isDashManifest = isDashStream,
                    thumbnailUrl = thumb,
                    relatedItems = related
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            StreamExtractionResult.Error(e.localizedMessage ?: e.javaClass.simpleName)
        }
    }

    private fun parseResolutionScore(res: String?): Int {
        if (res == null) return 0
        val digits = res.filter { it.isDigit() }
        return digits.toIntOrNull() ?: 0
    }

    private fun formatViews(views: Long): String {
        if (views <= 0) return "Trending"
        return when {
            views >= 1_000_000_000 -> String.format(Locale.US, "%.1fB views", views / 1_000_000_000.0)
            views >= 1_000_000 -> String.format(Locale.US, "%.1fM views", views / 1_000_000.0)
            views >= 1_000 -> String.format(Locale.US, "%.1fK views", views / 1_000.0)
            else -> "$views views"
        }
    }

    private fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "LIVE"
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hrs > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }
}
