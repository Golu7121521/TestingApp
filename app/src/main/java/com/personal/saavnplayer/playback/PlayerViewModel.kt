package com.personal.saavnplayer.playback

import android.app.Application
import android.app.DownloadManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.personal.saavnplayer.data.SaavnApi
import com.personal.saavnplayer.data.Song
import kotlinx.coroutines.launch
import java.io.File

data class LocalTrack(val file: File, val title: String)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Context = app

    var controller: MediaController? = null
        private set

    var query by mutableStateOf("")
    var results by mutableStateOf<List<Song>>(emptyList())
        private set
    var isSearching by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    var nowTitle by mutableStateOf<String?>(null)
        private set
    var nowArtist by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var downloads by mutableStateOf<List<LocalTrack>>(emptyList())
        private set

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            c.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    this@PlayerViewModel.isPlaying = isPlaying
                }
            })
        }, ContextCompat.getMainExecutor(context))

        refreshDownloads()
    }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            isSearching = true
            message = null
            try {
                results = SaavnApi.search(q)
                if (results.isEmpty()) message = "No results found"
            } catch (e: Exception) {
                message = "Search failed: ${e.message}"
            } finally {
                isSearching = false
            }
        }
    }

    fun playOnline(song: Song) {
        viewModelScope.launch {
            isLoading = true
            message = null
            try {
                val url = SaavnApi.getStreamUrl(song.id)
                play(song.id, url, song.title, song.artist)
            } catch (e: Exception) {
                message = "Could not play: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun playLocal(track: LocalTrack) {
        play(
            track.file.absolutePath,
            Uri.fromFile(track.file).toString(),
            track.title,
            "Downloaded"
        )
    }

    fun download(song: Song) {
        viewModelScope.launch {
            message = null
            try {
                val url = SaavnApi.getStreamUrl(song.id)
                val fileName = sanitize("${song.artist} - ${song.title}") + ".m4a"
                val request = DownloadManager.Request(Uri.parse(url))
                    .setTitle(song.title)
                    .setDescription("Saavn Player download")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MUSIC, fileName)
                    .setAllowedOverRoaming(true)
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                message = "Download started: ${song.title}"
            } catch (e: Exception) {
                message = "Download failed: ${e.message}"
            }
        }
    }

    fun refreshDownloads() {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        downloads = dir?.listFiles()
            ?.filter { it.isFile && it.extension.equals("m4a", ignoreCase = true) && it.length() > 0 }
            ?.sortedBy { it.name.lowercase() }
            ?.map { LocalTrack(it, it.nameWithoutExtension) }
            ?: emptyList()
    }

    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    private fun play(id: String, uri: String, title: String, artist: String) {
        val c = controller
        if (c == null) {
            message = "Player is starting, please try again in a moment"
            return
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .build()
        c.setMediaItem(
            MediaItem.Builder()
                .setMediaId(id)
                .setUri(uri)
                .setMediaMetadata(metadata)
                .build()
        )
        c.prepare()
        c.play()
        nowTitle = title
        nowArtist = artist
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()

    override fun onCleared() {
        controller?.release()
        super.onCleared()
    }
}
