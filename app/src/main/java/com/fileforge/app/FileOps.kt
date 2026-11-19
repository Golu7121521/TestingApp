package com.fileforge.app

import com.topjohnwu.superuser.Shell
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import java.io.File
import java.io.IOException

/**
 * All file operations. Every function has a normal (java.io.File) path and a root path
 * (shell commands through libsu). Call from a background thread only.
 */
object FileOps {
    lateinit var cacheDir: File

    private fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"

    private fun sh(cmd: String): Shell.Result = Shell.cmd(cmd).exec()

    fun hasRoot(): Boolean = try {
        Shell.getShell().isRoot
    } catch (e: Throwable) {
        false
    }

    // ---------------------------------------------------------------- list

    fun list(path: String, root: Boolean): List<FileItem> = if (root) listRoot(path) else listLocal(path)

    private fun listLocal(path: String): List<FileItem> {
        val files = File(path).listFiles() ?: throw IOException("Cannot read this folder")
        return files.map {
            FileItem(
                path = it.absolutePath,
                name = it.name,
                isDir = it.isDirectory,
                size = if (it.isDirectory) 0L else it.length(),
                modified = it.lastModified(),
            )
        }
    }

    private fun listRoot(path: String): List<FileItem> {
        val res = sh("cd ${q(path)} && stat -L -c '%F|%s|%Y|%n' .[!.]* ..?* * 2>/dev/null")
        val base = if (path.endsWith("/")) path else "$path/"
        return res.out.mapNotNull { line ->
            val p = line.split("|", limit = 4)
            if (p.size < 4) return@mapNotNull null
            FileItem(
                path = base + p[3],
                name = p[3],
                isDir = p[0].contains("directory"),
                size = p[1].toLongOrNull() ?: 0L,
                modified = (p[2].toLongOrNull() ?: 0L) * 1000,
            )
        }
    }

    // ---------------------------------------------------------------- copy / move / delete

    private fun uniqueTarget(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var i = 1
        while (true) {
            f = File(dir, if (ext.isEmpty() || base == name) "$base ($i)" else "$base ($i).$ext")
            if (!f.exists()) return f
            i++
        }
    }

    fun copy(src: String, destDir: String, root: Boolean) {
        if (root) {
            check(sh("cp -a ${q(src)} ${q(destDir)}/").isSuccess) { "Copy failed: $src" }
            return
        }
        val s = File(src)
        val d = uniqueTarget(File(destDir), s.name)
        if (s.isDirectory) {
            if (d.absolutePath.startsWith(s.absolutePath + "/")) throw IOException("Can't copy a folder into itself")
            s.copyRecursively(d, false) { _, e -> throw e }
        } else {
            s.copyTo(d)
        }
    }

    fun move(src: String, destDir: String, root: Boolean) {
        if (root) {
            check(sh("mv ${q(src)} ${q(destDir)}/").isSuccess) { "Move failed: $src" }
            return
        }
        val s = File(src)
        if (s.parentFile?.absolutePath == File(destDir).absolutePath) return
        val d = uniqueTarget(File(destDir), s.name)
        if (d.absolutePath.startsWith(s.absolutePath + "/")) throw IOException("Can't move a folder into itself")
        if (!s.renameTo(d)) {
            if (s.isDirectory) s.copyRecursively(d, false) { _, e -> throw e } else s.copyTo(d)
            if (!s.deleteRecursively()) throw IOException("Copied, but could not delete the source")
        }
    }

    fun delete(path: String, root: Boolean) {
        if (root) {
            check(sh("rm -rf ${q(path)}").isSuccess) { "Delete failed: $path" }
            return
        }
        if (!File(path).deleteRecursively()) throw IOException("Could not delete $path")
    }

    fun rename(path: String, newName: String, root: Boolean) {
        if (newName.contains('/')) throw IOException("Name cannot contain /")
        val s = File(path)
        val d = File(s.parentFile, newName)
        if (root) {
            check(sh("mv ${q(path)} ${q(d.absolutePath)}").isSuccess) { "Rename failed" }
            return
        }
        if (d.exists()) throw IOException("'$newName' already exists")
        if (!s.renameTo(d)) throw IOException("Rename failed")
    }

    fun mkdir(path: String, root: Boolean) {
        if (root) {
            check(sh("mkdir -p ${q(path)}").isSuccess) { "Could not create folder" }
            return
        }
        val f = File(path)
        if (f.exists()) throw IOException("Already exists")
        if (!f.mkdirs()) throw IOException("Could not create folder")
    }

    // ---------------------------------------------------------------- zip

    fun zip(paths: List<String>, destZip: String) {
        val out = File(destZip)
        if (out.exists()) throw IOException("${out.name} already exists")
        val zf = ZipFile(out)
        val params = ZipParameters()
        for (p in paths) {
            val f = File(p)
            if (f.isDirectory) zf.addFolder(f, params) else zf.addFile(f, params)
        }
    }

    fun unzip(zipPath: String, destDir: String) {
        ZipFile(File(zipPath)).extractAll(destDir)
    }

    // ---------------------------------------------------------------- text

    private const val MAX_TEXT = 5_000_000L

    fun readText(path: String, root: Boolean): String {
        val bytes: ByteArray = if (root) {
            val tmp = File.createTempFile("rd", ".tmp", cacheDir)
            try {
                check(sh("cat ${q(path)} > ${q(tmp.absolutePath)} && chmod 666 ${q(tmp.absolutePath)}").isSuccess) {
                    "Cannot read file"
                }
                tmp.readBytes()
            } finally {
                tmp.delete()
            }
        } else {
            val f = File(path)
            if (f.length() > MAX_TEXT) throw IOException("File too large for editor (max 5 MB)")
            f.readBytes()
        }
        if (bytes.size > MAX_TEXT) throw IOException("File too large for editor (max 5 MB)")
        val n = minOf(bytes.size, 8000)
        for (i in 0 until n) if (bytes[i] == 0.toByte()) throw IOException("Binary file - cannot edit as text")
        return String(bytes, Charsets.UTF_8)
    }

    fun writeText(path: String, text: String, root: Boolean) {
        if (!root) {
            File(path).writeText(text, Charsets.UTF_8)
            return
        }
        val tmp = File.createTempFile("wr", ".tmp", cacheDir)
        try {
            tmp.writeText(text, Charsets.UTF_8)
            check(sh("cat ${q(tmp.absolutePath)} > ${q(path)}").isSuccess) { "Cannot write file" }
        } finally {
            tmp.delete()
        }
    }
}
