package com.newtube.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale

object NewPipeRepository {

    private val youtubeService = ServiceList.YouTube

    /** Search / trending feed */
    suspend fun fetchVideos(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        try {
            val extractor = youtubeService.getSearchExtractor(query)
            extractor.fetchPage()
            extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .map { it.toVideoItem() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /** Subscribed channels ki latest videos (channel naam se search, phir channel filter) */
    suspend fun fetchChannelFeed(subs: List<Subscription>): List<VideoItem> = coroutineScope {
        subs.map { sub ->
            async(Dispatchers.IO) {
                fetchVideos(sub.name)
                    .filter { v ->
                        (sub.url.isNotBlank() && v.uploaderUrl == sub.url) || v.uploaderName == sub.name
                    }
                    .take(4)
            }
        }.awaitAll().flatten().distinctBy { it.videoUrl }
    }

    /**
     * Video ke playable options.
     * dashUrl: ExoPlayer network ke hisaab se quality khud badalta hai (buffering kam)
     * qualities: manual quality ke liye list
     */
    suspend fun extractStreamDetails(videoUrl: String): StreamExtractionResult = withContext(Dispatchers.IO) {
        try {
            val info = StreamInfo.getInfo(youtubeService, videoUrl)

            val qualities = buildQualities(info)
            val dash = info.dashMpdUrl?.takeIf { it.isNotBlank() }
            val hls = info.hlsUrl?.takeIf { it.isNotBlank() }

            if (qualities.isEmpty() && dash == null && hls == null) {
                return@withContext StreamExtractionResult.Error("No playable stream found for this video")
            }

            val subtitles = info.subtitles.mapNotNull { s ->
                val url = s.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val lang = s.languageTag ?: "en"
                val mime = if (s.format?.suffix == "ttml") "application/ttml+xml" else "text/vtt"
                SubtitleOption(label = lang, url = url, mimeType = mime, languageTag = lang)
            }

            val related = info.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .take(20)
                .map { it.toVideoItem() }

            StreamExtractionResult.Success(
                PlayedVideoStream(
                    title = info.name ?: "YouTube Video",
                    uploaderName = info.uploaderName ?: "Channel",
                    uploaderUrl = info.uploaderUrl ?: "",
                    uploaderAvatarUrl = info.uploaderAvatars.lastOrNull()?.url ?: "",
                    viewsAndDate = "${formatViews(info.viewCount)} • ${info.textualUploadDate ?: ""}",
                    dashUrl = dash,
                    hlsUrl = hls,
                    qualities = qualities,
                    subtitles = subtitles,
                    thumbnailUrl = info.thumbnails.lastOrNull()?.url ?: "",
                    relatedItems = related
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            StreamExtractionResult.Error(e.localizedMessage ?: e.javaClass.simpleName)
        }
    }

    /**
     * Progressive (single connection) ko priority, jisse buffering kam hoti hai.
     * Adaptive video-only + audio sirf wahan jahan progressive nahi mila.
     */
    private fun buildQualities(info: StreamInfo): List<QualityOption> {
        val progressive = info.videoStreams.mapNotNull { s ->
            val url = s.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (!isMp4(s.format?.suffix)) return@mapNotNull null
            val h = heightOf(s.resolution)
            if (h <= 0) null else QualityOption("${h}p", h, url, null, true)
        }

        val audio = info.audioStreams
            .filter { isAudioMp4(it.format?.suffix) && !it.content.isNullOrBlank() }
            .maxByOrNull { it.averageBitrate }
            ?.content

        val adaptive = if (audio.isNullOrBlank()) {
            emptyList()
        } else {
            info.videoOnlyStreams.mapNotNull { s ->
                val url = s.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                if (!isMp4(s.format?.suffix)) return@mapNotNull null
                val h = heightOf(s.resolution)
                if (h <= 0) null else QualityOption("${h}p", h, url, audio, false)
            }
        }

        val byHeight = linkedMapOf<Int, QualityOption>()
        progressive.forEach { byHeight.putIfAbsent(it.height, it) }
        adaptive.forEach { byHeight.putIfAbsent(it.height, it) }
        return byHeight.values.sortedByDescending { it.height }
    }

    private fun StreamInfoItem.toVideoItem(): VideoItem = VideoItem(
        title = name ?: "Untitled Video",
        uploaderName = uploaderName ?: "YouTube Channel",
        viewCountText = formatViews(viewCount) + " • " + (textualUploadDate ?: "Recently"),
        durationText = formatDuration(duration),
        thumbnailUrl = thumbnails.lastOrNull()?.url ?: "",
        videoUrl = url,
        isShort = isShortFormContent || (duration in 1..60),
        uploaderUrl = uploaderUrl ?: ""
    )

    private fun heightOf(res: String?): Int =
        res?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0

    private fun isMp4(suffix: String?): Boolean = suffix.equals("mp4", ignoreCase = true)

    private fun isAudioMp4(suffix: String?): Boolean =
        suffix.equals("m4a", ignoreCase = true) || suffix.equals("mp4", ignoreCase = true)

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
