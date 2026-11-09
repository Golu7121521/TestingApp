package com.example.filerecovery.data

import java.io.File
import java.io.FileOutputStream

/**
 * ROOT ZAROORI. /data partition ko raw block device se padhta hai aur
 * JPEG / PNG / PDF ke signature dhund kar file wapas banata hai (file carving).
 *
 * Important: agar phone FBE (file-based encryption) use karta hai, to raw data
 * encrypted hota hai aur file carving se file nahi khulegi.
 */
object RootCarver {

    private class Sig(
        val ext: String,
        val header: ByteArray,
        val footer: ByteArray,
        val maxLen: Long,
        val category: FileCategory
    )

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private val SIGS = listOf(
        Sig("jpg", bytes(0xFF, 0xD8, 0xFF), bytes(0xFF, 0xD9), 30L shl 20, FileCategory.PHOTOS),
        Sig(
            "png",
            bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
            bytes(0x49, 0x45, 0x4E, 0x44, 0xAE, 0x42, 0x60, 0x82),
            30L shl 20,
            FileCategory.PHOTOS
        ),
        Sig("pdf", "%PDF-".toByteArray(), "%%EOF".toByteArray(), 200L shl 20, FileCategory.DOCS)
    )

    private val MAX_HEADER = SIGS.maxOf { it.header.size }
    private const val MIN_SIZE = 10L * 1024

    fun isRooted(): Boolean = runCatching {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        val out = p.inputStream.bufferedReader().use { it.readText() }
        p.waitFor()
        out.contains("uid=0")
    }.getOrDefault(false)

    fun findDataDevice(): String? = runCatching {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /proc/mounts"))
        val lines = p.inputStream.bufferedReader().use { it.readLines() }
        p.waitFor()
        lines
            .map { it.trim().split(Regex("\\s+")) }
            .firstOrNull { it.size >= 2 && it[1] == "/data" }
            ?.get(0)
    }.getOrNull()

    fun deepScan(device: String, outDir: File, onProgress: (String) -> Unit): List<RecoverableFile> {
        outDir.mkdirs()
        val results = mutableListOf<RecoverableFile>()
        val buf = ByteArray(1 shl 20)
        var carried = ByteArray(0)
        var carving: Carving? = null
        var totalRead = 0L
        var chunks = 0

        try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat $device"))
            proc.inputStream.use { input ->
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    totalRead += read
                    chunks++
                    if (chunks % 64 == 0) onProgress("Scan ho raha hai: ${totalRead shr 20} MB")

                    val data = carried + buf.copyOf(read)
                    var pos = 0
                    var keepFrom = data.size

                    while (pos < data.size) {
                        val c = carving
                        if (c == null) {
                            val hit = findHeader(data, pos)
                            if (hit == null) {
                                keepFrom = maxOf(pos, data.size - (MAX_HEADER - 1))
                                break
                            }
                            carving = Carving(hit.first, File(outDir, "carved_${System.nanoTime()}.${hit.first.ext}"))
                            pos = hit.second
                        } else {
                            val footerAt = indexOf(data, c.sig.footer, pos)
                            if (footerAt >= 0) {
                                val end = footerAt + c.sig.footer.size
                                c.write(data, pos, end - pos)
                                pos = end
                                carving = null
                                c.finish()?.let { results += it }
                            } else {
                                val writeEnd = maxOf(pos, data.size - (c.sig.footer.size - 1))
                                c.write(data, pos, writeEnd - pos)
                                pos = writeEnd
                                keepFrom = pos
                                if (c.written >= c.sig.maxLen) {
                                    c.abort()
                                    carving = null
                                }
                                break
                            }
                        }
                    }
                    carried = data.copyOfRange(keepFrom, data.size)
                }
            }
            proc.waitFor()
        } catch (e: Exception) {
            // device read fail hua, jo mila wo return karenge
        }
        carving?.abort()
        return results
    }

    private class Carving(val sig: Sig, val file: File) {
        private val out = FileOutputStream(file)
        var written = 0L

        fun write(d: ByteArray, off: Int, len: Int) {
            if (len <= 0) return
            out.write(d, off, len)
            written += len
        }

        fun finish(): RecoverableFile? {
            out.close()
            return if (file.length() >= MIN_SIZE) {
                RecoverableFile(file, file.name, 0L, file.length(), sig.category, Source.CARVED)
            } else {
                file.delete()
                null
            }
        }

        fun abort() {
            runCatching { out.close() }
            file.delete()
        }
    }

    private fun findHeader(data: ByteArray, from: Int): Pair<Sig, Int>? {
        var best: Pair<Sig, Int>? = null
        for (s in SIGS) {
            val i = indexOf(data, s.header, from)
            if (i >= 0 && (best == null || i < best.second)) best = s to i
        }
        return best
    }

    private fun indexOf(data: ByteArray, pat: ByteArray, from: Int): Int {
        val last = data.size - pat.size
        var i = from
        outer@ while (i <= last) {
            for (j in pat.indices) {
                if (data[i + j] != pat[j]) {
                    i++
                    continue@outer
                }
            }
            return i
        }
        return -1
    }
}
