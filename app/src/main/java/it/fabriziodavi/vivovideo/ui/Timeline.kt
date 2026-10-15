package it.fabriziodavi.vivovideo.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.ClipSpeed
import it.fabriziodavi.vivovideo.model.Project
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

private val VIDEO_H = 54.dp
private val AUDIO_H = 22.dp
private val RULER_H = 24.dp
private val HANDLE_W = 14.dp

/**
 * Timeline con l'indicatore fisso al centro: scorrendo si sceglie il punto,
 * durante la riproduzione la timeline segue il video.
 */
@Composable
fun Timeline(
    project: Project,
    playheadUs: Long,
    selectedClipId: String?,
    dpPerSecond: Float,
    onSeek: (Long) -> Unit,
    onScrubStart: () -> Unit,
    onSelect: (String?) -> Unit,
    onTrim: (clipId: String, startUs: Long, endUs: Long) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val pxPerUs = with(density) { dpPerSecond.dp.toPx() } / 1_000_000f

    // Distingue il dito dell'utente dallo scorrimento automatico durante la riproduzione.
    var userTouch by remember { mutableStateOf(false) }
    LaunchedEffect(scroll) {
        scroll.interactionSource.interactions.collect { if (it is DragInteraction.Start) { userTouch = true; onScrubStart() } }
    }
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.isScrollInProgress }.collect { if (!it) userTouch = false }
    }
    LaunchedEffect(scroll, pxPerUs) {
        snapshotFlow { scroll.value }.collect { v -> if (userTouch) onSeek((v / pxPerUs).toLong()) }
    }
    LaunchedEffect(playheadUs, pxPerUs) {
        if (!userTouch) {
            val target = (playheadUs * pxPerUs).roundToInt()
            if (abs(target - scroll.value) > 1) scroll.scrollTo(target)
        }
    }

    // Taglio in corso con le maniglie (anteprima della nuova durata prima di confermare).
    var trimDraft by remember { mutableStateOf<Triple<String, Long, Long>?>(null) }
    val noRipple = remember { MutableInteractionSource() }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(RULER_H + VIDEO_H + AUDIO_H + 22.dp)
            .background(VV.Panel)
    ) {
        val half = maxWidth / 2
        Column(
            Modifier
                .horizontalScroll(scroll)
                .clickable(interactionSource = noRipple, indication = null) { onSelect(null) }
        ) {
            Row {
                Spacer(Modifier.width(half))
                Ruler(project.durationUs, dpPerSecond)
                Spacer(Modifier.width(half))
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(half))
                project.clips.forEach { original ->
                    val draft = trimDraft?.takeIf { it.first == original.id }
                    val clip = if (draft != null) original.copy(trimStartUs = draft.second, trimEndUs = draft.third) else original
                    val w = (clip.outputUs / 1e6f * dpPerSecond).dp
                    Column {
                        ClipBox(
                            clip = clip,
                            width = w,
                            selected = clip.id == selectedClipId,
                            pxPerUs = pxPerUs,
                            onClick = { onSelect(clip.id) },
                            onTrimDrag = { s, e -> trimDraft = Triple(clip.id, s, e) },
                            onTrimEnd = {
                                trimDraft?.let { (id, s, e) -> onTrim(id, s, e) }
                                trimDraft = null
                            },
                        )
                        Spacer(Modifier.height(4.dp))
                        AudioLane(clip, w)
                    }
                }
                Box(
                    Modifier
                        .padding(start = 12.dp)
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(VV.Raised)
                        .clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) { Icon(VVIcons.Add, "Aggiungi video", tint = VV.Text) }
                Spacer(Modifier.width(half))
            }
        }
        Playhead(Modifier.offset(x = half - 1.dp))
    }
}

