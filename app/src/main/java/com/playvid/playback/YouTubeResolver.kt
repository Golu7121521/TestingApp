package com.playvid.playback

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response as NpResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.net.URI

/** One selectable video-only or audio-only file for a YouTube video. [isM4a] = AAC-in-MP4 audio. */
data class YtStream(val label: String, val url: String, val height: Int = 0, val bitrate: Int = 0, val isM4a: Boolean = false)

/**
 * What the player needs to play one YouTube video.
 * [videos] / [audios] are each one specific file per quality/track: switching quality means
 * reopening the merged pair, never relying on YouTube's own adaptive HLS switching, which this
 * app found unreliable for edge resolutions (144p/1440p) on-device.
 */
data class YouTubeMedia(
    val title: String,
    val videos: List<YtStream> = emptyList(),
    val audios: List<YtStream> = emptyList(),
    /** Only set for a video with no separate video-only stream (rare): one fixed file, no quality choice. */
    val muxedUrl: String? = null,
    /** Only set for live broadcasts, which YouTube serves solely as an adaptive HLS manifest. */
    val liveHlsUrl: String? = null
)

/**
 * Turns a YouTube link into direct stream URLs using NewPipeExtractor.
 * Personal-use feature: it is not an official YouTube API and can break when YouTube changes.
 * Blocking: call from a background thread.
 */
class YouTubeResolver(private val httpClient: OkHttpClient) {

    fun resolve(url: String): YouTubeMedia {
        ensureInit()
        val info = StreamInfo.getInfo(ServiceList.YouTube, url)
        val title = info.name.orEmpty()

        if (info.hlsUrl?.isNotBlank() == true && info.videoOnlyStreams.isNullOrEmpty() && info.videoStreams.isNullOrEmpty()) {
            return YouTubeMedia(title, liveHlsUrl = info.hlsUrl)
        }

        val videos = info.videoOnlyStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.height > 0 }
            .groupBy { it.height }
            // One file per height: prefer MP4/H.264 (widely decodable), else the best available.
            .map { (_, list) -> list.firstOrNull { it.format == MediaFormat.MPEG_4 } ?: list.maxByOrNull { it.bitrate } }
            .filterNotNull()
            .sortedByDescending { it.height }
            .map { YtStream("${it.height}p", it.content, it.height, it.bitrate) }

        val audios = info.audioStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .sortedByDescending { it.averageBitrate }
            .mapIndexed { i, a ->
                val kbps = a.averageBitrate / 1000
                val lang = a.audioLocale?.displayLanguage?.takeIf { it.isNotBlank() }
                // averageBitrate is unreliable for some videos (comes back 0/unknown); fall back
                // to the codec name so the entries are at least distinguishable, not "(0 kbps)".
                val quality = if (kbps > 0) "$kbps kbps" else a.format?.name?.takeIf { it.isNotBlank() } ?: "Track ${i + 1}"
                YtStream(
                    lang?.let { "$it ($quality)" } ?: "Audio ${i + 1} ($quality)", a.content,
                    bitrate = a.averageBitrate, isM4a = a.format == MediaFormat.M4A
                )
            }

        if (videos.isNotEmpty() && audios.isNotEmpty()) return YouTubeMedia(title, videos = videos, audios = audios)

        val muxed = info.videoStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .maxByOrNull { it.height }
        if (muxed != null) return YouTubeMedia(title, muxedUrl = muxed.content)
        info.hlsUrl?.takeIf { it.isNotBlank() }?.let { return YouTubeMedia(title, liveHlsUrl = it) }

        throw IllegalStateException("No playable stream found")
    }

    private fun ensureInit() {
        if (initialized) return
        synchronized(lock) {
            if (!initialized) {
                NewPipe.init(OkHttpDownloader(httpClient))
                initialized = true
            }
        }
    }

    companion object {
        private val lock = Any()
        @Volatile private var initialized = false

        fun isYouTubeUrl(url: String): Boolean {
            val host = try { URI(url.trim()).host?.lowercase() } catch (_: Exception) { null } ?: return false
            return host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com") ||
                host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")
        }
    }
}

/** NewPipeExtractor makes all its web requests through this class. */
private class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: NpRequest): NpResponse {
        val url = request.url()
        val data = request.dataToSend()
        val body = data?.toRequestBody(null)

        val builder = okhttp3.Request.Builder()
            .method(request.httpMethod(), body)
            .url(url)
            .header("User-Agent", USER_AGENT)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }

        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) throw ReCaptchaException("reCaptcha challenge requested", url)
            val text = response.body?.string().orEmpty()
            return NpResponse(
                response.code,
                response.message,
                response.headers.toMultimap(),
                text,
                response.request.url.toString()
            )
        }
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    }
}

/**
 * YouTube stream links only work with the User-Agent of the client that created them
 * (the link says which one: `c=IOS`, `c=ANDROID`, ...). Media playback goes through this.
 */
class YouTubeHeadersInterceptor : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val host = request.url.host
        if (!host.endsWith("googlevideo.com") && !host.endsWith("youtube.com")) return chain.proceed(request)

        val client = request.url.queryParameter("c")
            ?: request.url.pathSegments.let { seg -> seg.indexOf("c").takeIf { it >= 0 }?.let { seg.getOrNull(it + 1) } }
        val ua = when (client) {
            "IOS" -> "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"
            "ANDROID" -> "com.google.android.youtube/19.28.35 (Linux; U; Android 14) gzip"
            "ANDROID_VR" -> "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
            else -> null
        } ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().header("User-Agent", ua).build())
    }
}
