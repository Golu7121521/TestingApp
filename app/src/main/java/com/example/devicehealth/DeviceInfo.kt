package com.example.devicehealth

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.ui.graphics.vector.ImageVector
import java.io.File
import java.util.Locale

data class InfoItem(val label: String, val value: String)

data class InfoSection(val title: String, val icon: ImageVector, val items: List<InfoItem>)

data class Battery(
    val level: Int,
    val charging: Boolean,
    val statusText: String,
    val source: String,
    val tempC: Float?,
    val voltageV: Float?,
    val healthText: String,
    val healthPct: Int?,
    val designMah: Long?,
    val chargeMah: Long?,
    val currentMa: Long?,
    val cycles: String,
    val tech: String,
)

data class Snapshot(val battery: Battery, val sections: List<InfoSection>)

object DeviceInfo {

    fun snapshot(ctx: Context): Snapshot {
        val battery = readBattery(ctx)
        val sections = listOf(
            InfoSection("Battery", Icons.Filled.BatteryFull, batteryItems(battery)),
            InfoSection(
                "Temperature",
                Icons.Filled.Thermostat,
                listOf(
                    InfoItem("Battery temp", battery.tempC?.let { String.format(Locale.US, "%.1f °C", it) } ?: "N/A"),
                    InfoItem(
                        "CPU / thermal (max)",
                        thermalMaxC()?.let { String.format(Locale.US, "%.1f °C", it) } ?: "N/A (restricted)"
                    ),
                )
            ),
            InfoSection("RAM", Icons.Filled.Memory, ramItems(ctx)),
            InfoSection("Storage", Icons.Filled.SdStorage, storageItems()),
            InfoSection("Processor", Icons.Filled.DeveloperBoard, cpuItems()),
            InfoSection("Display", Icons.Filled.Fullscreen, displayItems(ctx)),
            InfoSection("Device and OS", Icons.Filled.PhoneAndroid, deviceItems()),
            InfoSection(
                "Uptime",
                Icons.Filled.Schedule,
                listOf(InfoItem("Since boot", formatDuration(SystemClock.elapsedRealtime())))
            ),
        )
        return Snapshot(battery, sections)
    }

    // ---------- Battery ----------