@Composable
private fun Playhead(modifier: Modifier) {
    Canvas(modifier.width(2.dp).fillMaxHeight()) {
        drawRect(VV.Tally)
        val p = Path().apply {
            moveTo(size.width / 2 - 6.dp.toPx(), 0f)
            lineTo(size.width / 2 + 6.dp.toPx(), 0f)
            lineTo(size.width / 2, 8.dp.toPx())
            close()
        }
        drawPath(p, VV.Tally)
    }
}

@Composable
private fun Ruler(durationUs: Long, dpPerSecond: Float) {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontFamily = Plex, fontSize = 10.sp, color = VV.Dim)
    // Etichetta ogni 1, 2, 5 o 10 secondi a seconda dello zoom; tacche intermedie.
    val every = when {
        dpPerSecond >= 60 -> 1
        dpPerSecond >= 30 -> 2
        dpPerSecond >= 12 -> 5
        else -> 10
    }
    val widthDp = (durationUs / 1e6f * dpPerSecond).dp.coerceAtLeast(1.dp)
    Canvas(Modifier.width(widthDp).height(RULER_H)) {
        val pxPerS = dpPerSecond.dp.toPx()
        val seconds = ceil(durationUs / 1e6).toInt()
        val minorStep = every / 2f
        var t = 0f
        while (t <= seconds) {
            val x = t * pxPerS
            val major = (t % every) == 0f
            val h = if (major) 9.dp.toPx() else 5.dp.toPx()
            drawLine(Color(0xFF3A404A), Offset(x, size.height), Offset(x, size.height - h), 1.dp.toPx())
            if (major) {
                val s = t.toInt()
                drawText(measurer, "${s / 60}:%02d".format(s % 60), Offset(x + 3.dp.toPx(), 2.dp.toPx()), style)
            }
            t += minorStep
        }
    }
}

@Composable
private fun ClipBox(
    clip: Clip,
    width: Dp,
    selected: Boolean,
    pxPerUs: Float,
    onClick: () -> Unit,
    onTrimDrag: (Long, Long) -> Unit,
    onTrimEnd: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .padding(end = 2.dp)
            .width((width - 2.dp).coerceAtLeast(4.dp))
            .height(VIDEO_H)
            .clip(shape)
            .background(VV.Raised)
            .clickable(onClick = onClick)
    ) {
        Filmstrip(clip, width)
        Row(Modifier.align(Alignment.BottomStart).padding(start = if (selected) HANDLE_W + 3.dp else 4.dp, bottom = 3.dp)) {
            val label = buildString {
                if (clip.speed != ClipSpeed.NORMAL) append(clip.speed.label).append("  ")
                append("%.1f".format(clip.outputUs / 1e6).replace('.', ','))
            }
            Badge(label)
            if (clip.log) Badge("LOG")
        }
        if (selected) {
            Box(Modifier.fillMaxSize().border(2.dp, VV.Tally, shape))
            TrimHandle(Modifier.align(Alignment.CenterStart), left = true, clip, pxPerUs, onTrimDrag, onTrimEnd)
            TrimHandle(Modifier.align(Alignment.CenterEnd), left = false, clip, pxPerUs, onTrimDrag, onTrimEnd)
        }
    }
}

/** Maniglia gialla: trascinandola si accorcia o si allunga la clip da quel lato. */
@Composable
private fun TrimHandle(
    modifier: Modifier,
    left: Boolean,
    clip: Clip,
    pxPerUs: Float,
    onTrimDrag: (Long, Long) -> Unit,
    onTrimEnd: () -> Unit,
) {
    val current by rememberUpdatedState(clip)
    var start by remember { mutableStateOf(0L) }
    var end by remember { mutableStateOf(0L) }
    var avgSpeed by remember { mutableStateOf(1f) }
    var acc by remember { mutableStateOf(0f) }
    Box(
        modifier
            .width(HANDLE_W)
            .fillMaxHeight()
            .background(VV.Tally)
            .pointerInput(clip.id, left) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        start = current.trimStartUs; end = current.trimEndUs; acc = 0f
                        avgSpeed = current.trimmedUs.toFloat() / current.outputUs.coerceAtLeast(1)
                    },
                    onDragEnd = onTrimEnd,
                    onDragCancel = onTrimEnd,
                ) { change, dx ->
                    change.consume()
                    acc += dx
                    // Spostamento sullo schermo → tempo sorgente (tiene conto della velocità media).
                    val dSrc = (acc / pxPerUs * avgSpeed).toLong()
                    val min = 200_000L
                    if (left) {
                        val s = (start + dSrc).coerceIn(0L, end - min)
                        onTrimDrag(s, end)
                    } else {
                        val e = (end + dSrc).coerceIn(start + min, current.sourceDurationUs)
                        onTrimDrag(start, e)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(2.dp).height(16.dp).clip(RoundedCornerShape(1.dp)).background(VV.TallyInk))
    }
}

