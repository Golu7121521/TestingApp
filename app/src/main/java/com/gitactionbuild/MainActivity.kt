package com.gitactionbuild

import android.animation.*
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
import android.util.TypedValue
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.event.ContentChangeEvent
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

class MainActivity : Activity() {
    val base = File("/storage/emulated/0/GitActonBuild")
    val sp by lazy { getSharedPreferences("g", 0) }
    val tok get() = sp.getString("t", "") ?: ""
    lateinit var root: LinearLayout
    val h = Handler(Looper.getMainLooper())
    var onBack: (() -> Unit)? = null
    var pickRepo: File? = null; var pickDir: File? = null
    var live = 0
    val logMap = ConcurrentHashMap<Long, String>()
    val fullLog = ConcurrentHashMap<Long, String>()
    val spinViews = ArrayList<TextView>()
    var curDepth = 0
    var lastPull: PullLayout? = null
    var bootBox: View? = null
    var matrixView: MatrixView? = null
    val jobState = HashMap<Long, String>()
    val stepSeen = HashSet<String>()
    var prog = 0
    val rnd = java.util.Random()

    var lastFind = ""
    var lastReplace = ""
    var lastDownloadedPath = ""

    // terminal palette
    val TB = Color.parseColor("#050A06")
    val TG = Color.parseColor("#33FF66")
    val TT = Color.parseColor("#9FE8B4")
    val TGL = Color.parseColor("#7FD99A")
    val TD = Color.parseColor("#3E7A4D")
    val TA = Color.parseColor("#FFC857")
    val TR = Color.parseColor("#FF5C5C")
    val TRL = Color.parseColor("#FF9C9C")
    val TLOG = Color.parseColor("#0B170E")
    val TC = Color.parseColor("#4DD0E1")   // cyan: job / names
    val TM = Color.parseColor("#C792EA")   // magenta: commit / branch / tags
    val TBL = Color.parseColor("#61AFEF")  // blue: paths / repo
    val TW = Color.parseColor("#E6FFEE")   // near-white: commands / running
    val PROMPT = arrayOf("root@gab" to TG, ":" to TW, "~" to TBL, "# " to TW)
    val typedSeen = HashSet<String>()
    val JCOL = intArrayOf(Color.parseColor("#4DD0E1"), Color.parseColor("#C792EA"), Color.parseColor("#FFB86C"),
        Color.parseColor("#FF79C6"), Color.parseColor("#61AFEF"), Color.parseColor("#B8E986"))
    val curViews = ArrayList<TextView>()
    var lastReleaseTag = ""
    var procView: TextView? = null

    // ---------- helpers ----------
    val isNight get() = (resources.configuration.uiMode and 0x30) == 0x20
    fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
    fun col(i: Int) = getColor(i)
    fun ui(f: () -> Unit) = runOnUiThread(f)
    fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    fun bars(dark: Boolean) {
        var fl = 0x100 or 0x400 or 0x200
        if (!isNight && !dark) fl = fl or 0x2000 or 0x10
        window.decorView.systemUiVisibility = fl
    }
    fun themeBg() { root.setBackgroundColor(col(R.color.bg)); bars(false) }

    fun depthOf(repo: File, dir: File): Int {
        val r = dir.relativeTo(repo).path
        return 2 + (if (r.isEmpty()) 0 else r.split("/").size * 2)
    }

    // slide animation between pages (forward = from right, back = from left)
    fun anim(d: Int) {
        val s = if (d >= curDepth) 1 else -1
        curDepth = d
        val w = resources.displayMetrics.widthPixels * 0.5f
        for (i in 0 until root.childCount) {
            val v = root.getChildAt(i)
            v.alpha = 0f; v.translationX = s * w
            v.animate().alpha(1f).translationX(0f).setDuration(300).setInterpolator(DecelerateInterpolator(1.6f)).start()
        }
    }

