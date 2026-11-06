package com.example.devicehealth.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.LottieComposition
import com.example.devicehealth.InfoItem
import com.example.devicehealth.InfoSection
import com.example.devicehealth.system.Flashlight
import com.example.devicehealth.system.cameras
import com.example.devicehealth.system.playBeep
import com.example.devicehealth.system.vibrate
import java.util.Locale

private val GREEN_GRAD = listOf(Color(0xFF10B981), Color(0xFF34D399))
private val ORANGE_GRAD = listOf(Color(0xFFF97316), Color(0xFFFBBF24))
private val VIOLET_GRAD = listOf(Color(0xFF7C5CFF), Color(0xFF6366F1))

@Composable
fun SensorsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val sm = remember { ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val live = remember { mutableStateMapOf<Int, String>() }
    val liveTypes = listOf(
        Sensor.TYPE_ACCELEROMETER to "Accelerometer",
        Sensor.TYPE_GYROSCOPE to "Gyroscope",
        Sensor.TYPE_LIGHT to "Light",
        Sensor.TYPE_PROXIMITY to "Proximity",
    )

    DisposableEffect(Unit) {
        val listeners = liveTypes.mapNotNull { (type, _) ->
            val sensor = sm.getDefaultSensor(type) ?: return@mapNotNull null
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    live[type] = event.values.joinToString("  ") { String.format(Locale.US, "%.2f", it) }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            listener
        }
        onDispose { listeners.forEach { sm.unregisterListener(it) } }
    }

    val all = remember { sm.getSensorList(Sensor.TYPE_ALL) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ToolHeader(title = "Sensors", onBack = onBack)

        liveTypes.forEach { (type, name) ->
            val reading = when {
                sm.getDefaultSensor(type) == null -> "Not available on this phone"
                else -> live[type] ?: "Waiting for data..."
            }
            SectionCard(InfoSection(name, Icons.Filled.Sensors, listOf(InfoItem("Live reading", reading))))
        }

        SectionCard(
            InfoSection(
                title = "All sensors (${all.size})",
                icon = Icons.Filled.Sensors,
                items = all.mapIndexed { i, s -> InfoItem("Sensor ${i + 1}", s.name) }
            )
        )
    }
}

@Composable
fun HardwareScreen(lottie: LottieComposition?, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val hasFlash = remember { Flashlight.torchCameraId(ctx) != null }
    var torch by remember { mutableStateOf(false) }
    val cams = remember { cameras(ctx) }

    DisposableEffect(Unit) {
        onDispose { Flashlight.set(ctx, false) }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ToolHeader(title = "Hardware checks", onBack = onBack)

        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Flashlight", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (hasFlash) "Turn the torch on or off" else "No flash found on this phone",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = torch,
                    enabled = hasFlash,
                    onCheckedChange = {
                        torch = it
                        Flashlight.set(ctx, it)
                    }
                )
            }
        }

        LottieGlowButton(
            text = "Vibrate test",
            composition = lottie,
            colors = ORANGE_GRAD,
            modifier = Modifier.fillMaxWidth(),
            onClick = { vibrate(ctx) }
        )

        LottieGlowButton(
            text = "Speaker test",
            composition = lottie,
            colors = GREEN_GRAD,
            modifier = Modifier.fillMaxWidth(),
            onClick = { playBeep() }
        )

        if (cams.isEmpty()) {
            SectionCard(InfoSection("Cameras", Icons.Filled.CameraAlt, listOf(InfoItem("Status", "No camera info"))))
        }
        cams.forEach { c ->
            SectionCard(
                InfoSection(
                    title = "Camera ${c.id}",
                    icon = Icons.Filled.CameraAlt,
                    items = listOf(
                        InfoItem("Facing", c.facing),
                        InfoItem("Resolution", c.megapixels?.let { String.format(Locale.US, "%.1f MP", it) } ?: "N/A"),
                        InfoItem("Flash", if (c.flash) "Yes" else "No"),
                    )
                )
            )
        }
    }
}

@Composable
fun ColorTestScreen(onBack: () -> Unit) {
    val colors = listOf(
        Color(0xFFFFFFFF), Color(0xFF000000), Color(0xFFFF0000), Color(0xFF00FF00),
        Color(0xFF0000FF), Color(0xFF808080), Color(0xFFFFFF00),
    )
    var index by remember { mutableStateOf(0) }
    val bg = colors[index]
    val textColor = if (bg == Color(0xFF000000) || bg == Color(0xFF0000FF)) Color.White else Color.Black

    Box(
        Modifier
            .fillMaxSize()
            .background(bg)
            .clickable { index = (index + 1) % colors.size },
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Tap to change color. Look for dead or stuck pixels.",
            color = textColor,
            fontSize = 15.sp,
            modifier = Modifier.padding(32.dp)
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(top = 8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = textColor
            )
        }
    }
}