@Composable
private fun Badge(text: String) {
    Text(
        text,
        color = Color.White,
        fontSize = 10.sp,
        fontFamily = Plex,
        modifier = Modifier
            .padding(end = 3.dp)
            .background(Color(0x9E000000), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Composable
private fun Filmstrip(clip: Clip, width: Dp) {
    val tiles = ceil(width / VIDEO_H).toInt().coerceIn(1, 80)
    val curve = remember(clip.speed, clip.trimStartUs, clip.trimEndUs) { clip.speedCurve() }
    Row {
        for (i in 0 until tiles) {
            // Tempo sorgente al centro della tessera (tiene conto della velocità).
            val outT = ((i + 0.5f) / tiles * clip.outputUs).toLong()
            val srcT = clip.trimStartUs + curve.sourceTimeAt(outT)
            Thumb(clip.uri, srcT)
        }
    }
}

@Composable
private fun Thumb(uri: String, timeUs: Long) {
    val context = LocalContext.current
    val hPx = with(LocalDensity.current) { VIDEO_H.roundToPx() }
    // Arrotonda a 0,5 s per riusare le miniature mentre si taglia.
    val t = timeUs / 500_000 * 500_000
    val bmp by produceState<Bitmap?>(Thumbnails.cached(uri, t, hPx), uri, t) {
        if (value == null) value = Thumbnails.get(context, uri, t, hPx)
    }
    Box(Modifier.size(VIDEO_H)) {
        bmp?.let {
            Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.size(VIDEO_H))
        }
    }
}

/** Corsia audio sotto la clip, con la forma d'onda del tratto usato. */
@Composable
private fun AudioLane(clip: Clip, width: Dp) {
    val context = LocalContext.current
    val peaks by produceState(Waveforms.cached(clip.uri), clip.uri) {
        if (value == null) value = Waveforms.get(context, clip.uri)
    }
    val curve = remember(clip.speed, clip.trimStartUs, clip.trimEndUs) { clip.speedCurve() }
    Canvas(
        Modifier
            .padding(end = 2.dp)
            .width((width - 2.dp).coerceAtLeast(4.dp))
            .height(AUDIO_H)
            .clip(RoundedCornerShape(4.dp))
            .background(if (clip.muted) VV.Raised else VV.WaveBg)
    ) {
        val p = peaks
        val mid = size.height / 2
        if (clip.muted || p == null || p.isEmpty()) {
            drawLine(VV.Faint, Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx())
            return@Canvas
        }
        val step = 2.dp.toPx()
        val gain = clip.volume.coerceIn(0f, 2f)
        var x = 0f
        while (x < size.width) {
            val outT = (x / size.width * clip.outputUs).toLong()
            val src = clip.trimStartUs + curve.sourceTimeAt(outT)
            val idx = (src / Waveforms.BUCKET_US).toInt().coerceIn(0, p.size - 1)
            val a = (p[idx] * gain).coerceAtMost(1f) * (mid - 1.dp.toPx())
            drawLine(VV.Wave, Offset(x, mid - a), Offset(x, mid + a), 1.2.dp.toPx())
            x += step
        }
    }
}
