package com.example.devicehealth.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.devicehealth.Battery
import com.example.devicehealth.DeviceInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

data class Sample(val time: Long, val level: Int, val tempC: Float?, val charging: Boolean)

data class ChargeStats(val ratePctPerHour: Double?, val minutesToFull: Long?)

object BatteryHistory {
    private const val FILE = "battery_history.json"
    private const val MAX_SAMPLES = 2000
    private const val MIN_GAP_MS = 5 * 60 * 1000L

    @Volatile
    private var lastWrite = 0L

    fun read(ctx: Context): List<Sample> {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Sample(
                    time = o.getLong("t"),
                    level = o.getInt("l"),
                    tempC = if (o.has("c")) o.getDouble("c").toFloat() else null,
                    charging = o.getBoolean("g"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun append(ctx: Context, b: Battery, force: Boolean = false) {
        if (b.level < 0) return
        val now = System.currentTimeMillis()
        if (!force && now - lastWrite < MIN_GAP_MS) return
        lastWrite = now
        val list = (read(ctx) + Sample(now, b.level, b.tempC, b.charging)).takeLast(MAX_SAMPLES)
        val arr = JSONArray()
        list.forEach { s ->
            val o = JSONObject().put("t", s.time).put("l", s.level).put("g", s.charging)
            s.tempC?.let { o.put("c", it.toDouble()) }
            arr.put(o)
        }
        File(ctx.filesDir, FILE).writeText(arr.toString())
    }

    /** Charge speed from the most recent continuous charging run. */
    fun chargeStats(samples: List<Sample>): ChargeStats {
        val run = samples.takeLastWhile { it.charging }
        if (run.size < 2) return ChargeStats(null, null)
        val first = run.first()
        val last = run.last()
        val hours = (last.time - first.time) / 3_600_000.0
        if (hours <= 0 || last.level <= first.level) return ChargeStats(null, null)
        val rate = (last.level - first.level) / hours
        val minutes = ((100 - last.level) / rate * 60).toLong()
        return ChargeStats(rate, minutes)
    }
}

/** Runs every ~15 minutes in background, saves history and sends alerts. */
class BatteryLogWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val snap = DeviceInfo.snapshot(applicationContext)
        BatteryHistory.append(applicationContext, snap.battery, force = true)
        Alerts.check(applicationContext, snap.battery)
        return Result.success()
    }
}

object Alerts {
    private const val CHANNEL = "battery_alerts"

    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<BatteryLogWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(ctx)
            .enqueueUniquePeriodicWork("battery_log", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun check(ctx: Context, b: Battery) {
        if (!Prefs(ctx).alertsEnabled) return
        if (b.charging && b.level >= 80) {
            notify(ctx, 1, "Battery at ${b.level}%", "Unplug the charger to protect battery health.")
        }
        val temp = b.tempC
        if (temp != null && temp >= 42f) {
            notify(ctx, 2, "Phone is getting hot",
                String.format(Locale.US, "Battery is at %.1f C. Close heavy apps.", temp))
        }
    }

    private fun notify(ctx: Context, id: Int, title: String, text: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Battery alerts", NotificationManager.IMPORTANCE_DEFAULT)
        )
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        nm.notify(id, n)
    }
}
