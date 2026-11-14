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

// Terminal screen: push steps, build watch and the build log.

// ---------- terminal screen helpers ----------
fun MainActivity.scrollEnd(l: LinearLayout) { (l.parent as? ScrollView)?.post { (l.parent as ScrollView).fullScroll(View.FOCUS_DOWN) } }

// build one colored line out of (text, color) pieces
fun MainActivity.mspan(vararg p: Pair<String, Int>): SpannableStringBuilder {
    val sb = SpannableStringBuilder()
    for ((t, c) in p) { val s = sb.length; sb.append(t); sb.setSpan(ForegroundColorSpan(c), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    return sb
}

// typewriter effect: characters appear one by one with a block cursor at the end
fun MainActivity.typeIn(t: TextView, full: CharSequence, step: Int = 2) {
    if (!typedSeen.add(full.toString())) { t.text = full; return }
    val my = live
    var n = 0
    val r = object : Runnable {
        override fun run() {
            if (t.tag != null) return
            if (my != live) { t.text = full; return }
            n = minOf(n + step, full.length)
            if (n < full.length) {
                t.text = SpannableStringBuilder(full.subSequence(0, n)).append("\u2588")
                h.postDelayed(this, 22)
            } else t.text = full
        }
    }
    h.post(r)
}

fun MainActivity.tmono(cs: CharSequence, size: Float = 13f) = mono("", size, TG).apply { setTextIsSelectable(true) }.also { typeIn(it, cs) }

// colorize raw CI log lines by meaning
fun MainActivity.colorLog(t: String): CharSequence {
    val sb = SpannableStringBuilder()
    for (ln in t.lines()) {
        val c = when {
            ln.startsWith("e: ") || ln.contains("##[error]") || ln.contains("FAILED") || ln.contains("FAILURE") || ln.contains("Exception") -> TRL
            ln.startsWith("w: ") || ln.contains("##[warning]") || ln.contains("warning", true) -> TA
            ln.contains("##[group]") || ln.contains("##[endgroup]") -> TC
            ln.startsWith("> Task") -> TBL
            ln.contains("[command]") -> TM
            ln.contains("BUILD SUCCESSFUL") || ln.contains("successfully", true) -> TG
            ln.startsWith("Run ") || ln.startsWith("with:") -> TW
            else -> TGL
        }
        val s = sb.length; sb.append(ln).append('\n')
        sb.setSpan(ForegroundColorSpan(c), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return sb
}

fun MainActivity.tbtn(t: String, c: Int, f: () -> Unit) = mono(t, 12f, c).apply {
    background = box(Color.parseColor("#0A140C"), c, 6)
    setPadding(dp(12), dp(10), dp(12), dp(10))
    press(this, 0.96f)
    setOnClickListener { f() }
}

fun MainActivity.hideProc() = ui { procView?.visibility = View.GONE }

// full-screen hacker terminal: typed lines, spinning [ - ] tags, "Progress" footer
// matrix = true puts falling green characters behind the screen
fun MainActivity.term(cmd: String, my: Int, matrix: Boolean = false, close: () -> Unit): LinearLayout {
    onBack = close
    root.removeAllViews(); root.gravity = Gravity.TOP
    root.setBackgroundColor(TB); bars(true); spinViews.clear(); typedSeen.clear()
    matrixView = null; bootBox = null; prog = 0

    val colv = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val top = LinearLayout(this).apply { gravity = Gravity.END; setPadding(dp(16), dp(4), dp(16), dp(8)) }
    top.addView(mono("[x]", 15f, TR).apply { setPadding(dp(12), dp(4), dp(4), dp(4)); setOnClickListener { close() } })
    colv.addView(top)
    val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    colv.addView(ScrollView(this).apply { addView(l) }, lp(-1, 0, 1f))
    val pv = mono("", 13f, TA).apply { setPadding(dp(16), dp(10), dp(16), dp(6)) }
    procView = pv
    colv.addView(pv)

    val frame = FrameLayout(this)
    if (matrix) {
        val mv = MatrixView(this)
        matrixView = mv
        frame.addView(mv, FrameLayout.LayoutParams(-1, -1))
    }
    frame.addView(colv, FrameLayout.LayoutParams(-1, -1))
    root.addView(frame, lp(-1, 0, 1f))
    anim(99)

    val fr = "|/-\\"
    var tick = 0
    val spin = object : Runnable {
        override fun run() {
            if (my != live) return
            tick++
            val f = fr[(tick / 2) % 4]
            for (s in spinViews) s.text = "[ $f ]"
            h.postDelayed(this, 90)
        }
    }
    pv.text = footerText()
    h.post(spin)
    return l
}

// footer: "Progress" label and the bar, no animation
fun MainActivity.footerText(): CharSequence = mspan("Progress   " to TA, bar10(prog) to TG)

// the boot sequence: fake connection lines, one every 330ms
fun MainActivity.bootSeq(my: Int, box: LinearLayout, lines: List<String>, i: Int, then: () -> Unit) {
    if (my != live) return
    if (i >= lines.size) { then(); return }
    box.addView(tline("[ OK ]", TG, lines[i], TT))
    (box.parent as? LinearLayout)?.let { scrollEnd(it) }
    h.postDelayed({ bootSeq(my, box, lines, i + 1, then) }, 330L)
}

fun MainActivity.tline(tag: String, tc: Int, text: CharSequence, xc: Int, spin: Boolean = false): LinearLayout {
    val r = LinearLayout(this).apply { setPadding(0, dp(3), 0, dp(3)) }
    val t = mono(tag, 13f, tc).apply { setPadding(0, 0, dp(8), 0) }
    if (spin) spinViews.add(t)
    val x = mono("", 13f, xc).apply { setTextIsSelectable(true) }
    r.addView(t); r.addView(x, lp(0, -2, 1f))
    typeIn(x, text)
    return r
}

// add a spinning step line (safe to call from a background thread)
fun MainActivity.tstep(l: LinearLayout, text: CharSequence): LinearLayout {
    var row: LinearLayout? = null
    val latch = java.util.concurrent.CountDownLatch(1)
    ui { val rw = tline("[ - ]", TA, text, TW, true); row = rw; l.addView(rw); scrollEnd(l); latch.countDown() }
    latch.await()
    return row!!
}
fun MainActivity.tset(row: LinearLayout, text: CharSequence) = ui { val x = row.getChildAt(1) as TextView; x.tag = "stop"; x.text = text }
fun MainActivity.tdone(row: LinearLayout, ok: Boolean, text: CharSequence? = null) = ui {
    val t = row.getChildAt(0) as TextView; spinViews.remove(t)
    t.text = if (ok) "[ OK ]" else "[FAIL]"; t.setTextColor(if (ok) TG else TR)
    val x = row.getChildAt(1) as TextView
    x.tag = "stop"
    if (text != null) x.text = text
    x.setTextColor(if (ok) TT else TR)
}
fun MainActivity.tfail(l: LinearLayout, repo: File, msg: String) = ui {
    spinViews.clear()
    val fl = tline("[FAIL]", TR, "push aborted", TRL)
    l.addView(fl)
    glitch(fl.getChildAt(0) as TextView)
    l.addView(tmono(mspan("$ " to TR, "exit " to TW, "1" to TR, "  // push failed" to TD)).apply { setPadding(0, dp(10), 0, dp(4)) })
    l.addView(mono("", 11f, TRL).apply {
        setTextIsSelectable(true)
        text = colorLog(msg)
        background = box(TLOG, TD, 6); setPadding(dp(10), dp(8), dp(10), dp(8)); setHorizontallyScrolling(true)
    }, lp(-1, -2).apply { bottomMargin = dp(8) })
    val row = LinearLayout(this)
    val copyBtn = tbtn("[ copy error ]", TR) { copyText("error", msg) }
    row.addView(copyBtn, lp(-2, -2).apply { rightMargin = dp(8) })
    row.addView(tbtn("[ back ]", TG) { live++; browse(repo, repo) })
    l.addView(row, lp(-2, -2).apply { bottomMargin = dp(8) })
    pulse(copyBtn, 1100L, 0.4f)
    hideProc()
    scrollEnd(l)
    shake((l.parent as? View) ?: l)
    buzz(180)
}

fun MainActivity.pushOne(repo: File, f: File) {
    val my = ++live
    val l = term("git push origin main", my, matrix = true) { live++; browse(repo, repo) }
    val rel = f.relativeTo(repo).path

    bg {
        val r1 = tstep(l, "resolving repository...")
        try {
            val (full, br) = meta(repo); val p = rel.replace(" ", "%20")
            tdone(r1, true, mspan("repo: " to TD, full to TBL, "  " to TD, "[$br]" to TM))
            val r2 = tstep(l, mspan("checking remote file: " to TW, rel to TBL))
            val sha = try { JSONObject(api("GET", "/repos/$full/contents/$p?ref=$br")).getString("sha") } catch (e: Exception) { null }
            tdone(r2, true, if (sha != null) mspan("remote file exists, will update" to TA) else mspan("new file, will create" to TG))
            val r3 = tstep(l, mspan("uploading " to TW, rel to TBL))
            val j = JSONObject().put("message", "Update $p").put("branch", br).put("content", Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
            if (sha != null) j.put("sha", sha)
            val c = JSONObject(api("PUT", "/repos/$full/contents/$p", j.toString())).getJSONObject("commit").getString("sha")
            tdone(r3, true, mspan("pushed -> " to TD, c.take(7) to TM))
            if (my != live) return@bg
            Thread.sleep(500)
            ui { if (my == live) watch(full, c, repo, l, my) }
        } catch (e: Exception) {
            if (my == live) tfail(l, repo, e.message ?: "Unknown error")
        }
    }
}

fun MainActivity.pushAll(repo: File) {
    val my = ++live
    val l = term("git push --force origin main", my, matrix = true) { live++; browse(repo, repo) }

    bg {
        val r1 = tstep(l, "resolving repository...")
        try {
            val (full, br) = meta(repo)
            tdone(r1, true, mspan("repo: " to TD, full to TBL, "  " to TD, "[$br]" to TM))
            val r2 = tstep(l, "reading remote HEAD")
            val head = JSONObject(api("GET", "/repos/$full/git/ref/heads/$br")).getJSONObject("object").getString("sha")
            tdone(r2, true, mspan("HEAD " to TD, head.take(7) to TM))
            val all = files(repo)
            val tree = JSONArray()
            for ((n, f) in all.withIndex()) {
                if (my != live) return@bg
                val rel = f.relativeTo(repo).path
                val cnt = "(" + (n + 1) + "/" + all.size + ") "
                val row = tstep(l, mspan(cnt to TA, "uploading " to TW, rel to TBL))
                val b = JSONObject(api("POST", "/repos/$full/git/blobs", JSONObject()
                    .put("content", Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)).put("encoding", "base64").toString())).getString("sha")
                tree.put(JSONObject().put("path", rel).put("mode", "100644").put("type", "blob").put("sha", b))
                tdone(row, true, mspan(cnt to TA, rel to TBL, "  " + sz(f) to TD))
            }
            val r4 = tstep(l, "creating tree")
            val t = JSONObject(api("POST", "/repos/$full/git/trees", JSONObject().put("tree", tree).toString())).getString("sha")
            tdone(r4, true, mspan("tree " to TD, t.take(7) to TM))
            val r5 = tstep(l, "creating commit")
            val c = JSONObject(api("POST", "/repos/$full/git/commits", JSONObject().put("message", "Push all from Git Action Build")
                .put("tree", t).put("parents", JSONArray().put(head)).toString())).getString("sha")
            tdone(r5, true, mspan("commit " to TD, c.take(7) to TM))
            val r6 = tstep(l, "updating ref heads/$br")
            api("PATCH", "/repos/$full/git/refs/heads/$br", JSONObject().put("sha", c).put("force", true).toString())
            tdone(r6, true, mspan("ref updated" to TG))
            if (my != live) return@bg
            Thread.sleep(500)
            ui { if (my == live) watch(full, c, repo, l, my) }
        } catch (e: Exception) {
            if (my == live) tfail(l, repo, e.message ?: "Unknown error")
        }
    }
}

// ---------- Hacker terminal build monitor ----------
fun MainActivity.copyText(label: String, text: String) {
    (getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText(label, text))
    toast("Copied: $label")
}

// removes everything except the boot-sequence box, which stays on screen
fun MainActivity.clearKeep(l: LinearLayout) {
    for (i in l.childCount - 1 downTo 0) if (l.getChildAt(i) !== bootBox) l.removeViewAt(i)
}

fun MainActivity.draw(l: LinearLayout, r: JSONObject, js: JSONArray, artifactState: Int, full: String) {
    val sv = l.parent as? ScrollView; val y = sv?.scrollY ?: 0
    val atBottom = sv == null || (l.height - (sv.height + y)) < dp(80)
    clearKeep(l); spinViews.clear(); curViews.clear()

    l.addView(tmono(mspan("# " to TD, r.optString("name") to TC, "  run " to TD, "#" + r.optInt("run_number") to TA, "  " to TD, "[" + r.optString("head_branch") + "]" to TM), 12f).apply { setPadding(0, 0, 0, dp(6)) })

    // progress = completed steps / total steps
    var total = 0; var doneSteps = 0
    for (k in 0 until js.length()) {
        val sa = js.getJSONObject(k).optJSONArray("steps") ?: continue
        total += sa.length()
        for (q in 0 until sa.length()) if (sa.getJSONObject(q).getString("status") == "completed") doneSteps++
    }
    val finished = r.getString("status") == "completed"
    val pct = if (finished) 100 else minOf(99, if (total == 0) 0 else doneSteps * 100 / total)
    prog = maxOf(prog, pct)

    for (k in 0 until js.length()) {
        val j = js.getJSONObject(k)
        val id = j.getLong("id")
        val jname = j.getString("name")
        val jst = j.getString("status")
        val jc = j.optString("conclusion").let { if (it == "null") "" else it }
        val jcol = when {
            jc == "failure" -> TR
            jc == "success" -> TG
            jst == "in_progress" -> TA
            else -> TD
        }
        val state = when {
            jst == "in_progress" -> "in_progress"
            jc == "success" -> "success"
            jc == "failure" -> "failure"
            jst == "completed" -> "skip"
            else -> "queued"
        }
        val prev = jobState[id]
        jobState[id] = state

        val jcolr = JCOL[k % JCOL.size]
        val jl = mspan("$ " to TG, "job::" to TD, jname to jcolr)
        if (jc.isNotEmpty()) { jl.append(mspan("  => " to TD, jc.uppercase() to jcol)) }

        val jrow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, dp(4)) }
        jrow.addView(statusMark(state, prev != state), lp(dp(20), -2).apply { rightMargin = dp(6) })
        jrow.addView(tmono(jl), lp(0, -2, 1f))
        l.addView(jrow)

        val steps = j.optJSONArray("steps"); var shown = false
        if (steps != null) {
            for (q in 0 until steps.length()) {
                val st = steps.getJSONObject(q)
                val name = st.getString("name")
                val stStatus = st.getString("status")
                val stConcl = st.optString("conclusion").let { if (it == "null") "" else it }
                val isRunning = stStatus == "in_progress"
                val isFailed = stConcl == "failure"

                val row = when {
                    isRunning -> tline("[ - ]", TA, name, jcolr, true)
                    stStatus == "completed" -> when (stConcl) {
                        "success" -> tline("[ OK ]", TG, name, jcolr)
                        "failure" -> tline("[FAIL]", TR, name, TR)
                        else -> tline("[SKIP]", TD, name, TD)
                    }
                    else -> tline("[ .. ]", TD, name, TD)
                }
                l.addView(row)
                // glitch each failed step once, when it first shows up as failed
                if (isFailed && stepSeen.add("$id/$q")) glitch(row.getChildAt(0) as TextView)

                if ((isRunning || isFailed) && !shown) {
                    val lg = logMap[id]
                    if (!lg.isNullOrEmpty()) {
                        shown = true
                        l.addView(mono("", 10.5f, TGL).apply {
                            setTextIsSelectable(true)
                            text = colorLog(lg)
                            background = box(TLOG, jcolr, 6)
                            setPadding(dp(10), dp(8), dp(10), dp(8))
                            setHorizontallyScrolling(true)
                        }, lp(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(6) })
                    }
                }
            }
        }

        // copy button only for failed jobs; it pulses red so it is easy to spot
        if (jc == "failure") {
            val cb = tbtn("[ copy logs of failed job: $jname ]", TR) {
                val cached = fullLog[id]
                if (cached != null) copyText("job $jname", "### job: $jname (failed)\n$cached")
                else {
                    toast(getString(R.string.s_fetching_logs_d3dd49))
                    bg {
                        val cl = clean(String(http("GET", "/repos/$full/actions/jobs/$id/logs").inputStream.readBytes()))
                        fullLog[id] = cl
                        ui { copyText("job $jname", "### job: $jname (failed)\n$cl") }
                    }
                }
            }
            l.addView(cb, lp(-2, -2).apply { topMargin = dp(8); bottomMargin = dp(6) })
            pulse(cb, 1100L, 0.4f)
        }
    }

    if (artifactState > 0) {
        l.addView(tmono(mspan("$ " to TG, "releases" to TC)).apply { setPadding(0, dp(10), 0, dp(4)) })
        when (artifactState) {
            1 -> l.addView(tline("[ - ]", TA, "downloading apk from release...", TW, true))
            2 -> {
                l.addView(tline("[ OK ]", TG, mspan("apk published " to TT, "-> " to TD, lastReleaseTag to TM), TT))
                if (lastDownloadedPath.isNotEmpty()) l.addView(tmono(mspan("  saved: " to TD, lastDownloadedPath to TBL), 11f).apply { setHorizontallyScrolling(true) })
            }
            else -> l.addView(tline("[FAIL]", TR, "apk download failed", TR))
        }
    }

    if (finished) {
        val ok = r.optString("conclusion") == "success"
        l.addView(tmono(
            if (ok) mspan("$ " to TG, "exit " to TW, "0" to TG, "  // build passed" to TD)
            else mspan("$ " to TR, "exit " to TW, "1" to TR, "  // build failed" to TD)
        ).apply { setPadding(0, dp(12), 0, dp(0)) })
    }

    procView?.text = footerText()
    sv?.post { if (atBottom) sv.fullScroll(View.FOCUS_DOWN) else sv.scrollTo(0, y) }
}

// continues on the same screen as the push: the upload log is kept in one box above the build log
fun MainActivity.watch(full: String, sha: String, repo: File, l: LinearLayout, my: Int) {
    logMap.clear(); fullLog.clear(); jobState.clear(); stepSeen.clear()
    var artifactState = 0

    val history = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    while (l.childCount > 0) { val v = l.getChildAt(0); l.removeViewAt(0); history.addView(v) }
    l.addView(history)
    bootBox = history

    // fake boot lines first, then the real polling starts
    val boot = history
    val w = tline("[ - ]", TA, "waiting for workflow run to start...", TW, true)
    bootSeq(my, boot, listOf(
        "connecting to github...",
        "authenticating token...",
        "repo: $full",
        "attaching build log stream..."
    ), 0) {
        l.addView(w)
        bg {
            for (i in 0 until 600) {
                if (my != live || isDestroyed) return@bg
                Thread.sleep(3000)
                val rs = try { JSONObject(api("GET", "/repos/$full/actions/runs?head_sha=$sha")).getJSONArray("workflow_runs") } catch (e: Exception) { continue }
                if (rs.length() == 0) continue
                val r = rs.getJSONObject(0)
                val js = try { JSONObject(api("GET", r.getString("url") + "/jobs")).getJSONArray("jobs") } catch (e: Exception) { continue }

                for (k in 0 until js.length()) {
                    val j = js.getJSONObject(k)
                    val jid = j.getLong("id")
                    val st = j.getString("status")
                    val c = j.optString("conclusion")
                    val failedDone = st == "completed" && c == "failure"
                    if (st == "in_progress" || failedDone) {
                        if (failedDone && fullLog.containsKey(jid)) continue
                        try {
                            val cl = clean(String(http("GET", "/repos/$full/actions/jobs/$jid/logs").inputStream.readBytes()))
                            fullLog[jid] = cl
                            logMap[jid] = cl.lines().takeLast(80).joinToString("\n")
                        } catch (e: Exception) { }
                    }
                }

                val done = r.getString("status") == "completed"
                val success = r.optString("conclusion") == "success"

                if (done && success && artifactState == 0) artifactState = 1

                ui { if (my == live) { if (w.parent == l) l.removeView(w); draw(l, r, js, artifactState, full) } }

                if (done) {
                    // celebration (pass) or shake (fail), once, when the run finishes
                    ui { if (my == live) finale(success, l) }
                    notifyBuild(full, success)
                    if (success) {
                        try {
                            download(full, r)
                            artifactState = 2
                        } catch (e: Exception) {
                            artifactState = 3
                        }
                        ui { if (my == live) draw(l, r, js, artifactState, full) }
                    }
                    hideProc()
                    return@bg
                }
            }
            hideProc()
            ui {
                if (my == live) {
                    stopMatrix()
                    l.addView(tmono(mspan("$ " to TA, "timeout" to TA, "  // stopped watching after 30 min" to TD), 12f))
                }
            }
        }
    }
}

// APK is published to GitHub Releases by the workflow (tag: build-<run_number>)
fun MainActivity.download(full: String, r: JSONObject) {
    val n = r.optInt("run_number")
    val tag = "build-$n"
    val rel = JSONObject(api("GET", "/repos/$full/releases/tags/$tag"))
    val assets = rel.getJSONArray("assets")
    if (assets.length() == 0) throw Exception("No APK in release $tag")
    val x = assets.getJSONObject(0)
    val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())

    val out = File(base, "artifacts/" + x.getString("name").removeSuffix(".apk") + "_run" + n + "_" + stamp + ".apk")
    out.parentFile?.mkdirs()

    http("GET", "/repos/$full/releases/assets/" + x.getLong("id"), null, "application/octet-stream")
        .inputStream.use { i -> out.outputStream().use { i.copyTo(it) } }
    lastDownloadedPath = out.absolutePath
    lastReleaseTag = tag
    ui { toast("APK downloaded from release $tag") }
}
