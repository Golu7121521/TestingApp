package com.example.universalplayer.player

import android.net.Uri
import androidx.media3.common.MimeTypes
import com.example.universalplayer.AppInit
import com.example.universalplayer.data.AdvancedSettings
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class Fmt(
    val url: String,
    val height: Int,
    val vcodec: String,
    val acodec: String,
    val protocol: String,
    val tbr: Double,
    val lang: String?,
    val langPref: Int,
    val headers: Map<String, String>
)

data class SubTrack(val url: String, val lang: String, val label: String, val mime: String)

data class Resolved(
    val title: String,
    val thumb: String?,
    val isLive: Boolean,
    val formats: List<Fmt>,
    val subs: List<SubTrack>,
    val direct: Boolean,
    val directUrl: String?,
    val directMime: String?,
    val webUrl: String
)

object Resolver {
    private val progressiveExt =
        setOf("mp4", "webm", "mkv", "mov", "m4v", "3gp", "mp3", "m4a", "aac", "ogg", "opus", "flac", "wav")

    private fun directKind(url: String): String? {
        val path = runCatching { Uri.parse(url).path ?: "" }.getOrDefault("").lowercase()
        val ext = path.substringAfterLast('.', "")
        return when {
            ext == "m3u8" -> "hls"
            ext == "mpd" -> "dash"
            ext in progressiveExt -> "prog"
            else -> null
        }
    }

    private fun JSONObject.str(k: String): String? =
        if (isNull(k)) null else optString(k).takeIf { it.isNotBlank() }

    private fun headersOf(o: JSONObject?): Map<String, String> {
        val m = mutableMapOf<String, String>()
        o?.keys()?.forEach { k ->
            if (!k.equals("Accept-Encoding", true)) m[k] = o!!.optString(k)
        }
        return m
    }

    /**
     * With a DRM license URI set, yt-dlp is skipped and the link is played directly
     * as DASH (unless it is clearly HLS), because yt-dlp cannot handle DRM streams.
     */
    suspend fun resolve(url: String, opts: AdvancedSettings): Resolved = withContext(Dispatchers.IO) {
        (directKind(url) ?: if (opts.drmConfig() != null) "dash" else null)?.let { kind ->
            return@withContext Resolved(
                title = Uri.parse(url).lastPathSegment ?: url,
                thumb = null, isLive = false, formats = emptyList(), subs = emptyList(),
                direct = true, directUrl = url,
                directMime = when (kind) {
                    "hls" -> MimeTypes.APPLICATION_M3U8
                    "dash" -> MimeTypes.APPLICATION_MPD
                    else -> null
                },
                webUrl = url
            )
        }

        withTimeout(30_000) { AppInit.ready.await() }

        val req = YoutubeDLRequest(url).apply {
            addOption("--dump-single-json")
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--playlist-items", "1")
            addOption("--socket-timeout", "20")
            // Cookie / Referer / Origin / User-Agent from the Advanced tab
            opts.headers.forEach { (k, v) -> addOption("--add-headers", "$k:$v") }
        }
        val out = YoutubeDL.getInstance().execute(req).out ?: ""
        val line = out.lineSequence().map { it.trim() }.lastOrNull { it.startsWith("{") }
            ?: throw IllegalStateException("No data returned for this link")
        var j = JSONObject(line)
        j.optJSONArray("entries")?.let { if (it.length() > 0) j = it.getJSONObject(0) }

        val defHeaders = headersOf(j.optJSONObject("http_headers"))
        val fmts = mutableListOf<Fmt>()
        val arr = j.optJSONArray("formats")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val f = arr.getJSONObject(i)
                val u = f.str("url") ?: continue
                val proto = f.str("protocol") ?: "https"
                if (listOf("dash_segments", "ism", "f4m", "rtmp", "rtsp", "mhtml").any { proto.contains(it) }) continue
                if ((f.str("format_id") ?: "").startsWith("sb")) continue
                if (f.str("ext") == "mhtml") continue
                val vc = f.str("vcodec") ?: "unknown"
                val ac = f.str("acodec") ?: "unknown"
                if (vc == "none" && ac == "none") continue
                val h = headersOf(f.optJSONObject("http_headers")).ifEmpty { defHeaders }
                fmts += Fmt(
                    url = u, height = f.optInt("height", 0), vcodec = vc, acodec = ac,
                    protocol = proto, tbr = f.optDouble("tbr", 0.0),
                    lang = f.str("language"), langPref = f.optInt("language_preference", 0),
                    headers = h
                )
            }
        }
        val top = j.str("url")
        if (fmts.isEmpty() && top != null) {
            fmts += Fmt(
                top, j.optInt("height", 0), j.str("vcodec") ?: "unknown", j.str("acodec") ?: "unknown",
                j.str("protocol") ?: "https", 0.0, null, 0, defHeaders
            )
        }

        val manifest = j.str("manifest_url")
        val title = j.str("title") ?: url
        val thumb = j.str("thumbnail") ?: j.optJSONArray("thumbnails")?.let { t ->
            if (t.length() > 0) t.getJSONObject(t.length() - 1).str("url") else null
        }
        val live = j.optBoolean("is_live", false)

        if (fmts.isEmpty() && manifest != null) {
            return@withContext Resolved(
                title, thumb, live, emptyList(), emptyList(), true, manifest,
                if (manifest.contains("m3u8")) MimeTypes.APPLICATION_M3U8 else MimeTypes.APPLICATION_MPD, url
            )
        }
        if (fmts.isEmpty()) throw IllegalStateException("No playable stream found for this link")

        Resolved(title, thumb, live, fmts, parseSubs(j), false, null, null, url)
    }

    private fun subMime(ext: String) = when (ext) {
        "vtt" -> MimeTypes.TEXT_VTT
        "srt" -> MimeTypes.APPLICATION_SUBRIP
        else -> MimeTypes.APPLICATION_TTML
    }

    private fun parseSubs(j: JSONObject): List<SubTrack> {
        val out = mutableListOf<SubTrack>()
        fun collect(o: JSONObject?, accept: (String) -> Boolean) {
            if (o == null) return
            val keys = o.keys().asSequence().toList()
            for (lang in keys) {
                if (!accept(lang)) continue
                val arr = o.optJSONArray(lang) ?: continue
                val items = (0 until arr.length()).map { arr.getJSONObject(it) }
                val best = listOf("vtt", "srt", "ttml").firstNotNullOfOrNull { ext ->
                    items.firstOrNull { it.optString("ext") == ext && it.has("url") }
                } ?: continue
                out += SubTrack(
                    best.getString("url"), lang, best.str("name") ?: lang, subMime(best.optString("ext"))
                )
            }
        }
        collect(j.optJSONObject("subtitles")) { it != "live_chat" }
        if (out.isEmpty()) collect(j.optJSONObject("automatic_captions")) { it == "en" || it == "en-orig" }
        return out.take(6)
    }
}
