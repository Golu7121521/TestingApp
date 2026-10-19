@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.playnova.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.playnova.app.data.HistoryEntity
import com.playnova.app.data.Video
import com.playnova.app.data.fmtSize
import com.playnova.app.data.fmtTime

val CardShape = RoundedCornerShape(16.dp)
val SmallShape = RoundedCornerShape(12.dp)

@Composable
fun Thumb(v: Video, modifier: Modifier = Modifier) {
    Box(modifier.clip(SmallShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Movie, null, tint = Muted)
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(v.uri).crossfade(true).build(),
            contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()
        )
    }
}

@Composable
fun Skeleton(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "sk")
    val a by t.animateFloat(0.25f, 0.6f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    Box(modifier.clip(SmallShape).alpha(a).background(MaterialTheme.colorScheme.surfaceVariant))
}

@Composable
fun SkeletonGrid() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(4) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Skeleton(Modifier.weight(1f).height(110.dp)); Skeleton(Modifier.weight(1f).height(110.dp))
            }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, msg: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(84.dp).clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(0.35f), Color.Transparent))),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary) }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(msg, color = Muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        if (action != null) Button(onClick = onAction, shape = SmallShape) { Text(action) }
    }
}

@Composable
fun ErrorState(msg: String, onRetry: (() -> Unit)? = null) {
    EmptyState(Icons.Outlined.ErrorOutline, "Playback nahi ho paya", msg, if (onRetry != null) "Phir try karo" else null, onRetry ?: {})
}

@Composable
fun ScreenTitle(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") }
        else Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun highlight(text: String, q: String): AnnotatedString {
    if (q.isBlank()) return AnnotatedString(text)
    val c = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        var i = 0
        val lt = text.lowercase(); val lq = q.lowercase().trim()
        while (i < text.length) {
            val k = lt.indexOf(lq, i)
            if (k < 0) { append(text.substring(i)); break }
            append(text.substring(i, k))
            withStyle(SpanStyle(color = c, fontWeight = FontWeight.Bold)) { append(text.substring(k, k + lq.length)) }
            i = k + lq.length
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, danger: Boolean = false, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirm, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun NameDialog(title: String, initial: String, confirm: String, onOk: (String) -> Unit, onDismiss: () -> Unit) {
    var t by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(t, { t = it }, singleLine = true, label = { Text("Naam") }) },
        confirmButton = { TextButton(enabled = t.isNotBlank(), onClick = { onOk(t); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun progressOf(h: HistoryEntity?, v: Video): Float {
    if (h == null) return 0f
    val d = if (h.durationMs > 0) h.durationMs else v.durationMs
    return if (d > 0) (h.positionMs.toFloat() / d).coerceIn(0f, 1f) else 0f
}

@Composable
fun VideoGridItem(
    v: Video, h: HistoryEntity?, fav: Boolean, selected: Boolean, selecting: Boolean,
    onClick: () -> Unit, onLong: () -> Unit, modifier: Modifier = Modifier
) {
    val hf = LocalHapticFeedback.current
    Column(
        modifier.clip(CardShape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onClick, onLongClick = { hf.performHapticFeedback(HapticFeedbackType.LongPress); onLong() })
            .padding(6.dp)
    ) {
        Box {
            Thumb(v, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Text(fmtTime(v.durationMs), Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(RoundedCornerShape(6.dp))
                .background(Color(0xAA000000)).padding(horizontal = 6.dp, vertical = 2.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
            if (fav) Icon(Icons.Outlined.Favorite, "Favorite", Modifier.align(Alignment.TopStart).padding(6.dp).size(18.dp), tint = Color(0xFFFF5C8A))
            if (selecting) Icon(if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                if (selected) "Selected" else "Not selected", Modifier.align(Alignment.TopEnd).padding(6.dp), tint = Color.White)
            val p = progressOf(h, v)
            if (p > 0.01f) LinearProgressIndicator(progress = { p }, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp))
        }
        Text(v.title, Modifier.padding(top = 6.dp, start = 2.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        Text("${v.resolution}  ${fmtSize(v.size)}", Modifier.padding(start = 2.dp), color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
fun VideoListItem(
    v: Video, h: HistoryEntity?, fav: Boolean, selected: Boolean, selecting: Boolean, query: String = "",
    onClick: () -> Unit, onLong: () -> Unit, trailing: @Composable (() -> Unit)? = null
) {
    val hf = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(CardShape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onClick, onLongClick = { hf.performHapticFeedback(HapticFeedbackType.LongPress); onLong() })
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Thumb(v, Modifier.width(120.dp).aspectRatio(16f / 9f))
            val p = progressOf(h, v)
            if (p > 0.01f) LinearProgressIndicator(progress = { p }, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(highlight(v.title, query), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${fmtTime(v.durationMs)}  |  ${v.resolution}  |  ${fmtSize(v.size)}", color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Text(v.folder, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        if (fav) Icon(Icons.Outlined.Favorite, "Favorite", Modifier.size(18.dp), tint = Color(0xFFFF5C8A))
        if (selecting) Icon(if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, if (selected) "Selected" else "Not selected")
        trailing?.invoke()
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) },
        leadingIcon = if (selected) ({ Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) }) else null,
        modifier = Modifier.padding(end = 6.dp))
}

fun sem(desc: String) = Modifier.semantics { contentDescription = desc }
