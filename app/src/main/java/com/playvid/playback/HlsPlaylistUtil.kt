package com.playvid.playback

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI

/**
 * What a media (leaf) HLS playlist resolves to: whether it's VOD (has an end, so it can be
 * downloaded) or live, its segment URLs in order, and an optional init segment (fragmented-MP4
 * HLS declares one via #EXT-X-MAP; plain .ts segments don't have one).
 */
data class HlsPlan(val isVod: Boolean, val initSegmentUrl: String?, val segmentUrls: List<String>)

/** Fetches and parses just enough of an HLS manifest to answer "is this downloadable, and how". */
object HlsPlaylistUtil {

    /** Small GET with a byte cap so a huge/misbehaving response can't hang this. */
    private fun fetchText(client: OkHttpClient, url: String): String? = try {
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.string()
        }
    } catch (e: IOException) {
        null
    }

    private fun resolve(baseUrl: String, ref: String): String =
        if (ref.startsWith("http://") || ref.startsWith("https://")) ref
        else try {
            URI(baseUrl).resolve(ref).toString()
        } catch (e: Exception) {
            ref
        }

    /** [playlistUrl] can be a master or a media playlist; master playlists resolve to the first variant. */
    suspend fun resolvePlan(client: OkHttpClient, playlistUrl: String): HlsPlan? {
        val first = fetchText(client, playlistUrl) ?: return null
        val isMaster = first.lineSequence().any { it.startsWith("#EXT-X-STREAM-INF") }
        val (mediaUrl, mediaText) = if (isMaster) {
            val variantUrl = first.lineSequence()
                .withIndex()
                .firstOrNull { (_, line) -> line.startsWith("#EXT-X-STREAM-INF") }
                ?.let { (i, _) -> first.lines().getOrNull(i + 1) }
                ?.trim()
                ?.takeIf { it.isNotEmpty() && !it.startsWith("#") }
                ?: return null
            val resolved = resolve(playlistUrl, variantUrl)
            val text = fetchText(client, resolved) ?: return null
            resolved to text
        } else {
            playlistUrl to first
        }

        val isVod = mediaText.contains("#EXT-X-ENDLIST")
        var initSegmentUrl: String? = null
        val segments = mutableListOf<String>()
        mediaText.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("#EXT-X-MAP") -> {
                    Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1)?.let {
                        initSegmentUrl = resolve(mediaUrl, it)
                    }
                }
                line.isNotEmpty() && !line.startsWith("#") -> segments += resolve(mediaUrl, line)
            }
        }
        return HlsPlan(isVod = isVod, initSegmentUrl = initSegmentUrl, segmentUrls = segments)
    }
}
