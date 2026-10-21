package com.advancedpdf.toolkit

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.text.DateFormat
import java.util.Date

data class FileItem(
    val uri: Uri,
    val name: String,
    val size: Long,
    val modified: Long,
    val opened: Long,
    /** Non-null only for files created by this app (can be renamed/deleted). */
    val file: File?,
    val available: Boolean
)

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

object FileUtil {
    fun outputDir(c: Context): File {
        val d = c.getExternalFilesDir("PDFs") ?: File(c.filesDir, "PDFs")
        d.mkdirs()
        return d
    }

    fun imagesDir(c: Context): File {
        val d = c.getExternalFilesDir("Images") ?: File(c.filesDir, "Images")
        d.mkdirs()
        return d
    }

    fun uniqueFile(dir: File, base: String, ext: String): File {
        var f = File(dir, if (ext.isEmpty()) base else "$base.$ext")
        var i = 1
        while (f.exists()) {
            f = File(dir, if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext")
            i++
        }
        return f
    }

    fun safeBase(name: String): String {
        val b = name.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9 _.-]"), "_").trim().take(60)
        return if (b.isBlank()) "document" else b
    }

    fun fileUri(c: Context, f: File): Uri =
        FileProvider.getUriForFile(c, c.packageName + ".fileprovider", f)

