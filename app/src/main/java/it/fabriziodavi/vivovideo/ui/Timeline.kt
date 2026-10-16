package it.fabriziodavi.vivovideo.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.CircleShape
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
import it.fabriziodavi.vivovideo.model.MusicTrack
import it.fabriziodavi.vivovideo.model.Layer
import it.fabriziodavi.vivovideo.model.TextItem
import it.fabriziodavi.vivovideo.model.packLanes
import it.fabriziodavi.vivovideo.model.Project
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

private val VIDEO_H = 48.dp
private val AUDIO_H = 18.dp
private val RULER_H = 20.dp
private val HANDLE_W = 14.dp
private val MUSIC_H = 22.dp
private val CUT_D = 22.dp

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
    selectedMusicId: String? = null,
    onSelectMusic: (String) -> Unit = {},
    onMusicChange: (MusicTrack) -> Unit = {},
    onAddMusic: () -> Unit = {},
    selectedTextId: String? = null,
    onSelectText: (String) -> Unit = {},
    onTextChange: (TextItem) -> Unit = {},
    selectedLayerId: String? = null,
    onSelectLayer: (String) -> Unit = {},
    onLayerChange: (Layer) -> Unit = {},
    onClipLongPress: (String) -> Unit = {},
    onCutTap: (index: Int) -> Unit = {},
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
    // Brano trascinato o tagliato (anteprima prima di confermare).
    var musicDraft by remember { mutableStateOf<MusicTrack?>(null) }
    val music = project.music.map { if (it.id == musicDraft?.id) musicDraft!! else it }
    val lanes = project.musicLanes(music).ifEmpty { listOf(emptyList()) }
    var textDraft by remember { mutableStateOf<TextItem?>(null) }
    val texts = project.texts.map { if (it.id == textDraft?.id) textDraft!! else it }
    val textLanes = packLanes(texts, { it.startUs }, { it.endUs })
    var layerDraft by remember { mutableStateOf<Layer?>(null) }
    val layerItems = project.layers.map { if (it.id == layerDraft?.id) layerDraft!! else it }
    val layerLanes = packLanes(layerItems, { it.startUs }, { it.endUs })
    val noRipple = remember { MutableInteractionSource() }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(RULER_H + VIDEO_H + AUDIO_H + 16.dp + (MUSIC_H + 3.dp) * (lanes.size + textLanes.size + layerLanes.size))
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
                Ruler(project.durationUs, dpPerSecond, project.beatTimes())
                Spacer(Modifier.width(half))
            }
            Spacer(Modifier.height(4.dp))
            // Clip e, sopra, i pulsanti delle transizioni sui tagli.
            Box {
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
                            onLongClick = { onClipLongPress(clip.id) },
                            onTrimDrag = { s, e -> trimDraft = Triple(clip.id, s, e) },
                            onTrimEnd = {
                                trimDraft?.let { (id, s, e) -> onTrim(id, s, e) }
                                trimDraft = null
                            },
                        )
                        Spacer(Modifier.height(3.dp))
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
                ) { Icon(VVIcons.Add, "Aggiungi video e foto", tint = VV.Text) }
                Spacer(Modifier.width(half))
            }
            if (trimDraft == null && project.clips.size > 1) {
                val colH = VIDEO_H + 3.dp + AUDIO_H
                val top = (maxOf(colH, 40.dp) - colH) / 2 + (VIDEO_H - CUT_D) / 2
                var x = half
                project.clips.dropLast(1).forEachIndexed { i, c ->
                    x += (c.outputUs / 1e6f * dpPerSecond).dp
                    CutButton(
                        Modifier.offset(x = x - 1.dp - CUT_D / 2, y = top),
                        has = c.transitionOut != null,
                        onClick = { onCutTap(i) },
                    )
                }
            }
            }
            val totalW = (project.durationUs / 1e6f * dpPerSecond).dp
            // Righe delle immagini (livelli).
            layerLanes.forEach { lane ->
                Spacer(Modifier.height(3.dp))
                Row {
                    Spacer(Modifier.width(half))
                    Box(Modifier.width(totalW).height(MUSIC_H)) {
                        lane.forEach { l ->
                            RangeBar(
                                id = l.id, startUs = l.startUs, durationUs = l.durationUs,
                                label = l.name, icon = if (l.isVideo) VVIcons.Movie else VVIcons.Layers, background = VV.LayerBg,
                                minUs = Layer.MIN_US, projectUs = project.durationUs,
                                selected = l.id == selectedLayerId, dpPerSecond = dpPerSecond, pxPerUs = pxPerUs,
                                onClick = { onSelectLayer(l.id) },
                                onDraft = { st, du ->
                                    val orig = project.layers.firstOrNull { it.id == l.id } ?: l
                                    layerDraft = if (!orig.isVideo) orig.copy(startUs = st, durationUs = du) else {
                                        // Video: la maniglia sinistra taglia anche l'inizio del file; la durata non supera il file.
                                        val trimLeft = st != orig.startUs && du != orig.durationUs
                                        val ss = if (trimLeft) (orig.sourceStartUs + st - orig.startUs).coerceAtLeast(0L) else orig.sourceStartUs
                                        orig.copy(startUs = st, durationUs = minOf(du, orig.sourceDurationUs - ss).coerceAtLeast(Layer.MIN_US), sourceStartUs = ss)
                                    }
                                },
                                onCommit = { layerDraft?.let(onLayerChange); layerDraft = null },
                            )
                        }
                    }
                    Spacer(Modifier.width(half))
                }
            }
            // Righe dei testi.
            textLanes.forEach { lane ->
                Spacer(Modifier.height(3.dp))
                Row {
                    Spacer(Modifier.width(half))
                    Box(Modifier.width(totalW).height(MUSIC_H)) {
                        lane.forEach { t ->
                            RangeBar(
                                id = t.id, startUs = t.startUs, durationUs = t.durationUs,
                                label = t.text.replace('\n', ' '), icon = VVIcons.Text, background = VV.TextBg,
                                minUs = TextItem.MIN_US, projectUs = project.durationUs,
                                selected = t.id == selectedTextId, dpPerSecond = dpPerSecond, pxPerUs = pxPerUs,
                                onClick = { onSelectText(t.id) },
                                onDraft = { st, du -> textDraft = t.copy(startUs = st, durationUs = du) },
                                onCommit = { textDraft?.let(onTextChange); textDraft = null },
                            )
                        }
                    }
                    Spacer(Modifier.width(half))
                }
            }
            // Righe della musica.
            lanes.forEachIndexed { li, lane ->
                Spacer(Modifier.height(3.dp))
                Row {
                    Spacer(Modifier.width(half))
                    Box(Modifier.width(totalW).height(MUSIC_H)) {
                        if (lane.isEmpty() && li == 0) {
                            Row(
                                Modifier.fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(VV.Raised)
                                    .clickable(onClick = onAddMusic).padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(VVIcons.Music, null, tint = VV.Music, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Aggiungi musica", color = VV.Dim, fontSize = 10.sp, fontFamily = Plex)
                            }
                        }
                        lane.forEach { t ->
                            MusicBar(
                                track = t,
                                projectUs = project.durationUs,
                                selected = t.id == selectedMusicId,
                                dpPerSecond = dpPerSecond,
                                pxPerUs = pxPerUs,
                                onClick = { onSelectMusic(t.id) },
                                onDraft = { musicDraft = it },
                                onCommit = { musicDraft?.let(onMusicChange); musicDraft = null },
                            )
                        }
                    }
                    Spacer(Modifier.width(half))
                }
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
private fun Ruler(durationUs: Long, dpPerSecond: Float, beats: List<Long> = emptyList()) {
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
                // Misura senza vincoli: vicino al bordo destro lo spazio residuo sarebbe negativo.
                val layout = measurer.measure("${s / 60}:%02d".format(s % 60), style)
                if (x + 3.dp.toPx() + layout.size.width <= size.width) {
                    drawText(layout, topLeft = Offset(x + 3.dp.toPx(), 2.dp.toPx()))
                }
            }
            t += minorStep
        }
        // Battiti della musica: tacche arancioni sotto il righello.
        beats.forEach { b ->
            val x = b / 1e6f * pxPerS
            drawLine(VV.Tally, Offset(x, size.height), Offset(x, size.height - 6.dp.toPx()), 2.dp.toPx())
        }
    }
}

