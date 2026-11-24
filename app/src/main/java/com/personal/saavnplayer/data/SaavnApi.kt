package com.personal.saavnplayer.data

import android.text.Html
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val image: String?
)

/**
 * Minimal client for the unofficial JioSaavn web API.
 * These endpoints are not officially documented and may change at any time.
 */
object SaavnApi {
    private const val ENDPOINT = "https://www.jiosaavn.com/api.php"
    private const val DES_KEY = "38346591"
    private val http = OkHttpClient()

    suspend fun search(query: String): List<Song> = withContext(Dispatchers.IO) {
        val json = JSONObject(
            fetch(endpoint("__call" to "search.getResults", "q" to query, "n" to "30", "p" to "1"))
        )
        val arr = json.optJSONArray("results") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isEmpty()) continue
                add(
                    Song(
                        id = id,
                        title = clean(o.optString("title")),
                        artist = clean(o.optString("subtitle")),
                        image = o.optString("image").takeIf { it.isNotEmpty() }
                            ?.replace("150x150", "500x500")
                    )
                )
            }
        }
    }

    /** Returns a playable direct URL (prefers 320 kbps when available). */
    suspend fun getStreamUrl(id: String): String = withContext(Dispatchers.IO) {
        val json = JSONObject(fetch(endpoint("__call" to "song.getDetails", "pids" to id)))
        val song = findSong(json, id) ?: throw IOException("Song details not found")
        val encrypted = song.optString("encrypted_media_url").ifEmpty {
            song.optJSONObject("more_info")?.optString("encrypted_media_url").orEmpty()
        }
        if (encrypted.isEmpty()) throw IOException("No media URL for this song")
        best(decrypt(encrypted))
    }

    private fun findSong(json: JSONObject, id: String): JSONObject? {
        json.optJSONArray("songs")?.optJSONObject(0)?.let { return it }
        json.optJSONObject(id)?.let { return it }
        for (key in json.keys()) {
            json.optJSONObject(key)?.let { return it }
        }
        return null
    }

    private fun decrypt(encrypted: String): String {
        val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(DES_KEY.toByteArray(), "DES"))
        return String(cipher.doFinal(Base64.decode(encrypted, Base64.DEFAULT)))
    }

    private fun best(url: String): String {
        if (!url.contains("_96.")) return url
        val hq = url.replace("_96.", "_320.")
        return try {
            http.newCall(Request.Builder().url(hq).head().build()).execute().use { response ->
                if (response.isSuccessful) hq else url
            }
        } catch (e: IOException) {
            url
        }
    }

    private fun fetch(url: HttpUrl): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body?.string() ?: throw IOException("Empty response")
        }
    }

    private fun endpoint(vararg params: Pair<String, String>): HttpUrl {
        val builder = ENDPOINT.toHttpUrl().newBuilder()
        for ((k, v) in params) builder.addQueryParameter(k, v)
        builder.addQueryParameter("_format", "json")
        builder.addQueryParameter("_marker", "0")
        builder.addQueryParameter("api_version", "4")
        builder.addQueryParameter("ctx", "web6dot0")
        return builder.build()
    }

    private fun clean(text: String): String =
        Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString().trim()
}
