package com.gitactionbuild

import android.animation.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.app.*
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.*
import android.text.style.ForegroundColorSpan
import android.util.Base64
import android.util.Log
import android.util.TypedValue
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.event.ContentChangeEvent
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.util.regex.RegexBackrefGrammar
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.component.Magnifier
import io.github.rosemoe.sora.widget.subscribeAlways
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlin.math.abs
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import org.json.*
import java.io.*
import java.net.*
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

// Repository list: GitHub repos, clone and open.

// ---------- repositories ----------
fun MainActivity.repos() {
    val l = page(getString(R.string.s_repositories_de1f88), btn(getString(R.string.s_token_459a6f)) { tokenScreen() }, 0, pull = true)
    val pl = lastPull
    pl?.onRefresh = { loadRepos(l, pl, false) }
    loadRepos(l, pl, true)
}

fun MainActivity.loadRepos(l: LinearLayout, pl: PullLayout?, first: Boolean) {
    if (first) l.addView(tv(getString(R.string.s_loading_repositories_217447), 14, false, R.color.muted))
    bg {
        try {
            val a = JSONArray(api("GET", "/user/repos?per_page=100&sort=updated"))
            ui {
                l.removeAllViews()
                for (i in 0 until a.length()) {
                    val j = a.getJSONObject(i); val name = j.getString("name"); val dir = File(base, name)
                    val c = card(); val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                    info.addView(tv(name, 17, true))
                    info.addView(tv((if (j.getBoolean("private")) "Private" else "Public") + "  |  " + j.getString("default_branch"), 13, false, R.color.muted))
                    c.addView(info, lp(0, -2, 1f))
                    var b: Button? = null
                    b = if (dir.exists()) btn(getString(R.string.s_open_c3bf44)) { browse(dir, dir) } else btn(getString(R.string.s_clone_ff2459)) {
                        // progress bar replaces the old "Cloning..." label
                        b?.text = pbar(0); b?.isEnabled = false; b?.typeface = Typeface.MONOSPACE
                        bg {
                            try {
                                val conn = http("GET", "/repos/" + j.getString("full_name") + "/zipball")
                                val total = conn.contentLengthLong
                                var lastP = -1
                                val src = CountIn(conn.inputStream) { got ->
                                    if (total > 0) {
                                        val p = (got * 100 / total).toInt()
                                        if (p != lastP) { lastP = p; ui { b?.text = pbar(p) } }
                                    }
                                }
                                unzip(src, dir)
                                File(dir, ".gab").writeText(j.getString("full_name") + "\n" + j.getString("default_branch"))
                                ui { browse(dir, dir) }
                            } catch (e: Exception) { dir.deleteRecursively(); ui { repos(); toast(getString(R.string.s_clone_failed_e76ad0)) } }
                        }
                    }
                    c.addView(b); add(l, c)
                }
                pl?.finishRefresh()
            }
        } catch (e: Exception) {
            ui {
                pl?.finishRefresh()
                if (first) l.removeAllViews()
                alert(getString(R.string.s_error_occurred_e31971), e.message ?: "Unknown error")
            }
        }
    }
}
