package com.example.devicehealth.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import java.net.InetAddress

data class WifiInfoData(
    val connected: Boolean,
    val ssid: String,
    val rssi: Int?,
    val linkMbps: Int?,
    val freqMhz: Int?,
    val ip: String,
    val gateway: String,
    val dns: String,
)

data class WifiNet(val ssid: String, val rssi: Int, val freqMhz: Int)

data class DnsResult(val host: String, val ips: List<String>, val ms: Long, val error: String?)

private fun ipToString(ip: Int): String =
    "${ip and 0xff}.${(ip shr 8) and 0xff}.${(ip shr 16) and 0xff}.${(ip shr 24) and 0xff}"

fun transportName(ctx: Context): String {
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "No connection"
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
        else -> "Other"
    }
}

fun isWifi(ctx: Context): Boolean = transportName(ctx) == "Wi-Fi"

@Suppress("DEPRECATION")
fun wifiInfo(ctx: Context): WifiInfoData = runCatching {
    val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val info = wm.connectionInfo
    val dhcp = wm.dhcpInfo
    WifiInfoData(
        connected = isWifi(ctx),
        ssid = info.ssid?.trim('"') ?: "Unknown",
        rssi = info.rssi,
        linkMbps = info.linkSpeed,
        freqMhz = info.frequency,
        ip = ipToString(dhcp.ipAddress),
        gateway = ipToString(dhcp.gateway),
        dns = ipToString(dhcp.dns1),
    )
}.getOrElse { WifiInfoData(false, "Unavailable", null, null, null, "--", "--", "--") }

/** Needs ACCESS_FINE_LOCATION and location turned on. */
@Suppress("DEPRECATION")
fun wifiScan(ctx: Context): List<WifiNet> = runCatching {
    val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    wm.startScan()
    wm.scanResults
        .filter { it.SSID.isNotBlank() }
        .sortedByDescending { it.level }
        .map { WifiNet(it.SSID, it.level, it.frequency) }
}.getOrDefault(emptyList())

fun mobileOperator(ctx: Context): String = runCatching {
    val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    tm.networkOperatorName.ifBlank { "Unknown" }
}.getOrDefault("Unavailable")

fun signalQuality(rssi: Int): String = when {
    rssi >= -55 -> "Excellent"
    rssi >= -67 -> "Good"
    rssi >= -80 -> "Fair"
    else -> "Weak"
}

fun bandName(freqMhz: Int): String = if (freqMhz >= 5000) "5 GHz" else "2.4 GHz"

fun dnsLookup(host: String): DnsResult {
    val start = System.nanoTime()
    return try {
        val ips = InetAddress.getAllByName(host).map { it.hostAddress ?: "" }
        DnsResult(host, ips, (System.nanoTime() - start) / 1_000_000, null)
    } catch (e: Exception) {
        DnsResult(host, emptyList(), (System.nanoTime() - start) / 1_000_000, e.message ?: "Lookup failed")
    }
}
