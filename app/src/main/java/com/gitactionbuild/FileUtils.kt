package com.gitactionbuild

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

// Pure helpers: no Activity state, so they can be used and tested on their own.

fun sz(f: File) = if (f.isDirectory) (f.listFiles()?.size ?: 0).toString() + " items" else if (f.length() < 1024) f.length().toString() + " B" else (f.length() / 1024).toString() + " KB"

fun pbar(p: Int): String {
    val q = p.coerceIn(0, 100)
    val n = q / 10
    return "[" + "#".repeat(n) + "-".repeat(10 - n) + "] " + q + "%"
}

fun bar10(p: Int): String {
    val q = p.coerceIn(0, 100)
    val n = q / 10
    return "[" + "\u2588".repeat(n) + "\u2591".repeat(10 - n) + "] " + q + "%"
}

// clone progress: [#####-----] 50%

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

fun clean(t: String): String {
    val ansi = Regex("\u001B\\[[0-9;]*m"); val ts = Regex("^\\uFEFF?\\d{4}-\\d\\d-\\d\\dT[\\d:.]+Z ")
    return t.lines().joinToString("\n") { ts.replace(ansi.replace(it, ""), "") }
}
