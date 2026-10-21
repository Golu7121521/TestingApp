package com.advancedpdf.toolkit

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.InputType
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.math.min

class ReaderActivity : AppCompatActivity() {

    private val lock = Any()
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val exec = Executors.newSingleThreadExecutor()
    private lateinit var cache: LruCache<Int, Bitmap>
    private lateinit var recycler: RecyclerView
    private lateinit var indicator: TextView
    private lateinit var uri: Uri
    private var lm: LinearLayoutManager? = null
    private var pageCount = 0
    private var aspect = 1.414f
    private var zoom = 1f
    private var dark = false

    private val invert = ColorMatrixColorFilter(
        ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)
        val data = intent?.data
        if (data == null) {
            finish()
            return
        }
        uri = data
        dark = Store.readerDark(this)

        val maxKb = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
        cache = object : LruCache<Int, Bitmap>(maxKb) {
            override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024
        }

        val tb = findViewById<MaterialToolbar>(R.id.toolbar)
        tb.title = FileUtil.queryName(this, uri)
        tb.setNavigationIcon(R.drawable.ic_back)
        tb.setNavigationContentDescription("Back")
        tb.setNavigationOnClickListener { finish() }
        tb.inflateMenu(R.menu.reader)
        tb.menu.findItem(R.id.m_dark).isChecked = dark
        tb.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.m_goto -> goToDialog()
                R.id.m_zoom_in -> setZoom(zoom + 0.25f)
                R.id.m_zoom_out -> setZoom(zoom - 0.25f)
                R.id.m_fit -> setZoom(1f)
                R.id.m_dark -> {
                    dark = !dark
                    item.isChecked = dark
                    Store.setReaderDark(this, dark)
                    recycler.adapter?.notifyDataSetChanged()
                }
                R.id.m_share -> shareUris(this, listOf(uri), "application/pdf")
                R.id.m_info -> infoDialog()
            }
            true
        }

        recycler = findViewById(R.id.recycler)
        indicator = findViewById(R.id.indicator)
        indicator.setOnClickListener { goToDialog() }
        openDocument()
    }

    private fun openDocument() {
        exec.execute {
            var err: String? = null
            try {
                val d = try {
                    contentResolver.openFileDescriptor(uri, "r")
                } catch (e: SecurityException) {
                    null
                } catch (e: FileNotFoundException) {
                    null
                }
                if (d == null) {
                    err = "This file is unavailable. It may have been moved or deleted, or access was removed."
                } else {
                    try {
                        val r = PdfRenderer(d)
                        synchronized(lock) {
                            pfd = d
                            renderer = r
                        }
                        pageCount = r.pageCount
                        if (pageCount == 0) {
                            err = "This PDF has no pages."
                        } else {
                            val p = r.openPage(0)
                            aspect = p.height.toFloat() / p.width.toFloat()
                            p.close()
                        }
                    } catch (e: SecurityException) {
                        d.close()
                        err = "This PDF is password-protected. Password-protected PDFs can't be opened in this version of the reader."
                    } catch (e: Exception) {
                        d.close()
                        err = "This PDF is damaged or uses features that aren't supported."
                    }
                }
            } catch (e: Exception) {
                err = "This file could not be opened."
            }
            val result = err
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                findViewById<ProgressBar>(R.id.loading).visibility = View.GONE
                if (result != null) showError(result) else onOpened()
            }
        }
    }

    private fun showError(msg: String) {
        val t = findViewById<TextView>(R.id.error)
        t.text = msg
        t.visibility = View.VISIBLE
        indicator.visibility = View.GONE
    }

    private fun onOpened() {
        Store.addRecent(this, uri.toString(), FileUtil.queryName(this, uri))
        val layout = LinearLayoutManager(this)
        lm = layout
        recycler.layoutManager = layout
        recycler.setHasFixedSize(false)
        recycler.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                outRect.bottom = dp(6)
            }
        })
        recycler.adapter = PageAdapter()
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) = updateIndicator()
        })
        applyWidth()
        val last = Store.lastPage(this, uri.toString())
        if (last in 1 until pageCount) layout.scrollToPositionWithOffset(last, 0)
        updateIndicator()
    }

    private fun pageWidth(): Int = (resources.displayMetrics.widthPixels * zoom).toInt()

    private fun applyWidth() {
        recycler.layoutParams = FrameLayout.LayoutParams(pageWidth(), ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun setZoom(z: Float) {
        val nz = z.coerceIn(1f, 2f)
        if (nz == zoom) return
        val cur = currentPage()
        zoom = nz
        cache.evictAll()
        applyWidth()
        recycler.adapter?.notifyDataSetChanged()
        recycler.post { lm?.scrollToPositionWithOffset(cur, 0) }
    }

    private fun currentPage(): Int {
        val l = lm ?: return 0
        var p = l.findFirstCompletelyVisibleItemPosition()
        if (p < 0) p = l.findFirstVisibleItemPosition()
        return p.coerceAtLeast(0)
    }

    private fun updateIndicator() {
        if (pageCount > 0) indicator.text = "Page ${currentPage() + 1} of $pageCount"
    }

    private fun goToDialog() {
        if (pageCount == 0) return
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.hint = "1 – $pageCount"
        MaterialAlertDialogBuilder(this).setTitle("Go to page").setView(input)
            .setPositiveButton("Go") { _, _ ->
                val p = input.text.toString().toIntOrNull()
                if (p == null || p < 1 || p > pageCount) toast("Enter a page between 1 and $pageCount.")
                else lm?.scrollToPositionWithOffset(p - 1, 0)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun infoDialog() {
        val st = FileUtil.stat(this, uri)
        val sb = StringBuilder()
        sb.append("Name: ").append(FileUtil.queryName(this, uri)).append('\n')
        sb.append("Pages: ").append(pageCount).append('\n')
        if (st != null) {
            sb.append("Size: ").append(FileUtil.formatSize(st[0])).append('\n')
            if (st[1] > 0) sb.append("Modified: ").append(FileUtil.formatDate(st[1]))
        }
        MaterialAlertDialogBuilder(this).setTitle("Document info")
            .setMessage(sb.toString()).setPositiveButton("OK", null).show()
    }

    private fun renderPage(index: Int, width: Int): Bitmap? {
        synchronized(lock) {
            val r = renderer ?: return null
            return try {
                val page = r.openPage(index)
                try {
                    val h = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                } finally {
                    page.close()
                }
            } catch (e: OutOfMemoryError) {
                cache.evictAll()
                null
            } catch (e: Exception) {
                null
            }
        }
    }

    private class PageVH(val image: ImageView) : RecyclerView.ViewHolder(image) {
        @Volatile
        var index = -1
    }

    private inner class PageAdapter : RecyclerView.Adapter<PageVH>() {
        override fun getItemCount() = pageCount

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
            val iv = ImageView(parent.context)
            iv.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            iv.scaleType = ImageView.ScaleType.FIT_XY
            iv.setBackgroundColor(Color.WHITE)
            return PageVH(iv)
        }

        override fun onViewRecycled(holder: PageVH) {
            holder.index = -1
            holder.image.setImageDrawable(null)
        }

        override fun onBindViewHolder(h: PageVH, pos: Int) {
            h.index = pos
            val w = pageWidth()
            h.image.colorFilter = if (dark) invert else null
            val cached = cache.get(pos)
            if (cached != null) {
                show(h, cached, w)
                return
            }
            h.image.setImageDrawable(null)
            setHeight(h, (w * aspect).toInt())
            val bw = min(w, 2000)
            exec.execute {
                if (h.index != pos) return@execute
                val b = renderPage(pos, bw)
                if (b != null) {
                    cache.put(pos, b)
                    runOnUiThread {
                        if (!isDestroyed && h.index == pos) show(h, b, w)
                    }
                }
            }
        }

        private fun show(h: PageVH, b: Bitmap, w: Int) {
            h.image.setImageBitmap(b)
            setHeight(h, (w.toLong() * b.height / b.width).toInt())
        }

        private fun setHeight(h: PageVH, px: Int) {
            val lp = h.image.layoutParams
            if (lp.height != px) {
                lp.height = px
                h.image.layoutParams = lp
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (pageCount > 0 && lm != null) Store.setLastPage(this, uri.toString(), currentPage())
    }

    override fun onDestroy() {
        super.onDestroy()
        exec.shutdownNow()
        synchronized(lock) {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
            renderer = null
            pfd = null
        }
        if (::cache.isInitialized) cache.evictAll()
    }
}
