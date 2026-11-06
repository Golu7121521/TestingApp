package com.example.devicehealth.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.airbnb.lottie.LottieComposition
import com.example.devicehealth.DeviceInfo
import com.example.devicehealth.InfoItem
import com.example.devicehealth.InfoSection
import com.example.devicehealth.system.AppItem
import com.example.devicehealth.system.BenchResult
import com.example.devicehealth.system.CpuSampler
import com.example.devicehealth.system.StorageScan
import com.example.devicehealth.system.cacheBytes
import com.example.devicehealth.system.clearCache
import com.example.devicehealth.system.hasUsageAccess
import com.example.devicehealth.system.installedApps
import com.example.devicehealth.system.primaryStorageRoot
import com.example.devicehealth.system.runBenchmark
import com.example.devicehealth.system.scanStorage
import com.example.devicehealth.system.screenTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val GREEN_GRAD = listOf(Color(0xFF10B981), Color(0xFF34D399))
private val ORANGE_GRAD = listOf(Color(0xFFF97316), Color(0xFFFBBF24))
private val BLUE_GRAD = listOf(Color(0xFF0EA5E9), Color(0xFF22D3EE))
private val VIOLET_GRAD = listOf(Color(0xFF7C5CFF), Color(0xFF6366F1))

@Composable
fun MonitorScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cpuHist = remember { mutableStateListOf<Float>() }
    val ramHist = remember { mutableStateListOf<Float>() }
    var cpuNow by remember { mutableStateOf<Double?>(null) }
    var ramNow by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            val cpu = withContext(Dispatchers.Default) { CpuSampler.sample() }
            val mi = DeviceInfo.memInfo(ctx)
            val ram = if (mi.totalMem > 0) (mi.totalMem - mi.availMem) * 100f / mi.totalMem else 0f
            cpuNow = cpu
            ramNow = ram
            cpu?.let { cpuHist.add(it.toFloat()) }
            ramHist.add(ram)
            if (cpuHist.size > 60) cpuHist.removeAt(0)
            if (ramHist.size > 60) ramHist.removeAt(0)
            delay(1000)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ToolHeader(title = "Live monitor", onBack = onBack)
        SectionCard(
            InfoSection(
                "CPU and RAM", Icons.Filled.Timeline,
                listOf(
                    InfoItem("CPU usage", cpuNow?.let { String.format(java.util.Locale.US, "%.0f%%", it) } ?: "Reading..."),
                    InfoItem("RAM usage", String.format(java.util.Locale.US, "%.0f%%", ramNow)),
                )
            )
        )
        GlassCard {
            Text("CPU history", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            LineChart(cpuHist.toList(), Color(0xFF7C5CFF))
        }
        GlassCard {
            Text("RAM history", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            LineChart(ramHist.toList(), Color(0xFF10B981))
        }
        Text(
            "CPU is the average since the first reading. Some phones restrict CPU stats to system apps.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun BenchmarkScreen(lottie: LottieComposition?, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<BenchResult?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ToolHeader(title = "Benchmark", onBack = onBack)
        GlassCard {
            Text(
                "Runs a prime-number sieve (CPU) and a large array copy (memory). Lower time is better.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        result?.let { r ->
            SectionCard(
                InfoSection(
                    "Results", Icons.Filled.Bolt,
                    listOf(
                        InfoItem("CPU test", "${r.cpuMs} ms"),
                        InfoItem("Memory test", "${r.memMs} ms"),
                    )
                )
            )
        }
        LottieGlowButton(
            text = if (running) "Running..." else "Run benchmark",
            composition = lottie,
            colors = ORANGE_GRAD,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (!running) {
                    running = true
                    scope.launch {
                        result = withContext(Dispatchers.Default) { runBenchmark() }
                        running = false
                    }
                }
            }
        )
    }
}

@Composable
fun StorageScreen(lottie: LottieComposition?, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cacheText by remember { mutableStateOf("Calculating...") }
    var scan by remember { mutableStateOf<StorageScan?>(null) }
    var scanning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        cacheText = DeviceInfo.fmtBytes(withContext(Dispatchers.IO) { cacheBytes(ctx) })
    }

    fun runScan() {
        scanning = true
        scope.launch {
            scan = withContext(Dispatchers.IO) { scanStorage(primaryStorageRoot()) }
            scanning = false
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) runScan()
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ToolHeader(title = "Storage cleaner", onBack = onBack)

        SectionCard(
            InfoSection(
                "App cache", Icons.Filled.FolderOpen,
                listOf(InfoItem("Cache size", cacheText))
            )
        )
        LottieGlowButton(
            text = "Clear app cache",
            composition = lottie,
            colors = BLUE_GRAD,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { clearCache(ctx) }
                    cacheText = DeviceInfo.fmtBytes(withContext(Dispatchers.IO) { cacheBytes(ctx) })
                }
            }
        )

        LottieGlowButton(
            text = if (scanning) "Scanning..." else "Scan storage",
            composition = lottie,
            colors = VIOLET_GRAD,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (!scanning) {
                    val granted = ContextCompat.checkSelfPermission(
                        ctx, Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) runScan() else permLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        )

        scan?.let { s ->
            GlassCard {
                Text("Scanned ${s.scanned} files", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SectionCard(
                InfoSection(
                    "Large files (50 MB+)", Icons.Filled.Memory,
                    if (s.large.isEmpty()) {
                        listOf(InfoItem("Result", "None found"))
                    } else {
                        s.large.take(15).map { InfoItem(File(it.path).name, DeviceInfo.fmtBytes(it.size)) }
                    }
                )
            )
            s.duplicates.take(8).forEachIndexed { i, group ->
                SectionCard(
                    InfoSection(
                        "Duplicate group ${i + 1} (${group.size} copies)", Icons.Filled.FolderOpen,
                        group.map { InfoItem(File(it.path).name, DeviceInfo.fmtBytes(it.size)) }
                    )
                )
            }
            if (s.duplicates.isEmpty()) {
                SectionCard(InfoSection("Duplicates", Icons.Filled.FolderOpen, listOf(InfoItem("Result", "None found"))))
            }
        }
    }
}

@Composable
fun AppsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var showSystem by remember { mutableStateOf(false) }
    val usageOk = remember { hasUsageAccess(ctx) }
    var screenTimeRows by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { installedApps(ctx) }
        if (usageOk) screenTimeRows = withContext(Dispatchers.IO) { screenTime(ctx) }
        loaded = true
    }

    val shown = apps.filter { showSystem || !it.system }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ToolHeader(title = "Installed apps", onBack = onBack) }

        item {
            SectionCard(
                InfoSection(
                    "Screen time (last 24 h)", Icons.Filled.Timeline,
                    when {
                        !usageOk -> listOf(InfoItem("Status", "Allow usage access in Settings to see this"))
                        screenTimeRows.isEmpty() -> listOf(InfoItem("Status", "No data yet"))
                        else -> screenTimeRows.map { InfoItem(it.first, "${it.second} min") }
                    }
                )
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !showSystem,
                    onClick = { showSystem = false },
                    label = { Text("User apps") }
                )
                FilterChip(
                    selected = showSystem,
                    onClick = { showSystem = true },
                    label = { Text("All apps") }
                )
            }
        }

        item {
            Text(
                if (loaded) "${shown.size} apps" else "Loading apps...",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(shown, key = { it.pkg }) { app ->
            GlassCard {
                Text(app.label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    "${app.pkg}   v${app.version}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
