package com.example.universalplayer.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.universalplayer.R

/** The app logo: brand gradient circle with the stylish play mark. Same look as the launcher icon. */
@Composable
fun BrandLogo(size: Dp, rotate: Boolean, pulse: Boolean = false) {
    val t = rememberInfiniteTransition(label = "logo")
    val rot by t.animateFloat(
        0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "rotate"
    )
    val s by t.animateFloat(
        1f, 1.08f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse"
    )
    Box(
        Modifier.size(size)
            .graphicsLayer {
                if (pulse) { scaleX = s; scaleY = s }
                if (rotate) rotationZ = rot
            }
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(BrandStart, BrandEnd))),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(R.drawable.play_mark), null,
            tint = Color.Unspecified, modifier = Modifier.requiredSize(size * 1.5f)
        )
    }
}

/** Shown for 2 seconds at start: logo + app name. Uses theme colours, so it is readable in light and dark. */
@Composable
fun SplashContent() {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val textAlpha by animateFloatAsState(if (shown) 1f else 0f, tween(700), label = "splashText")
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).pointerInput(Unit) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BrandLogo(110.dp, rotate = true)
        Spacer(Modifier.height(22.dp))
        Text(
            "Universal Player",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.graphicsLayer { alpha = textAlpha }
        )
    }
}
