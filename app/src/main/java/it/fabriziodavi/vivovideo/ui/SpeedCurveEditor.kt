package it.fabriziodavi.vivovideo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.fabriziodavi.vivovideo.model.SpeedInterp
import it.fabriziodavi.vivovideo.model.SpeedLimits
import it.fabriziodavi.vivovideo.model.SpeedPoint
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln

/**
 * Editor della curva di velocità.
 * Trascina un punto per cambiarne velocità e posizione; tocca un punto vuoto per aggiungerne uno;
 * doppio tocco su un punto per toglierlo. Inizio e fine restano ai bordi della clip.
 */
@Composable
fun SpeedCurveEditor(
    points: List<SpeedPoint>,
    playFraction: Float?,
    enabled: Boolean,
    onChange: (List<SpeedPoint>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(points) { mutableStateOf(points.sortedBy { it.t }) }
    var activeIndex by remember { mutableStateOf<Int?>(null) }
    val currentPoints by rememberUpdatedState(points)
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = Plex, fontSize = 10.sp, color = VV.Dim)
    val valueStyle = TextStyle(fontFamily = Plex, fontSize = 12.sp, color = VV.TallyInk)

    val minL = ln(SpeedLimits.MIN)
    val maxL = ln(SpeedLimits.MAX)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(VV.Raised)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val padL = 34.dp.toPx(); val padR = 14.dp.toPx(); val padV = 14.dp.toPx()
                fun x(t: Float) = padL + (size.width - padL - padR) * t
                fun y(s: Float) = padV + (size.height - 2 * padV) * (1 - (ln(s) - minL) / (maxL - minL))
                fun tAt(px: Float) = ((px - padL) / (size.width - padL - padR)).coerceIn(0f, 1f)
                fun sAt(py: Float): Float {
                    val v = 1 - (py - padV) / (size.height - 2 * padV)
                    var s = exp(minL + v.coerceIn(0f, 1f) * (maxL - minL))
                    if (abs(s - 1f) < 0.06f) s = 1f // aggancio a 1×
                    return s.coerceIn(SpeedLimits.MIN, SpeedLimits.MAX)
                }
                var lastTapTime = 0L
                var lastTapIndex = -1
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val pts = currentPoints.sortedBy { it.t }
                    val hit = pts.indices.minByOrNull { hypot(x(pts[it].t) - down.position.x, y(pts[it].speed) - down.position.y) }
                        ?.takeIf { hypot(x(pts[it].t) - down.position.x, y(pts[it].speed) - down.position.y) < 28.dp.toPx() }
                    var moved = false
                    var work = pts
                    activeIndex = hit
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull() ?: break
                        if (!ch.pressed) break
                        if (hit != null && (moved || ch.positionChange().getDistance() > 2f)) {
                            moved = true
                            ch.consume()
                            val i = hit
                            val t = when (i) {
                                0 -> 0f
                                pts.lastIndex -> 1f
                                else -> tAt(ch.position.x).coerceIn(pts[i - 1].t + 0.03f, pts[i + 1].t - 0.03f)
                            }
                            work = pts.toMutableList().also { it[i] = SpeedPoint(t, sAt(ch.position.y)) }
                            draft = work
                        }
                    }
                    if (moved) {
                        onChange(work)
                    } else {
                        val now = System.currentTimeMillis()
                        if (hit != null) {
                            // Doppio tocco su un punto interno: lo toglie.
                            if (hit == lastTapIndex && now - lastTapTime < 350 && hit != 0 && hit != pts.lastIndex) {
                                onChange(pts.filterIndexed { k, _ -> k != hit })
                                lastTapIndex = -1
                            } else {
                                lastTapIndex = hit; lastTapTime = now
                            }
                        } else {
                            // Tocco su un punto vuoto: aggiunge un punto lì.
                            val t = tAt(down.position.x)
                            if (t > 0.02f && t < 0.98f && pts.none { abs(it.t - t) < 0.03f }) {
                                onChange((pts + SpeedPoint(t, sAt(down.position.y))).sortedBy { it.t })
                            }
                            lastTapIndex = -1
                        }
                    }
                    activeIndex = null
                }
            }
    ) {
        val padL = 34.dp.toPx(); val padR = 14.dp.toPx(); val padV = 14.dp.toPx()
        fun x(t: Float) = padL + (size.width - padL - padR) * t
        fun y(s: Float) = padV + (size.height - 2 * padV) * (1 - (ln(s) - minL) / (maxL - minL))

        // Griglia delle velocità
        for (g in listOf(0.25f, 0.5f, 1f, 2f, 4f)) {
            val yy = y(g)
            drawLine(if (g == 1f) VV.Dim.copy(alpha = 0.5f) else VV.Line, Offset(padL, yy), Offset(size.width - padR, yy), 1.dp.toPx())
            val label = (if (g < 1f) g.toString().replace('.', ',') else g.toInt().toString()) + "×"
            val lay = measurer.measure(label, labelStyle)
            drawText(lay, topLeft = Offset(4.dp.toPx(), yy - lay.size.height / 2))
        }
        // Posizione corrente del video dentro la clip
        playFraction?.let { f ->
            drawLine(VV.Text.copy(alpha = 0.35f), Offset(x(f), padV / 2), Offset(x(f), size.height - padV / 2), 1.dp.toPx())
        }
        // Curva
        val path = Path()
        val n = 120
        for (i in 0..n) {
            val t = i / n.toFloat()
            val xx = x(t); val yy = y(SpeedInterp.valueAt(draft, t))
            if (i == 0) path.moveTo(xx, yy) else path.lineTo(xx, yy)
        }
        drawPath(path, VV.Tally, style = Stroke(2.5.dp.toPx()))
        // Punti
        draft.forEachIndexed { i, p ->
            val c = Offset(x(p.t), y(p.speed))
            val active = i == activeIndex
            drawCircle(VV.Tally, radius = if (active) 9.dp.toPx() else 6.dp.toPx(), center = c)
            drawCircle(VV.Raised, radius = if (active) 4.dp.toPx() else 2.5.dp.toPx(), center = c)
            if (active) {
                val txt = "%.2f×".format(p.speed).replace('.', ',')
                val lay = measurer.measure(txt, valueStyle)
                val bx = (c.x - lay.size.width / 2f - 6.dp.toPx()).coerceIn(0f, size.width - lay.size.width - 12.dp.toPx())
                val by = (c.y - 34.dp.toPx()).coerceAtLeast(0f)
                drawRoundRect(VV.Tally, Offset(bx, by), androidx.compose.ui.geometry.Size(lay.size.width + 12.dp.toPx(), lay.size.height + 6.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
                drawText(lay, topLeft = Offset(bx + 6.dp.toPx(), by + 3.dp.toPx()))
            }
        }
    }
}
