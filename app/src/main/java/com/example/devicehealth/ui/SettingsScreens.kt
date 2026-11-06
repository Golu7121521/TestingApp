package com.example.devicehealth.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.LottieComposition

@Composable
fun SettingsScreen(
    themeMode: Int,
    onTheme: (Int) -> Unit,
    accentIndex: Int,
    onAccent: (Int) -> Unit,
    alerts: Boolean,
    onAlerts: (Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onAlerts(granted)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Settings", fontSize = 26.sp, fontWeight = FontWeight.Bold)

        GlassCard {
            Text("Theme", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("System", "Light", "Dark").forEachIndexed { i, label ->
                    FilterChip(
                        selected = themeMode == i,
                        onClick = { onTheme(i) },
                        label = { Text(label) }
                    )
                }
            }
        }

        GlassCard {
            Text("Accent color", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AccentChoices.forEachIndexed { i, c ->
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(
                                width = if (i == accentIndex) 3.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape
                            )
                            .clickable { onAccent(i) }
                    )
                }
            }
        }

        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Battery alerts", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Notify when charging above 80% or when the phone overheats",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = alerts,
                    onCheckedChange = { on ->
                        when {
                            !on -> onAlerts(false)
                            Build.VERSION.SDK_INT >= 33 ->
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else -> onAlerts(true)
                        }
                    }
                )
            }
        }

        GlassCard {
            Text("Screen time access", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Lets the Installed apps screen show how long each app was used. Android requires you to allow this manually.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = {
                ctx.startActivity(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }) {
                Text("Open usage access")
            }
        }

        GlassCard {
            Text("About", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Device Health 2.0. Battery history is saved about every 15 minutes in the background.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun OnboardingScreen(lottie: LottieComposition?, onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        LottieHero(lottie, 140.dp)
        Text("Welcome to Device Health", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Everything about your phone in one place.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        GlassCard {
            listOf(
                "Battery health, temperature and charging history",
                "Speed, ping and Wi-Fi network checks",
                "Sensors, flashlight, camera and screen tests",
                "Storage cleaner, live monitor and installed apps",
            ).forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(line, fontSize = 14.sp)
                }
            }
        }
        Text(
            "The app asks for a permission only when a feature needs it.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        LottieGlowButton(
            text = "Get started",
            composition = lottie,
            colors = listOf(androidx.compose.ui.graphics.Color(0xFF7C5CFF), androidx.compose.ui.graphics.Color(0xFF22D3EE)),
            modifier = Modifier.fillMaxWidth(),
            onClick = onDone
        )
    }
}
