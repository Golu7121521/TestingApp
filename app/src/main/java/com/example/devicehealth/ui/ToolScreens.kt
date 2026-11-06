package com.example.devicehealth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.example.devicehealth.net.NetTests
import com.example.devicehealth.net.PingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val PING_HOSTS = listOf("1.1.1.1", "8.8.8.8", "www.google.com")

private fun Double?.ms(): String =
    this?.let { String.format(Locale.US, "%.1f ms", it) } ?: "--"

private fun Double?.mbps(): String =
    this?.let { String.format(Locale.US, "%.1f Mbps", it) } ?: "--"

// ---------- Tools list ----------

@Composable
fun ToolsScreen(onSpeed: () -> Unit, onPing: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Tools", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(
            "Check the quality of your internet connection",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.6f)
        )
        Spacer(Modifier.size(4.dp))
        ToolCard(
            title = "Speed test",
            subtitle = "Download, upload and latency",
            icon = Icons.Filled.Speed,
            colors = listOf(Color(0xFF0EA5E9), Color(0xFF22D3EE)),
            onClick = onSpeed
        )
        ToolCard(
            title = "Ping test",
            subtitle = "Latency and packet loss to 3 servers",
            icon = Icons.Filled.NetworkCheck,
            colors = listOf(Color(0xFF7C5CFF), Color(0xFF6366F1)),
            onClick = onPing
        )
    }
}

@Composable
private fun ToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    colors: List<Color>,
    onClick: () -> Unit,
) {
    GlassCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(colors)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

// ---------- Speed test ----------

@Composable
fun SpeedTestScreen(
    lottie: LottieComposition?,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Tap start to measure your connection") }
    var pingMs by remember { mutableStateOf<Double?>(null) }
    var downMbps by remember { mutableStateOf<Double?>(null) }
    var upMbps by remember { mutableStateOf<Double?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ToolHeader(title = "Speed test", onBack = onBack)

        GlassCard {
            Text(status, fontSize = 14.sp, color = Color.White.copy(alpha = 0.75f))
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
            colors = listOf(Color(0xFF0EA5E9), Color(0xFF22D3EE)),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (!running) {
                    running = true
                    pingMs = null
                    downMbps = null
                    upMbps = null
                    scope.launch {
                        status = "Measuring latency..."
                        pingMs = withContext(Dispatchers.IO) {
                            NetTests.ping("1.1.1.1", 443, 4).avgMs
                        }
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

// ---------- Ping test ----------

@Composable
fun PingScreen(
    lottie: LottieComposition?,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<PingResult>>(emptyList()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ToolHeader(title = "Ping test", onBack = onBack)

        GlassCard {
            Text(
                "Measures connection time to 3 servers, 5 samples each. " +
                        "Lower is better. Packet loss shows failed attempts.",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.75f)
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
            colors = listOf(Color(0xFF7C5CFF), Color(0xFF6366F1)),
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
