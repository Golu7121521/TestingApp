@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.mytube.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

sealed class Load<out T> {
    object Idle : Load<Nothing>()
    object Loading : Load<Nothing>()
    class Ok<T>(val value: T) : Load<T>()
    class Err(val msg: String) : Load<Nothing>()
}

class MainVM(private val app: Application) : AndroidViewModel(app) {

    val chips = listOf("All", "Music", "Gaming", "News", "Live", "Movies", "Cricket", "Podcasts")

    var tab by mutableIntStateOf(0)
    var chip by mutableIntStateOf(0)
    var home by mutableStateOf<Load<List<StreamInfoItem>>>(Load.Loading)
    var query by mutableStateOf("")
    var results by mutableStateOf<Load<List<StreamInfoItem>>>(Load.Idle)

    var watchOpen by mutableStateOf(false)
    var watch by mutableStateOf<Load<StreamInfo>>(Load.Idle)
    var qualities by mutableStateOf<List<Int>>(emptyList())
    var quality by mutableIntStateOf(0)
    private var current: StreamInfo? = null

    private val yt = ServiceList.YouTube

    // ---------- Player ----------
    private val resolver = ResolvingDataSource.Resolver { spec ->
        val ua = when (spec.uri.getQueryParameter("c")) {
            "ANDROID" -> "com.google.android.youtube/19.28.35 (Linux; U; Android 14) gzip"
            "IOS" -> "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)"
            else -> DownloaderImpl.USER_AGENT
        }
        spec.withRequestHeaders(spec.httpRequestHeaders + ("User-Agent" to ua))
    }

    private val dsFactory = ResolvingDataSource.Factory(
        DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(20000),
        resolver
    )

    val player: ExoPlayer by lazy {
        ExoPlayer.Builder(app)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dsFactory))
            .build()
    }

    init { loadHome() }

    // ---------- Feed ----------
    fun selectChip(i: Int) { chip = i; loadHome() }

    fun loadHome() {
        home = Load.Loading
        viewModelScope.launch {
            home = try {
                val list = withContext(Dispatchers.IO) {
                    if (chip == 0) {
                        val fromKiosk = try {
                            val ex = yt.kioskList.defaultKioskExtractor
                            ex.fetchPage()
                            ex.initialPage.items.filterIsInstance<StreamInfoItem>()
                        } catch (e: Throwable) { emptyList() }
                        fromKiosk.ifEmpty { searchItems("trending videos") }
                    } else searchItems(chips[chip])
                }
                if (list.isEmpty()) Load.Err("Koi video nahi mila") else Load.Ok(list)
            } catch (e: Throwable) {
                Load.Err(e.message ?: e.toString())
            }
        }
    }

    private fun searchItems(q: String): List<StreamInfoItem> {
        val handler = yt.searchQHFactory.fromQuery(q)
        return SearchInfo.getInfo(yt, handler).relatedItems.filterIsInstance<StreamInfoItem>()
    }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        results = Load.Loading
        viewModelScope.launch {
            results = try {
                val list = withContext(Dispatchers.IO) { searchItems(q) }
                if (list.isEmpty()) Load.Err("Kuch nahi mila") else Load.Ok(list)
            } catch (e: Throwable) {
                Load.Err(e.message ?: e.toString())
            }
        }
    }

    // ---------- Watch ----------
    fun openVideo(url: String) {
        watchOpen = true
        watch = Load.Loading
        qualities = emptyList()
        player.stop()
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { StreamInfo.getInfo(yt, url) }
                current = info
                val heights = info.videoOnlyStreams
                    .filter { it.isUrl && it.format == MediaFormat.MPEG_4 }
                    .map { it.height }.distinct().sortedDescending()
                qualities = heights
                quality = heights.firstOrNull { it <= 720 } ?: heights.lastOrNull() ?: 0
                watch = Load.Ok(info)
                val src = buildSource(info, quality)
                if (src != null) {
                    player.setMediaSource(src)
                    player.prepare()
                    player.playWhenReady = true
                } else {
                    watch = Load.Err("Is video ki stream nahi mili")
                }
            } catch (e: Throwable) {
                watch = Load.Err(e.message ?: e.toString())
            }
        }
    }

    fun setQuality(h: Int) {
        val info = current ?: return
        quality = h
        val pos = player.currentPosition
        buildSource(info, h)?.let {
            player.setMediaSource(it, pos)
            player.prepare()
            player.playWhenReady = true
        }
    }

    fun closeVideo() {
        watchOpen = false
        watch = Load.Idle
        current = null
        player.stop()
        player.clearMediaItems()
    }

    private fun buildSource(info: StreamInfo, height: Int): MediaSource? {
        val progressive = ProgressiveMediaSource.Factory(dsFactory)
        fun prog(u: String) = progressive.createMediaSource(MediaItem.fromUri(u))
        fun hls() = HlsMediaSource.Factory(dsFactory).createMediaSource(MediaItem.fromUri(info.hlsUrl))

        val live = info.streamType == StreamType.LIVE_STREAM ||
            info.streamType == StreamType.AUDIO_LIVE_STREAM
        if (live && info.hlsUrl.isNotBlank()) return hls()

        val audio = info.audioStreams.filter { it.isUrl }
            .let { l -> l.filter { it.format == MediaFormat.M4A }.ifEmpty { l } }
            .maxByOrNull { it.averageBitrate }
        val video = info.videoOnlyStreams
            .filter { it.isUrl && it.format == MediaFormat.MPEG_4 && it.height == height }
            .firstOrNull()
        if (video != null && audio != null) {
            return MergingMediaSource(prog(video.content), prog(audio.content))
        }
        val muxed = info.videoStreams.filter { it.isUrl }.maxByOrNull { it.height }
        if (muxed != null) return prog(muxed.content)
        if (info.hlsUrl.isNotBlank()) return hls()
        return null
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}
