package com.litetube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class VideoItem(
    val url: String,
    val title: String,
    val channel: String,
    val thumbnail: String?,
    val durationSec: Long
)

data class StreamData(
    val title: String,
    val channel: String,
    val playUrl: String,
    val related: List<VideoItem>
)

/** All network / scraping work happens here, always on IO thread. */
object Repo {
    private val service get() = ServiceList.YouTube

    suspend fun search(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val handler = service.searchQHFactory.fromQuery(query)
        val info = SearchInfo.getInfo(service, handler)
        info.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideoItem() }
    }

    suspend fun streamUrl(watchUrl: String): StreamData = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(service, watchUrl)

        // Muxed (video+audio) stream, highest resolution available.
        // 720p+ ke liye DASH merging ka support next step mein add kar sakte hain.
        val muxed = info.videoStreams
            .filter { !it.content.isNullOrBlank() }
            .maxByOrNull { it.resolution.orEmpty().filter(Char::isDigit).toIntOrNull() ?: 0 }
            ?.content

        val playable = muxed
            ?: info.hlsUrl?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Playable stream nahi mila")

        StreamData(
            title = info.name.orEmpty(),
            channel = info.uploaderName.orEmpty(),
            playUrl = playable,
            related = info.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideoItem() }
        )
    }

    private fun StreamInfoItem.toVideoItem() = VideoItem(
        url = url,
        title = name.orEmpty(),
        channel = uploaderName.orEmpty(),
        thumbnail = thumbnails.firstOrNull()?.url,
        durationSec = duration
    )
}
