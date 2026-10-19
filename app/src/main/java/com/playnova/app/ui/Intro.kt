@file:OptIn(ExperimentalFoundationApi::class)

package com.playnova.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.playnova.app.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onDone: () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(700)); delay(500); onDone() }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary.copy(0.35f), MaterialTheme.colorScheme.background))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(a.value).scale(0.8f + 0.2f * a.value)) {
            Box(Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.PlayArrow, null, Modifier.size(56.dp), tint = Color.White)
            }
            Spacer(Modifier.height(16.dp))
            Text("PlayNova", style = MaterialTheme.typography.headlineMedium)
            Text("Premium video player", color = Muted)
        }
    }
}

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pages = listOf<Triple<ImageVector, String, String>>(
        Triple(Icons.Outlined.PlayCircle, "Smooth playback", "Hardware decoding ke saath MP4, MKV, WebM, MOV aur zyada formats. Phone ka decoder jo support kare wo sab."),
        Triple(Icons.Outlined.TouchApp, "Gestures", "Double-tap se seek, left swipe brightness, right swipe volume, long-press se fast speed."),
        Triple(Icons.Outlined.VideoLibrary, "Aapki library", "Folders, playlists, favorites aur history. Resume wahin se jahan chhoda tha.")
    )
    val state = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onDone) { Text("Skip") } }
        HorizontalPager(state, Modifier.weight(1f)) { i ->
            val (ic, t, d) = pages[i]
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(140.dp).clip(RoundedCornerShape(36.dp)).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(0.4f), Color.Transparent))), contentAlignment = Alignment.Center) {
                    Icon(ic, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(28.dp))
                Text(t, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(d, color = Muted, textAlign = TextAlign.Center)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
            repeat(pages.size) { i ->
                Box(Modifier.size(if (i == state.currentPage) 24.dp else 8.dp, 8.dp).clip(CircleShape)
                    .background(if (i == state.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline))
            }
        }
        Button(onClick = { if (state.currentPage == pages.lastIndex) onDone() else scope.launch { state.animateScrollToPage(state.currentPage + 1) } },
            Modifier.fillMaxWidth().height(52.dp), shape = SmallShape) { Text(if (state.currentPage == pages.lastIndex) "Shuru karo" else "Aage") }
    }
}

@Composable
fun PermissionScreen(vm: AppViewModel, onDone: () -> Unit) {
    val ask = rememberScanAction(vm) { onDone() }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        EmptyState(Icons.Outlined.Lock, "Videos tak access", "PlayNova ko aapke phone ke videos dikhane ke liye video access chahiye. Ye data kahin upload nahi hota. Aap baad mein bhi 'File kholo' se koi bhi video chala sakte ho.")
        Spacer(Modifier.height(12.dp))
        Button(onClick = ask, Modifier.fillMaxWidth().height(52.dp), shape = SmallShape) { Text("Permission do") }
        TextButton(onClick = onDone) { Text("Abhi nahi") }
    }
}
