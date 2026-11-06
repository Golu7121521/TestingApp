package com.example.devicehealth.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.airbnb.lottie.LottieComposition
import com.example.devicehealth.InfoItem
import com.example.devicehealth.InfoSection
import com.example.devicehealth.net.DnsResult
import com.example.devicehealth.net.WifiNet
import com.example.devicehealth.net.bandName
import com.example.devicehealth.net.dnsLookup
import com.example.devicehealth.net.mobileOperator
import com.example.devicehealth.net.signalQuality
import com.example.devicehealth.net.transportName
import com.example.devicehealth.net.wifiInfo
import com.example.devicehealth.net.wifiScan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BLUE_GRAD = listOf(Color(0xFF0EA5E9), Color(0xFF22D3EE))
private val VIOLET_GRAD = listOf(Color(0xFF7C5CFF), Color(0xFF6366F1))

@Composable
fun WifiScreen(lottie: LottieComposition?, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf(wifiInfo(ctx)) }
    var nets by remember { mutableStateOf<List<WifiNet>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    val carrier = remember { mobileOperator(ctx) }

    fun doScan() {
        scanning = true
        scope.launch {
            nets = withContext(Dispatchers.IO) { wifiScan(ctx) }
            info = wifiInfo(ctx)
            scanning = false
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) doScan()
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ToolHeader(title = "Wi-Fi details", onBack = onBack)

        SectionCard(
            InfoSection(
                title = "Connection",
                icon = Icons.Filled.Wifi,
                items = listOf(
                    InfoItem("Active network", transportName(ctx)),
                    InfoItem("Wi-Fi name", info.ssid),
                    InfoItem("Signal", info.rssi?.let { "$it dBm (${signalQuality(it)})" } ?: "--"),
                    InfoItem("Link speed", info.linkMbps?.let { "$it Mbps" } ?: "--"),
                    InfoItem("Band", info.freqMhz?.let { bandName(it) } ?: "--"),
                    InfoItem("IP address", info.ip),
                    InfoItem("Gateway", info.gateway),
                    InfoItem("DNS", info.dns),
                    InfoItem("Mobile operator", carrier),
                )
            )
        )

        LottieGlowButton(
            text = if (scanning) "Scanning..." else "Scan nearby networks",
            composition = lottie,
            colors = BLUE_GRAD,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (!scanning) {
                    val granted = ContextCompat.checkSelfPermission(
                        ctx, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) doScan() else permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }
        )

        if (nets.isNotEmpty()) {
            SectionCard(
                InfoSection(
                    title = "Nearby networks (${nets.size})",
                    icon = Icons.Filled.Wifi,
                    items = nets.map { InfoItem(it.ssid, "${it.rssi} dBm, ${bandName(it.freqMhz)}") }
                )
            )
        }
    }
}

@Composable
fun DnsScreen(lottie: LottieComposition?, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("google.com") }
    var result by remember { mutableStateOf<DnsResult?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ToolHeader(title = "DNS lookup", onBack = onBack)

        OutlinedTextField(
            value = host,
            onValueChange = { host = it.trim() },
            label = { Text("Domain") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        LottieGlowButton(
            text = if (busy) "Looking up..." else "Lookup",
            composition = lottie,
            colors = VIOLET_GRAD,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (!busy && host.isNotBlank()) {
                    busy = true
                    scope.launch {
                        result = withContext(Dispatchers.IO) { dnsLookup(host) }
                        busy = false
                    }
                }
            }
        )

        result?.let { r ->
            val items = if (r.error != null) {
                listOf(InfoItem("Error", r.error.orEmpty()))
            } else {
                listOf(
                    InfoItem("Lookup time", "${r.ms} ms"),
                    InfoItem("Addresses", r.ips.joinToString(", ")),
                )
            }
            SectionCard(InfoSection(r.host, Icons.Filled.Dns, items))
        }
    }
}
