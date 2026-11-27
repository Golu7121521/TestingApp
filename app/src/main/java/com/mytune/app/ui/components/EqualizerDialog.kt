package com.mytune.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mytune.app.audio.EqualizerManager

/** Equalizer: on/off, presets, bass + surround and five frequency bands, with a reset button. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualizerDialog(eq: EqualizerManager, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { eq.ensureLoaded() }
    val st0 by eq.state.collectAsState()
    val phones by eq.headphones.collectAsState()
    // Without earphones the equalizer is always off.
    val st = st0.copy(enabled = st0.enabled && phones)
    val alpha = if (st.enabled) 1f else 0.45f

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.padding(16.dp).fillMaxWidth().heightIn(max = 640.dp),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Equalizer, null, tint = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Equalizer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(if (st.enabled) st.preset else "Off", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = st.enabled, enabled = phones, onCheckedChange = { eq.setEnabled(it) })
                }
                if (!phones) {
                    Spacer(Modifier.height(10.dp))
                    Text("Connect wired or Bluetooth earphones to use the equalizer. It stays off on the phone speaker.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }

                Spacer(Modifier.height(16.dp))
                Text("Presets", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    EqualizerManager.PRESETS.forEach { p ->
                        FilterChip(selected = st.enabled && st.preset == p.name, enabled = phones, onClick = { eq.applyPreset(p) }, label = { Text(p.name) })
                    }
                }

                Spacer(Modifier.height(16.dp))
                LabeledSlider("Bass", st.bass / 1000f, st.enabled) { eq.setBass((it * 1000).toInt()) }
                LabeledSlider("Surround", st.surround / 1000f, st.enabled) { eq.setSurround((it * 1000).toInt()) }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().graphicsAlpha(alpha), horizontalArrangement = Arrangement.SpaceBetween) {
                    EqualizerManager.BAND_LABELS.forEachIndexed { i, label ->
                        BandSlider(label, st.bands[i], st.enabled) { eq.setBand(i, it) }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { eq.reset() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.RestartAlt, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reset")
                    }
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Done") }
                }
            }
        }
    }
}

private fun Modifier.graphicsAlpha(a: Float): Modifier = this.alpha(a)

@Composable
private fun LabeledSlider(label: String, value: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(74.dp), fontWeight = FontWeight.Medium)
        Slider(value = value.coerceIn(0f, 1f), onValueChange = onChange, enabled = enabled, modifier = Modifier.weight(1f))
        Text("${(value * 100).toInt()}%", Modifier.width(40.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Vertical slider for one band: drag or tap on the track, -12..+12 dB. */
@Composable
private fun BandSlider(label: String, db: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    val max = EqualizerManager.MAX_DB
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val change by rememberUpdatedState(onChange)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(54.dp)) {
        Text("${if (db > 0) "+" else ""}${db.toInt()} dB", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier.width(44.dp).height(170.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { o -> change(((0.5f - o.y / size.height) * 2f * max).coerceIn(-max, max)) }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectVerticalDragGestures { ch, _ ->
                        ch.consume()
                        change(((0.5f - ch.position.y / size.height) * 2f * max).coerceIn(-max, max))
                    }
                },
        ) {
            val w = 8.dp.toPx()
            val x = (size.width - w) / 2f
            drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(w / 2f))
            val mid = size.height / 2f
            val y = mid - (db / max) * mid
            val top = minOf(y, mid)
            val h = kotlin.math.abs(y - mid)
            drawRoundRect(Brush.verticalGradient(listOf(primary, primary.copy(alpha = 0.5f))), Offset(x, top), Size(w, h.coerceAtLeast(1f)), CornerRadius(w / 2f))
            drawLine(Color.White.copy(alpha = 0.25f), Offset(x - 6.dp.toPx(), mid), Offset(x + w + 6.dp.toPx(), mid), 1.dp.toPx())
            drawCircle(primary.copy(alpha = 0.25f), 15.dp.toPx(), Offset(size.width / 2f, y))
            drawCircle(Color.White, 9.dp.toPx(), Offset(size.width / 2f, y))
            drawCircle(primary, 5.dp.toPx(), Offset(size.width / 2f, y))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
