package com.example.filerecovery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.filerecovery.data.FileCategory
import com.example.filerecovery.data.RecoverableFile
import com.example.filerecovery.data.formatExpiry
import com.example.filerecovery.data.formatSize
import com.example.filerecovery.ui.components.FancyGradientButton
import com.example.filerecovery.ui.components.PulseRing

@Composable
fun PermissionScreen(onGrant: () -> Unit, onAllFiles: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🛟", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Storage access chahiye",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Deleted files dhundhne ke liye app ko storage aur 'All files access' permission chahiye. Sab kuch aapke phone par hi hota hai, kahin upload nahi hota.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        FancyGradientButton(
            text = "Media permission do",
            icon = Icons.Default.Check,
            onClick = onGrant,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        FancyGradientButton(
            text = "All files access kholo",
            icon = Icons.Default.Settings,
            onClick = onAllFiles,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun RecoveryScreen(vm: RecoveryViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val visible = remember(state.files, state.filter) {
        state.files.filter { state.filter == FileCategory.ALL || it.category == state.filter }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            "File Recovery",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            state.status,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (!state.scanning) {
                PulseRing(Modifier.matchParentSize())
            }
            FancyGradientButton(
                text = if (state.scanning) "Scanning..." else "Deleted files scan karo",
                icon = Icons.Default.Search,
                onClick = vm::scan,
                modifier = Modifier.fillMaxWidth(0.9f),
                enabled = !state.scanning
            )
        }

        Spacer(Modifier.height(16.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FileCategory.entries) { category ->
                FilterChip(
                    selected = state.filter == category,
                    onClick = { vm.setFilter(category) },
                    label = { Text(category.label) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${visible.size} items", style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = vm::selectAllVisible, enabled = visible.isNotEmpty()) {
                Text("Sab select karo")
            }
        }

        if (visible.isEmpty() && !state.scanning) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Yahan recoverable files dikhengi",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(visible, key = { it.id }) { file ->
                    FileRow(
                        file = file,
                        selected = file.id in state.selected,
                        onToggle = { vm.toggle(file.id) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state.selected.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            FancyGradientButton(
                text = "Restore ${state.selected.size} selected",
                icon = Icons.Default.Check,
                onClick = vm::restoreSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )
        }
    }
}

@Composable
fun FileRow(
    file: RecoverableFile,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onToggle,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emojiFor(file.category), fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    file.originalName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${formatSize(file.sizeBytes)} • Expires ${formatExpiry(file.expiresAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Checkbox(checked = selected, onCheckedChange = null)
        }
    }
}

private fun emojiFor(category: FileCategory): String = when (category) {
    FileCategory.PHOTOS -> "🖼️"
    FileCategory.VIDEOS -> "🎬"
    FileCategory.AUDIO -> "🎵"
    FileCategory.DOCS -> "📄"
    else -> "📁"
}
