@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.universalplayer.player

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/** Disk cache for short videos (<= 1 min). It is wiped every time the app process starts. */
object ShortCache {
    @Volatile private var cache: SimpleCache? = null

    fun clearOnStart(ctx: Context) {
        runCatching { File(ctx.cacheDir, "shorts").deleteRecursively() }
        runCatching { ctx.deleteDatabase("exoplayer_internal.db") }
    }

    fun get(ctx: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            File(ctx.cacheDir, "shorts"),
            LeastRecentlyUsedCacheEvictor(300L * 1024 * 1024),
            StandaloneDatabaseProvider(ctx)
        ).also { cache = it }
    }

    fun remove(ctx: Context, key: String) {
        runCatching { get(ctx).removeResource(key) }
    }
}
