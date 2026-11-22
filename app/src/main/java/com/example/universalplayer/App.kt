package com.example.universalplayer

import android.app.Application
import com.example.universalplayer.data.HistoryStore
import com.example.universalplayer.data.PlaylistStore
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CompletableDeferred
import kotlin.concurrent.thread

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        com.example.universalplayer.player.ShortCache.clearOnStart(this)
        HistoryStore.init(this)
        com.example.universalplayer.data.CookieVault.init(this)
        PlaylistStore.init(this)
        AppInit.start(this)
    }
}

/** Initialises the yt-dlp engine in background and keeps it updated (sites change often). */
object AppInit {
    val ready = CompletableDeferred<Unit>()

    fun start(app: Application) {
        thread(name = "ytdlp-init") {
            try {
                YoutubeDL.getInstance().init(app)
                ready.complete(Unit)
            } catch (e: Throwable) {
                ready.completeExceptionally(e)
                return@thread
            }
            update(app)
        }
    }

    /** Updates yt-dlp. Automatic once per 12 h; [force] = from the "Update video engine" button. */
    fun update(app: Application, force: Boolean = false): Boolean {
        val prefs = app.getSharedPreferences("ytdlp", 0)
        val last = prefs.getLong("last_update", 0L)
        if (!force && System.currentTimeMillis() - last < 12 * 3600_000L) return false
        try {
            // Reflection keeps this independent of small API naming changes between versions.
            val cls = Class.forName("com.yausername.youtubedl_android.YoutubeDL\$UpdateChannel")
            val channel = listOf("NIGHTLY", "_NIGHTLY", "STABLE", "_STABLE").firstNotNullOfOrNull { n ->
                runCatching { cls.getField(n).get(null) }.getOrNull()
            }
            val method = YoutubeDL::class.java.methods.firstOrNull {
                it.name == "updateYoutubeDL" && it.parameterTypes.size == 2
            }
            if (channel != null && method != null) {
                method.invoke(YoutubeDL.getInstance(), app, channel)
                prefs.edit().putLong("last_update", System.currentTimeMillis()).apply()
                return true
            }
        } catch (_: Throwable) {
        }
        return false
    }
}
