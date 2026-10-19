package com.playnova.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.playnova.app.data.fmtTime

/** Timeline with buffered progress and A-B markers. Touch height 40dp. */
@Composable
fun SeekBar(
    pos: Long, dur: Long, buffered: Long, markA: Long, markB: Long,
    onScrub: (Float) -> Unit, onDone: (Float) -> Unit, modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    var drag by remember { mutableStateOf<Float?>(null) }
    val frac = drag ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val bufF = if (dur > 0) (buffered.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val durS by rememberUpdatedState(dur)
    Canvas(
        modifier.fillMaxWidth().height(40.dp)
            .semantics { contentDescription = "Seek bar ${fmtTime(pos)} of ${fmtTime(dur)}" }
            .pointerInput(Unit) { detectTapGestures { o -> if (durS > 0) onDone((o.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> if (durS > 0) { drag = (o.x / size.width).coerceIn(0f, 1f); onScrub(drag!!) } },
                    onHorizontalDrag = { c, _ -> if (durS > 0) { c.consume(); drag = (c.position.x / size.width).coerceIn(0f, 1f); onScrub(drag!!) } },
                    onDragEnd = { drag?.let(onDone); drag = null },
                    onDragCancel = { drag = null }
                )
            }
    ) {
        val h = 4.dp.toPx(); val y = size.height / 2 - h / 2; val r = CornerRadius(h / 2)
        drawRoundRect(Color.White.copy(0.28f), Offset(0f, y), Size(size.width, h), r)
        drawRoundRect(Color.White.copy(0.5f), Offset(0f, y), Size(size.width * bufF, h), r)
        drawRoundRect(primary, Offset(0f, y), Size(size.width * frac, h), r)
        if (dur > 0) {
            if (markA >= 0) drawRect(Color(0xFFFFC107), Offset(size.width * markA / dur, y - 4.dp.toPx()), Size(2.dp.toPx(), h + 8.dp.toPx()))
            if (markB >= 0) drawRect(Color(0xFFFFC107), Offset(size.width * markB / dur, y - 4.dp.toPx()), Size(2.dp.toPx(), h + 8.dp.toPx()))
        }
        drawCircle(Color.White, if (drag != null) 10.dp.toPx() else 7.dp.toPx(), Offset(size.width * frac, size.height / 2))
    }
}
