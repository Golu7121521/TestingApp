package com.example.devicehealth.system

import com.example.devicehealth.Snapshot

data class HealthReport(val score: Int, val tips: List<String>)

/** Combines battery, temperature, storage and RAM into a 0-100 score with tips. */
fun healthReport(s: Snapshot): HealthReport {
    val b = s.battery

    val battery = b.healthPct?.coerceIn(0, 100) ?: when (b.healthText) {
        "Good" -> 90
        "Unknown" -> 80
        else -> 50
    }

    val t = b.tempC
    val temp = if (t == null) 80 else when {
        t <= 35f -> 100
        t <= 40f -> 80
        t <= 45f -> 50
        else -> 20
    }

    val storage = s.metrics.storageFreePct?.let { p ->
        when {
            p >= 25 -> 100
            p >= 10 -> 70
            else -> 40
        }
    } ?: 80

    val ram = s.metrics.ramAvailPct?.let { p ->
        when {
            p >= 30 -> 100
            p >= 15 -> 70
            else -> 40
        }
    } ?: 80

    val score = (battery + temp + storage + ram) / 4

    val tips = mutableListOf<String>()
    if (battery < 80) tips += "Battery health is reduced. Keep charging between 20% and 80% to slow wear."
    if (temp < 80) tips += "Phone is running warm. Remove the case while charging and close heavy apps."
    if (storage < 100) tips += "Storage is getting full. Use the storage cleaner to find large files and cache."
    if (ram < 100) tips += "Memory is mostly in use. Close background apps or restart the phone."
    if (b.level in 0..20 && !b.charging) tips += "Battery is low. Turn on battery saver mode."
    if (tips.isEmpty()) tips += "Everything looks good. Keep it up."

    return HealthReport(score, tips)
}
