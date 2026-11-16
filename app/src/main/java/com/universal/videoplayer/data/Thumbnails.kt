package com.universal.videoplayer.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Best-effort thumbnail generation. Returns null for sources that cannot be sampled (e.g. HLS/DASH). */
object Thumbnails {
    suspend fun generate(context: Context, url: String, headers: Map<String, String>): String? =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "thumbs").apply { mkdirs() }
            val name = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
                .joinToString("") { "%02x".format(it) }.take(32)
            val file = File(dir, "$name.jpg")
            if (file.exists() && file.length() > 0) return@withContext file.absolutePath
            val r = MediaMetadataRetriever()
            try {
                if (url.startsWith("http")) r.setDataSource(url, headers) else r.setDataSource(url)
                val frame = r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: return@withContext null
                val w = 480
                val h = (frame.height * (w.toFloat() / frame.width)).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(frame, w, h, true)
                FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.JPEG, 80, it) }
                file.absolutePath
            } catch (e: Exception) {
                null
            } finally {
                runCatching { r.release() }
            }
        }
}
