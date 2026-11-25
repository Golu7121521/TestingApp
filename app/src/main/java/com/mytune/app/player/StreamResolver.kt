package com.mytune.app.player

import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.mytune.app.data.db.DownloadStatus
import com.mytune.app.data.db.LibraryDao
import com.mytune.app.data.repository.SettingsStore
import com.mytune.app.domain.MusicRepository
import com.mytune.app.domain.pickQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Queue items use a placeholder URI (mytune://song/<id>). When ExoPlayer actually opens the item,
 * this resolver swaps it for the real source: the downloaded file if present (offline-first),
 * otherwise the stream URL at the user's preferred quality. It runs on ExoPlayer's loading thread,
 * so blocking calls here are fine.
 */
class StreamResolver(
    private val repo: MusicRepository,
    private val dao: LibraryDao,
    private val settings: SettingsStore,
) : ResolvingDataSource.Resolver {

    private val urlCache = ConcurrentHashMap<String, Map<Int, String>>()

    /** songId -> kbps actually used (0 = playing from a local file). Shown in the player. */
    val qualities = MutableStateFlow<Map<String, Int>>(emptyMap())

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme != SCHEME) return dataSpec
        val id = uri.lastPathSegment ?: throw IOException("Invalid song id")

        val local = runBlocking { dao.download(id) }
        if (local != null && local.status == DownloadStatus.DONE && File(local.filePath).exists()) {
            qualities.update { it + (id to 0) }
            return dataSpec.withUri(Uri.fromFile(File(local.filePath)))
        }

        val urls = urlCache[id] ?: runBlocking { repo.song(id).getOrNull()?.streamUrls }
            ?.takeIf { it.isNotEmpty() }
            ?.also { urlCache[id] = it }
            ?: throw IOException("No stream URL available")
        val q = urls.pickQuality(settings.streamQualityNow) ?: throw IOException("No stream URL available")
        qualities.update { it + (id to q) }
        return dataSpec.withUri(Uri.parse(urls.getValue(q)))
    }

    companion object {
        const val SCHEME = "mytune"
    }
}
