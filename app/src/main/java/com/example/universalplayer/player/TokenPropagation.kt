package com.example.universalplayer.player

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource

/**
 * Many CDNs (Google/Akamai/CloudFront style) put a signed token only on the master playlist URL, e.g.
 *   master.m3u8?edge-cache-token=URLPrefix=...~Expires=...~Signature=...
 * but the player resolves child playlists / segments / keys without that query, so the CDN answers 403.
 * This resolver re-attaches the token params to every request to the same host that doesn't have them.
 */
@UnstableApi
object TokenPropagation {
    private val tokenKeys = setOf(
        "edge-cache-token", "token", "hdnts", "expires", "signature", "policy",
        "key-pair-id", "hmac", "auth", "e", "st", "sig"
    )

    fun resolver(masterUrl: String): ResolvingDataSource.Resolver {
        val master = runCatching { Uri.parse(masterUrl) }.getOrNull()
        val host = master?.host
        val raw = masterUrl.substringAfter('?', "").substringBefore('#')
        val pairs = raw.split('&').filter { p ->
            p.isNotEmpty() && p.substringBefore('=').lowercase() in tokenKeys
        }
        val keys = pairs.map { it.substringBefore('=').lowercase() }
        val extra = pairs.joinToString("&")
        return ResolvingDataSource.Resolver { spec: DataSpec ->
            if (host == null || extra.isEmpty()) return@Resolver spec
            val s = spec.uri.toString()
            if (!spec.uri.host.equals(host, true)) return@Resolver spec
            val q = s.substringAfter('?', "").lowercase()
            if (keys.any { k -> q.startsWith("$k=") || q.contains("&$k=") }) return@Resolver spec
            val base = s.substringBefore('#')
            val joined = if (base.contains('?')) "$base&$extra" else "$base?$extra"
            spec.withUri(Uri.parse(joined))
        }
    }
}
