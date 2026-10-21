package com.advancedpdf.toolkit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

class CancelledException : RuntimeException()

/** All PDF work. Every function blocks: call from a background thread. */
object PdfOps {
    private var inited = false

    @Synchronized
    fun init(c: Context) {
        if (!inited) {
            PDFBoxResourceLoader.init(c.applicationContext)
            inited = true
        }
    }

    fun describe(e: Throwable): String = when {
        e is InvalidPasswordException -> "A file is password-protected. This version can't process protected PDFs."
        e is OutOfMemoryError -> "Not enough memory to finish. Try fewer or smaller files."
        e is SecurityException -> "A file is password-protected or access to it was denied."
        e is IllegalArgumentException -> e.message ?: "Invalid input."
        e is IOException && ((e.message ?: "").contains("ENOSPC") ||
                (e.message ?: "").contains("space", true)) -> "Not enough free storage space."
        e is IOException -> "A file could not be read or written. It may be damaged or unsupported."
        else -> "The operation failed (${e.javaClass.simpleName})."
    }

    fun parseRanges(text: String, max: Int): List<IntRange> {
        val out = ArrayList<IntRange>()
        for (raw in text.split(',')) {
            val t = raw.trim()
            if (t.isEmpty()) continue
            val parts = t.split('-').map { it.trim() }
            val a = parts[0].toIntOrNull()
            val b = when (parts.size) {
                1 -> a
                2 -> parts[1].toIntOrNull()
                else -> null
            }
            if (a == null || b == null || a < 1 || b < a || b > max) {
                throw IllegalArgumentException("\"$t\" is not a valid range. This document has $max pages.")
            }
            out.add(a..b)
        }
        if (out.isEmpty()) throw IllegalArgumentException("Enter at least one page range, for example 1-3, 5.")
        return out
    }

    fun pageCount(c: Context, uri: Uri): Int {
        val pfd = c.contentResolver.openFileDescriptor(uri, "r") ?: throw IOException("Cannot open file")
        try {
            val r = PdfRenderer(pfd)
            try {
                return r.pageCount
            } finally {
                r.close()
            }
        } finally {
            pfd.close()
        }
    }

