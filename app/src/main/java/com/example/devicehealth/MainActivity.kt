package com.example.devicehealth

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.devicehealth.ui.BatteryRing
import com.example.devicehealth.ui.DeviceHealthTheme
import com.example.devicehealth.ui.GlassCard
import com.example.devicehealth.ui.LottieGlowButton
import com.example.devicehealth.ui.PingScreen
import com.example.devicehealth.ui.SectionCard
import com.example.devicehealth.ui.SpeedTestScreen
import com.example.devicehealth.ui.ToolsScreen

enum class AppScreen { DEVICE, TOOLS, SPEED, PING }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DeviceHealthTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot(vm: DeviceViewModel = viewModel()) {
    var screen by rememberSaveable { mutableStateOf(AppScreen.DEVICE) }
    val snapshot by vm.snapshot.collectAsState()
    val context = LocalContext.current
    val lottie by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.pulse))

    val isMainTab = screen == AppScreen.DEVICE || screen == AppScreen.TOOLS

    // Back from a tool screen goes to the Tools list, not out of the app.
    BackHandler(enabled = !isMainTab) { screen = AppScreen.TOOLS }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (isMainTab) {
                NavigationBar(containerColor = Color(0xFF111827)) {
                    NavigationBarItem(
                        selected = screen == AppScreen.DEVICE,
                        onClick = { screen = AppScreen.DEVICE },
                        icon = { Icon(Icons.Filled.PhoneAndroid, contentDescription = null) },
                        label = { Text("Device") }
                    )
                    NavigationBarItem(
                        selected = screen == AppScreen.TOOLS,
                        onClick = { screen = AppScreen.TOOLS },
                        icon = { Icon(Icons.Filled.Build, contentDescription = null) },
                        label = { Text("Tools") }
                    )
                }
            }
        }
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0B0F1A), Color(0xFF151C3A), Color(0xFF0B0F1A))
                    )
                )
                .padding(inner)
        ) {
            when (screen) {
                AppScreen.DEVICE -> DeviceScreen(
                    snapshot = snapshot,
                    lottie = lottie,
                    onRefresh = { vm.refresh() },
                    onShare = { snapshot?.let { shareReport(context, it) } }
                )
                AppScreen.TOOLS -> ToolsScreen(
                    onSpeed = { screen = AppScreen.SPEED },
                    onPing = { screen = AppScreen.PING }
                )
                AppScreen.SPEED -> SpeedTestScreen(
                    lottie = lottie,
                    onBack = { screen = AppScreen.TOOLS }
                )
                AppScreen.PING -> PingScreen(
                    lottie = lottie,
                    onBack = { screen = AppScreen.TOOLS }
                )
            }
        }
    }
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
            CircularProgressIndicator(color = Color(0xFF7C5CFF))
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Header(lottie) }
        item { BatteryHero(snapshot.battery) }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
    val progress by animateLottieCompositionAsState(
        composition = lottie,
        iterations = LottieConstants.IterateForever
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        LottieAnimation(
            composition = lottie,
            progress = { progress },
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = "Device Health",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.6f)
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
                MiniStat("Temperature", b.tempC?.let { String.format(java.util.Locale.US, "%.1f °C", it) } ?: "N/A")
                MiniStat("Health", b.healthText)
                MiniStat("Source", b.source)
                MiniStat("Voltage", b.voltageV?.let { String.format(java.util.Locale.US, "%.2f V", it) } ?: "N/A")
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun shareReport(context: Context, s: Snapshot) {
    val text = buildString {
        appendLine("Device Health Report")
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
