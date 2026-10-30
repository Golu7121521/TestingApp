package com.example.youtubeclone.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NPRequest
import org.schabi.newpipe.extractor.downloader.Response as NPResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.TimeUnit

class OkHttpNewPipeDownloader : Downloader() {
    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun execute(request: NPRequest): NPResponse {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBody = dataToSend?.toRequestBody(null, 0, dataToSend.size)
        val builder = Request.Builder()
            .url(url)
            .method(httpMethod, if (httpMethod == "POST" || httpMethod == "PUT") requestBody else null)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; rv:109.0) Gecko/20100101 Firefox/119.0"
            )

        for ((headerName, headerValueList) in headers) {
            if (headerValueList.size > 1) {
                builder.removeHeader(headerName)
                for (headerValue in headerValueList) {
                    builder.addHeader(headerName, headerValue)
                }
            } else if (headerValueList.size == 1) {
                builder.header(headerName, headerValueList[0])
            }
        }

        val response = client.newCall(builder.build()).execute()
        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", url)
        }

        val responseBodyString = response.body?.string() ?: ""
        val latestUrl = response.request.url.toString()
        return NPResponse(
            response.code,
            response.message,
            response.headers.toMultimap(),
            responseBodyString,
            latestUrl
        )
    }
}

data class RealVideoDetails(
    val url: String,
    val title: String,
    val uploaderName: String,
    val uploaderAvatar: String,
    val viewCountText: String,
    val uploadDate: String,
    val durationSec: Long,
    val thumbnailUrl: String,
    val streamUrl: String?,
    val description: String,
    val likeCountText: String,
    val subscriberText: String,
    val relatedStreams: List<RealVideoItem>
)

data class RealVideoItem(
    val url: String,
    val title: String,
    val uploaderName: String,
    val thumbnailUrl: String,
    val viewsText: String,
    val uploadedText: String,
    val durationText: String
)

object NewPipeRepository {
    private var isInitialized = false

    fun initIfNeeded() {
        if (!isInitialized) {
            NewPipe.init(
                OkHttpNewPipeDownloader(),
                Localization("en", "IN"),
                ContentCountry("IN")
            )
            isInitialized = true
        }
    }

    suspend fun fetchTrendingOrQuery(query: String): Result<List<RealVideoItem>> = withContext(Dispatchers.IO) {
        runCatching {
            initIfNeeded()
            val ytService = ServiceList.YouTube
            val items: List<StreamInfoItem> = if (query.isBlank() || query.equals("All", ignoreCase = true) || query.equals("Trending", ignoreCase = true)) {
                val kioskList = ytService.kioskList
                val extractor = kioskList.getDefaultKioskExtractor()
                extractor.fetchPage()
                extractor.initialPage.items.filterIsInstance<StreamInfoItem>()
            } else {
                val searchExtractor = ytService.getSearchExtractor(query)
                searchExtractor.fetchPage()
                searchExtractor.initialPage.items.filterIsInstance<StreamInfoItem>()
            }

            items.map { item ->
                val thumb = item.thumbnails.maxByOrNull { it.height }?.url ?: ""
                RealVideoItem(
                    url = item.url,
                    title = item.name ?: "Untitled Video",
                    uploaderName = item.uploaderName ?: "YouTube Creator",
                    thumbnailUrl = thumb,
                    viewsText = formatViews(item.viewCount),
                    uploadedText = item.textualUploadDate ?: "Recently",
                    durationText = formatDuration(item.duration)
                )
            }
        }
    }

    suspend fun fetchStreamDetails(videoUrl: String): Result<RealVideoDetails> = withContext(Dispatchers.IO) {
        runCatching {
            initIfNeeded()
            val info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)

            // Pick best progressive MP4 stream with both video+audio, or fallback to HLS
            val progressiveStream = info.videoStreams
                .filter { !it.isVideoOnly }
                .maxByOrNull { it.height }
                ?.content

            val playableUrl = progressiveStream ?: info.hlsUrl

            val related = info.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .take(12)
                .map { item ->
                    RealVideoItem(
                        url = item.url,
                        title = item.name ?: "Video",
                        uploaderName = item.uploaderName ?: "Channel",
                        thumbnailUrl = item.thumbnails.maxByOrNull { it.height }?.url ?: "",
                        viewsText = formatViews(item.viewCount),
                        uploadedText = item.textualUploadDate ?: "",
                        durationText = formatDuration(item.duration)
                    )
                }

            RealVideoDetails(
                url = info.url,
                title = info.name ?: "Video",
                uploaderName = info.uploaderName ?: "Channel",
                uploaderAvatar = info.uploaderAvatars.firstOrNull()?.url ?: "",
                viewCountText = formatViews(info.viewCount),
                uploadDate = info.textualUploadDate ?: "Published recently",
                durationSec = info.duration,
                thumbnailUrl = info.thumbnails.maxByOrNull { it.height }?.url ?: "",
                streamUrl = playableUrl,
                description = info.description?.content ?: "",
                likeCountText = if (info.likeCount >= 0) formatCount(info.likeCount) else "Like",
                subscriberText = if (info.uploaderSubscriberCount >= 0) "${formatCount(info.uploaderSubscriberCount)} subscribers" else "YouTube Channel",
                relatedStreams = related
            )
        }
    }

    private fun formatViews(views: Long): String {
        if (views < 0) return "Live / Views"
        return "${formatCount(views)} views"
    }

    private fun formatCount(count: Long): String {
        return when {
            count >= 1_000_000_000 -> String.format("%.1fB", count / 1_000_000_000.0)
            count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }

    private fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "LIVE"
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hrs > 0) {
            String.format("%d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }
}