    /** A produced PDF is valid if the system renderer can open it and it has pages. */
    fun validate(f: File): Boolean {
        if (!f.exists() || f.length() < 100) return false
        return try {
            val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val r = PdfRenderer(pfd)
                val ok = r.pageCount > 0
                r.close()
                ok
            } finally {
                pfd.close()
            }
        } catch (e: Exception) {
            false
        }
    }

    // ---------------------------------------------------------------- Merge
    fun merge(c: Context, uris: List<Uri>, out: File) {
        init(c)
        val streams = ArrayList<InputStream>()
        try {
            val merger = PDFMergerUtility()
            for (u in uris) {
                val s = c.contentResolver.openInputStream(u) ?: throw IOException("Cannot open file")
                streams.add(s)
                merger.addSource(s)
            }
            FileOutputStream(out).use { os ->
                merger.destinationStream = os
                merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
            }
        } finally {
            for (s in streams) try { s.close() } catch (_: Exception) {}
        }
    }

    // ---------------------------------------------------------------- Split / extract
    fun split(
        c: Context, uri: Uri, ranges: List<IntRange>, separate: Boolean,
        outDir: File, base: String, cancel: () -> Boolean
    ): List<File> {
        init(c)
        val files = ArrayList<File>()
        val input = c.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open file")
        try {
            val src = PDDocument.load(input, MemoryUsageSetting.setupTempFileOnly())
            try {
                if (separate) {
                    for (r in ranges) {
                        if (cancel()) throw CancelledException()
                        val f = FileUtil.uniqueFile(outDir, "${base}_${r.first}-${r.last}", "pdf")
                        files.add(f)
                        val dst = PDDocument(MemoryUsageSetting.setupTempFileOnly())
                        try {
                            for (p in r) dst.importPage(src.getPage(p - 1))
                            FileOutputStream(f).use { dst.save(it) }
                        } finally {
                            dst.close()
                        }
                    }
                } else {
                    val f = FileUtil.uniqueFile(outDir, "${base}_extract", "pdf")
                    files.add(f)
                    val dst = PDDocument(MemoryUsageSetting.setupTempFileOnly())
                    try {
                        for (r in ranges) for (p in r) dst.importPage(src.getPage(p - 1))
                        FileOutputStream(f).use { dst.save(it) }
                    } finally {
                        dst.close()
                    }
                }
            } finally {
                src.close()
            }
        } catch (e: Throwable) {
            files.forEach { it.delete() }
            throw e
        } finally {
            try { input.close() } catch (_: Exception) {}
        }
        return files
    }

    // ---------------------------------------------------------------- Compress
    /** Re-renders every page as a JPEG. Returns false (and deletes output) when not smaller. */
    fun compress(
        c: Context, uri: Uri, level: Int, out: File,
        cancel: () -> Boolean, progress: (Int, Int) -> Unit
    ): Boolean {
        init(c)
        val scale: Float
        val quality: Float
        when (level) {
            0 -> { scale = 2.0f; quality = 0.85f }
            1 -> { scale = 1.5f; quality = 0.70f }
            else -> { scale = 1.0f; quality = 0.50f }
        }
        val originalSize = FileUtil.stat(c, uri)?.get(0) ?: -1L
        val pfd = c.contentResolver.openFileDescriptor(uri, "r") ?: throw IOException("Cannot open file")
        try {
            val r = PdfRenderer(pfd)
            val doc = PDDocument(MemoryUsageSetting.setupTempFileOnly())
            try {
                val total = r.pageCount
                for (i in 0 until total) {
                    if (cancel()) throw CancelledException()
                    progress(i, total)
                    val page = r.openPage(i)
                    val pw = page.width
                    val ph = page.height
                    var s = scale
                    while ((pw * s) * (ph * s) > 5_000_000f) s *= 0.8f
                    val bw = max(1, (pw * s).toInt())
                    val bh = max(1, (ph * s).toInt())
                    val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                    try {
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    } finally {
                        page.close()
                    }
                    val img = JPEGFactory.createFromImage(doc, bmp, quality)
                    bmp.recycle()
                    val pd = PDPage(PDRectangle(pw.toFloat(), ph.toFloat()))
                    doc.addPage(pd)
                    PDPageContentStream(doc, pd).use { cs ->
                        cs.drawImage(img, 0f, 0f, pw.toFloat(), ph.toFloat())
                    }
                }
                progress(total, total)
                FileOutputStream(out).use { doc.save(it) }
            } finally {
                doc.close()
                r.close()
            }
        } finally {
            pfd.close()
        }
        if (!validate(out) || (originalSize > 0 && out.length() >= originalSize)) {
            out.delete()
            return false
        }
        return true
    }

    // ---------------------------------------------------------------- Images -> PDF
    private fun decode(c: Context, u: Uri, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        c.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = c.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null
        var degrees = 0f
        try {
            val o = c.contentResolver.openInputStream(u)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
            degrees = when (o) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (_: Exception) {
        }
        val rotated = if (degrees != 0f) {
            val m = Matrix().apply { postRotate(degrees) }
            val r = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
            if (r !== raw) raw.recycle()
            r
        } else raw
        // JPEG has no alpha: flatten transparent PNGs onto white.
        val flat = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(rotated, 0f, 0f, null)
        }
        rotated.recycle()
        return flat
    }

    /** size: "a4", "letter" or "fit" */
    fun imagesToPdf(
        c: Context, uris: List<Uri>, size: String, out: File,
        cancel: () -> Boolean, progress: (Int, Int) -> Unit
    ) {
        init(c)
        val doc = PDDocument(MemoryUsageSetting.setupTempFileOnly())
        try {
            uris.forEachIndexed { i, u ->
                if (cancel()) throw CancelledException()
                progress(i, uris.size)
                val bmp = decode(c, u, 2000)
                    ?: throw IOException("Could not read image ${FileUtil.queryName(c, u)}")
                val iw = bmp.width.toFloat()
                val ih = bmp.height.toFloat()
                val img = JPEGFactory.createFromImage(doc, bmp, 0.85f)
                bmp.recycle()

                val pageW: Float
                val pageH: Float
                val margin: Float
                if (size == "fit") {
                    pageW = iw * 0.48f
                    pageH = ih * 0.48f
                    margin = 0f
                } else {
                    val base = if (size == "letter") PDRectangle.LETTER else PDRectangle.A4
                    val landscape = iw > ih
                    pageW = if (landscape) base.height else base.width
                    pageH = if (landscape) base.width else base.height
                    margin = 24f
                }
                val availW = pageW - 2 * margin
                val availH = pageH - 2 * margin
                val k = min(availW / iw, availH / ih)
                val dw = iw * k
                val dh = ih * k
                val pd = PDPage(PDRectangle(pageW, pageH))
                doc.addPage(pd)
                PDPageContentStream(doc, pd).use { cs ->
                    cs.drawImage(img, (pageW - dw) / 2f, (pageH - dh) / 2f, dw, dh)
                }
            }
            progress(uris.size, uris.size)
            FileOutputStream(out).use { doc.save(it) }
        } finally {
            doc.close()
        }
    }

    // ---------------------------------------------------------------- PDF -> images
    fun pdfToImages(
        c: Context, uri: Uri, png: Boolean, outDir: File, base: String,
        cancel: () -> Boolean, progress: (Int, Int) -> Unit
    ): List<File> {
        val files = ArrayList<File>()
        val pfd = c.contentResolver.openFileDescriptor(uri, "r") ?: throw IOException("Cannot open file")
        try {
            val r = PdfRenderer(pfd)
            try {
                val total = r.pageCount
                for (i in 0 until total) {
                    if (cancel()) throw CancelledException()
                    progress(i, total)
                    val page = r.openPage(i)
                    var s = 2f
                    while ((page.width * s) * (page.height * s) > 8_000_000f) s *= 0.8f
                    val bmp = Bitmap.createBitmap(
                        max(1, (page.width * s).toInt()), max(1, (page.height * s).toInt()),
                        Bitmap.Config.ARGB_8888
                    )
                    try {
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    } finally {
                        page.close()
                    }
                    val f = File(outDir, "${base}_page${String.format("%03d", i + 1)}.${if (png) "png" else "jpg"}")
                    files.add(f)
                    FileOutputStream(f).use {
                        bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 90, it)
                    }
                    bmp.recycle()
                }
                progress(total, total)
            } finally {
                r.close()
            }
        } catch (e: Throwable) {
            files.forEach { it.delete() }
            throw e
        } finally {
            pfd.close()
        }
        return files
    }
}
