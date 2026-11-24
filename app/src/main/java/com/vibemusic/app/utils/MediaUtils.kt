package com.vibemusic.app.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.FileProvider
import com.vibemusic.app.domain.model.Song
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

object MediaUtils {

    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "0:00"
        val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.2f MB", mb)
        } else {
            String.format(Locale.US, "%.1f KB", kb)
        }
    }

    fun shareSong(context: Context, song: Song) {
        try {
            val file = File(song.filePath)
            val shareUri: Uri = if (file.exists()) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } else {
                song.uri
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = song.mimeType.ifBlank { "audio/*" }
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(intent, "Share ${song.title}").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to share file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun setAsRingtone(context: Context, song: Song) {
        if (!Settings.System.canWrite(context)) {
            Toast.makeText(
                context,
                "Please grant Modify System Settings permission to set ringtone",
                Toast.LENGTH_LONG
            ).show()
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        }

        try {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.IS_RINGTONE, true)
            }
            context.contentResolver.update(song.uri, values, null, null)
            RingtoneManager.setActualDefaultRingtoneUri(
                context,
                RingtoneManager.TYPE_RINGTONE,
                song.uri
            )
            Toast.makeText(context, "\"${song.title}\" set as Ringtone", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot set ringtone on this device: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun buildDeleteRequest(context: Context, song: Song): IntentSenderRequest? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pendingIntent = MediaStore.createDeleteRequest(
                context.contentResolver,
                listOf(song.uri)
            )
            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
        } else {
            null
        }
    }

    fun deleteLegacySong(context: Context, song: Song): Boolean {
        return try {
            val deleted = context.contentResolver.delete(song.uri, null, null)
            if (deleted > 0) true else File(song.filePath).delete()
        } catch (_: Exception) {
            false
        }
    }
}
