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

// File browser: folders, file rows (swipe rename/delete), import, search and push.

// ---------- file manager ----------
fun MainActivity.browse(repo: File, dir: File) {
    val rel = dir.relativeTo(repo).path
    val l = page(repo.name + (if (rel.isEmpty()) "" else "/$rel"), btn(getString(R.string.s_repos_e77ec8)) { repos() }, depthOf(repo, dir), pull = true)
    val pl = lastPull
    pl?.onRefresh = { h.postDelayed({ l.removeAllViews(); fillDir(l, repo, dir); pl?.finishRefresh() }, 350L) }
    fillDir(l, repo, dir)
}

// toolbar, parent link and file cards of one folder
fun MainActivity.fillDir(l: LinearLayout, repo: File, dir: File) {
    onBack = { if (dir != repo) browse(repo, dir.parentFile!!) else repos() }
    l.addView(bar(
        btn(getString(R.string.s_new_file_681e10)) { ask(getString(R.string.s_file_name_340826)) {
            if (it.isNotEmpty()) {
                val f = File(dir, it)
                f.parentFile?.mkdirs()
                f.createNewFile()
                edit(repo, f.parentFile!!, f)
            }
        } },
        btn(getString(R.string.s_new_folder_0d0a9c)) { ask(getString(R.string.s_folder_name_710417)) {
            if (it.isNotEmpty()) {
                val f = File(dir, it)
                f.mkdirs()
                browse(repo, f)
            }
        } },
        btn(getString(R.string.s_import_72d6d7)) { pickRepo = repo; pickDir = dir; importFile() },
        btn(getString(R.string.s_search_133484)) { ask(getString(R.string.s_search_files_61aef9)) { if (it.isNotEmpty()) search(repo, it) } },
        btn(getString(R.string.s_push_all_371255)) {
            dlg(getString(R.string.s_push_all_371255), tv(getString(R.string.s_all_existing_files_in_the_github_reposit_fc30c1), 15, false, R.color.muted), getString(R.string.s_push_9c6a9d)) { pushAll(repo) }
        }), lp(-1, -2).apply { bottomMargin = dp(12) })
    if (dir != repo) {
        val c = card().apply { setOnClickListener { browse(repo, dir.parentFile!!) } }
        c.addView(tv(getString(R.string.s_parent_folder_2adec3), 15, true, R.color.muted)); add(l, c)
    }
    dir.listFiles()!!.filter { !it.name.startsWith(".gab") }
        .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).forEach { f ->
            val c = card()
            var rowRef: SwipeRow? = null
            c.setOnClickListener {
                val rr = rowRef
                if (rr != null && rr.open) rr.close()
                else if (f.isDirectory) browse(repo, f)
                else edit(repo, dir, f)
            }
            val ext = if (f.isDirectory) "DIR" else f.extension.uppercase().take(4).ifEmpty { "FILE" }
            c.addView(tv(ext, 11, true, if (f.isDirectory) R.color.white else R.color.fg).apply {
                gravity = Gravity.CENTER; background = box(if (f.isDirectory) col(R.color.p1) else col(R.color.stroke), 0, 8)
            }, lp(dp(44), dp(36)).apply { rightMargin = dp(14) })
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            info.addView(tv(f.name, 15, true).apply { setSingleLine(); ellipsize = TextUtils.TruncateAt.MIDDLE })
            info.addView(tv(sz(f), 12, false, R.color.muted))
            c.addView(info, lp(0, -2, 1f))
            c.addView(dots { menu(repo, dir, f) })
            val row = SwipeRow(this, c, swipeActions(repo, dir, f) { rowRef?.close() }, dp(150).toFloat())
            rowRef = row
            add(l, row)
        }
}

// revealed when a file row is swiped left: rename and delete
fun MainActivity.swipeActions(repo: File, dir: File, f: File, close: () -> Unit): View {
    val v = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL or Gravity.END
        setPadding(0, 0, dp(6), 0)
    }
    v.addView(actIcon("\u270E", "Rename", R.color.p1) { close(); renameFile(repo, dir, f) })
    v.addView(actIcon("\uD83D\uDDD1", "Delete", R.color.bad) { close(); deleteFile(repo, dir, f) })
    return v
}

fun MainActivity.actIcon(icon: String, label: String, colorRes: Int, f: () -> Unit): LinearLayout {
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = box(col(colorRes), 0, 10)
        addView(tv(icon, 16, true, R.color.white).apply { gravity = Gravity.CENTER })
        addView(tv(label, 10, false, R.color.white).apply { gravity = Gravity.CENTER })
        layoutParams = lp(dp(64), -1, 0f, 0).apply { setMargins(dp(3), dp(8), dp(3), dp(8)) }
        setOnClickListener { f() }
    }
}

fun MainActivity.renameFile(repo: File, dir: File, f: File) {
    ask(getString(R.string.s_new_name_f97f85), f.name) { if (it.isNotEmpty()) { f.renameTo(File(dir, it)); browse(repo, dir) } }
}

fun MainActivity.deleteFile(repo: File, dir: File, f: File) {
    dlg(getString(R.string.s_delete_f2a6c4), tv("Delete " + f.name + "? This cannot be undone.", 15, false, R.color.muted), getString(R.string.s_delete_f2a6c4)) {
        f.deleteRecursively(); browse(repo, dir)
    }
}

fun MainActivity.menu(repo: File, dir: File, f: File) {
    menuDlg(f.name, listOf("Rename", "Delete", "Push this file", "Export to device")) { w ->
        when (w) {
            0 -> renameFile(repo, dir, f)
            1 -> deleteFile(repo, dir, f)
            2 -> if (f.isFile) pushOne(repo, f) else toast(getString(R.string.s_use_push_all_for_folders_e6c270))
            3 -> bg {
                val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                val nm = if (f.isFile && f.extension.isNotEmpty()) f.nameWithoutExtension + "_" + stamp + "." + f.extension else f.name + "_" + stamp
                val o = File(base, "Exports/" + nm)
                o.parentFile?.mkdirs()
                f.copyRecursively(o, true)
                ui { toast("Exported to " + o.path) }
            }
        }
    }
}

fun MainActivity.importFile() {
    startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), 7)
}
fun MainActivity.search(repo: File, q: String) {
    val l = page("Search: $q", btn(getString(R.string.s_back_0557fa)) { browse(repo, repo) }, depthOf(repo, repo) + 1)
    onBack = { browse(repo, repo) }
    l.addView(tv(getString(R.string.s_searching_1243da), 14, false, R.color.muted))
    bg {
        val res = ArrayList<Pair<File, String>>()
        for (f in files(repo)) {
            if (f.relativeTo(repo).path.contains(q, true)) res.add(f to "File name match")
            else if (f.length() < 300000) {
                val ln = try { f.readLines().indexOfFirst { it.contains(q, true) } } catch (e: Exception) { -1 }
                if (ln >= 0) res.add(f to "Line " + (ln + 1))
            }
            if (res.size >= 100) break
        }
        ui {
            l.removeAllViews()
            if (res.isEmpty()) l.addView(tv(getString(R.string.s_no_results_found_e576c2), 14, false, R.color.muted))
            for ((f, why) in res) {
                val c = card().apply { setOnClickListener { edit(repo, f.parentFile!!, f) } }
                val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                info.addView(tv(f.relativeTo(repo).path, 15, true)); info.addView(tv(why, 12, false, R.color.muted))
                c.addView(info); add(l, c)
            }
        }
    }
}
