package com.example.universalplayer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Rounded.Home),
    Advanced("Advanced", Icons.Rounded.Tune),
    History("History", Icons.Rounded.History),
    Playlist("Playlist", Icons.Rounded.VideoLibrary)
}

/** Slim (64dp) bottom navigation bar with animated selection pill. */
@Composable
fun AppBottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
            Row(Modifier.fillMaxWidth().height(64.dp)) {
                Tab.entries.forEach { t ->
                    val sel = t == selected
                    val pillColor by animateColorAsState(
                        if (sel) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        tween(220), label = "pill"
                    )
                    val pillWidth by animateDpAsState(if (sel) 56.dp else 32.dp, tween(220), label = "pillw")
                    val scale by animateFloatAsState(if (sel) 1.12f else 1f, tween(220), label = "scale")
                    val iconColor by animateColorAsState(
                        if (sel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        tween(220), label = "icon"
                    )
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(t) }
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            Modifier.width(pillWidth).height(28.dp).clip(RoundedCornerShape(14.dp)).background(pillColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                t.icon, t.label,
                                Modifier.size(22.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                                tint = iconColor
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            t.label, fontSize = 11.sp,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
