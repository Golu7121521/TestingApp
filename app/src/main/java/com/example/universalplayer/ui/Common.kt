package com.example.universalplayer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.universalplayer.data.HistoryItem

fun HistoryItem.progress(): Float =
    if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

@Composable
fun Thumb(url: String?, modifier: Modifier = Modifier.width(140.dp), progress: Float = 0f, fit: Boolean = false) {
    Box(
        modifier.aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            AsyncImage(
                model = url, contentDescription = null, contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (progress > 0f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                trackColor = Color.Black.copy(alpha = 0.35f)
            )
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Swipe left to delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeRow(onDelete: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
    })
    SwipeToDismissBox(
        state = state, modifier = modifier, enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) { Icon(Icons.Rounded.Delete, "Delete", tint = MaterialTheme.colorScheme.onErrorContainer) }
        }
    ) {
        Surface(color = MaterialTheme.colorScheme.background) { content() }
    }
}

/** Tap = click, long press = onLongClick (with haptic feedback). */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.longPressable(onClick: () -> Unit, onLongClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick, onLongClick = onLongClick)

@Composable
fun TextDialog(
    title: String, label: String, initial: String, confirm: String,
    onDismiss: () -> Unit,
    validate: (String) -> Boolean = { it.isNotBlank() },
    error: String = "Enter a value",
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    var err by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                text, { text = it; err = false }, singleLine = true, label = { Text(label) },
                isError = err, supportingText = { if (err) Text(error) }
            )
        },
        confirmButton = {
            TextButton(onClick = { if (validate(text)) onConfirm(text) else err = true }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Long-press menu: Rename / Remove (remove asks for confirmation). */
@Composable
fun ItemMenuHost(
    name: String,
    removeText: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onRemove: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    when (step) {
        0 -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    MenuRow(Icons.Rounded.Edit, "Rename") { step = 1 }
                    MenuRow(Icons.Rounded.Delete, "Remove") { step = 2 }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
        1 -> TextDialog("Rename", "Name", name, "Save", onDismiss) { onRename(it.trim()); onDismiss() }
        else -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Remove?") },
            text = { Text(removeText) },
            confirmButton = { TextButton(onClick = { onRemove(); onDismiss() }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
