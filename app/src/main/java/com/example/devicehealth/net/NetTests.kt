package com.example.devicehealth.net

import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.math.abs

data class PingResult(
    val host: String,
    val avgMs: Double?,
    val minMs: Double?,
    val maxMs: Double?,
    val jitterMs: Double?,
    val lossPct: Int,
)

/**
 * Network tests without any extra library.
 * Ping is measured as TCP connect time (works without root, unlike ICMP ping on Android).
 * Speed uses Cloudflare's public speed endpoints.
 */
object NetTests {

    private const val SPEED_BASE = "https://speed.cloudflare.com"

    fun ping(host: String, port: Int = 443, samples: Int = 5): PingResult {
        val times = mutableListOf<Double>()
        var failed = 0
        repeat(samples) {
            val ms = tcpConnectMs(host, port)
            if (ms == null) {
                failed++
            } else {
                times.add(ms)
            }
        }
        return PingResult(
            host = host,
            avgMs = times.takeIf { it.isNotEmpty() }?.average(),
            minMs = times.minOrNull(),
            maxMs = times.maxOrNull(),
            jitterMs = if (times.size > 1) times.zipWithNext { a, b -> abs(b - a) }.average() else null,
            lossPct = failed * 100 / samples,
        )
    }

    private fun tcpConnectMs(host: String, port: Int): Double? = try {
        val start = System.nanoTime()
        Socket().use { it.connect(InetSocketAddress(host, port), 3000) }
        (System.nanoTime() - start) / 1_000_000.0
    } catch (e: Exception) {
        null
    }

    /** Download throughput in Mbps. */
    fun download(bytes: Int = 10_000_000): Double? = runCatching {
        val conn = URL("$SPEED_BASE/__down?bytes=$bytes").openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 15000
        try {
            conn.inputStream.use { input ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                val start = System.nanoTime()
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                }
                val secs = (System.nanoTime() - start) / 1e9
                total * 8.0 / secs / 1_000_000.0
            }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** Upload throughput in Mbps. */
    fun upload(bytes: Int = 5_000_000): Double? = runCatching {
        val conn = URL("$SPEED_BASE/__up").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 5000
            conn.readTimeout = 20000
            conn.setFixedLengthStreamingMode(bytes)
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            val payload = ByteArray(bytes)
            val start = System.nanoTime()
            conn.outputStream.use { it.write(payload) }
            check(conn.responseCode in 200..299) { "Upload failed" }
            val secs = (System.nanoTime() - start) / 1e9
            bytes * 8.0 / secs / 1_000_000.0
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
