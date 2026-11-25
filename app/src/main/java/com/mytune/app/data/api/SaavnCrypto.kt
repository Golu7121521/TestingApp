package com.mytune.app.data.api

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Decrypts "encrypted_media_url" values found in raw JioSaavn responses (DES/ECB, well-known key).
 * Used by SongDto.toSong() when the response has no ready-made download URLs.
 */
object SaavnCrypto {
    private const val KEY = "38346591"

    fun decryptUrl(encrypted: String): String? = try {
        val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(KEY.toByteArray(), "DES"))
        String(cipher.doFinal(Base64.decode(encrypted, Base64.DEFAULT)))
    } catch (e: Exception) {
        null
    }
}
