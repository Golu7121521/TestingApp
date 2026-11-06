package com.example.devicehealth.system

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.VibrationEffect
import android.os.Vibrator
import java.io.File
import java.security.MessageDigest

// ---------- Flashlight ----------

object Flashlight {
    fun torchCameraId(ctx: Context): String? = runCatching {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cm.cameraIdList.firstOrNull { id ->
            cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()

    fun set(ctx: Context, on: Boolean): Boolean = runCatching {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = torchCameraId(ctx) ?: return false
        cm.setTorchMode(id, on)
        true
    }.getOrDefault(false)
}

// ---------- Vibration and speaker ----------

fun vibrate(ctx: Context, ms: Long = 500) {
    val v = ctx.getSystemService(Vibrator::class.java) ?: return
    v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
}

fun playBeep() {
    val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
    tone.startTone(ToneGenerator.TONE_PROP_BEEP, 600)
    Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 900)
}

// ---------- Cameras ----------

data class CameraItem(val id: String, val facing: String, val megapixels: Double?, val flash: Boolean)

fun cameras(ctx: Context): List<CameraItem> = runCatching {
    val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    cm.cameraIdList.map { id ->
        val c = cm.getCameraCharacteristics(id)
        val facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
            CameraCharacteristics.LENS_FACING_FRONT -> "Front"
            CameraCharacteristics.LENS_FACING_BACK -> "Back"
            else -> "External"
        }
        val px = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        CameraItem(
            id = id,
            facing = facing,
            megapixels = px?.let { it.width.toDouble() * it.height / 1_000_000.0 },
            flash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
        )
    }
}.getOrDefault(emptyList())

// ---------- CPU usage (from /proc/stat) ----------

object CpuSampler {
    private var prevTotal = 0L
    private var prevIdle = 0L

    /** Returns CPU busy percentage since the previous call, or null if unavailable. */
    fun sample(): Double? = runCatching {
        val line = File("/proc/stat").useLines { it.first() }
        val parts = line.trim().split(Regex("\\s+")).drop(1).map { it.toLong() }
        val idle = parts[3] + parts.getOrElse(4) { 0L }
        val total = parts.sum()
        val dTotal = total - prevTotal
        val dIdle = idle - prevIdle
        prevTotal = total
        prevIdle = idle
        if (dTotal <= 0) null else (1.0 - dIdle.toDouble() / dTotal) * 100.0
    }.getOrNull()
}

// ---------- Benchmark ----------

data class BenchResult(val cpuMs: Long, val memMs: Long)

fun runBenchmark(): BenchResult {
    val cpuStart = System.nanoTime()
    val n = 3_000_000
    val composite = BooleanArray(n + 1) { true }
    composite[0] = false
    composite[1] = false
    var i = 2
    while (i.toLong() * i <= n) {
        if (composite[i]) {
            var j = i * i
            while (j <= n) {
                composite[j] = false
                j += i
            }
        }
        i++
    }
    val cpuMs = (System.nanoTime() - cpuStart) / 1_000_000

    val memStart = System.nanoTime()
    val a = IntArray(8_000_000) { it }
    val b = IntArray(a.size)
    System.arraycopy(a, 0, b, 0, a.size)
    var sum = 0L
    for (x in b) sum += x
    check(sum >= 0)
    val memMs = (System.nanoTime() - memStart) / 1_000_000

    return BenchResult(cpuMs, memMs)
}

// ---------- Cache ----------

private fun dirSize(f: File): Long = when {
    !f.exists() -> 0L
    f.isFile -> f.length()
    else -> f.listFiles()?.sumOf { dirSize(it) } ?: 0L
}

fun cacheBytes(ctx: Context): Long =
    dirSize(ctx.cacheDir) + (ctx.externalCacheDir?.let { dirSize(it) } ?: 0L)

fun clearCache(ctx: Context) {
    ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    ctx.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
}

// ---------- Installed apps ----------

data class AppItem(val label: String, val pkg: String, val version: String, val system: Boolean)

fun installedApps(ctx: Context): List<AppItem> = runCatching {
    val pm = ctx.packageManager
    pm.getInstalledPackages(0).map { p ->
        val ai = p.applicationInfo
        AppItem(
            label = ai?.loadLabel(pm)?.toString() ?: p.packageName,
            pkg = p.packageName,
            version = p.versionName ?: "",
            system = ai != null && (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
        )
    }.sortedBy { it.label.lowercase() }
}.getOrDefault(emptyList())

fun hasUsageAccess(ctx: Context): Boolean {
    val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    @Suppress("DEPRECATION")
    val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}

/** Foreground time per app in the last 24 hours, as (label, minutes). */
fun screenTime(ctx: Context): List<Pair<String, Long>> = runCatching {
    val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val end = System.currentTimeMillis()
    val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, end - 24L * 3_600_000L, end)
    val pm = ctx.packageManager
    stats.groupBy { it.packageName }
        .map { (pkg, list) -> pkg to list.sumOf { it.totalTimeInForeground } }
        .filter { it.second > 60_000 }
        .sortedByDescending { it.second }
        .take(15)
        .map { (pkg, ms) -> labelFor(pm, pkg) to ms / 60_000 }
}.getOrDefault(emptyList())

private fun labelFor(pm: PackageManager, pkg: String): String =
    runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

// ---------- Storage scan ----------

@Suppress("DEPRECATION")
fun primaryStorageRoot(): File = Environment.getExternalStorageDirectory()

data class FileItem(val path: String, val size: Long)

data class StorageScan(val scanned: Int, val large: List<FileItem>, val duplicates: List<List<FileItem>>)

fun scanStorage(root: File, largeMb: Long = 50, maxFiles: Int = 20_000): StorageScan {
    val files = root.walkTopDown()
        .filter { it.isFile && !it.isHidden }
        .take(maxFiles)
        .map { FileItem(it.absolutePath, it.length()) }
        .toList()

    val large = files
        .filter { it.size >= largeMb * 1024 * 1024 }
        .sortedByDescending { it.size }
        .take(30)

    // Only hash files that share a size with another file, which is much faster.
    val candidates = files
        .filter { it.size >= 1024 * 1024 }
        .groupBy { it.size }
        .values
        .filter { it.size > 1 }
        .flatten()
        .take(400)

    val dupGroups = candidates
        .groupBy { md5(File(it.path)) ?: "" }
        .filterKeys { it.isNotEmpty() }
        .values
        .filter { it.size > 1 }
        .sortedByDescending { it.size * it.first().size }
        .take(20)

    return StorageScan(files.size, large, dupGroups)
}

private fun md5(f: File): String? = runCatching {
    val md = MessageDigest.getInstance("MD5")
    f.inputStream().use { input ->
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
    }
    md.digest().joinToString("") { "%02x".format(it) }
}.getOrNull()