    fun press(v: View, s: Float = 0.94f) {
        v.setOnTouchListener { x, e ->
            val a = e.actionMasked
            if (a == MotionEvent.ACTION_DOWN) x.animate().scaleX(s).scaleY(s).setDuration(90).start()
            else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) x.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
            false
        }
    }

    fun bg(f: () -> Unit) = Thread {
        try { f() } catch (e: Exception) { ui { alert("Error Occurred", e.message ?: "Unknown error") } }
    }.start()

    fun lp(w: Int = -2, hh: Int = -2, wt: Float = 0f, m: Int = 0) = LinearLayout.LayoutParams(w, hh, wt).apply { setMargins(dp(m), dp(m), dp(m), dp(m)) }
    fun box(fill: Int, stroke: Int, r: Int) = GradientDrawable().apply { setColor(fill); setStroke(dp(1), stroke); cornerRadius = dp(r).toFloat() }

    // list items fade + slide up with a small stagger
    fun add(l: LinearLayout, v: View) {
        l.addView(v, lp(-1, -2).apply { bottomMargin = dp(8) })
        v.alpha = 0f; v.translationY = dp(24).toFloat()
        v.animate().alpha(1f).translationY(0f).setDuration(320)
            .setStartDelay(minOf(l.childCount, 10) * 40L)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    fun tv(t: String, s: Int = 14, bold: Boolean = false, c: Int = R.color.fg) = TextView(this).apply {
        text = t; textSize = s.toFloat(); setTextColor(col(c))
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }

    fun mono(t: String, s: Float = 12f, c: Int = TG) = TextView(this).apply {
        text = t; textSize = s; typeface = Typeface.MONOSPACE; setTextColor(c)
    }

    fun btn(t: String, f: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; textSize = 13f; setTextColor(-1)
        minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0; stateListAnimator = null
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = GradientDrawable().apply { setColor(Color.parseColor("#3B82F6")); cornerRadius = dp(8).toFloat() }
        layoutParams = lp(-2, -2, 0f, 0).apply { setMargins(dp(4), 0, dp(4), 0) }
        press(this)
        setOnClickListener { f() }
    }

    fun smallBtn(t: String, f: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; textSize = 11f; setTextColor(-1)
        minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0; stateListAnimator = null
        setPadding(dp(10), dp(5), dp(10), dp(5))
        background = GradientDrawable().apply { setColor(Color.parseColor("#3B82F6")); cornerRadius = dp(6).toFloat() }
        layoutParams = lp(-2, -2, 0f, 0).apply { setMargins(dp(2), 0, dp(2), 0) }
        press(this)
        setOnClickListener { f() }
    }

    fun dots(f: () -> Unit) = TextView(this).apply {
        text = "\u22EE"; textSize = 22f; setTextColor(col(R.color.muted)); gravity = Gravity.CENTER
        setPadding(dp(10), 0, dp(6), 0); setOnClickListener { f() }
    }

    fun card() = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; background = box(col(R.color.card), col(R.color.stroke), 12)
        setPadding(dp(14), dp(12), dp(10), dp(12))
        press(this, 0.98f)
    }

    fun bar(vararg v: View) = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(this@MainActivity).apply { v.forEach { addView(it) } })
    }

    fun flat(t: String, f: () -> Unit) = TextView(this).apply {
        text = t; textSize = 14f; setTextColor(col(R.color.muted)); typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        setPadding(dp(14), dp(8), dp(14), dp(8)); setOnClickListener { f() }
    }

    fun dlg(title: String, v: View?, ok: String?, cancel: String? = "Cancel", wide: Boolean = false, onOk: () -> Unit = {}): Dialog {
        val d = Dialog(this); d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val b = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(col(R.color.card)); cornerRadius = dp(16).toFloat(); setStroke(dp(1), col(R.color.stroke)) }
            setPadding(dp(24), dp(24), dp(24), dp(16))
        }
        b.addView(tv(title, 18, true).apply { setPadding(0, 0, 0, dp(12)) })
        if (v != null) b.addView(v, if (wide) lp(-1, 0, 1f) else lp(-1, -2))
        val r = LinearLayout(this).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; setPadding(0, dp(16), 0, 0) }
        if (cancel != null) r.addView(flat(cancel) { d.dismiss() })
        if (ok != null) r.addView(btn(ok) { d.dismiss(); onOk() })
        b.addView(r); d.setContentView(b)
        d.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0))
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val m = resources.displayMetrics
        d.window?.setLayout((m.widthPixels * 0.90).toInt(), if (wide) (m.heightPixels * 0.85).toInt() else -2)
        d.show()
        b.scaleX = 0.85f; b.scaleY = 0.85f; b.alpha = 0f
        b.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(240).setInterpolator(OvershootInterpolator(1.1f)).start()
        return d
    }

    fun alert(title: String, msg: String) = ui {
        dlg(title, tv(msg, 14, false, R.color.muted), "OK", null) {}
    }

    fun menuDlg(title: String, names: List<String>, pick: (Int) -> Unit) {
        var d: Dialog? = null
        val v = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(6), 0, dp(6)) }
        names.forEachIndexed { i, n ->
            v.addView(tv(n, 15, true).apply {
                setPadding(dp(6), dp(14), dp(6), dp(14))
                setOnClickListener { d?.dismiss(); pick(i) }
            }, lp(-1, -2))
        }
        d = dlg(title, v, null, "Close")
    }

    fun ask(t: String, init: String = "", f: (String) -> Unit) {
        val e = EditText(this).apply {
            setText(init); setSingleLine(); setTextColor(col(R.color.fg)); textSize = 15f
            setPadding(dp(14), dp(12), dp(14), dp(12)); background = box(col(R.color.bg), col(R.color.stroke), 8); requestFocus()
        }
        dlg(t, e, "OK") { f(e.text.toString().trim()) }
    }

    // pull = true wraps the list in a PullLayout (pull-to-refresh); the instance is kept in lastPull
    fun page(t: String, act: View? = null, d: Int = 0, pull: Boolean = false): LinearLayout {
        onBack = null; root.removeAllViews(); root.gravity = Gravity.TOP; themeBg()
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(12)) }
        top.addView(tv(t, 22, true).apply { setSingleLine(); ellipsize = TextUtils.TruncateAt.START }, lp(0, -2, 1f))
        if (act != null) top.addView(act)
        root.addView(top)
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), dp(16)) }
        if (pull) {
            val pl = PullLayout(this)
            pl.setContent(l)
            lastPull = pl
            root.addView(pl, lp(-1, 0, 1f))
        } else {
            lastPull = null
            root.addView(ScrollView(this).apply { addView(l) }, lp(-1, 0, 1f))
        }
        anim(d)
        return l
    }

    fun center(title: String, msg: String): LinearLayout {
        onBack = null; root.removeAllViews(); root.gravity = Gravity.CENTER; themeBg()
        val b = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            background = box(col(R.color.card), col(R.color.stroke), 16); setPadding(dp(24), dp(30), dp(24), dp(30))
        }
        b.addView(tv(title, 22, true))
        b.addView(tv(msg, 14, false, R.color.muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(24)) })
        root.addView(b, lp(-1, -2, 0f, 12))
        anim(0)
        return b
    }

    // ---------- effects ----------
    // short haptic tap; uses the modern API when available
    fun buzz(ms: Long) {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(ms)
        }
    }

    // left-right-left shake (two or three swings)
    fun shake(v: View) {
        val a = dp(12).toFloat(); val b = dp(7).toFloat(); val c = dp(3).toFloat()
        ObjectAnimator.ofFloat(v, View.TRANSLATION_X, 0f, -a, a, -b, b, -c, c, 0f).apply {
            duration = 520
            start()
        }
    }

    // infinite alpha pulse; phase is synced to the wall clock so views recreated on each poll keep the same beat
    fun pulse(v: View, period: Long, lo: Float) {
        val a = ObjectAnimator.ofFloat(v, View.ALPHA, 1f, lo)
        a.duration = period / 2
        a.repeatCount = ObjectAnimator.INFINITE
        a.repeatMode = ObjectAnimator.REVERSE
        a.interpolator = AccelerateDecelerateInterpolator()
        a.start()
        if (Build.VERSION.SDK_INT >= 26) a.currentPlayTime = System.currentTimeMillis() % period
        v.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(x: View) {}
            override fun onViewDetachedFromWindow(x: View) { a.cancel() }
        })
    }

    // status dot before a job: amber pulsing while running, green tick / red cross pop in on change
    fun statusMark(state: String, popIn: Boolean): TextView {
        val (glyph, color) = when (state) {
            "in_progress" -> "\u25CF" to TA
            "success" -> "\u2713" to TG
            "failure" -> "\u2717" to TR
            "queued" -> "\u25CB" to TD
            else -> "\u2013" to TD
        }
        val v = mono(glyph, 15f, color).apply { gravity = Gravity.CENTER }
        if (state == "in_progress") pulse(v, 1000L, 0.2f)
        else if (popIn) {
            v.scaleX = 0f; v.scaleY = 0f
            v.animate().scaleX(1f).scaleY(1f).setDuration(420).setInterpolator(OvershootInterpolator(2.4f)).start()
        }
        return v
    }

    // [FAIL] tag jumps around for a moment
    fun glitch(t: TextView) {
        val orig = t.text.toString()
        val junk = "#@%&*!?/\\|01"
        val dd = resources.displayMetrics.density
        var n = 0
        val r = object : Runnable {
            override fun run() {
                if (n >= 9) { t.text = orig; t.translationX = 0f; return }
                t.translationX = (rnd.nextInt(9) - 4) * dd
                t.text = orig.map { if (rnd.nextInt(3) == 0) junk[rnd.nextInt(junk.length)] else it }.joinToString("")
                n++
                h.postDelayed(this, 55L)
            }
        }
        h.post(r)
    }

    // small progress bar: [██████░░░░] 60%
    fun bar10(p: Int): String {
        val q = p.coerceIn(0, 100)
        val n = q / 10
        return "[" + "\u2588".repeat(n) + "\u2591".repeat(10 - n) + "] " + q + "%"
    }

    // clone progress: [#####-----] 50%
    fun pbar(p: Int): String {
        val q = p.coerceIn(0, 100)
        val n = q / 10
        return "[" + "#".repeat(n) + "-".repeat(10 - n) + "] " + q + "%"
    }

    fun stopMatrix() {
        val m = matrixView ?: return
        matrixView = null
        m.animate().alpha(0f).setDuration(900).withEndAction { m.stop() }.start()
    }

    // build failed: shake the log box and vibrate once
    fun finale(ok: Boolean, l: LinearLayout) {
        stopMatrix()
        if (!ok) {
            shake((l.parent as? View) ?: l)
            buzz(180)
        }
    }

    // bottom toast that slides up, waits, then slides away
    fun slideToast(parent: FrameLayout, msg: String) {
        val t = TextView(this).apply {
            text = msg; textSize = 13f; setTextColor(-1)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            background = GradientDrawable().apply { setColor(col(R.color.ok)); cornerRadius = dp(20).toFloat() }
            setPadding(dp(16), dp(9), dp(16), dp(9))
        }
        parent.addView(t, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(20) })
        val off = dp(90).toFloat()
        t.translationY = off; t.alpha = 0f
        t.animate().translationY(0f).alpha(1f).setDuration(260).setInterpolator(DecelerateInterpolator()).withEndAction {
            h.postDelayed({
                t.animate().translationY(off).alpha(0f).setDuration(260).withEndAction { parent.removeView(t) }.start()
            }, 1100L)
        }.start()
    }

    // ---------- lifecycle ----------
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        bars(false)
        window.statusBarColor = 0; window.navigationBarColor = 0
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(col(R.color.bg))
            setOnApplyWindowInsetsListener { v, i ->
                v.setPadding(0, i.systemWindowInsetTop, 0, i.systemWindowInsetBottom); i
            }
        }
        setContentView(root)
    }
    override fun onResume() { super.onResume(); if (root.childCount == 0 || !granted()) start() }
    override fun onBackPressed() { val b = onBack; if (b != null) b() else super.onBackPressed() }
    override fun onRequestPermissionsResult(c: Int, p: Array<String>, g: IntArray) { start() }

    fun granted() = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun start() {
        if (!granted()) {
            val b = center("Storage access", "Git Action Build needs storage access to create and manage the GitActonBuild folder on your device.")
            b.addView(btn("Allow access") {
                if (Build.VERSION.SDK_INT >= 30) startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
                else requestPermissions(arrayOf("android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.READ_EXTERNAL_STORAGE"), 1)
            })
        } else { base.mkdirs(); if (tok.isEmpty()) tokenScreen() else repos() }
    }

    fun tokenScreen() {
        val b = center("GitHub token", "Paste a personal access token with repo and workflow scopes.")
        val e = EditText(this).apply {
            hint = "Personal access token"; setSingleLine(); setTextColor(col(R.color.fg)); setHintTextColor(col(R.color.muted))
            textSize = 15f; setPadding(dp(14), dp(12), dp(14), dp(12)); background = box(col(R.color.bg), col(R.color.stroke), 8)
        }
        b.addView(e, lp(-1, -2).apply { bottomMargin = dp(16) })
        b.addView(btn("Continue") { sp.edit().putString("t", e.text.toString().trim()).apply(); repos() })
    }

    // ---------- repositories ----------
    fun repos() {
        val l = page("Repositories", btn("Token") { tokenScreen() }, 0, pull = true)
        val pl = lastPull
        pl?.onRefresh = { loadRepos(l, pl, false) }
        loadRepos(l, pl, true)
    }

    fun loadRepos(l: LinearLayout, pl: PullLayout?, first: Boolean) {
        if (first) l.addView(tv("Loading repositories...", 14, false, R.color.muted))
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
                        b = if (dir.exists()) btn("Open") { browse(dir, dir) } else btn("Clone") {
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
                                } catch (e: Exception) { dir.deleteRecursively(); ui { repos(); toast("Clone failed") } }
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
                    alert("Error Occurred", e.message ?: "Unknown error")
                }
            }
        }
    }

    // ---------- file manager ----------
    fun sz(f: File) = if (f.isDirectory) (f.listFiles()?.size ?: 0).toString() + " items" else if (f.length() < 1024) f.length().toString() + " B" else (f.length() / 1024).toString() + " KB"

    fun browse(repo: File, dir: File) {
        val rel = dir.relativeTo(repo).path
        val l = page(repo.name + (if (rel.isEmpty()) "" else "/$rel"), btn("Repos") { repos() }, depthOf(repo, dir), pull = true)
        val pl = lastPull
        pl?.onRefresh = { h.postDelayed({ l.removeAllViews(); fillDir(l, repo, dir); pl?.finishRefresh() }, 350L) }
        fillDir(l, repo, dir)
    }

    // toolbar, parent link and file cards of one folder
    fun fillDir(l: LinearLayout, repo: File, dir: File) {
        onBack = { if (dir != repo) browse(repo, dir.parentFile!!) else repos() }
        l.addView(bar(
            btn("New file") { ask("File name") {
                if (it.isNotEmpty()) {
                    val f = File(dir, it)
                    f.parentFile?.mkdirs()
                    f.createNewFile()
                    edit(repo, f.parentFile!!, f)
                }
            } },
            btn("New folder") { ask("Folder name") {
                if (it.isNotEmpty()) {
                    val f = File(dir, it)
                    f.mkdirs()
                    browse(repo, f)
                }
            } },
            btn("Import") { pickRepo = repo; pickDir = dir; importFile() },
            btn("Search") { ask("Search files") { if (it.isNotEmpty()) search(repo, it) } },
            btn("Push all") {
                dlg("Push all", tv("All existing files in the GitHub repository will be deleted first, then every local file will be pushed. Continue?", 15, false, R.color.muted), "Push") { pushAll(repo) }
            }), lp(-1, -2).apply { bottomMargin = dp(12) })
        if (dir != repo) {
            val c = card().apply { setOnClickListener { browse(repo, dir.parentFile!!) } }
            c.addView(tv("..  Parent folder", 15, true, R.color.muted)); add(l, c)
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
    fun swipeActions(repo: File, dir: File, f: File, close: () -> Unit): View {
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setPadding(0, 0, dp(6), 0)
        }
        v.addView(actIcon("\u270E", "Rename", R.color.p1) { close(); renameFile(repo, dir, f) })
        v.addView(actIcon("\uD83D\uDDD1", "Delete", R.color.bad) { close(); deleteFile(repo, dir, f) })
        return v
    }

    fun actIcon(icon: String, label: String, colorRes: Int, f: () -> Unit): LinearLayout {
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

    fun renameFile(repo: File, dir: File, f: File) {
        ask("New name", f.name) { if (it.isNotEmpty()) { f.renameTo(File(dir, it)); browse(repo, dir) } }
    }

    fun deleteFile(repo: File, dir: File, f: File) {
        dlg("Delete", tv("Delete " + f.name + "? This cannot be undone.", 15, false, R.color.muted), "Delete") {
            f.deleteRecursively(); browse(repo, dir)
        }
    }

    fun menu(repo: File, dir: File, f: File) {
        menuDlg(f.name, listOf("Rename", "Delete", "Push this file", "Export to device")) { w ->
            when (w) {
                0 -> renameFile(repo, dir, f)
                1 -> deleteFile(repo, dir, f)
                2 -> if (f.isFile) pushOne(repo, f) else toast("Use Push all for folders")
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

    fun importFile() {
        startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), 7)
    }
    override fun onActivityResult(rq: Int, rc: Int, d: Intent?) {
        super.onActivityResult(rq, rc, d)
        if (rq != 7 || rc != RESULT_OK || d == null) return
        val uris = ArrayList<Uri>()
        val cd = d.clipData
        if (cd != null) { for (i in 0 until cd.itemCount) uris.add(cd.getItemAt(i).uri) } else d.data?.let { uris.add(it) }
        bg {
            for (u in uris) {
                var n = "imported_" + System.currentTimeMillis()
                contentResolver.query(u, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) { val ix = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (ix >= 0) n = c.getString(ix) }
                }
                contentResolver.openInputStream(u)?.use { i -> File(pickDir, n).outputStream().use { i.copyTo(it) } }
            }
            ui { toast("Imported " + uris.size + " file(s)"); browse(pickRepo!!, pickDir!!) }
        }
    }

    fun search(repo: File, q: String) {
        val l = page("Search: $q", btn("Back") { browse(repo, repo) }, depthOf(repo, repo) + 1)
        onBack = { browse(repo, repo) }
        l.addView(tv("Searching...", 14, false, R.color.muted))
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
                if (res.isEmpty()) l.addView(tv("No results found", 14, false, R.color.muted))
                for ((f, why) in res) {
                    val c = card().apply { setOnClickListener { edit(repo, f.parentFile!!, f) } }
                    val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                    info.addView(tv(f.relativeTo(repo).path, 15, true)); info.addView(tv(why, 12, false, R.color.muted))
                    c.addView(info); add(l, c)
                }
            }
        }
    }

    // ---------- code editor (Sora Editor) ----------
    // Colored plain text for every file (keywords, strings, numbers, comments, functions, types)
    fun languageFor(f: File): Language = TextColorLanguage()

    fun makeEditor(f: File, text: String): CodeEditor {
        val dp = resources.displayMetrics.density
        val scheme = EditorColorScheme()
        scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, col(R.color.bg))
        scheme.setColor(EditorColorScheme.TEXT_NORMAL, col(R.color.fg))
        scheme.setColor(EditorColorScheme.LINE_NUMBER, col(R.color.muted))
        scheme.setColor(EditorColorScheme.LINE_NUMBER_PANEL, col(R.color.bg))
        scheme.setColor(EditorColorScheme.CURRENT_LINE, Color.argb(34, 59, 130, 246))
        scheme.setColor(EditorColorScheme.KEYWORD, col(R.color.kw))
        scheme.setColor(EditorColorScheme.LITERAL, col(R.color.st))
        scheme.setColor(EditorColorScheme.COMMENT, col(R.color.cm))
        scheme.setColor(EditorColorScheme.ATTRIBUTE_VALUE, col(R.color.nu))
        scheme.setColor(EditorColorScheme.FUNCTION_NAME, col(R.color.fn))
        scheme.setColor(EditorColorScheme.ANNOTATION, col(R.color.ty))

        val ed = CodeEditor(this)
        ed.setColorScheme(scheme)
        ed.setTypefaceText(Typeface.MONOSPACE)
        ed.setTextSizePx(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f, resources.displayMetrics))
        ed.setLineSpacing(2f, 1.1f)
        ed.setLineNumberEnabled(true)
        ed.setHighlightCurrentLine(true)      // current line background
        ed.setHighlightCurrentBlock(true)     // current code block
        ed.setBlockLineEnabled(true)          // code block lines
        ed.setHighlightBracketPair(true)      // matching bracket pairs
        ed.setWordwrap(false)
        ed.setScalable(true)                  // pinch zoom
        ed.setScaleTextSizes(8f * dp, 40f * dp)
        ed.setNonPrintablePaintingFlags(
            CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or CodeEditor.FLAG_DRAW_LINE_SEPARATOR or
                CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION or CodeEditor.FLAG_DRAW_SOFT_WRAP
        )
        ed.props.stickyScroll = true          // header lines of current block stay on top
        ed.props.autoIndent = true
        ed.getComponent(Magnifier::class.java).isEnabled = true
        ed.getComponent(EditorAutoCompletion::class.java).setEnabledAnimation(true)
        ed.setEditorLanguage(languageFor(f))
        ed.setText(text)
        return ed
    }

    fun edit(repo: File, dir: File, f: File) {
        if (f.length() > 400000) { alert("Error", "File is too large to edit"); return }
        val ed = makeEditor(f, f.readText())
        val frame = FrameLayout(this).apply { layoutParams = lp(-1, 0, 1f) }
        // green flash over the editor after save; the view does not take touches
        val flashView = View(this).apply { setBackgroundColor(col(R.color.ok)); alpha = 0f }
        frame.addView(ed, FrameLayout.LayoutParams(-1, -1))
        frame.addView(flashView, FrameLayout.LayoutParams(-1, -1))

        // Unsaved-changes dot on the title, driven by Sora's event system
        var dirty = false
        var title: TextView? = null
        ed.subscribeAlways<ContentChangeEvent> {
            if (!dirty) {
                dirty = true
                title?.text = f.name + " \u2022"
            }
        }

        fun flashSave() {
            flashView.alpha = 0.22f
            flashView.animate().alpha(0f).setDuration(900).start()
        }
        fun markClean() {
            dirty = false
            title?.text = f.name
        }
        fun save() {
            f.writeText(ed.text.toString())
            markClean()
            flashSave()
            slideToast(frame, "Saved")
        }
        // leaving the file (back) saves unsaved changes
        fun autoSave() {
            if (dirty) {
                f.writeText(ed.text.toString())
                markClean()
            }
        }

        // ---- search and replace: Sora's own searcher (undo-friendly, highlights matches) ----
        val searchOpts = EditorSearcher.SearchOptions(
            EditorSearcher.SearchOptions.TYPE_NORMAL, true, RegexBackrefGrammar.DEFAULT
        )
        fun searchNext() {
            try { ed.searcher.gotoNext() } catch (e: IllegalStateException) { e.printStackTrace() }
        }

        root.removeAllViews(); root.gravity = Gravity.TOP; themeBg()
        onBack = { autoSave(); ed.release(); browse(repo, dir) }

        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(4), dp(16), dp(12)) }
        val titleView = tv(f.name, 15, true).apply {
            setSingleLine(); ellipsize = TextUtils.TruncateAt.MIDDLE; setPadding(0, 0, dp(8), 0)
        }
        title = titleView
        top.addView(titleView, lp(0, -2, 1f))

        val searchBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = box(col(R.color.card), col(R.color.stroke), 12).apply { alpha = 180 }
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = FrameLayout.LayoutParams(-1, -2).apply { gravity = Gravity.TOP; setMargins(dp(10), dp(10), dp(10), dp(10)) }
            visibility = View.GONE
            elevation = dp(4).toFloat()
        }
        // scale from the top-right corner, where the Search button is
        searchBox.addOnLayoutChangeListener { v, l, _, r, _, _, _, _, _ ->
            v.pivotX = (r - l) * 0.9f
            v.pivotY = 0f
        }
        val fInput = EditText(this).apply {
            hint = "Find..."; setText(lastFind); textSize = 13f; setTextColor(col(R.color.fg))
            background = box(col(R.color.bg), col(R.color.stroke), 6).apply { alpha = 180 }
            setPadding(dp(10), dp(10), dp(10), dp(10)); layoutParams = lp(-1, -2).apply { bottomMargin = dp(6) }
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    lastFind = s?.toString() ?: ""
                    if (lastFind.isEmpty()) ed.searcher.stopSearch()
                    else ed.searcher.search(lastFind, searchOpts)
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        val rInput = EditText(this).apply {
            hint = "Replace with..."; setText(lastReplace); textSize = 13f; setTextColor(col(R.color.fg))
            background = box(col(R.color.bg), col(R.color.stroke), 6).apply { alpha = 180 }
            setPadding(dp(10), dp(10), dp(10), dp(10)); layoutParams = lp(-1, -2).apply { bottomMargin = dp(10) }
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { lastReplace = s?.toString() ?: "" }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        searchBox.addView(fInput); searchBox.addView(rInput)

        // floating search: fades and scales in / out
        var searchOn = false
        fun setSearch(show: Boolean) {
            if (show == searchOn) return
            searchOn = show
            searchBox.animate().cancel()
            if (show) {
                searchBox.visibility = View.VISIBLE
                searchBox.alpha = 0f; searchBox.scaleX = 0.9f; searchBox.scaleY = 0.9f
                searchBox.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200)
                    .setInterpolator(DecelerateInterpolator()).start()
            } else {
                searchBox.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(160)
                    .withEndAction {
                        searchBox.visibility = View.GONE
                        searchBox.alpha = 1f; searchBox.scaleX = 1f; searchBox.scaleY = 1f
                    }.start()
            }
        }
        val sBtnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        sBtnRow.addView(smallBtn("Next") { searchNext() })
        sBtnRow.addView(smallBtn("Replace") {
            try { ed.searcher.replaceCurrentMatch(rInput.text.toString()) } catch (e: IllegalStateException) { e.printStackTrace() }
        })
        sBtnRow.addView(smallBtn("All") {
            try { ed.searcher.replaceAll(rInput.text.toString()) } catch (e: IllegalStateException) { e.printStackTrace() }
        })
        sBtnRow.addView(smallBtn("Close") {
            ed.searcher.stopSearch()
            setSearch(false)
        })
        searchBox.addView(sBtnRow)

        val actBar = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val actRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actRow.addView(smallBtn("Save") { save() })
        actRow.addView(smallBtn("Search") { setSearch(!searchOn) })
        actRow.addView(smallBtn("Undo") { ed.undo() })
        actRow.addView(smallBtn("Redo") { ed.redo() })
        actRow.addView(smallBtn("Push") {
            save()
            ed.release()
            pushOne(repo, f)
        })
        actBar.addView(actRow)
        top.addView(actBar)
        root.addView(top)

        frame.addView(searchBox)
        root.addView(frame)
        anim(depthOf(repo, dir) + 1)
    }

    // ---------- GitHub API ----------
    fun http(m: String, u: String, body: String? = null, accept: String = "application/vnd.github+json"): HttpURLConnection {
        val c = URL(if (u.startsWith("http")) u else "https://api.github.com$u").openConnection() as HttpURLConnection
        c.requestMethod = m
        c.setRequestProperty("Authorization", "Bearer $tok")
        c.setRequestProperty("Accept", accept)
        if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
        if (c.responseCode >= 400) throw Exception(c.responseCode.toString() + " " + c.errorStream?.bufferedReader()?.readText())
        return c
    }
    fun api(m: String, u: String, body: String? = null) = http(m, u, body).inputStream.bufferedReader().readText()

    fun meta(repo: File): List<String> {
        val m = File(repo, ".gab")
        if (!m.exists()) {
            val a = JSONArray(api("GET", "/user/repos?per_page=100"))
            var found = false
            for (i in 0 until a.length()) {
                val j = a.getJSONObject(i)
                if (j.getString("name").equals(repo.name, ignoreCase = true)) {
                    m.writeText(j.getString("full_name") + "\n" + j.getString("default_branch"))
                    found = true
                    break
                }
            }
            if (!found) throw Exception("Repository not found on GitHub. Push feature requires a valid connected repository.")
        }
        return m.readLines()
    }

    fun files(repo: File) = repo.walkTopDown().filter { it.isFile && !it.relativeTo(repo).path.let { p -> p.startsWith(".gab") || p.startsWith(".git/") } }.toList()

    fun unzip(i: InputStream, out: File) {
        ZipInputStream(i).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val n = e.name.substringAfter('/')
                if (n.isNotEmpty()) {
                    val f = File(out, n)
                    if (e.isDirectory) f.mkdirs() else { f.parentFile?.mkdirs(); f.outputStream().use { z.copyTo(it) } }
                }
                e = z.nextEntry
            }
        }
    }

    // ---------- terminal screen helpers ----------
    fun scrollEnd(l: LinearLayout) { (l.parent as? ScrollView)?.post { (l.parent as ScrollView).fullScroll(View.FOCUS_DOWN) } }

    // build one colored line out of (text, color) pieces
    fun mspan(vararg p: Pair<String, Int>): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        for ((t, c) in p) { val s = sb.length; sb.append(t); sb.setSpan(ForegroundColorSpan(c), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        return sb
    }

    // typewriter effect: characters appear one by one with a block cursor at the end
    fun typeIn(t: TextView, full: CharSequence, step: Int = 2) {
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

    fun tmono(cs: CharSequence, size: Float = 13f) = mono("", size, TG).apply { setTextIsSelectable(true) }.also { typeIn(it, cs) }

    // colorize raw CI log lines by meaning
    fun colorLog(t: String): CharSequence {
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

    fun tbtn(t: String, c: Int, f: () -> Unit) = mono(t, 12f, c).apply {
        background = box(Color.parseColor("#0A140C"), c, 6)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        press(this, 0.96f)
        setOnClickListener { f() }
    }

    fun hideProc() = ui { procView?.visibility = View.GONE }

    // full-screen hacker terminal: typed lines, spinning [ - ] tags, "Progress" footer
    // matrix = true puts falling green characters behind the screen
    fun term(cmd: String, my: Int, matrix: Boolean = false, close: () -> Unit): LinearLayout {
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
    fun footerText(): CharSequence = mspan("Progress   " to TA, bar10(prog) to TG)

    // the boot sequence: fake connection lines, one every 330ms
    fun bootSeq(my: Int, box: LinearLayout, lines: List<String>, i: Int, then: () -> Unit) {
        if (my != live) return
        if (i >= lines.size) { then(); return }
        box.addView(tline("[ OK ]", TG, lines[i], TT))
        (box.parent as? LinearLayout)?.let { scrollEnd(it) }
        h.postDelayed({ bootSeq(my, box, lines, i + 1, then) }, 330L)
    }

    fun tline(tag: String, tc: Int, text: CharSequence, xc: Int, spin: Boolean = false): LinearLayout {
        val r = LinearLayout(this).apply { setPadding(0, dp(3), 0, dp(3)) }
        val t = mono(tag, 13f, tc).apply { setPadding(0, 0, dp(8), 0) }
        if (spin) spinViews.add(t)
        val x = mono("", 13f, xc).apply { setTextIsSelectable(true) }
        r.addView(t); r.addView(x, lp(0, -2, 1f))
        typeIn(x, text)
        return r
    }

    // add a spinning step line (safe to call from a background thread)
    fun tstep(l: LinearLayout, text: CharSequence): LinearLayout {
        var row: LinearLayout? = null
        val latch = java.util.concurrent.CountDownLatch(1)
        ui { val rw = tline("[ - ]", TA, text, TW, true); row = rw; l.addView(rw); scrollEnd(l); latch.countDown() }
        latch.await()
        return row!!
    }
    fun tset(row: LinearLayout, text: CharSequence) = ui { val x = row.getChildAt(1) as TextView; x.tag = "stop"; x.text = text }
    fun tdone(row: LinearLayout, ok: Boolean, text: CharSequence? = null) = ui {
        val t = row.getChildAt(0) as TextView; spinViews.remove(t)
        t.text = if (ok) "[ OK ]" else "[FAIL]"; t.setTextColor(if (ok) TG else TR)
        val x = row.getChildAt(1) as TextView
        x.tag = "stop"
        if (text != null) x.text = text
        x.setTextColor(if (ok) TT else TR)
    }
    fun tfail(l: LinearLayout, repo: File, msg: String) = ui {
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

    fun pushOne(repo: File, f: File) {
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

    fun pushAll(repo: File) {
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
    fun clean(t: String): String {
        val ansi = Regex("\u001B\\[[0-9;]*m"); val ts = Regex("^\\uFEFF?\\d{4}-\\d\\d-\\d\\dT[\\d:.]+Z ")
        return t.lines().joinToString("\n") { ts.replace(ansi.replace(it, ""), "") }
    }

    fun copyText(label: String, text: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText(label, text))
        toast("Copied: $label")
    }

    // removes everything except the boot-sequence box, which stays on screen
    fun clearKeep(l: LinearLayout) {
        for (i in l.childCount - 1 downTo 0) if (l.getChildAt(i) !== bootBox) l.removeViewAt(i)
    }

    fun draw(l: LinearLayout, r: JSONObject, js: JSONArray, artifactState: Int, full: String) {
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
                        toast("Fetching logs...")
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
    fun watch(full: String, sha: String, repo: File, l: LinearLayout, my: Int) {
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
                    if (my != live) return@bg
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
    fun download(full: String, r: JSONObject) {
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
}