/** Pulsante sul taglio fra due clip: apre la transizione (pieno se ce n'è già una). */
@Composable
private fun CutButton(modifier: Modifier, has: Boolean, onClick: () -> Unit) {
    Box(
        modifier.size(CUT_D).clip(CircleShape)
            .background(if (has) VV.Tally else VV.Canvas)
            .border(1.5.dp, if (has) VV.TallyInk else VV.Text, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (has) {
            Canvas(Modifier.size(12.dp)) {
                val w = size.width; val h = size.height
                val bow = Path().apply {
                    moveTo(0f, h * 0.15f); lineTo(w / 2, h / 2); lineTo(0f, h * 0.85f); close()
                    moveTo(w, h * 0.15f); lineTo(w / 2, h / 2); lineTo(w, h * 0.85f); close()
                }
                drawPath(bow, VV.TallyInk)
            }
        } else {
            Icon(VVIcons.Add, "Transizione", tint = VV.Text, modifier = Modifier.size(14.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClipBox(
    clip: Clip,
    width: Dp,
    selected: Boolean,
    pxPerUs: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
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
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Filmstrip(clip, width)
        Row(Modifier.align(Alignment.BottomStart).padding(start = if (selected) HANDLE_W + 3.dp else 4.dp, bottom = 3.dp)) {
            val label = buildString {
                if (clip.speed != ClipSpeed.NORMAL) append(clip.speed.label).append("  ")
                append("%.1f".format(clip.outputUs / 1e6).replace('.', ','))
            }
            if (clip.isPhoto) Badge("FOTO")
            Badge(label)
            if (clip.log) Badge("LOG")
        }
        // Punti velocità (pallini in basso) nella loro posizione sul video finale.
        if (clip.speed == ClipSpeed.RAMP) {
            val curve = clip.speedCurve()
            Canvas(Modifier.fillMaxSize()) {
                clip.curvePoints.forEach { p ->
                    val x = (curve.outputTimeAt((p.t * clip.trimmedUs).toLong()).toFloat() / clip.outputUs.coerceAtLeast(1)) * size.width
                    drawCircle(VV.TallyInk, 4.5.dp.toPx(), Offset(x.coerceIn(5.dp.toPx(), size.width - 5.dp.toPx()), size.height - 5.dp.toPx()))
                    drawCircle(if (selected) VV.Tally else VV.Text, 3.dp.toPx(), Offset(x.coerceIn(5.dp.toPx(), size.width - 5.dp.toPx()), size.height - 5.dp.toPx()))
                }
            }
        }
        // Rombi dei keyframe, nella loro posizione sul video finale.
        if (clip.keyframes.isNotEmpty()) {
            val curve = clip.speedCurve()
            Canvas(Modifier.fillMaxSize()) {
                val r = 5.dp.toPx()
                clip.keyframes.forEach { k ->
                    val x = (curve.outputTimeAt(k.timeUs).toFloat() / clip.outputUs.coerceAtLeast(1)) * size.width
                    val p = Path().apply {
                        moveTo(x, 3.dp.toPx()); lineTo(x + r, 3.dp.toPx() + r); lineTo(x, 3.dp.toPx() + 2 * r); lineTo(x - r, 3.dp.toPx() + r); close()
                    }
                    drawPath(p, if (selected) VV.Tally else VV.Text)
                    drawPath(p, VV.TallyInk, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                }
            }
        }
        // Transizione verso la clip successiva: zona sfumata e simbolo sul bordo destro.
        clip.transitionOut?.let { spec ->
            val halfUs = minOf(spec.durationUs / 2, clip.outputUs / 2)
            Canvas(Modifier.fillMaxSize()) {
                val zone = (halfUs.toFloat() / clip.outputUs.coerceAtLeast(1)) * size.width
                drawRect(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(Color.Transparent, VV.Tally.copy(alpha = 0.35f)),
                        startX = size.width - zone, endX = size.width,
                    ),
                    topLeft = Offset(size.width - zone, 0f),
                    size = androidx.compose.ui.geometry.Size(zone, size.height),
                )
            }
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
            .background(if (clip.muted || clip.isPhoto) VV.Raised else VV.WaveBg)
    ) {
        val p = peaks
        val mid = size.height / 2
        if (clip.muted || clip.isPhoto || p == null || p.isEmpty()) {
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


/** Brano sulla timeline: tocco = seleziona; se selezionato si sposta trascinandolo e si taglia dalle maniglie. */
@Composable
private fun MusicBar(
    track: MusicTrack,
    projectUs: Long,
    selected: Boolean,
    dpPerSecond: Float,
    pxPerUs: Float,
    onClick: () -> Unit,
    onDraft: (MusicTrack) -> Unit,
    onCommit: () -> Unit,
) {
    val context = LocalContext.current
    val current by rememberUpdatedState(track)
    val peaks by produceState(Waveforms.cached(track.uri), track.uri) {
        if (value == null) value = Waveforms.get(context, track.uri)
    }
    val play = track.playUs(projectUs)
    val x = (track.startUs / 1e6f * dpPerSecond).dp
    val w = (play / 1e6f * dpPerSecond).dp.coerceAtLeast(6.dp)
    var origin by remember { mutableStateOf(track) }
    var acc by remember { mutableStateOf(0f) }
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier
            .offset(x = x)
            .width(w)
            .fillMaxHeight()
            .clip(shape)
            .background(VV.MusicBg)
            .clickable(onClick = onClick)
            .then(
                if (!selected) Modifier else Modifier.pointerInput(track.id) {
                    detectHorizontalDragGestures(
                        onDragStart = { origin = current; acc = 0f },
                        onDragEnd = onCommit,
                        onDragCancel = onCommit,
                    ) { change, dx ->
                        change.consume()
                        acc += dx
                        val d = (acc / pxPerUs).toLong()
                        val s = (origin.startUs + d).coerceIn(0L, (projectUs - MusicTrack.MIN_US).coerceAtLeast(0L))
                        onDraft(origin.copy(startUs = s))
                    }
                }
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val p = peaks
            val mid = size.height / 2
            // Battiti: puntini arancioni in alto.
            if (play > 0) {
                track.timelineAutoBeats(projectUs).forEach { b ->
                    val bx = (b - track.startUs).toFloat() / play * size.width
                    drawCircle(VV.Tally, 2.dp.toPx(), Offset(bx, 3.dp.toPx()))
                }
                // Battiti a mano: tacca bianca su tutta l'altezza.
                track.timelineMarkers(projectUs).forEach { b ->
                    val bx = (b - track.startUs).toFloat() / play * size.width
                    drawLine(Color.White, Offset(bx, 0f), Offset(bx, size.height), 1.5.dp.toPx())
                }
            }
            if (p == null || p.isEmpty() || play <= 0) {
                drawLine(VV.Music.copy(alpha = 0.5f), Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx())
                return@Canvas
            }
            val step = 2.dp.toPx()
            var px = 0f
            while (px < size.width) {
                val t = (px / size.width * play).toLong()
                val idx = ((track.offsetUs + t) / Waveforms.BUCKET_US).toInt().coerceIn(0, p.size - 1)
                val a = (p[idx] * track.gainAt(t, projectUs).coerceIn(0f, 2f)).coerceAtMost(1f) * (mid - 1.dp.toPx())
                drawLine(VV.Music.copy(alpha = 0.75f), Offset(px, mid - a), Offset(px, mid + a), 1.2.dp.toPx())
                px += step
            }
        }
        Row(Modifier.align(Alignment.CenterStart).padding(start = if (selected) HANDLE_W + 3.dp else 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(VVIcons.Music, null, tint = Color.White, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(3.dp))
            Text(track.name, color = Color.White, fontSize = 10.sp, fontFamily = Plex, maxLines = 1)
        }
        if (selected) {
            Box(Modifier.fillMaxSize().border(2.dp, VV.Tally, shape))
            MusicHandle(Modifier.align(Alignment.CenterStart), left = true, track, projectUs, pxPerUs, onDraft, onCommit)
            MusicHandle(Modifier.align(Alignment.CenterEnd), left = false, track, projectUs, pxPerUs, onDraft, onCommit)
        }
    }
}

@Composable
private fun MusicHandle(
    modifier: Modifier,
    left: Boolean,
    track: MusicTrack,
    projectUs: Long,
    pxPerUs: Float,
    onDraft: (MusicTrack) -> Unit,
    onCommit: () -> Unit,
) {
    val current by rememberUpdatedState(track)
    var origin by remember { mutableStateOf(track) }
    var acc by remember { mutableStateOf(0f) }
    Box(
        modifier
            .width(HANDLE_W)
            .fillMaxHeight()
            .background(VV.Tally)
            .pointerInput(track.id, left) {
                detectHorizontalDragGestures(
                    onDragStart = { origin = current; acc = 0f },
                    onDragEnd = onCommit,
                    onDragCancel = onCommit,
                ) { change, dx ->
                    change.consume()
                    acc += dx
                    val d = (acc / pxPerUs).toLong()
                    val o = origin
                    val min = MusicTrack.MIN_US
                    if (left) {
                        // Il bordo sinistro taglia l'inizio del brano: la musica resta ferma nel tempo.
                        val dd = d.coerceIn(maxOf(-o.offsetUs, -o.startUs), o.lengthUs - min)
                        onDraft(o.copy(startUs = o.startUs + dd, offsetUs = o.offsetUs + dd, lengthUs = o.lengthUs - dd))
                    } else {
                        val maxLen = minOf(o.sourceDurationUs - o.offsetUs, projectUs - o.startUs).coerceAtLeast(min)
                        val len = (minOf(o.lengthUs, maxLen) + d).coerceIn(min, maxLen)
                        onDraft(o.copy(lengthUs = len))
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(2.dp).height(10.dp).clip(RoundedCornerShape(1.dp)).background(VV.TallyInk))
    }
}


/**
 * Elemento con inizio e durata su una riga della timeline (testi, immagini):
 * tocco = seleziona; se selezionato si sposta trascinandolo e si allunga/accorcia dalle maniglie.
 */
@Composable
private fun RangeBar(
    id: String,
    startUs: Long,
    durationUs: Long,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    minUs: Long,
    projectUs: Long,
    selected: Boolean,
    dpPerSecond: Float,
    pxPerUs: Float,
    onClick: () -> Unit,
    onDraft: (startUs: Long, durationUs: Long) -> Unit,
    onCommit: () -> Unit,
) {
    val curStart by rememberUpdatedState(startUs)
    val curDur by rememberUpdatedState(durationUs)
    var o0 by remember { mutableStateOf(0L) }
    var d0 by remember { mutableStateOf(0L) }
    var acc by remember { mutableStateOf(0f) }
    val shape = RoundedCornerShape(4.dp)
    fun drag(mode: Int) = Modifier.pointerInput(id, mode) {
        detectHorizontalDragGestures(
            onDragStart = { o0 = curStart; d0 = curDur; acc = 0f },
            onDragEnd = onCommit,
            onDragCancel = onCommit,
        ) { change, dx ->
            change.consume()
            acc += dx
            val d = (acc / pxPerUs).toLong()
            when (mode) {
                0 -> onDraft((o0 + d).coerceIn(0L, (projectUs - d0).coerceAtLeast(0L)), d0)
                1 -> { val dd = d.coerceIn(-o0, d0 - minUs); onDraft(o0 + dd, d0 - dd) }
                else -> onDraft(o0, (d0 + d).coerceIn(minUs, (projectUs - o0).coerceAtLeast(minUs)))
            }
        }
    }
    Box(
        Modifier
            .offset(x = (startUs / 1e6f * dpPerSecond).dp)
            .width((durationUs / 1e6f * dpPerSecond).dp.coerceAtLeast(6.dp))
            .fillMaxHeight()
            .clip(shape)
            .background(background)
            .clickable(onClick = onClick)
            .then(if (selected) drag(0) else Modifier)
    ) {
        Row(
            Modifier.align(Alignment.CenterStart).padding(start = if (selected) HANDLE_W + 3.dp else 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = VV.Tally, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(3.dp))
            Text(label, color = Color.White, fontSize = 10.sp, fontFamily = Plex, maxLines = 1)
        }
        if (selected) {
            Box(Modifier.fillMaxSize().border(2.dp, VV.Tally, shape))
            for (left in listOf(true, false)) {
                Box(
                    Modifier.align(if (left) Alignment.CenterStart else Alignment.CenterEnd)
                        .width(HANDLE_W).fillMaxHeight().background(VV.Tally)
                        .then(drag(if (left) 1 else 2)),
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.width(2.dp).height(10.dp).clip(RoundedCornerShape(1.dp)).background(VV.TallyInk)) }
            }
        }
    }
}
