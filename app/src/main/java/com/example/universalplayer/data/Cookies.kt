package com.example.universalplayer.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.webkit.CookieManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class LoginSite(val name: String, val url: String, val domain: String)

val LOGIN_SITES = listOf(
    LoginSite("Instagram", "https://www.instagram.com/accounts/login/", "instagram.com"),
    LoginSite("Facebook", "https://m.facebook.com/login", "facebook.com"),
    LoginSite("X (Twitter)", "https://x.com/i/flow/login", "x.com")
)

/** Stores cookies of sites the user signed in to (used by yt-dlp for age-restricted / private videos). */
object CookieVault {
    private var prefs: SharedPreferences? = null
    private var appCtx: Context? = null
    private val _domains = MutableStateFlow<Set<String>>(emptySet())
    val domains: StateFlow<Set<String>> = _domains.asStateFlow()

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        prefs = ctx.getSharedPreferences("site_cookies", Context.MODE_PRIVATE)
        _domains.value = prefs?.all?.keys?.toSet() ?: emptySet()
    }

    fun rootDomain(host: String): String =
        host.lowercase().removePrefix("www.").split('.').takeLast(2).joinToString(".")

    fun save(domain: String, cookie: String) {
        prefs?.edit()?.putString(domain, cookie)?.apply()
        _domains.value = _domains.value + domain
    }

    fun remove(domain: String) {
        prefs?.edit()?.remove(domain)?.apply()
        _domains.value = _domains.value - domain
        runCatching { CookieManager.getInstance().removeAllCookies(null) }
    }

    fun forUrl(url: String): String? {
        val host = runCatching { Uri.parse(url).host }.getOrNull() ?: return null
        return prefs?.getString(rootDomain(host), null)
    }

    /** Writes a Netscape cookie file (what yt-dlp --cookies expects) and returns it. */
    fun writeFile(url: String, cookie: String): File? {
        val ctx = appCtx ?: return null
        val host = runCatching { Uri.parse(url).host }.getOrNull() ?: return null
        val domain = rootDomain(host)
        val text = if (cookie.contains('\t') || cookie.startsWith("# Netscape")) cookie else {
            val sb = StringBuilder("# Netscape HTTP Cookie File\n")
            cookie.split(';').map { it.trim() }.filter { it.contains('=') }.forEach {
                sb.append(".$domain\tTRUE\t/\tTRUE\t2147483647\t${it.substringBefore('=')}\t${it.substringAfter('=')}\n")
            }
            sb.toString()
        }
        return runCatching { File(ctx.cacheDir, "cookies.txt").also { it.writeText(text) } }.getOrNull()
    }
}
