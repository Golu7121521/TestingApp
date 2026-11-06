package com.example.devicehealth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.LottieComposition
import com.example.devicehealth.InfoItem
import com.example.devicehealth.InfoSection
import com.example.devicehealth.Route
import com.example.devicehealth.net.NetTests
import com.example.devicehealth.net.PingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val PING_HOSTS = listOf("1.1.1.1", "8.8.8.8", "www.google.com")

private val BLUE = listOf(Color(0xFF0EA5E9), Color(0xFF22D3EE))
private val VIOLET = listOf(Color(0xFF7C5CFF), Color(0xFF6366F1))
private val GREEN = listOf(Color(0xFF10B981), Color(0xFF34D399))
private val ORANGE = listOf(Color(0xFFF97316), Color(0xFFFBBF24))

fun Double?.ms(): String = this?.let { String.format(Locale.US, "%.1f ms", it) } ?: "--"
fun Double?.mbps(): String = this?.let { String.format(Locale.US, "%.1f Mbps", it) } ?: "--"

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
fun ToolCard(title: String, subtitle: String, icon: ImageVector, colors: List<Color>, onClick: () -> Unit) {
    GlassCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(colors)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ToolsScreen(onOpen: (Route) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Tools", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "Network, hardware and system checks",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SectionLabel("Network")
        ToolCard("Speed test", "Download, upload and latency", Icons.Filled.Speed, BLUE) { onOpen(Route.Speed) }
        ToolCard("Ping test", "Latency and packet loss to 3 servers", Icons.Filled.NetworkCheck, VIOLET) { onOpen(Route.Ping) }
        ToolCard("Wi-Fi details", "Signal, link speed, IP, band and nearby networks", Icons.Filled.Wifi, BLUE) { onOpen(Route.Wifi) }
        ToolCard("DNS lookup", "Resolve a domain and measure lookup time", Icons.Filled.Dns, VIOLET) { onOpen(Route.Dns) }
        SectionLabel("Hardware")
        ToolCard("Sensors", "Live accelerometer, gyroscope, light and proximity", Icons.Filled.Sensors, GREEN) { onOpen(Route.Sensors) }
        ToolCard("Hardware checks", "Flashlight, vibration, speaker and cameras", Icons.Filled.Tune, ORANGE) { onOpen(Route.Hardware) }
        ToolCard("Screen test", "Cycle colors to find dead or stuck pixels", Icons.Filled.Palette, VIOLET) { onOpen(Route.ColorTest) }
        SectionLabel("System")
        ToolCard("Live monitor", "CPU and RAM usage graphs", Icons.Filled.Timeline, GREEN) { onOpen(Route.Monitor) }
        ToolCard("Benchmark", "Quick CPU and memory score", Icons.Filled.Bolt, ORANGE) { onOpen(Route.Benchmark) }
        ToolCard("Storage cleaner", "Large files, duplicates and app cache", Icons.Filled.FolderOpen, BLUE) { onOpen(Route.Storage) }
        ToolCard("Installed apps", "All apps with version and screen time", Icons.Filled.Apps, VIOLET) { onOpen(Route.Apps) }
    }
}

@Composable
fun SpeedTestScreen(lottie: LottieComposition?, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Tap start to measure your connection") }
    var pingMs by remember { mutableStateOf<Double?>(null) }
    var downMbps by remember { mutableStateOf<Double?>(null) }
    var upMbps by remember { mutableStateOf<Double?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ToolHeader(title = "Speed test", onBack = onBack)
        GlassCard {
            Text(status, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionCard(
            InfoSection(
                title = "Results",
                icon = Icons.Filled.Speed,
                items = listOf(
                    InfoItem("Latency (ping)", pingMs.ms()),
                    InfoItem("Download", downMbps.mbps()),
                    InfoItem("Upload", upMbps.mbps()),
                )
            )
        )
        LottieGlowButton(
            text = if (running) "Testing..." else "Start speed test",
            composition = lottie,
            colors = BLUE,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (!running) {
                    running = true
                    pingMs = null
                    downMbps = null
                    upMbps = null
                    scope.launch {
                        status = "Measuring latency..."
                        pingMs = withContext(Dispatchers.IO) { NetTests.ping("1.1.1.1", 443, 4).avgMs }
                        status = "Testing download speed..."
                        downMbps = withContext(Dispatchers.IO) { NetTests.download() }
                        status = "Testing upload speed..."
                        upMbps = withContext(Dispatchers.IO) { NetTests.upload() }
                        status = if (downMbps == null && upMbps == null) {
                            "Test failed. Check your internet connection and try again."
                        } else {
                            "Test complete"
                        }
                        running = false
                    }
                }
            }
        )
    }
}

@Composable
fun PingScreen(lottie: LottieComposition?, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<PingResult>>(emptyList()) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ToolHeader(title = "Ping test", onBack = onBack)
        GlassCard {
            Text(
                "Measures connection time to 3 servers, 5 samples each. Lower is better.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        results.forEach { r ->
            SectionCard(
                InfoSection(
                    title = r.host,
                    icon = Icons.Filled.NetworkCheck,
                    items = listOf(
                        InfoItem("Average", r.avgMs.ms()),
                        InfoItem("Min / Max", "${r.minMs.ms()} / ${r.maxMs.ms()}"),
                        InfoItem("Jitter", r.jitterMs.ms()),
                        InfoItem("Packet loss", "${r.lossPct}%"),
                    )
                )
            )
        }
        LottieGlowButton(
            text = if (running) "Pinging..." else "Start ping test",
            composition = lottie,
            colors = VIOLET,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (!running) {
                    running = true
                    results = emptyList()
                    scope.launch {
                        for (host in PING_HOSTS) {
                            val r = withContext(Dispatchers.IO) { NetTests.ping(host, 443, 5) }
                            results = results + r
                        }
                        running = false
                    }
                }
            }
        )
    }
}
