package com.example.devicehealth.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.devicehealth.InfoItem
import com.example.devicehealth.InfoSection
import com.example.devicehealth.Snapshot
import com.example.devicehealth.system.BatteryHistory
import com.example.devicehealth.system.HealthReport
import com.example.devicehealth.system.Sample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun HealthCard(report: HealthReport) {
    val color = when {
        report.score >= 80 -> Color(0xFF10B981)
        report.score >= 60 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Phone health", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${report.score}/100", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = color)
            }
            Icon(Icons.Filled.Favorite, contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(8.dp))
        report.tips.forEach { tip ->
            Text(
                "- $tip",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 3.dp)
            )
        }
    }
}

private fun formatMinutes(total: Long): String = "${total / 60}h ${total % 60}m"

@Composable
fun BatteryScreen(snapshot: Snapshot?) {
    val context = LocalContext.current
    val level = snapshot?.battery?.level
    var samples by remember { mutableStateOf<List<Sample>>(emptyList()) }

    LaunchedEffect(level) {
        samples = withContext(Dispatchers.IO) { BatteryHistory.read(context) }
    }

    val charge = BatteryHistory.chargeStats(samples)
    val recent = samples.takeLast(120)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Battery", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        SectionCard(
            InfoSection(
                title = "Charging",
                icon = Icons.Filled.BatteryChargingFull,
                items = listOf(
                    InfoItem(
                        "Charge speed",
                        charge.ratePctPerHour?.let { String.format(Locale.US, "%.1f %% per hour", it) }
                            ?: "Not enough data yet"
                    ),
                    InfoItem("Time to full", charge.minutesToFull?.let { formatMinutes(it) } ?: "--"),
                    InfoItem("Saved samples", "${samples.size}"),
                )
            )
        )
        GlassCard {
            Text("Battery level", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            LineChart(recent.map { it.level.toFloat() }, MaterialTheme.colorScheme.primary)
        }
        GlassCard {
            Text("Battery temperature", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            LineChart(recent.mapNotNull { it.tempC }, Color(0xFFF97316))
        }
        Text(
            "Data is saved in the background about every 15 minutes, and whenever the app is open.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
