package com.example.tubeclone.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.TimeUnit

data class VideoItem(
    val title: String,
    val url: String,
    val channel: String,
    val channelUrl: String,
    val thumb: String?,
    val durationSec: Long,
    val views: Long
)

data class Channel(val name: String, val url: String)

/** NewPipe Extractor ko network access dene ke liye OkHttp wrapper */
class OkHttpDownloader : Downloader() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun execute(request: Request): Response {
        val builder = okhttp3.Request.Builder().url(request.url())
        for ((key, values) in request.headers()) {
            builder.removeHeader(key)
            values.forEach { builder.addHeader(key, it) }
        }
        val body = request.dataToSend()?.toRequestBody(null)
        builder.method(request.httpMethod(), body)

        client.newCall(builder.build()).execute().use { resp ->
            return Response(
                resp.code,
                resp.message,
                resp.headers.toMultimap(),
                resp.body?.string(),
                resp.request.url.toString()
            )
        }
    }
}

object Repo {
    private var prefs: SharedPreferences? = null

    fun init(ctx: Context) {
        if (prefs != null) return
        NewPipe.init(OkHttpDownloader())
        prefs = ctx.applicationContext.getSharedPreferences("tubeclone", Context.MODE_PRIVATE)
    }

    private fun p(): SharedPreferences = prefs!!

    // ---------- YouTube data (NewPipe Extractor) ----------

    suspend fun search(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val ex = ServiceList.YouTube.getSearchExtractor(query)
        ex.fetchPage()
        ex.initialPage.items.filterIsInstance<StreamInfoItem>().map { it.toVideo() }
    }

    /** Channel ke latest videos: channel naam se search karke usi channel ke videos filter karo */
    suspend fun channelVideos(name: String, channelUrl: String, max: Int = 3): List<VideoItem> =
        search(name).filter { it.channelUrl == channelUrl }.take(max)

    /** Video ka playable URL (HLS ho to best, warna progressive) */
    suspend fun resolve(url: String): String = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(ServiceList.YouTube, url)
        val hls = info.hlsUrl
        if (!hls.isNullOrBlank()) return@withContext hls
        info.videoStreams.firstOrNull { !it.isVideoOnly }?.content
            ?: throw IllegalStateException("Is video ka playable stream nahi mila")
    }

    private fun StreamInfoItem.toVideo() = VideoItem(
        title = name ?: "",
        url = url,
        channel = uploaderName ?: "",
        channelUrl = uploaderUrl ?: "",
        thumb = thumbnails.firstOrNull()?.url,
        durationSec = duration,
        views = viewCount
    )

    // ---------- Subscriptions (local) ----------

    fun subs(): List<Channel> {
        val arr = JSONArray(p().getString("subs", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Channel(o.getString("name"), o.getString("url"))
        }
    }

    fun isSubscribed(url: String): Boolean = subs().any { it.url == url }

    /** Returns true agar ab subscribed hai, false agar unsubscribe hua */
    fun toggleSub(c: Channel): Boolean {
        val list = subs().toMutableList()
        val exists = list.any { it.url == c.url }
        if (exists) list.removeAll { it.url == c.url } else list.add(0, c)
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("name", it.name).put("url", it.url)) }
        p().edit().putString("subs", arr.toString()).apply()
        return !exists
    }

    // ---------- History (local) ----------

    fun history(): List<VideoItem> {
        val arr = JSONArray(p().getString("history", "[]"))
        return (0 until arr.length()).map { videoFromJson(arr.getJSONObject(it)) }
    }

    fun addHistory(v: VideoItem) {
        val arr = JSONArray(p().getString("history", "[]"))
        val out = JSONArray().put(videoToJson(v))
        var count = 1
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.getString("url") != v.url && count < 200) {
                out.put(o)
                count++
            }
        }
        p().edit().putString("history", out.toString()).apply()
    }

    fun clearHistory() {
        p().edit().remove("history").apply()
    }

    private fun videoToJson(v: VideoItem): JSONObject = JSONObject()
        .put("title", v.title)
        .put("url", v.url)
        .put("channel", v.channel)
        .put("channelUrl", v.channelUrl)
        .put("thumb", v.thumb ?: JSONObject.NULL)
        .put("durationSec", v.durationSec)
        .put("views", v.views)

    private fun videoFromJson(o: JSONObject): VideoItem = VideoItem(
        title = o.getString("title"),
        url = o.getString("url"),
        channel = o.getString("channel"),
        channelUrl = o.getString("channelUrl"),
        thumb = if (o.isNull("thumb")) null else o.getString("thumb"),
        durationSec = o.getLong("durationSec"),
        views = o.getLong("views")
    )
}
