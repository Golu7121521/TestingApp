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
     * Extracts direct playable MP4/HLS stream URL & related videos for a YouTube video URL.
     */
    suspend fun extractStreamDetails(videoUrl: String): PlayedVideoStream? = withContext(Dispatchers.IO) {
        try {
            val streamInfo = StreamInfo.getInfo(youtubeService, videoUrl)
            // Pick highest quality progressive video+audio stream (smooth hardware decoding on Redmi 9 Prime)
            val bestVideoStream = streamInfo.videoStreams
                .filter { it.format?.suffix == "mp4" }
                .maxByOrNull { it.resolution }
                ?: streamInfo.videoStreams.firstOrNull()

            val playableUrl = bestVideoStream?.content ?: streamInfo.hlsUrl ?: return@withContext null
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

            PlayedVideoStream(
                title = streamInfo.name ?: "YouTube Video",
                uploaderName = streamInfo.uploaderName ?: "Channel",
                viewsAndDate = "${formatViews(streamInfo.viewCount)} • ${streamInfo.textualUploadDate ?: ""}",
                streamUrl = playableUrl,
                thumbnailUrl = thumb,
                relatedItems = related
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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