    private fun readBattery(ctx: Context): Battery {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val rawLevel = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = (i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).takeIf { it > 0 } ?: 100
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val healthCode = i?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val tempRaw = i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val voltageMv = i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val tech = i?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "N/A"

        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val chargeUah = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val currentUa = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

        val designUah = readSys("/sys/class/power_supply/battery/charge_full_design")?.toLongOrNull()
        val fullUah = readSys("/sys/class/power_supply/battery/charge_full")?.toLongOrNull()
        val healthPct = if (designUah != null && fullUah != null && designUah > 0) {
            (fullUah * 100 / designUah).toInt()
        } else null

        val designMah = designUah?.let { it / 1000 } ?: powerProfileCapacity(ctx)

        return Battery(
            level = if (rawLevel >= 0) rawLevel * 100 / scale else -1,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL,
            statusText = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
                else -> "Unknown"
            },
            source = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC charger"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                else -> "On battery"
            },
            tempC = if (tempRaw == -1) null else tempRaw / 10f,
            voltageV = if (voltageMv > 0) voltageMv / 1000f else null,
            healthText = healthText(healthCode),
            healthPct = healthPct,
            designMah = designMah,
            chargeMah = chargeUah.takeIf { it > 0 }?.let { it / 1000 },
            currentMa = currentUa.takeIf { it != Long.MIN_VALUE }?.let { it / 1000 },
            cycles = readSys("/sys/class/power_supply/battery/cycle_count") ?: "N/A",
            tech = tech,
        )
    }

    private fun batteryItems(b: Battery) = listOf(
        InfoItem("Level", "${b.level}%"),
        InfoItem("Status", b.statusText),
        InfoItem("Charging via", b.source),
        InfoItem("Health (system)", b.healthText),
        InfoItem("Health estimate", b.healthPct?.let { "$it%" } ?: "N/A (restricted)"),
        InfoItem("Design capacity", b.designMah?.let { "$it mAh" } ?: "N/A"),
        InfoItem("Current charge", b.chargeMah?.let { "$it mAh" } ?: "N/A"),
        InfoItem("Current now", b.currentMa?.let { "$it mA" } ?: "N/A"),
        InfoItem("Voltage", b.voltageV?.let { String.format(Locale.US, "%.2f V", it) } ?: "N/A"),
        InfoItem("Cycle count", b.cycles),
        InfoItem("Technology", b.tech),
    )

    private fun healthText(code: Int): String = when (code) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
        else -> "Unknown"
    }

    private fun powerProfileCapacity(ctx: Context): Long? = runCatching {
        val cls = Class.forName("com.android.internal.os.PowerProfile")
        val obj = cls.getConstructor(Context::class.java).newInstance(ctx)
        (cls.getMethod("getBatteryCapacity").invoke(obj) as Double).toLong()
    }.getOrNull()

    // ---------- Temperature ----------

    private fun thermalMaxC(): Float? = runCatching {
        File("/sys/class/thermal").listFiles()
            ?.filter { it.name.startsWith("thermal_zone") }
            ?.mapNotNull { zone ->
                File(zone, "temp").takeIf { it.canRead() }?.readText()?.trim()?.toIntOrNull()
            }
            ?.filter { it in 1..150_000 }
            ?.maxOrNull()
            ?.let { it / 1000f }
    }.getOrNull()

    // ---------- RAM ----------

    private fun ramItems(ctx: Context): List<InfoItem> {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val used = mi.totalMem - mi.availMem
        val pct = if (mi.totalMem > 0) used * 100 / mi.totalMem else 0
        return listOf(
            InfoItem("Total", fmtBytes(mi.totalMem)),
            InfoItem("Used", fmtBytes(used)),
            InfoItem("Available", fmtBytes(mi.availMem)),
            InfoItem("Usage", "$pct%"),
            InfoItem("Low memory", if (mi.lowMemory) "Yes" else "No"),
        )
    }

    // ---------- Storage ----------

    private fun storageItems(): List<InfoItem> {
        val st = StatFs(Environment.getDataDirectory().path)
        val total = st.blockCountLong * st.blockSizeLong
        val free = st.availableBlocksLong * st.blockSizeLong
        val used = total - free
        val pct = if (total > 0) used * 100 / total else 0
        return listOf(
            InfoItem("Total", fmtBytes(total)),
            InfoItem("Used", fmtBytes(used)),
            InfoItem("Free", fmtBytes(free)),
            InfoItem("Usage", "$pct%"),
        )
    }

    // ---------- CPU ----------

    private fun cpuItems(): List<InfoItem> {
        val cores = Runtime.getRuntime().availableProcessors()
        val maxKhz = (0 until cores)
            .mapNotNull { readSys("/sys/devices/system/cpu/cpu$it/cpufreq/cpuinfo_max_freq")?.toLongOrNull() }
            .maxOrNull()
        val hardware = runCatching { File("/proc/cpuinfo").readLines() }.getOrDefault(emptyList())
            .firstOrNull { it.startsWith("Hardware") }
            ?.substringAfter(":")?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: Build.HARDWARE
        return listOf(
            InfoItem("Chipset", hardware),
            InfoItem("Cores", "$cores"),
            InfoItem(
                "Max frequency",
                maxKhz?.let { String.format(Locale.US, "%.2f GHz", it / 1_000_000.0) } ?: "N/A"
            ),
            InfoItem("ABI", Build.SUPPORTED_ABIS.joinToString()),
        )
    }

    // ---------- Display ----------

    private fun displayItems(ctx: Context): List<InfoItem> {
        val dm = ctx.resources.displayMetrics
        @Suppress("DEPRECATION")
        val hz = (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.refreshRate
        return listOf(
            InfoItem("Resolution", "${dm.widthPixels} x ${dm.heightPixels}"),
            InfoItem("Density", "${dm.densityDpi} dpi"),
            InfoItem("Refresh rate", String.format(Locale.US, "%.0f Hz", hz)),
        )
    }

    // ---------- Device and OS ----------

    private fun deviceItems() = listOf(
        InfoItem("Brand", Build.MANUFACTURER),
        InfoItem("Model", Build.MODEL),
        InfoItem("Device codename", Build.DEVICE),
        InfoItem("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
        InfoItem("Build", Build.DISPLAY),
        InfoItem("Security patch", Build.VERSION.SECURITY_PATCH),
    )

    // ---------- Helpers ----------

    private fun readSys(path: String): String? =
        runCatching { File(path).readText().trim() }.getOrNull()

    fun fmtBytes(b: Long): String = when {
        b >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", b / 1073741824.0)
        b >= 1L shl 20 -> String.format(Locale.US, "%.0f MB", b / 1048576.0)
        else -> "$b B"
    }

    private fun formatDuration(ms: Long): String {
        val s = ms / 1000
        val d = s / 86400
        val h = (s % 86400) / 3600
        val m = (s % 3600) / 60
        return "${d}d ${h}h ${m}m"
    }
}
