package com.mytune.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Animated splash: shown for exactly 2 seconds (1.75 s + 0.25 s fade). */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1750)
        visible = false
        delay(250)
        onFinished()
    }
    val primary = MaterialTheme.colorScheme.primary
    val container = MaterialTheme.colorScheme.primaryContainer

    val logoScale = remember { Animatable(0.3f) }
    val textAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        logoScale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 180f))
    }
    LaunchedEffect(Unit) {
        delay(450)
        textAlpha.animateTo(1f, tween(500))
    }
    val inf = rememberInfiniteTransition(label = "splash")
    val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "spin")
    val pulse by inf.animateFloat(0.9f, 1.15f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse")

    AnimatedVisibility(visible = visible, exit = fadeOut(tween(250))) {
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B0B0D), container.copy(alpha = 0.9f), Color(0xFF0B0B0D)))),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(170.dp).scale(logoScale.value), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize().scale(pulse)) {
                        drawCircle(primary.copy(alpha = 0.12f))
                    }
                    Canvas(Modifier.fillMaxSize().rotate(spin)) {
                        drawArc(
                            Brush.sweepGradient(listOf(Color.Transparent, primary)),
                            startAngle = 0f, sweepAngle = 270f, useCenter = false,
                            topLeft = Offset(6f, 6f), size = Size(size.width - 12f, size.height - 12f),
                            style = Stroke(width = 8f, cap = StrokeCap.Round),
                        )
                    }
                    Box(
                        Modifier.size(104.dp).clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(container, primary))),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(64.dp)) }
                }
                Spacer(Modifier.height(24.dp))
                Text("MyTune", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.alpha(textAlpha.value))
                Text("Your music, your way", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, modifier = Modifier.alpha(textAlpha.value))
            }
        }
    }
}
