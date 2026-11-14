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

class MainActivity : ComponentActivity() {
    val base = File("/storage/emulated/0/GitActonBuild")
    // GitHub token lives in encrypted storage. A token saved by an older build is moved over once.
    val sp: SharedPreferences by lazy { securePrefs() }

    private fun securePrefs(): SharedPreferences {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val secure = EncryptedSharedPreferences.create(
            "secure_prefs",
            masterKey,
            this,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        val old = getSharedPreferences("g", 0)
        val oldToken = old.getString("t", null)
        if (oldToken != null) {
            secure.edit().putString("t", oldToken).apply()
            old.edit().remove("t").apply()
        }
        return secure
    }
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

    // Background work runs on IO and is cancelled when the activity is destroyed
    fun bg(f: () -> Unit) = lifecycleScope.launch(Dispatchers.IO) {
        try { f() } catch (e: Exception) { ui { alert(getString(R.string.s_error_occurred_e31971), e.message ?: "Unknown error") } }
    }

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
        dlg(title, tv(msg, 14, false, R.color.muted), getString(R.string.s_ok_e0aa02), null) {}
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
        d = dlg(title, v, null, getString(R.string.s_close_d3d2e6))
    }

    fun ask(t: String, init: String = "", f: (String) -> Unit) {
        val e = EditText(this).apply {
            setText(init); setSingleLine(); setTextColor(col(R.color.fg)); textSize = 15f
            setPadding(dp(14), dp(12), dp(14), dp(12)); background = box(col(R.color.bg), col(R.color.stroke), 8); requestFocus()
        }
        dlg(t, e, getString(R.string.s_ok_e0aa02)) { f(e.text.toString().trim()) }
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
        // system back: a screen's own back action first, otherwise the default (leave the app)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val b = onBack
                if (b != null) {
                    b()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }
    override fun onResume() { super.onResume(); if (root.childCount == 0 || !granted()) start() }
    override fun onRequestPermissionsResult(c: Int, p: Array<String>, g: IntArray) { start() }

    fun granted() = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun start() {
        if (!granted()) {
            val b = center(getString(R.string.s_storage_access_c836a2), getString(R.string.s_git_action_build_needs_storage_access_to_e97e61))
            b.addView(btn(getString(R.string.s_allow_access_db8e6e)) {
                if (Build.VERSION.SDK_INT >= 30) startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
                else requestPermissions(arrayOf("android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.READ_EXTERNAL_STORAGE"), 1)
            })
        } else { base.mkdirs(); askNotificationPermission(); if (tok.isEmpty()) tokenScreen() else repos() }
    }

    // Pass / fail notification for a finished build. Tapping it opens the app.
    @Suppress("DEPRECATION")
    fun notifyBuild(repoName: String, ok: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("builds", "Builds", NotificationManager.IMPORTANCE_DEFAULT))
            Notification.Builder(this, "builds")
        } else {
            Notification.Builder(this)
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = builder
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(if (ok) "Build passed" else "Build failed")
            .setContentText(repoName)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(repoName.hashCode(), n)
        } catch (e: SecurityException) {
            Log.w("GAB", "Notification blocked", e)
        }
    }

    // Android 13+ asks for notification permission; asked once so it does not nag on every start
    fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        val prefs = getSharedPreferences("ui", 0)
        if (prefs.getBoolean("asked_notif", false)) return
        prefs.edit().putBoolean("asked_notif", true).apply()
        requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 2)
    }

    fun tokenScreen() {
        val b = center(getString(R.string.s_github_token_8efc95), getString(R.string.s_paste_a_personal_access_token_with_repo__ba9acc))
        val e = EditText(this).apply {
            hint = getString(R.string.s_personal_access_token_8b8943); setSingleLine(); setTextColor(col(R.color.fg)); setHintTextColor(col(R.color.muted))
            textSize = 15f; setPadding(dp(14), dp(12), dp(14), dp(12)); background = box(col(R.color.bg), col(R.color.stroke), 8)
        }
        b.addView(e, lp(-1, -2).apply { bottomMargin = dp(16) })
        b.addView(btn(getString(R.string.s_continue_a0bfb8)) { sp.edit().putString("t", e.text.toString().trim()).apply(); repos() })
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

}
