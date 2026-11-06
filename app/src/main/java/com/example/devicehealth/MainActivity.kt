package com.example.devicehealth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.devicehealth.system.Alerts
import com.example.devicehealth.system.BatteryHistory
import com.example.devicehealth.system.Prefs
import com.example.devicehealth.system.healthReport
import com.example.devicehealth.ui.AppsScreen
import com.example.devicehealth.ui.BatteryRing
import com.example.devicehealth.ui.BatteryScreen
import com.example.devicehealth.ui.BenchmarkScreen
import com.example.devicehealth.ui.ColorTestScreen
import com.example.devicehealth.ui.DeviceHealthTheme
import com.example.devicehealth.ui.DnsScreen
import com.example.devicehealth.ui.GlassCard
import com.example.devicehealth.ui.HardwareScreen
import com.example.devicehealth.ui.HealthCard
import com.example.devicehealth.ui.LocalDark
import com.example.devicehealth.ui.LottieGlowButton
import com.example.devicehealth.ui.LottieHero
import com.example.devicehealth.ui.MonitorScreen
import com.example.devicehealth.ui.OnboardingScreen
import com.example.devicehealth.ui.PingScreen
import com.example.devicehealth.ui.SectionCard
import com.example.devicehealth.ui.SensorsScreen
import com.example.devicehealth.ui.SettingsScreen
import com.example.devicehealth.ui.SlimBottomBar
import com.example.devicehealth.ui.SpeedTestScreen
import com.example.devicehealth.ui.StatRow
import com.example.devicehealth.ui.StorageScreen
import com.example.devicehealth.ui.ToolsScreen
import com.example.devicehealth.ui.WifiScreen
import com.example.devicehealth.ui.backgroundBrush

sealed interface Route {
    data object Device : Route
    data object Tools : Route
    data object Battery : Route
    data object Settings : Route
    data object Onboarding : Route
    data object Speed : Route
    data object Ping : Route
    data object Wifi : Route
    data object Dns : Route
    data object Sensors : Route
    data object Hardware : Route
    data object ColorTest : Route
    data object Monitor : Route
    data object Benchmark : Route
    data object Storage : Route
    data object Apps : Route
}

private val TAB_ROUTES: List<Route> = listOf(Route.Device, Route.Tools, Route.Battery, Route.Settings)
private val TAB_LABELS = listOf("Device", "Tools", "Battery", "Settings")
private val TAB_ICONS: List<ImageVector> = listOf(
    Icons.Filled.PhoneAndroid,
    Icons.Filled.Build,
    Icons.Filled.BatteryFull,
    Icons.Filled.Settings,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppRoot() }
    }
}

