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

// Editor screen: opens a file in Sora Editor, search/replace, save and push.

// ---------- code editor (Sora Editor) ----------
// Colored plain text for every file (keywords, strings, numbers, comments, functions, types)
fun MainActivity.languageFor(f: File): Language = TextColorLanguage()

fun MainActivity.makeEditor(f: File, text: String): CodeEditor {
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

fun MainActivity.edit(repo: File, dir: File, f: File) {
    if (f.length() > 400000) { alert(getString(R.string.s_error_902b0d), getString(R.string.s_file_is_too_large_to_edit_24ae33)); return }
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
        slideToast(frame, getString(R.string.s_saved_248336))
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
        hint = getString(R.string.s_find_fe77d5); setText(lastFind); textSize = 13f; setTextColor(col(R.color.fg))
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
        hint = getString(R.string.s_replace_with_4de2c8); setText(lastReplace); textSize = 13f; setTextColor(col(R.color.fg))
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
    sBtnRow.addView(smallBtn(getString(R.string.s_next_10ac3d)) { searchNext() })
    sBtnRow.addView(smallBtn(getString(R.string.s_replace_0ebe6d)) {
        try { ed.searcher.replaceCurrentMatch(rInput.text.toString()) } catch (e: IllegalStateException) { e.printStackTrace() }
    })
    sBtnRow.addView(smallBtn(getString(R.string.s_all_b1c94c)) {
        try { ed.searcher.replaceAll(rInput.text.toString()) } catch (e: IllegalStateException) { e.printStackTrace() }
    })
    sBtnRow.addView(smallBtn(getString(R.string.s_close_d3d2e6)) {
        ed.searcher.stopSearch()
        setSearch(false)
    })
    searchBox.addView(sBtnRow)

    val actBar = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
    val actRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    actRow.addView(smallBtn(getString(R.string.s_save_c9cc8c)) { save() })
    actRow.addView(smallBtn(getString(R.string.s_search_133484)) { setSearch(!searchOn) })
    actRow.addView(smallBtn(getString(R.string.s_undo_1cdc07)) { ed.undo() })
    actRow.addView(smallBtn(getString(R.string.s_redo_5afeab)) { ed.redo() })
    actRow.addView(smallBtn(getString(R.string.s_push_9c6a9d)) {
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
