package com.desiflix.hindi_movie_stream

import android.os.Handler
import android.os.Looper
import androidx.annotation.NonNull
import com.frostwire.jlibtorrent.AlertListener
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.TorrentHandle
import com.frostwire.jlibtorrent.alerts.Alert
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.viplayer.torrent_bridge"
    private var sessionManager: SessionManager? = null
    private var currentHandle: TorrentHandle? = null

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "startBuffering" -> {
                    val magnetUri = call.argument<String>("magnetUri")
                    if (magnetUri.isNullOrEmpty()) {
                        result.error("INVALID_URI", "Magnet URI cannot be null or empty", null)
                        return@setMethodCallHandler
                    }

                    Thread {
                        try {
                            val filePath = startSequentialTorrent(magnetUri)
                            Handler(Looper.getMainLooper()).post {
                                if (filePath != null) {
                                    result.success(filePath)
                                } else {
                                    result.error("BUFFER_TIMEOUT", "Failed to buffer initial pieces in time", null)
                                }
                            }
                        } catch (e: Exception) {
                            Handler(Looper.getMainLooper()).post {
                                result.error("TORRENT_ERROR", e.localizedMessage, null)
                            }
                        }
                    }.start()
                }

                "stopTorrent" -> {
                    stopCurrentSession()
                    result.success(true)
                }

                else -> result.notImplemented()
            }
        }
    }

    private fun startSequentialTorrent(magnetUri: String): String? {
        val saveDir = File(context.cacheDir, "stream_cache")
        if (!saveDir.exists()) saveDir.mkdirs()

        if (sessionManager == null) {
            sessionManager = SessionManager()
            sessionManager?.start()
        }

        val session = sessionManager ?: return null
        currentHandle = null

        // Empty dummy listener to satisfy jlibtorrent requirements without any casting
        session.addListener(object : AlertListener {
            override fun types(): IntArray? = null
            override fun alert(alert: Alert<*>) {}
        })

        // Start torrent session download
        session.download(magnetUri, saveDir)

        // Polling loop to find the valid torrent handle from cache/downloads
        var waited = 0
        var targetFile: File? = null

        while (waited < 35) {
            Thread.sleep(1000)
            waited++

            try {
                val activeDownloads = session.downloads()
                if (activeDownloads != null && activeDownloads.isNotEmpty()) {
                    for (dl in activeDownloads) {
                        if (dl.handle != null && dl.handle.isValid) {
                            currentHandle = dl.handle
                            break
                        }
                    }
                }
            } catch (_: Exception) {}

            val handle = currentHandle
            if (handle != null && handle.isValid) {
                val status = handle.status()
                val ti = handle.torrentFile()

                if (ti != null && targetFile == null) {
                    var largestSize = 0L
                    var bestPath = ""
                    for (i in 0 until ti.numFiles()) {
                        val size = ti.files().fileSize(i)
                        if (size > largestSize) {
                            largestSize = size
                            bestPath = ti.files().filePath(i)
                        }
                    }
                    if (bestPath.isNotEmpty()) {
                        targetFile = File(saveDir, bestPath)
                    }
                }

                if (status.totalDone() >= 10 * 1024 * 1024L && targetFile != null && targetFile!!.exists()) {
                    break
                }
            }
        }

        return targetFile?.absolutePath
    }

    private fun stopCurrentSession() {
        try {
            currentHandle?.let {
                if (it.isValid) {
                    sessionManager?.remove(it)
                }
            }
            currentHandle = null
            sessionManager?.stop()
            sessionManager = null
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        stopCurrentSession()
        super.onDestroy()
    }
}
