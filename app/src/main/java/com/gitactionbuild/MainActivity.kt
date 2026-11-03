package com.gitactionbuild

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
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import org.json.*
import java.io.*
import java.net.*
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

class Hl(c: Int) : ForegroundColorSpan(c)

class CodeView(c: Context) : EditText(c) {
    val gp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textAlign = Paint.Align.RIGHT }
    val lnp = Paint().apply { strokeWidth = 2f }
    val bgp = Paint().apply { style = Paint.Style.FILL }
    var gw = 0f; var dg = 0; var gc = 0
    val d = resources.displayMetrics.density
    var acc = 1f; var multi = false
    var lineNo = IntArray(0); var lastL: Layout? = null

    init {
        setHorizontallyScrolling(true)
        setBackgroundColor(Color.TRANSPARENT)
        setPadding(0, dp(8), dp(8), dp(8))
    }

    fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    val sd = ScaleGestureDetector(c, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(s: ScaleGestureDetector): Boolean {
            acc *= s.scaleFactor
            if (acc > 1.25f || acc < 0.75f) {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, (textSize * acc).coerceIn(8 * d, 40 * d))
                acc = 1f; pad(true)
            }
            return true
        }
    })

    fun pad(force: Boolean = false) {
        val digits = (text.count { it == '\n' } + 1).toString().length.coerceAtLeast(3)
        if (!force && digits == dg) return
        dg = digits; gp.textSize = textSize * 0.85f
        gw = gp.measureText("9".repeat(digits)) + 16 * d
        setPadding((gw + 8 * d).toInt(), dp(12), dp(12), dp(12))
    }

    override fun onDraw(cv: Canvas) {
        bgp.color = gc and 0x11FFFFFF
        cv.drawRect(scrollX.toFloat(), 0f, scrollX + gw + 4 * d, height.toFloat() + scrollY.toFloat(), bgp)

        val l = layout
        if (l != null) {
            if (lastL !== l || lineNo.size != l.lineCount) {
                lastL = l; val t = text; val a = IntArray(l.lineCount); var n = 1
                for (i in 0 until l.lineCount) { if (i > 0 && t[l.getLineStart(i) - 1] == '\n') n++; a[i] = n }
                lineNo = a
            }
            gp.color = gc; lnp.color = gc and 0x44FFFFFF
            val clip = cv.clipBounds; val pt = compoundPaddingTop
            val f = l.getLineForVertical((clip.top - pt).coerceAtLeast(0)); val e = l.getLineForVertical((clip.bottom - pt).coerceAtLeast(0))
            for (i in f..e) {
                if (i == 0 || text[l.getLineStart(i) - 1] == '\n') {
                    cv.drawText(lineNo[i].toString(), scrollX + gw - 6 * d, l.getLineBaseline(i) + pt.toFloat(), gp)
                }
            }
            cv.drawLine(scrollX + gw, clip.top.toFloat(), scrollX + gw, clip.bottom.toFloat(), lnp)
        }
        super.onDraw(cv)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        sd.onTouchEvent(e)
        if (e.actionMasked == MotionEvent.ACTION_DOWN) { multi = false; acc = 1f }
        if (e.pointerCount > 1) {
            if (!multi) {
                multi = true
                val c = MotionEvent.obtain(e); c.action = MotionEvent.ACTION_CANCEL; super.onTouchEvent(c); c.recycle()
                clearFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(windowToken, 0)
            }
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }
        if (multi && e.actionMasked == MotionEvent.ACTION_UP) { multi = false; return true }
        return super.onTouchEvent(e)
    }
}

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

    val KW = Regex("\\b(class|fun|val|var|def|function|return|if|else|for|while|import|package|public|private|protected|static|void|int|new|true|false|null|const|let|async|await|try|catch|from|in|is|when|override|this|echo|run|break|continue)\\b")
    val NUM = Regex("\\b\\d+(\\.\\d+)?\\b")
    val STR = Regex("\"[^\"\\n]*\"|'[^'\\n]*'")
    val CMT = Regex("//.*|/\\*[\\s\\S]*?\\*/|#.*")
    val TY = Regex("\\b[A-Z][a-zA-Z0-9_]*\\b")
    val FN = Regex("\\b[a-z_][a-zA-Z0-9_]*(?=\\s*\\()")

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

    fun page(t: String, act: View? = null, d: Int = 0): LinearLayout {
        onBack = null; root.removeAllViews(); root.gravity = Gravity.TOP; themeBg()
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, dp(12)) }
        top.addView(tv(t, 22, true).apply { setSingleLine(); ellipsize = TextUtils.TruncateAt.START }, lp(0, -2, 1f))
        if (act != null) top.addView(act)
        root.addView(top)
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(l) }, lp(-1, 0, 1f))
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

    // ---------- lifecycle ----------
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        bars(false)
        window.statusBarColor = 0; window.navigationBarColor = 0
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(col(R.color.bg))
            setOnApplyWindowInsetsListener { v, i ->
                v.setPadding(dp(16), i.systemWindowInsetTop + dp(12), dp(16), i.systemWindowInsetBottom + dp(12)); i
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
        val l = page("Repositories", btn("Token") { tokenScreen() }, 0)
        val wait = tv("Loading repositories...", 14, false, R.color.muted); l.addView(wait)
        bg {
            val a = JSONArray(api("GET", "/user/repos?per_page=100&sort=updated"))
            ui {
                l.removeView(wait)
                for (i in 0 until a.length()) {
                    val j = a.getJSONObject(i); val name = j.getString("name"); val dir = File(base, name)
                    val c = card(); val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                    info.addView(tv(name, 17, true))
                    info.addView(tv((if (j.getBoolean("private")) "Private" else "Public") + "  |  " + j.getString("default_branch"), 13, false, R.color.muted))
                    c.addView(info, lp(0, -2, 1f))
                    var b: Button? = null
                    b = if (dir.exists()) btn("Open") { browse(dir, dir) } else btn("Clone") {
                        b?.text = "Cloning..."; b?.isEnabled = false
                        bg {
                            try {
                                unzip(http("GET", "/repos/" + j.getString("full_name") + "/zipball").inputStream, dir)
                                File(dir, ".gab").writeText(j.getString("full_name") + "\n" + j.getString("default_branch"))
                                ui { browse(dir, dir) }
                            } catch (e: Exception) { dir.deleteRecursively(); ui { repos(); toast("Clone failed") } }
                        }
                    }
                    c.addView(b); add(l, c)
                }
            }
        }
    }

    // ---------- file manager ----------
    fun sz(f: File) = if (f.isDirectory) (f.listFiles()?.size ?: 0).toString() + " items" else if (f.length() < 1024) f.length().toString() + " B" else (f.length() / 1024).toString() + " KB"

    fun browse(repo: File, dir: File) {
        val rel = dir.relativeTo(repo).path
        val l = page(repo.name + (if (rel.isEmpty()) "" else "/$rel"), btn("Repos") { repos() }, depthOf(repo, dir))
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
                val c = card().apply { setOnClickListener { if (f.isDirectory) browse(repo, f) else edit(repo, dir, f) } }
                val ext = if (f.isDirectory) "DIR" else f.extension.uppercase().take(4).ifEmpty { "FILE" }
                c.addView(tv(ext, 11, true, if (f.isDirectory) R.color.white else R.color.fg).apply {
                    gravity = Gravity.CENTER; background = box(if (f.isDirectory) col(R.color.p1) else col(R.color.stroke), 0, 8)
                }, lp(dp(44), dp(36)).apply { rightMargin = dp(14) })
                val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                info.addView(tv(f.name, 15, true).apply { setSingleLine(); ellipsize = TextUtils.TruncateAt.MIDDLE })
                info.addView(tv(sz(f), 12, false, R.color.muted))
                c.addView(info, lp(0, -2, 1f)); c.addView(dots { menu(repo, dir, f) }); add(l, c)
            }
    }

    fun menu(repo: File, dir: File, f: File) {
        menuDlg(f.name, listOf("Rename", "Delete", "Push this file", "Export to device")) { w ->
            when (w) {
                0 -> ask("New name", f.name) { if (it.isNotEmpty()) { f.renameTo(File(dir, it)); browse(repo, dir) } }
                1 -> dlg("Delete", tv("Delete " + f.name + "? This cannot be undone.", 15, false, R.color.muted), "Delete") { f.deleteRecursively(); browse(repo, dir) }
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

    // ---------- code editor ----------
    fun hl(e: Editable) {
        for (s in e.getSpans(0, e.length, Hl::class.java)) e.removeSpan(s)
        if (e.length > 200000) return
        val t = e.toString()
        fun mark(r: Regex, c: Int) { for (m in r.findAll(t)) e.setSpan(Hl(c), m.range.first, m.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }

        mark(FN, col(R.color.fn))
        mark(TY, col(R.color.ty))
        mark(KW, col(R.color.kw))
        mark(NUM, col(R.color.nu))
        mark(STR, col(R.color.st))
        mark(CMT, col(R.color.cm))
    }

    fun edit(repo: File, dir: File, f: File) {
        if (f.length() > 400000) { alert("Error", "File is too large to edit"); return }
        var last = f.readText(); var silent = false
        val undo = ArrayList<String>(); val redo = ArrayList<String>()

        val cv = CodeView(this).apply {
            setText(last); typeface = Typeface.MONOSPACE; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(col(R.color.fg)); gravity = Gravity.TOP; gc = col(R.color.muted)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 20; pad(true)
            layoutParams = FrameLayout.LayoutParams(-2, -2)
        }
        hl(cv.text)
        val commit = Runnable {
            val cur = cv.text.toString()
            if (cur != last) { undo.add(last); if (undo.size > 200) undo.removeAt(0); last = cur; redo.clear() }
            hl(cv.text)
        }
        cv.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { if (!silent) { h.removeCallbacks(commit); h.postDelayed(commit, 500) }; cv.pad() }
        })
        fun flush() { h.removeCallbacks(commit); commit.run() }
        fun put(s: String) { silent = true; cv.setText(s); silent = false; last = s; hl(cv.text); cv.pad(true) }
        fun save() { f.writeText(cv.text.toString()); toast("Saved") }

        root.removeAllViews(); root.gravity = Gravity.TOP; themeBg()
        onBack = { flush(); browse(repo, dir) }

        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, dp(12)) }
        val title = tv(f.name, 15, true).apply { setSingleLine(); ellipsize = TextUtils.TruncateAt.MIDDLE; setPadding(0, 0, dp(8), 0) }
        top.addView(title, lp(0, -2, 1f))

        val frame = FrameLayout(this).apply { layoutParams = lp(-1, 0, 1f) }

        val searchBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = box(col(R.color.card), col(R.color.stroke), 12).apply { alpha = 180 }
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = FrameLayout.LayoutParams(-1, -2).apply { gravity = Gravity.TOP; setMargins(dp(10), dp(10), dp(10), dp(10)) }
            visibility = View.GONE
            elevation = dp(4).toFloat()
        }

        val fInput = EditText(this).apply {
            hint = "Find..."; setText(lastFind); textSize = 13f; setTextColor(col(R.color.fg));
            background = box(col(R.color.bg), col(R.color.stroke), 6).apply { alpha = 180 }
            setPadding(dp(10), dp(10), dp(10), dp(10)); layoutParams = lp(-1, -2).apply { bottomMargin = dp(6) }
            addTextChangedListener(object: TextWatcher {
                override fun afterTextChanged(s: Editable?) { lastFind = s?.toString() ?: "" }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }

        val rInput = EditText(this).apply {
            hint = "Replace with..."; setText(lastReplace); textSize = 13f; setTextColor(col(R.color.fg));
            background = box(col(R.color.bg), col(R.color.stroke), 6).apply { alpha = 180 }
            setPadding(dp(10), dp(10), dp(10), dp(10)); layoutParams = lp(-1, -2).apply { bottomMargin = dp(10) }
            addTextChangedListener(object: TextWatcher {
                override fun afterTextChanged(s: Editable?) { lastReplace = s?.toString() ?: "" }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        searchBox.addView(fInput); searchBox.addView(rInput)

        var idx = 0
        val sBtnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        sBtnRow.addView(smallBtn("Next") {
            val q = lastFind
            if (q.isNotEmpty()) {
                val txt = cv.text.toString()
                idx = txt.indexOf(q, idx, ignoreCase = true)
                if (idx >= 0) {
                    cv.setSelection(idx, idx + q.length)
                    cv.requestFocus()
                    idx += q.length
                } else {
                    idx = 0; toast("No more matches")
                }
            }
        })
        sBtnRow.addView(smallBtn("Replace") {
            if (cv.selectionEnd > cv.selectionStart) {
                cv.text.replace(cv.selectionStart, cv.selectionEnd, lastReplace)
                hl(cv.text)
            }
        })
        sBtnRow.addView(smallBtn("All") {
            if (lastFind.isNotEmpty()) {
                val nt = cv.text.toString().replace(lastFind, lastReplace, ignoreCase = true)
                cv.setText(nt)
                hl(cv.text)
                toast("Replaced all matches")
            }
        })
        sBtnRow.addView(smallBtn("Close") { searchBox.visibility = View.GONE })
        searchBox.addView(sBtnRow)

        val actBar = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val actRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actRow.addView(smallBtn("Save") { save() })
        actRow.addView(smallBtn("Search") { searchBox.visibility = if (searchBox.visibility == View.VISIBLE) View.GONE else View.VISIBLE })
        actRow.addView(smallBtn("Undo") { flush(); if (undo.isNotEmpty()) { redo.add(last); put(undo.removeAt(undo.size - 1)) } })
        actRow.addView(smallBtn("Redo") { flush(); if (redo.isNotEmpty()) { undo.add(last); put(redo.removeAt(redo.size - 1)) } })
        actRow.addView(smallBtn("Push") { save(); pushOne(repo, f) })
        actBar.addView(actRow)
        top.addView(actBar)
        root.addView(top)

        val hsv = HorizontalScrollView(this).apply { isFillViewport = true; addView(cv) }
        val vsv = ScrollView(this).apply { isFillViewport = true; addView(hsv) }
        frame.addView(vsv, FrameLayout.LayoutParams(-1, -1))
        frame.addView(searchBox)
        root.addView(frame)
        anim(depthOf(repo, dir) + 1)
    }

    // ---------- GitHub API ----------
    fun http(m: String, u: String, body: String? = null): HttpURLConnection {
        val c = URL(if (u.startsWith("http")) u else "https://api.github.com$u").openConnection() as HttpURLConnection
        c.requestMethod = m
        c.setRequestProperty("Authorization", "Bearer $tok")
        c.setRequestProperty("Accept", "application/vnd.github+json")
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

    fun pushOne(repo: File, f: File) {
        val l = page("Pushing...", btn("Cancel") { live++; browse(repo, repo) }, 98)
        val w = card(); w.addView(ProgressBar(this).apply { isIndeterminate = true; layoutParams = lp(dp(16), dp(16)).apply { rightMargin = dp(10) } })
        w.addView(tv("Uploading file to GitHub...", 14, false, R.color.muted)); add(l, w)

        bg {
            try {
                val (full, br) = meta(repo); val p = f.relativeTo(repo).path.replace(" ", "%20")
                val sha = try { JSONObject(api("GET", "/repos/$full/contents/$p?ref=$br")).getString("sha") } catch (e: Exception) { null }
                val j = JSONObject().put("message", "Update $p").put("branch", br).put("content", Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
                if (sha != null) j.put("sha", sha)
                val c = JSONObject(api("PUT", "/repos/$full/contents/$p", j.toString())).getJSONObject("commit").getString("sha")
                ui { watch(full, c, repo) }
            } catch(e: Exception) {
                ui { alert("Push Failed", e.message ?: "Unknown error"); browse(repo, repo) }
            }
        }
    }

    fun pushAll(repo: File) {
        val l = page("Pushing...", btn("Cancel") { live++; browse(repo, repo) }, 98)
        val w = card(); w.addView(ProgressBar(this).apply { isIndeterminate = true; layoutParams = lp(dp(16), dp(16)).apply { rightMargin = dp(10) } })
        w.addView(tv("Uploading all files to GitHub...", 14, false, R.color.muted)); add(l, w)

        bg {
            try {
                val (full, br) = meta(repo)
                val head = JSONObject(api("GET", "/repos/$full/git/ref/heads/$br")).getJSONObject("object").getString("sha")
                val tree = JSONArray()
                for (f in files(repo)) {
                    val b = JSONObject(api("POST", "/repos/$full/git/blobs", JSONObject()
                        .put("content", Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)).put("encoding", "base64").toString())).getString("sha")
                    tree.put(JSONObject().put("path", f.relativeTo(repo).path).put("mode", "100644").put("type", "blob").put("sha", b))
                }
                val t = JSONObject(api("POST", "/repos/$full/git/trees", JSONObject().put("tree", tree).toString())).getString("sha")
                val c = JSONObject(api("POST", "/repos/$full/git/commits", JSONObject().put("message", "Push all from Git Action Build")
                    .put("tree", t).put("parents", JSONArray().put(head)).toString())).getString("sha")
                api("PATCH", "/repos/$full/git/refs/heads/$br", JSONObject().put("sha", c).put("force", true).toString())
                ui { watch(full, c, repo) }
            } catch(e: Exception) {
                ui { alert("Push Failed", e.message ?: "Unknown error"); browse(repo, repo) }
            }
        }
    }

    // ---------- Hacker terminal build monitor ----------
    fun clean(t: String): String {
        val ansi = Regex("\u001B\\[[0-9;]*m"); val ts = Regex("^\\uFEFF?\\d{4}-\\d\\d-\\d\\dT[\\d:.]+Z ")
        return t.lines().joinToString("\n") { ts.replace(ansi.replace(it, ""), "") }
    }

    fun tline(tag: String, tc: Int, text: String, xc: Int, spin: Boolean = false): LinearLayout {
        val r = LinearLayout(this).apply { setPadding(0, dp(3), 0, dp(3)) }
        val t = mono(tag, 13f, tc).apply { setPadding(0, 0, dp(8), 0) }
        if (spin) spinViews.add(t)
        r.addView(t); r.addView(mono(text, 13f, xc), lp(0, -2, 1f))
        return r
    }

    fun copyText(label: String, text: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText(label, text))
        toast("Copied: $label")
    }

    fun draw(l: LinearLayout, r: JSONObject, js: JSONArray, artifactState: Int) {
        val sv = l.parent as? ScrollView; val y = sv?.scrollY ?: 0
        val atBottom = sv == null || (l.height - (sv.height + y)) < dp(80)
        l.removeAllViews(); spinViews.clear()

        l.addView(mono("# " + r.optString("name") + "  run #" + r.optInt("run_number") + "  [" + r.optString("head_branch") + "]", 12f, TD).apply { setPadding(0, 0, 0, dp(6)) })

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
            l.addView(mono("$ job::" + jname + (if (jc.isNotEmpty()) "  => " + jc.uppercase() else ""), 13f, jcol).apply { setPadding(0, dp(10), 0, dp(4)) })

            val steps = j.optJSONArray("steps"); var shown = false
            if (steps != null) {
                for (q in 0 until steps.length()) {
                    val st = steps.getJSONObject(q)
                    val name = st.getString("name")
                    val stStatus = st.getString("status")
                    val stConcl = st.optString("conclusion").let { if (it == "null") "" else it }
                    val isRunning = stStatus == "in_progress"
                    val isFailed = stConcl == "failure"

                    if (isRunning) l.addView(tline("[ - ]", TA, name, TG, true))
                    else if (stStatus == "completed") {
                        when (stConcl) {
                            "success" -> l.addView(tline("[ OK ]", TG, name, TT))
                            "failure" -> l.addView(tline("[FAIL]", TR, name, TR))
                            else -> l.addView(tline("[SKIP]", TD, name, TD))
                        }
                    } else l.addView(tline("[ .. ]", TD, name, TD))

                    if ((isRunning || isFailed) && !shown) {
                        val lg = logMap[id]
                        if (!lg.isNullOrEmpty()) {
                            shown = true
                            l.addView(mono(lg, 10.5f, if (isFailed) TRL else TGL).apply {
                                background = box(TLOG, TD, 6)
                                setPadding(dp(10), dp(8), dp(10), dp(8))
                                setHorizontallyScrolling(true)
                            }, lp(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(6) })
                        }
                    }
                }
            }

            // copy button only for failed jobs
            if (jc == "failure") {
                l.addView(mono("[ copy logs of failed job: $jname ]", 12f, TR).apply {
                    background = box(Color.parseColor("#1A0A0A"), TR, 6)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    press(this, 0.96f)
                    setOnClickListener {
                        val lg = fullLog[id] ?: logMap[id] ?: ""
                        if (lg.isEmpty()) toast("Logs not ready yet") else copyText("job $jname", "### job: $jname (failed)\n$lg")
                    }
                }, lp(-2, -2).apply { topMargin = dp(8); bottomMargin = dp(6) })
            }
        }

        if (artifactState > 0) {
            l.addView(mono("$ artifacts", 13f, if (artifactState == 3) TR else TA).apply { setPadding(0, dp(10), 0, dp(4)) })
            when (artifactState) {
                1 -> l.addView(tline("[ - ]", TA, "downloading artifact...", TG, true))
                2 -> {
                    l.addView(tline("[ OK ]", TG, "artifact downloaded", TT))
                    if (lastDownloadedPath.isNotEmpty()) l.addView(mono("  -> $lastDownloadedPath", 11f, TGL).apply { setHorizontallyScrolling(true) })
                }
                else -> l.addView(tline("[FAIL]", TR, "artifact download failed", TR))
            }
        }

        if (r.getString("status") == "completed") {
            val ok = r.optString("conclusion") == "success"
            l.addView(mono("$ exit " + (if (ok) "0  // build passed" else "1  // build failed"), 13f, if (ok) TG else TR).apply { setPadding(0, dp(12), 0, dp(24)) })
        }

        sv?.post { if (atBottom) sv.fullScroll(View.FOCUS_DOWN) else sv.scrollTo(0, y) }
    }

    fun watch(full: String, sha: String, repo: File) {
        val my = ++live; logMap.clear(); fullLog.clear(); spinViews.clear()
        var artifactState = 0

        root.removeAllViews(); root.gravity = Gravity.TOP
        root.setBackgroundColor(TB); bars(true)
        onBack = { live++; browse(repo, repo) }

        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, dp(10)) }
        val hdr = mono("", 13f, TG).apply { setSingleLine() }
        val cur = mono("\u2588", 13f, TG)
        top.addView(hdr); top.addView(cur)
        top.addView(View(this), lp(0, 1, 1f))
        top.addView(mono("[x]", 15f, TR).apply {
            setPadding(dp(12), dp(4), dp(4), dp(4))
            setOnClickListener { live++; browse(repo, repo) }
        })
        root.addView(top)
        root.addView(View(this).apply { setBackgroundColor(TD) }, lp(-1, dp(1)).apply { bottomMargin = dp(8) })
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(l) }, lp(-1, 0, 1f))
        anim(99)

        val w = tline("[ - ]", TA, "triggering workflow run...", TG, true)
        l.addView(w)

        // terminal animation: typewriter header, blinking cursor, spinners
        val fullHdr = "root@gab:~# tail -f build.log"
        val fr = "|/-\\"
        var tick = 0
        val spin = object : Runnable {
            override fun run() {
                if (my != live) return
                tick++
                hdr.text = fullHdr.substring(0, minOf(tick * 2, fullHdr.length))
                cur.text = if ((tick / 5) % 2 == 0) "\u2588" else " "
                val f = fr[(tick / 2) % 4]
                for (s in spinViews) s.text = "[ $f ]"
                h.postDelayed(this, 90)
            }
        }
        h.post(spin)

        bg {
            for (i in 0 until 600) {
                if (my != live) return@bg
                Thread.sleep(3000)
                val rs = try { JSONObject(api("GET", "/repos/$full/actions/runs?head_sha=$sha")).getJSONArray("workflow_runs") } catch(e: Exception) { continue }
                if (rs.length() == 0) continue
                val r = rs.getJSONObject(0)
                val js = try { JSONObject(api("GET", r.getString("url") + "/jobs")).getJSONArray("jobs") } catch(e: Exception) { continue }

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

                ui { if (my == live) { if (w.parent == l) l.removeView(w); draw(l, r, js, artifactState) } }

                if (done) {
                    if (success) {
                        try {
                            download(r)
                            artifactState = 2
                        } catch (e: Exception) {
                            artifactState = 3
                        }
                        ui { if (my == live) draw(l, r, js, artifactState) }
                    }
                    return@bg
                }
            }
        }
    }

    fun download(r: JSONObject) {
        val a = JSONObject(api("GET", r.getString("url") + "/artifacts")).getJSONArray("artifacts")
        if (a.length() == 0) return
        val x = a.getJSONObject(0)
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())

        val out = File(base, "artifacts/" + x.getString("name") + "_run" + r.optInt("run_number") + "_" + stamp + ".zip")
        out.parentFile?.mkdirs()

        http("GET", x.getString("archive_download_url")).inputStream.use { i -> out.outputStream().use { i.copyTo(it) } }
        lastDownloadedPath = out.absolutePath
        ui { toast("Artifact downloaded successfully") }
    }
}
