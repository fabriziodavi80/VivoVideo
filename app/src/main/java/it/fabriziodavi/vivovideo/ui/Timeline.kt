package it.fabriziodavi.vivovideo.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.ClipSpeed
import it.fabriziodavi.vivovideo.model.Project
import kotlin.math.ceil
import kotlin.math.roundToInt

private val TRACK_H = 56.dp

/**
 * Timeline con l'indicatore fisso al centro: scorrendo si sceglie il punto,
 * durante la riproduzione la timeline segue il video.
 */
@Composable
fun Timeline(
    project: Project,
    playheadUs: Long,
    isPlaying: Boolean,
    selectedClipId: String?,
    dpPerSecond: Float,
    onSeek: (Long) -> Unit,
    onScrubStart: () -> Unit,
    onSelect: (String) -> Unit,
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
    // Scorrimento dell'utente (trascinamento e inerzia) → sposta il tempo.
    LaunchedEffect(scroll, pxPerUs) {
        snapshotFlow { scroll.value }.collect { v -> if (userTouch) onSeek((v / pxPerUs).toLong()) }
    }
    // Riproduzione o modifiche → la timeline segue l'indicatore.
    LaunchedEffect(playheadUs, pxPerUs) {
        if (!userTouch) {
            val target = (playheadUs * pxPerUs).roundToInt()
            if (kotlin.math.abs(target - scroll.value) > 1) scroll.scrollTo(target)
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth().height(TRACK_H + 34.dp)) {
        val half = maxWidth / 2
        Column(Modifier.horizontalScroll(scroll)) {
            // Righello dei tempi
            Row {
                Spacer(Modifier.width(half))
                Ruler(project.durationUs, dpPerSecond)
                Spacer(Modifier.width(half))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(half))
                project.clips.forEach { clip ->
                    ClipBox(
                        clip = clip,
                        width = (clip.outputUs / 1e6f * dpPerSecond).dp,
                        selected = clip.id == selectedClipId,
                        onClick = { onSelect(clip.id) },
                    )
                }
                Spacer(Modifier.width(half))
            }
        }
        // Indicatore fisso al centro
        Box(
            Modifier
                .offset(x = half - 1.dp)
                .width(2.dp)
                .fillMaxHeight()
                .background(Color.White)
        )
    }
}

@Composable
private fun Ruler(durationUs: Long, dpPerSecond: Float) {
    val seconds = ceil(durationUs / 1e6).toInt().coerceAtLeast(1)
    // Un'etichetta ogni 1, 2, 5 o 10 secondi a seconda dello zoom.
    val every = when {
        dpPerSecond >= 60 -> 1
        dpPerSecond >= 30 -> 2
        dpPerSecond >= 12 -> 5
        else -> 10
    }
    Box(Modifier.width((durationUs / 1e6f * dpPerSecond).dp).height(26.dp)) {
        for (s in 0..seconds step every) {
            Text(
                "${s / 60}:%02d".format(s % 60),
                color = TextDim,
                fontSize = 10.sp,
                modifier = Modifier.offset(x = (s * dpPerSecond).dp).padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun ClipBox(clip: Clip, width: Dp, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .padding(horizontal = 1.dp)
            .width(width)
            .height(TRACK_H)
            .clip(shape)
            .background(Surface2)
            .clickable(onClick = onClick)
            .then(if (selected) Modifier.border(2.dp, Accent, shape) else Modifier)
    ) {
        Filmstrip(clip, width)
        // Etichette: durata e velocità
        Row(Modifier.align(Alignment.BottomStart).padding(3.dp)) {
            Badge(formatTime(clip.outputUs))
            if (clip.speed != ClipSpeed.NORMAL) Badge(clip.speed.label)
            if (clip.log) Badge("LOG")
            if (clip.muted) Badge("muto")
        }
    }
}

@Composable
private fun Badge(text: String) {
    Text(
        text,
        color = Color.White,
        fontSize = 9.sp,
        modifier = Modifier
            .padding(end = 3.dp)
            .background(Color(0xAA000000), RoundedCornerShape(3.dp))
            .padding(horizontal = 3.dp, vertical = 1.dp),
    )
}

@Composable
private fun Filmstrip(clip: Clip, width: Dp) {
    val tiles = ceil(width / TRACK_H).toInt().coerceIn(1, 60)
    val curve = remember(clip) { clip.speedCurve() }
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
    val hPx = with(LocalDensity.current) { TRACK_H.roundToPx() }
    val bmp by produceState<Bitmap?>(Thumbnails.cached(uri, timeUs, hPx), uri, timeUs) {
        if (value == null) value = Thumbnails.get(context, uri, timeUs, hPx)
    }
    Box(Modifier.size(TRACK_H)) {
        bmp?.let {
            Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.size(TRACK_H))
        }
    }
}
