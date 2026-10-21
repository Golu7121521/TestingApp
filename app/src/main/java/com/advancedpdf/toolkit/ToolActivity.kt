package com.advancedpdf.toolkit

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.io.File

class ToolActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_MODE = "mode"
        fun start(c: Context, mode: String) {
            c.startActivity(Intent(c, ToolActivity::class.java).putExtra(EXTRA_MODE, mode))
        }
    }

    private lateinit var mode: String
    private val inputs = ArrayList<Uri>()
    private var running = false

    @Volatile
    private var cancelled = false

    private lateinit var selectedBox: LinearLayout
    private lateinit var optionsBox: LinearLayout
    private lateinit var resultsBox: LinearLayout
    private lateinit var runBtn: MaterialButton
    private lateinit var cancelBtn: MaterialButton
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView

    // options
    private lateinit var rangesInput: EditText
    private lateinit var separateBox: CheckBox
    private lateinit var choice: RadioGroup

    private var saveSource: File? = null

    private val pickMany = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { list ->
        onPicked(list)
    }
    private val pickOne = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) onPicked(listOf(u))
    }
    private val createDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { u ->
        val src = saveSource
        if (u != null && src != null) {
            try {
                contentResolver.openOutputStream(u)?.use { out -> src.inputStream().use { it.copyTo(out) } }
                toast("Saved a copy.")
            } catch (e: Exception) {
                toast("Could not save the copy.")
            }
        }
    }

    private val multi get() = mode == "merge" || mode == "img2pdf"
    private val imageInput get() = mode == "img2pdf"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tool)
        mode = intent.getStringExtra(EXTRA_MODE) ?: "merge"

        val tb = findViewById<MaterialToolbar>(R.id.toolbar)
        tb.setNavigationIcon(R.drawable.ic_back)
        tb.setNavigationContentDescription("Back")
        tb.setNavigationOnClickListener { finish() }

        val (title, desc) = when (mode) {
            "split" -> "Split / Extract pages" to "Pick a PDF and enter the page ranges you want to keep, for example 1-3, 5, 8-10."
            "compress" -> "Compress PDF" to "Reduces size by re-rendering each page as an image. Text becomes non-selectable and results vary. If the result isn't smaller, nothing is saved."
            "img2pdf" -> "Images to PDF" to "Combine images (JPG, PNG and other formats Android supports) into one PDF, in the order shown."
            "pdf2img" -> "PDF to Images" to "Save every page of a PDF as an image file."
            else -> "Merge PDF" to "Combine several PDFs into one file, in the order shown. Password-protected PDFs are not supported."
        }
        tb.title = title
        findViewById<TextView>(R.id.desc).text = desc

        selectedBox = findViewById(R.id.selected)
        optionsBox = findViewById(R.id.options)
        resultsBox = findViewById(R.id.results)
        runBtn = findViewById(R.id.run)
        cancelBtn = findViewById(R.id.cancel)
        progress = findViewById(R.id.progress)
        status = findViewById(R.id.status)

        findViewById<MaterialButton>(R.id.pick).apply {
            text = if (multi) "Add files" else "Select a file"
            setOnClickListener {
                val types = if (imageInput) arrayOf("image/*") else arrayOf("application/pdf")
                if (multi) pickMany.launch(types) else pickOne.launch(types)
            }
        }
        runBtn.text = when (mode) {
            "split" -> "Extract pages"
            "compress" -> "Compress"
            "img2pdf" -> "Create PDF"
            "pdf2img" -> "Save as images"
            else -> "Merge"
        }
        runBtn.setOnClickListener { start() }
        cancelBtn.setOnClickListener {
            cancelled = true
            cancelBtn.isEnabled = false
            status.text = "Cancelling…"
        }
        buildOptions()
        refreshSelected()
    }

    private fun radio(text: String, id: Int, checked: Boolean): RadioButton {
        val r = RadioButton(this)
        r.text = text
        r.id = 100 + id
        r.isChecked = checked
        r.minHeight = dp(48)
        return r
    }

    private fun buildOptions() {
        when (mode) {
            "split" -> {
                rangesInput = EditText(this)
                rangesInput.hint = "Page ranges, e.g. 1-3, 5, 8-10"
                rangesInput.maxLines = 1
                rangesInput.inputType = android.text.InputType.TYPE_CLASS_TEXT
                separateBox = CheckBox(this)
                separateBox.text = "Create a separate file for each range"
                separateBox.minHeight = dp(48)
                optionsBox.addView(rangesInput)
                optionsBox.addView(separateBox)
            }
            "compress" -> {
                choice = RadioGroup(this)
                val def = Store.compression(this)
                choice.addView(radio("Low compression (best quality)", 0, def == 0))
                choice.addView(radio("Medium compression", 1, def == 1))
                choice.addView(radio("High compression (smallest size)", 2, def == 2))
                optionsBox.addView(choice)
            }
            "img2pdf" -> {
                choice = RadioGroup(this)
                choice.addView(radio("A4 page", 0, true))
                choice.addView(radio("Letter page", 1, false))
                choice.addView(radio("Fit page to image", 2, false))
                optionsBox.addView(choice)
            }
            "pdf2img" -> {
                choice = RadioGroup(this)
                choice.addView(radio("PNG (lossless, larger files)", 0, true))
                choice.addView(radio("JPEG (smaller files)", 1, false))
                optionsBox.addView(choice)
            }
        }
    }

    private fun onPicked(list: List<Uri>) {
        if (list.isEmpty()) return
        if (!multi) inputs.clear()
        for (u in list) if (!inputs.contains(u)) inputs.add(u)
        clearResults()
        refreshSelected()
    }

    private fun clearResults() {
        resultsBox.removeAllViews()
        status.text = ""
    }

    private fun action(text: String, onClick: () -> Unit): TextView {
        val t = TextView(this)
        t.text = text
        t.gravity = Gravity.CENTER
        t.minWidth = dp(48)
        t.minHeight = dp(48)
        t.setPadding(dp(8), 0, dp(8), 0)
        t.setTextColor(ContextCompat.getColor(this, R.color.accent))
        t.textSize = 14f
        val tv = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        t.setBackgroundResource(tv.resourceId)
        t.setOnClickListener { onClick() }
        return t
    }

    private fun refreshSelected() {
        selectedBox.removeAllViews()
        if (inputs.isEmpty()) {
            val t = TextView(this)
            t.text = "No files selected"
            t.setTextColor(ContextCompat.getColor(this, R.color.text2))
            t.setPadding(0, dp(12), 0, dp(12))
            selectedBox.addView(t)
            return
        }
        inputs.forEachIndexed { i, u ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val name = TextView(this)
            name.text = "${i + 1}. ${FileUtil.queryName(this, u)}"
            name.maxLines = 1
            name.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            row.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (multi) {
                row.addView(action("▲") {
                    if (i > 0) { inputs.add(i - 1, inputs.removeAt(i)); refreshSelected() }
                }.also { it.contentDescription = "Move up" })
                row.addView(action("▼") {
                    if (i < inputs.size - 1) { inputs.add(i + 1, inputs.removeAt(i)); refreshSelected() }
                }.also { it.contentDescription = "Move down" })
            }
            row.addView(action("✕") { inputs.removeAt(i); refreshSelected() }
                .also { it.contentDescription = "Remove" })
            selectedBox.addView(row)
        }
    }

    private fun setBusy(busy: Boolean) {
        running = busy
        runBtn.isEnabled = !busy
        findViewById<View>(R.id.pick).isEnabled = !busy
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        val canCancel = busy && mode != "merge" && mode != "split"
        cancelBtn.visibility = if (canCancel) View.VISIBLE else View.GONE
        cancelBtn.isEnabled = true
    }

    private fun start() {
        if (running) return
        if (inputs.isEmpty()) { toast("Select file(s) first."); return }
        if (mode == "merge" && inputs.size < 2) { toast("Select at least two PDFs to merge."); return }
        val outDir = if (mode == "pdf2img") FileUtil.imagesDir(this) else FileUtil.outputDir(this)
        if (outDir.usableSpace < 50L * 1024 * 1024) {
            status.text = "Not enough free storage. At least 50 MB is needed."
            return
        }
        clearResults()
        cancelled = false
        setBusy(true)
        val indeterminate = mode == "merge" || mode == "split"
        progress.isIndeterminate = indeterminate
        progress.progress = 0
        status.text = "Working…"

        val app = applicationContext
        val list = ArrayList(inputs)
        val rangesText = if (mode == "split") rangesInput.text.toString() else ""
        val separate = mode == "split" && separateBox.isChecked
        val option = if (mode == "split" || mode == "merge") 0 else choice.checkedRadioButtonId - 100
        val prog: (Int, Int) -> Unit = { done, total ->
            runOnUiThread {
                progress.max = total
                progress.progress = done
                status.text = "Processing $done of $total"
            }
        }

        Thread {
            var summary = ""
            var outputs: List<File> = emptyList()
            var error: String? = null
            try {
                PdfOps.init(app)
                val base = FileUtil.safeBase(FileUtil.queryName(app, list[0]))
                when (mode) {
                    "merge" -> {
                        val out = FileUtil.uniqueFile(outDir, "merged", "pdf")
                        try {
                            PdfOps.merge(app, list, out)
                        } catch (e: Throwable) { out.delete(); throw e }
                        if (!PdfOps.validate(out)) { out.delete(); throw java.io.IOException("Invalid output") }
                        outputs = listOf(out)
                        summary = "Merged ${list.size} files."
                    }
                    "split" -> {
                        val total = PdfOps.pageCount(app, list[0])
                        val ranges = PdfOps.parseRanges(rangesText, total)
                        val files = PdfOps.split(app, list[0], ranges, separate, outDir, base) { cancelled }
                        if (files.any { !PdfOps.validate(it) }) {
                            files.forEach { it.delete() }
                            throw java.io.IOException("Invalid output")
                        }
                        outputs = files
                        summary = "Created ${files.size} file(s) from $total pages."
                    }
                    "compress" -> {
                        val out = FileUtil.uniqueFile(outDir, "${base}_compressed", "pdf")
                        val originalSize = FileUtil.stat(app, list[0])?.get(0) ?: -1L
                        val ok = try {
                            PdfOps.compress(app, list[0], option, out, { cancelled }, prog)
                        } catch (e: Throwable) { out.delete(); throw e }
                        if (ok) {
                            outputs = listOf(out)
                            val pct = if (originalSize > 0) 100 - (out.length() * 100 / originalSize) else 0
                            summary = "Original: ${FileUtil.formatSize(originalSize)} → New: ${FileUtil.formatSize(out.length())} ($pct% smaller)."
                        } else {
                            summary = "The compressed version wasn't smaller than the original, so nothing was saved. This file is probably already well optimized."
                        }
                    }
                    "img2pdf" -> {
                        val out = FileUtil.uniqueFile(outDir, "images", "pdf")
                        val size = when (option) { 1 -> "letter"; 2 -> "fit"; else -> "a4" }
                        try {
                            PdfOps.imagesToPdf(app, list, size, out, { cancelled }, prog)
                        } catch (e: Throwable) { out.delete(); throw e }
                        if (!PdfOps.validate(out)) { out.delete(); throw java.io.IOException("Invalid output") }
                        outputs = listOf(out)
                        summary = "Created a PDF from ${list.size} image(s)."
                    }
                    else -> {
                        val folder = FileUtil.uniqueFile(outDir, base, "")
                        folder.mkdirs()
                        val files = PdfOps.pdfToImages(app, list[0], option == 0, folder, base, { cancelled }, prog)
                        if (files.isEmpty() || files.any { !it.exists() || it.length() == 0L }) {
                            files.forEach { it.delete() }
                            throw java.io.IOException("Invalid output")
                        }
                        outputs = files
                        summary = "Saved ${files.size} image(s)."
                    }
                }
            } catch (e: CancelledException) {
                error = "Cancelled."
            } catch (e: Throwable) {
                error = PdfOps.describe(e)
            }
            val finalError = error
            val finalOutputs = outputs
            val finalSummary = summary
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                setBusy(false)
                if (finalError != null) {
                    status.text = finalError
                } else {
                    showResult(finalSummary, finalOutputs)
                }
            }
        }.start()
    }

    private fun showResult(summary: String, files: List<File>) {
        status.text = "Done. $summary"
        resultsBox.removeAllViews()
        val shown = files.take(6)
        for (f in shown) {
            val isPdf = f.name.endsWith(".pdf", true)
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.setPadding(0, dp(8), 0, dp(8))
            val name = TextView(this)
            name.text = "${f.name}\n${FileUtil.formatSize(f.length())} • Saved in the app's “${if (isPdf) "PDFs" else "Images"}” folder"
            col.addView(name)
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.addView(action("Open") {
                val u = FileUtil.fileUri(this, f)
                if (isPdf) openReader(this, u)
                else startActivity(
                    Intent(Intent.ACTION_VIEW).setDataAndType(u, "image/*")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
            })
            row.addView(action("Share") {
                shareUris(this, listOf(FileUtil.fileUri(this, f)), if (isPdf) "application/pdf" else "image/*")
            })
            if (isPdf) row.addView(action("Save a copy") {
                saveSource = f
                createDoc.launch(f.name)
            })
            col.addView(row)
            resultsBox.addView(col)
        }
        if (files.size > shown.size) {
            val more = TextView(this)
            more.text = "…and ${files.size - shown.size} more file(s)."
            resultsBox.addView(more)
        }
        if (files.size > 1) {
            val allPdf = files.all { it.name.endsWith(".pdf", true) }
            resultsBox.addView(action("Share all") {
                shareUris(this, files.map { FileUtil.fileUri(this, it) }, if (allPdf) "application/pdf" else "image/*")
            })
        }
    }

    override fun onBackPressed() {
        if (running) {
            toast("Please wait for the operation to finish, or tap Cancel.")
        } else {
            super.onBackPressed()
        }
    }
}