    fun queryName(c: Context, uri: Uri): String {
        try {
            if (uri.scheme == "file") return File(uri.path ?: "").name
            c.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) {
                    val s = it.getString(0)
                    if (!s.isNullOrBlank()) return s
                }
            }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment ?: "document.pdf"
    }

    /** Returns [size, lastModified] or null when the file cannot be reached. */
    fun stat(c: Context, uri: Uri): LongArray? {
        try {
            if (uri.scheme == "file") {
                val f = File(uri.path ?: return null)
                return if (f.exists()) longArrayOf(f.length(), f.lastModified()) else null
            }
            c.contentResolver.query(uri, null, null, null, null)?.use { cur ->
                if (!cur.moveToFirst()) return null
                val si = cur.getColumnIndex(OpenableColumns.SIZE)
                val mi = cur.getColumnIndex("last_modified")
                val size = if (si >= 0 && !cur.isNull(si)) cur.getLong(si) else -1L
                val mod = if (mi >= 0 && !cur.isNull(mi)) cur.getLong(mi) else 0L
                return longArrayOf(size, mod)
            }
        } catch (_: Exception) {
        }
        return null
    }

    fun formatSize(b: Long): String = when {
        b < 0 -> "—"
        b < 1024 -> "$b B"
        b < 1024 * 1024 -> String.format("%.1f KB", b / 1024.0)
        else -> String.format("%.1f MB", b / (1024.0 * 1024.0))
    }

    fun formatDate(ms: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))

    fun fromEntry(c: Context, e: Store.Entry): FileItem {
        val u = Uri.parse(e.uri)
        val s = stat(c, u)
        return FileItem(u, e.name, s?.get(0) ?: -1L, s?.get(1) ?: 0L, e.time, null, s != null)
    }

    fun listMine(c: Context): List<FileItem> {
        val opened = Store.recents(c).associate { it.uri to it.time }
        val files = outputDir(c).listFiles { f -> f.isFile && f.name.endsWith(".pdf", true) } ?: return emptyList()
        return files.map {
            val u = fileUri(c, it)
            FileItem(u, it.name, it.length(), it.lastModified(), opened[u.toString()] ?: 0L, it, true)
        }
    }

    /** Lists PDFs the app is allowed to see through MediaStore. */
    fun scanMediaStore(c: Context): List<FileItem> {
        val out = ArrayList<FileItem>()
        val col = MediaStore.Files.getContentUri("external")
        val proj = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )
        try {
            c.contentResolver.query(
                col, proj, MediaStore.Files.FileColumns.MIME_TYPE + "=?", arrayOf("application/pdf"),
                MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC"
            )?.use { cur ->
                while (cur.moveToNext()) {
                    out.add(
                        FileItem(
                            ContentUris.withAppendedId(col, cur.getLong(0)),
                            cur.getString(1) ?: "document.pdf",
                            cur.getLong(2), cur.getLong(3) * 1000L, 0L, null, true
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    /** Deletes cache contents, returns bytes freed. */
    fun cleanTemp(c: Context): Long {
        var n = 0L
        c.cacheDir.listFiles()?.forEach { n += deleteRec(it) }
        return n
    }

    private fun deleteRec(f: File): Long {
        var n = 0L
        if (f.isDirectory) f.listFiles()?.forEach { n += deleteRec(it) }
        val len = if (f.isFile) f.length() else 0L
        if (f.delete()) n += len
        return n
    }
}

fun openReader(c: Context, uri: Uri) {
    c.startActivity(
        Intent(c, ReaderActivity::class.java).setData(uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    )
}

fun shareUris(c: Context, uris: List<Uri>, mime: String) {
    val i = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    }
    i.type = mime
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    c.startActivity(Intent.createChooser(i, "Share"))
}

/** Binds an item_file row. */
object FileRow {
    fun bind(
        v: View, item: FileItem,
        onClick: () -> Unit, onLong: () -> Unit, onStarChanged: () -> Unit
    ) {
        val c = v.context
        v.findViewById<TextView>(R.id.name).text = item.name
        val parts = ArrayList<String>()
        if (!item.available) parts.add("File unavailable")
        else {
            if (item.size >= 0) parts.add(FileUtil.formatSize(item.size))
            if (item.modified > 0) parts.add(FileUtil.formatDate(item.modified))
        }
        v.findViewById<TextView>(R.id.meta).text = parts.joinToString(" • ")
        v.alpha = if (item.available) 1f else 0.5f
        val star = v.findViewById<ImageButton>(R.id.star)
        fun paint() {
            val on = Store.isStarred(c, item.uri.toString())
            star.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(c, if (on) R.color.accent else R.color.divider)
            )
            star.contentDescription = if (on) "Remove from favorites" else "Add to favorites"
        }
        paint()
        star.setOnClickListener {
            Store.toggleStar(c, item.uri.toString(), item.name)
            paint()
            onStarChanged()
        }
        v.setOnClickListener { onClick() }
        v.setOnLongClickListener { onLong(); true }
    }
}

/** Long-press / context menu for a file. */
object FileActions {
    fun show(ctx: Context, item: FileItem, onChanged: () -> Unit) {
        val uriStr = item.uri.toString()
        val starred = Store.isStarred(ctx, uriStr)
        val labels = ArrayList<String>()
        val acts = ArrayList<() -> Unit>()

        labels.add("Open"); acts.add { openOrWarn(ctx, item) }
        labels.add("Share"); acts.add {
            if (item.available) shareUris(ctx, listOf(item.uri), "application/pdf")
            else ctx.toast("This file is unavailable.")
        }
        labels.add(if (starred) "Remove from favorites" else "Add to favorites")
        acts.add { Store.toggleStar(ctx, uriStr, item.name); onChanged() }

        val f = item.file
        if (f != null) {
            labels.add("Rename"); acts.add { rename(ctx, f, onChanged) }
            labels.add("Delete"); acts.add { delete(ctx, item, f, onChanged) }
        } else {
            labels.add("Remove from list"); acts.add {
                Store.removeRecent(ctx, uriStr); Store.removeStar(ctx, uriStr); onChanged()
            }
        }
        MaterialAlertDialogBuilder(ctx).setTitle(item.name)
            .setItems(labels.toTypedArray()) { _, i -> acts[i]() }.show()
    }

    fun openOrWarn(ctx: Context, item: FileItem) {
        if (item.available) openReader(ctx, item.uri)
        else ctx.toast("This file is unavailable. It may have been moved or deleted.")
    }

    private fun rename(ctx: Context, f: File, done: () -> Unit) {
        val input = EditText(ctx)
        input.setText(f.name.removeSuffix(".pdf"))
        MaterialAlertDialogBuilder(ctx).setTitle("Rename file").setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val base = FileUtil.safeBase(input.text.toString())
                val target = File(f.parentFile, "$base.pdf")
                if (target.exists()) ctx.toast("A file with that name already exists.")
                else if (f.renameTo(target)) {
                    val old = FileUtil.fileUri(ctx, f).toString()
                    Store.removeRecent(ctx, old); Store.removeStar(ctx, old)
                    done()
                } else ctx.toast("Could not rename the file.")
            }.setNegativeButton("Cancel", null).show()
    }

    private fun delete(ctx: Context, item: FileItem, f: File, done: () -> Unit) {
        MaterialAlertDialogBuilder(ctx).setTitle("Delete file?")
            .setMessage("“${item.name}” will be permanently deleted.")
            .setPositiveButton("Delete") { _, _ ->
                if (f.delete()) {
                    Store.removeRecent(ctx, item.uri.toString()); Store.removeStar(ctx, item.uri.toString())
                    done()
                } else ctx.toast("Could not delete the file.")
            }.setNegativeButton("Cancel", null).show()
    }
}
