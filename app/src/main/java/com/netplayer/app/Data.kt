@file:OptIn(UnstableApi::class)

package com.netplayer.app

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableStateMapOf
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import org.json.JSONArray
import org.json.JSONObject

val UA_OPTIONS = linkedMapOf(
    "Custom" to "",
    "Default" to "",
    "Chrome (Android)" to "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36",
    "Chrome (Desktop)" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
    "Firefox (Desktop)" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:127.0) Gecko/20100101 Firefox/127.0",
    "VLC" to "VLC/3.0.20 LibVLC/3.0.20",
    "ExoPlayer" to "ExoPlayerLib/2.19.1"
)
val DRM_SCHEMES = listOf("widevine", "playready", "clearkey")

data class StreamRequest(
    val url: String,
    val cookie: String = "",
    val referer: String = "",
    val origin: String = "",
    val drmUrl: String = "",
    val uaMode: String = "Custom",
    val customUa: String = "",
    val drmScheme: String = "widevine"
) {
    fun toIntent(ctx: Context): Intent = Intent(ctx, PlayerActivity::class.java).apply {
        putExtra("url", url); putExtra("cookie", cookie); putExtra("referer", referer)
        putExtra("origin", origin); putExtra("drmUrl", drmUrl); putExtra("uaMode", uaMode)
        putExtra("customUa", customUa); putExtra("drmScheme", drmScheme)
    }

    fun userAgent(): String? =
        (if (uaMode == "Custom") customUa.trim() else (UA_OPTIONS[uaMode] ?: "")).ifEmpty { null }

    fun headers(): Map<String, String> = buildMap {
        if (cookie.isNotBlank()) put("Cookie", cookie.trim())
        if (referer.isNotBlank()) put("Referer", referer.trim())
        if (origin.isNotBlank()) put("Origin", origin.trim())
    }

    companion object {
        fun from(i: Intent): StreamRequest? {
            val u = i.getStringExtra("url") ?: return null
            return StreamRequest(
                u, i.getStringExtra("cookie") ?: "", i.getStringExtra("referer") ?: "",
                i.getStringExtra("origin") ?: "", i.getStringExtra("drmUrl") ?: "",
                i.getStringExtra("uaMode") ?: "Custom", i.getStringExtra("customUa") ?: "",
                i.getStringExtra("drmScheme") ?: "widevine"
            )
        }
    }
}

data class HistoryItem(val req: StreamRequest, val time: Long)

class HistoryStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("history", Context.MODE_PRIVATE)

    fun load(): List<HistoryItem> = try {
        val arr = JSONArray(sp.getString("items", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            HistoryItem(
                StreamRequest(
                    o.getString("url"), o.optString("cookie"), o.optString("referer"),
                    o.optString("origin"), o.optString("drmUrl"), o.optString("uaMode", "Custom"),
                    o.optString("customUa"), o.optString("drmScheme", "widevine")
                ), o.optLong("time")
            )
        }
    } catch (e: Exception) { emptyList() }

    private fun save(list: List<HistoryItem>) {
        val arr = JSONArray()
        list.forEach { h ->
            arr.put(JSONObject().apply {
                put("url", h.req.url); put("cookie", h.req.cookie); put("referer", h.req.referer)
                put("origin", h.req.origin); put("drmUrl", h.req.drmUrl); put("uaMode", h.req.uaMode)
                put("customUa", h.req.customUa); put("drmScheme", h.req.drmScheme); put("time", h.time)
            })
        }
        sp.edit().putString("items", arr.toString()).apply()
    }

    fun add(r: StreamRequest) {
        val l = load().filter { it.req.url != r.url }.toMutableList()
        l.add(0, HistoryItem(r, System.currentTimeMillis()))
        save(l.take(200))
    }

    fun remove(url: String) = save(load().filter { it.req.url != url })
    fun clear() = save(emptyList())
}

val GESTURES = listOf(
    "brightness" to "Brightness swipe (left side)",
    "volume" to "Volume swipe (right side)",
    "doubleTap" to "Double tap seek (10 sec)",
    "pinch" to "Pinch zoom in / out",
    "hold2x" to "Press & hold for 2x speed"
)

class GesturePrefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("gestures", Context.MODE_PRIVATE)
    private val state = mutableStateMapOf<String, Boolean>().apply {
        GESTURES.forEach { (k, _) -> put(k, sp.getBoolean(k, true)) }
    }
    fun get(k: String): Boolean = state[k] ?: true
    fun set(k: String, v: Boolean) { state[k] = v; sp.edit().putBoolean(k, v).apply() }
}

object PlayerFactory {
    fun guessMime(url: String): String? {
        val u = url.lowercase()
        return when {
            u.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
            u.contains(".mpd") -> MimeTypes.APPLICATION_MPD
            u.contains(".ism/") || u.contains(".ism?") || u.endsWith(".ism") -> MimeTypes.APPLICATION_SS
            else -> null
        }
    }

    fun mediaItem(r: StreamRequest, mime: String?): MediaItem {
        val b = MediaItem.Builder().setUri(r.url.trim())
        if (mime != null) b.setMimeType(mime)
        if (r.drmUrl.isNotBlank()) {
            val uuid = when (r.drmScheme) {
                "playready" -> C.PLAYREADY_UUID
                "clearkey" -> C.CLEARKEY_UUID
                else -> C.WIDEVINE_UUID
            }
            val h = r.headers().toMutableMap()
            r.userAgent()?.let { h["User-Agent"] = it }
            b.setDrmConfiguration(
                MediaItem.DrmConfiguration.Builder(uuid)
                    .setLicenseUri(r.drmUrl.trim())
                    .setLicenseRequestHeaders(h)
                    .setMultiSession(true)
                    .build()
            )
        }
        return b.build()
    }

    fun build(ctx: Context, r: StreamRequest): ExoPlayer {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setDefaultRequestProperties(r.headers())
        r.userAgent()?.let { http.setUserAgent(it) }
        val dsf = DefaultDataSource.Factory(ctx, http)
        val drm = DefaultDrmSessionManagerProvider()
        drm.setDrmHttpDataSourceFactory(http)
        val msf = DefaultMediaSourceFactory(dsf).setDrmSessionManagerProvider(drm)
        val renderers = DefaultRenderersFactory(ctx).setEnableDecoderFallback(true)
        return ExoPlayer.Builder(ctx, renderers)
            .setMediaSourceFactory(msf)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .build()
    }
}
