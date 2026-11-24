package com.personal.saavntune.util

import android.text.Html
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object SaavnDecryptor {
    private const val DES_KEY = "38346591"

    fun decryptMediaUrl(encryptedUrl: String?, highQuality: Boolean = true): String? {
        if (encryptedUrl.isNullOrBlank()) return null
        return try {
            val keySpec = SecretKeySpec(DES_KEY.toByteArray(Charsets.UTF_8), "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val decodedBytes = Base64.decode(encryptedUrl, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            var url = String(decryptedBytes, Charsets.UTF_8)
            if (highQuality) {
                url = url
                    .replace("_96.mp4", "_320.mp4")
                    .replace("_160.mp4", "_320.mp4")
            } else {
                url = url.replace("_96.mp4", "_160.mp4")
            }
            url.replace("http://", "https://")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getHighResArtwork(imageUrl: String?): String {
        if (imageUrl.isNullOrBlank()) return ""
        return imageUrl
            .replace("50x50", "500x500")
            .replace("150x150", "500x500")
            .replace("http://", "https://")
    }

    fun cleanHtml(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString().trim()
    }

    fun formatDuration(secondsStr: String?): String {
        val totalSec = secondsStr?.toIntOrNull() ?: return "0:00"
        val mins = totalSec / 60
        val secs = totalSec % 60
        return "%d:%02d".format(mins, secs)
    }

    fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._ -]"), "_").trim().take(80)
    }
}