@Composable
fun AppRoot(vm: DeviceViewModel = viewModel()) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var themeMode by remember { mutableIntStateOf(prefs.themeMode) }
    var accentIndex by remember { mutableIntStateOf(prefs.accentIndex) }
    var alerts by remember { mutableStateOf(prefs.alertsEnabled) }
    val stack = remember {
        mutableStateListOf<Route>(if (prefs.onboardingDone) Route.Device else Route.Onboarding)
    }
    val current = stack.last()
    val snapshot by vm.snapshot.collectAsState()
    val lottie by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.pulse))

    fun go(r: Route) {
        stack.add(r)
    }

    fun tab(r: Route) {
        stack.clear()
        stack.add(r)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    LaunchedEffect(Unit) { Alerts.schedule(context) }
    LaunchedEffect(snapshot) { snapshot?.let { BatteryHistory.append(context, it.battery) } }
    BackHandler(enabled = stack.size > 1) { back() }

    DeviceHealthTheme(themeMode = themeMode, accentIndex = accentIndex) {
        val dark = LocalDark.current
        StatusBarIcons(dark)

        // The background is drawn behind the whole screen, including the status bar area.
        Box(Modifier.fillMaxSize().background(backgroundBrush(dark))) {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    val idx = TAB_ROUTES.indexOf(current)
                    if (idx >= 0) {
                        SlimBottomBar(
                            selected = idx,
                            labels = TAB_LABELS,
                            icons = TAB_ICONS,
                            onSelect = { tab(TAB_ROUTES[it]) }
                        )
                    }
                }
            ) { inner ->
                Box(Modifier.fillMaxSize().padding(inner)) {
                    when (current) {
                        Route.Onboarding -> OnboardingScreen(lottie = lottie, onDone = {
                            prefs.onboardingDone = true
                            tab(Route.Device)
                        })
                        Route.Device -> DeviceScreen(
                            snapshot = snapshot,
                            lottie = lottie,
                            onRefresh = { vm.refresh() },
                            onShare = { snapshot?.let { shareReport(context, it) } }
                        )
                        Route.Tools -> ToolsScreen(onOpen = { go(it) })
                        Route.Battery -> BatteryScreen(snapshot)
                        Route.Settings -> SettingsScreen(
                            themeMode = themeMode,
                            onTheme = { themeMode = it; prefs.themeMode = it },
                            accentIndex = accentIndex,
                            onAccent = { accentIndex = it; prefs.accentIndex = it },
                            alerts = alerts,
                            onAlerts = { alerts = it; prefs.alertsEnabled = it },
                        )
                        Route.Speed -> SpeedTestScreen(lottie = lottie, onBack = { back() })
                        Route.Ping -> PingScreen(lottie = lottie, onBack = { back() })
                        Route.Wifi -> WifiScreen(lottie = lottie, onBack = { back() })
                        Route.Dns -> DnsScreen(lottie = lottie, onBack = { back() })
                        Route.Sensors -> SensorsScreen(onBack = { back() })
                        Route.Hardware -> HardwareScreen(lottie = lottie, onBack = { back() })
                        Route.ColorTest -> ColorTestScreen(onBack = { back() })
                        Route.Monitor -> MonitorScreen(onBack = { back() })
                        Route.Benchmark -> BenchmarkScreen(lottie = lottie, onBack = { back() })
                        Route.Storage -> StorageScreen(lottie = lottie, onBack = { back() })
                        Route.Apps -> AppsScreen(onBack = { back() })
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBarIcons(dark: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c != null) {
        if (c is Activity) return c
        c = (c as? ContextWrapper)?.baseContext
    }
    return null
}

@Composable
private fun DeviceScreen(
    snapshot: Snapshot?,
    lottie: LottieComposition?,
    onRefresh: () -> Unit,
    onShare: () -> Unit,
) {
    if (snapshot == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val report = healthReport(snapshot)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Header(lottie) }
        item { HealthCard(report) }
        item { BatteryHero(snapshot.battery) }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LottieGlowButton(
                    text = "Refresh",
                    composition = lottie,
                    colors = listOf(Color(0xFF7C5CFF), Color(0xFF6366F1)),
                    modifier = Modifier.weight(1f),
                    onClick = onRefresh
                )
                LottieGlowButton(
                    text = "Share report",
                    composition = lottie,
                    colors = listOf(Color(0xFF0EA5E9), Color(0xFF22D3EE)),
                    modifier = Modifier.weight(1f),
                    onClick = onShare
                )
            }
        }
        items(snapshot.sections) { SectionCard(it) }
    }
}

@Composable
private fun Header(lottie: LottieComposition?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LottieHero(lottie, 64.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Device Health", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}",
                fontSize = 13.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BatteryHero(b: Battery) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BatteryRing(level = b.level, charging = b.charging)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                StatRow("Temperature", b.tempC?.let { String.format(java.util.Locale.US, "%.1f C", it) } ?: "N/A")
                StatRow("Health", b.healthText)
                StatRow("Source", b.source)
                StatRow("Voltage", b.voltageV?.let { String.format(java.util.Locale.US, "%.2f V", it) } ?: "N/A")
            }
        }
    }
}

private fun shareReport(context: Context, s: Snapshot) {
    val text = buildString {
        appendLine("Device Health Report")
        appendLine("Health score: ${healthReport(s).score}/100")
        s.sections.forEach { section ->
            appendLine()
            appendLine(section.title)
            section.items.forEach { appendLine("${it.label}: ${it.value}") }
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share report"))
}
