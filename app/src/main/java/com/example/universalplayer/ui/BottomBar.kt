package com.example.universalplayer.ui

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Rounded.Home),
    Advanced("Advanced", Icons.Rounded.Tune),
    History("History", Icons.Rounded.History),
    Playlist("Playlist", Icons.Rounded.VideoLibrary)
}

/** Slim (64dp) bottom navigation bar, similar in size to the Play Store one. */
@Composable
fun AppBottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
            Row(Modifier.fillMaxWidth().height(64.dp)) {
                Tab.entries.forEach { t ->
                    val sel = t == selected
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
                            Modifier.width(56.dp).height(28.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (sel) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                t.icon, t.label, Modifier.size(22.dp),
                                tint = if (sel) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            t.label, fontSize = 11.sp,
                            color = if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
