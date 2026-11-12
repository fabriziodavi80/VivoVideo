package it.fabriziodavi.vivovideo.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import it.fabriziodavi.vivovideo.engine.CollageRenderer
import it.fabriziodavi.vivovideo.engine.TextRenderer
import it.fabriziodavi.vivovideo.model.CollageAspect
import it.fabriziodavi.vivovideo.model.CollageBg
import it.fabriziodavi.vivovideo.model.CollageFamily
import it.fabriziodavi.vivovideo.model.CollageMotion
import it.fabriziodavi.vivovideo.model.CollagePlan
import it.fabriziodavi.vivovideo.model.CollageSpec
import it.fabriziodavi.vivovideo.model.CollageTemplate
import it.fabriziodavi.vivovideo.model.CollageTemplates
import it.fabriziodavi.vivovideo.model.TextFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private enum class CollageTab(val label: String) { MODELS("Modelli"), STYLE("Stile"), MOTION("Movimento"), PHOTOS("Foto e formato") }

/**
 * Collage di foto e video: si sceglie il modello (centinaia, divisi per famiglia, già con le tue foto dentro), lo stile,
 * il movimento e il formato; si tocca una foto per scambiarla con un'altra o spostarla nella cella.
 * Poi: immagine nella galleria, oppure pezzo di video nel montaggio.
 */
@Composable
fun CollageScreen(vm: EditorViewModel) {
    val spec = vm.collage ?: return
    val ctx = LocalContext.current
    val busy = vm.busy
    BackHandler(enabled = busy == null) { vm.closeCollage() }
    val template = vm.collageTemplate() ?: CollageTemplates.forCount(spec.items.size, spec.aspect).first()
    val thumbs = vm.collageThumbs
    val face = remember { TextRenderer.typeface(ctx, TextFont.ELEGANTE) }
    var tab by remember { mutableStateOf(CollageTab.MODELS) }
    var selected by remember { mutableIntStateOf(-1) }
    var playing by remember { mutableStateOf(false) }
    val adder = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(CollageSpec.MAX_ITEMS)) { uris -> vm.addCollageMedia(uris) }
    fun frames(): CollageRenderer.Frames = CollageRenderer.Frames { i, _ -> spec.items.getOrNull(i)?.let { thumbs[it.uri] } }

    Column(Modifier.fillMaxSize().background(VV.Canvas).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(VVIcons.Back, "Indietro", enabled = busy == null) { vm.closeCollage() }
            Text("Collage", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(if (playing) "Ferma" else "▶ Prova il movimento", style = MaterialTheme.typography.labelLarge, color = VV.Tally,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = busy == null) { playing = !playing; selected = -1 }.padding(10.dp))
        }

        // Anteprima: grande quanto lo spazio lo permette, con il formato giusto.
        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            CollagePreview(spec, template, thumbs, face, selected, playing, frames(),
                onTap = { cell ->
                    when {
                        cell < 0 -> selected = -1
                        selected < 0 || selected == cell -> selected = if (selected == cell) -1 else cell
                        else -> { vm.swapCollageCells(selected, cell); selected = -1 }
                    }
                },
                onDrag = { dx, dy -> if (selected >= 0) vm.frameCollageCell(selected, dx, dy) },
            )
        }
        if (selected >= 0 && !playing) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Hint("Tocca un'altra foto per scambiarle. Trascina sulla foto per spostarla nella cella.")
                val item = spec.items.getOrNull(CollagePlan.itemFor(selected, spec.items.size))
                LabeledSlider("Ingrandisci", null, (((item?.zoom ?: 1f) - 1f) * 100).toInt(), range = 0..150, format = { "${100 + it}%" }) { v ->
                    vm.frameCollageCell(selected, zoom = 1f + v / 100f)
                }
            }
        }

        // Schede.
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Segmented(CollageTab.entries, tab, { it.label }, enabled = busy == null) { tab = it }
        }
        Box(Modifier.fillMaxWidth().height(250.dp).padding(horizontal = 12.dp)) {
            when (tab) {
                CollageTab.MODELS -> ModelsPanel(spec, template, thumbs, face, busy == null) { vm.chooseCollageTemplate(it); selected = -1 }
                CollageTab.STYLE -> StylePanel(vm, spec)
                CollageTab.MOTION -> MotionPanel(vm, spec)
                CollageTab.PHOTOS -> PhotosPanel(vm, spec, thumbs, onAdd = { adder.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) })
            }
        }

        // In fondo: avanzamento o le due uscite.
        if (busy != null) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(busy, style = MaterialTheme.typography.bodyMedium)
                val p = vm.collageProgress
                if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth(), color = VV.Tally, trackColor = VV.Raised)
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = VV.Tally, trackColor = VV.Raised)
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Salva immagine", Modifier.weight(1f)) { vm.saveCollageImage() }
                PrimaryButton(when (vm.collageTarget) {
                    is EditorViewModel.CollageTarget.Edit -> "Aggiorna nel video"
                    EditorViewModel.CollageTarget.Add -> "Aggiungi al video"
                    EditorViewModel.CollageTarget.NewProject -> "Crea il video"
                }, Modifier.weight(1f)) { playing = false; vm.finishCollageVideo() }
            }
        }
    }
}

/** L'anteprima disegnata con lo stesso codice dell'immagine e del video (i video qui sono fermi sul primo fotogramma). */
@Composable
private fun CollagePreview(
    spec: CollageSpec, template: CollageTemplate, thumbs: Map<String, Bitmap>, face: android.graphics.Typeface?, selected: Int, playing: Boolean,
    frames: CollageRenderer.Frames, onTap: (Int) -> Unit, onDrag: (Float, Float) -> Unit,
) {
    var box by remember { mutableStateOf(IntSize.Zero) }
    val ratio = spec.aspect.ratio
    val curSpec by rememberUpdatedState(spec)
    val curTemplate by rememberUpdatedState(template)
    val curOnTap by rememberUpdatedState(onTap)
    val curOnDrag by rememberUpdatedState(onDrag)
    // Immagine ferma, rifatta a ogni modifica.
    val still by produceState<Bitmap?>(null, spec, template, thumbs, selected, box) {
        if (box.width <= 0) return@produceState
        value = withContext(Dispatchers.Default) {
            val (w, h) = previewSize(spec.aspect, box)
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
                CollageRenderer.draw(Canvas(b), w, h, template, spec, spec.durationUs, true, frames, face, selected)
            }
        }
    }
    // Movimento: fotogrammi disegnati in due immagini che si alternano (circa 20 al secondo), a ripetizione.
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(playing, spec, template, box) {
        if (!playing || box.width <= 0) { frame = null; return@LaunchedEffect }
        val (w0, h0) = previewSize(spec.aspect, box)
        val k = minOf(1f, 540f / minOf(w0, h0))
        val w = (w0 * k).toInt().coerceAtLeast(2); val h = (h0 * k).toInt().coerceAtLeast(2)
        val bufs = listOf(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888), Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888))
        var n = 0
        val start = System.nanoTime()
        while (isActive) {
            val t = ((System.nanoTime() - start) / 1000) % (spec.durationUs + 600_000)
            val b = bufs[n % 2]
            withContext(Dispatchers.Default) {
                b.eraseColor(0)
                CollageRenderer.draw(Canvas(b), w, h, template, spec, t.coerceAtMost(spec.durationUs), false, frames, face)
            }
            frame = b
            n++
            delay(30)
        }
    }
    val shown = if (playing) frame ?: still else still
    Box(
        Modifier.fillMaxSize().onSizeChanged { box = it },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = ratio < 1f).clip(RoundedCornerShape(6.dp)).background(VV.Raised)
                .semantics { contentDescription = "Anteprima del collage" }
                .pointerInput(Unit) {
                    detectTapGestures { o ->
                        val s = curSpec; val t = curTemplate
                        curOnTap(CollageRenderer.cellAt(t, s, size.width, size.height, o.x, o.y))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, d ->
                        change.consume()
                        // Trascinare a destra porta la foto a destra: l'inquadratura va a sinistra.
                        curOnDrag(-d.x / size.width * 3f, -d.y / size.height * 3f)
                    }
                },
        ) {
            shown?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize()) }
        }
    }
}

private fun previewSize(a: CollageAspect, box: IntSize): Pair<Int, Int> {
    val r = a.ratio
    val w = minOf(box.width.toFloat(), box.height * r)
    val h = w / r
    // Anteprima leggera: al massimo 900 pixel sul lato lungo.
    val k = minOf(1f, 900f / maxOf(w, h))
    return (w * k).toInt().coerceAtLeast(2) to (h * k).toInt().coerceAtLeast(2)
}

@Composable
private fun ModelsPanel(
    spec: CollageSpec, current: CollageTemplate, thumbs: Map<String, Bitmap>, face: android.graphics.Typeface?, enabled: Boolean,
    onPick: (CollageTemplate) -> Unit,
) {
    val all = remember(spec.items.size, spec.aspect) { CollageTemplates.forCount(spec.items.size, spec.aspect) }
    val families = remember(all) { all.map { it.family }.distinct() }
    var fam by remember(families) { mutableStateOf(if (current.family in families) current.family else families.first()) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PillRow(families, fam, { "${it.label} ${all.count { t -> t.family == it }}" }, enabled) { fam = it }
        Hint(fam.hint)
        LazyVerticalGrid(GridCells.Adaptive(76.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(all.filter { it.family == fam }, key = { it.id }) { t ->
                TemplateThumb(t, spec, thumbs, face, t.id == current.id, enabled) { onPick(t) }
            }
        }
    }
}

/** Miniatura del modello con le tue foto già dentro. */
@Composable
private fun TemplateThumb(t: CollageTemplate, spec: CollageSpec, thumbs: Map<String, Bitmap>, face: android.graphics.Typeface?, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val img by produceState<Bitmap?>(null, t.id, spec.items, spec.aspect, thumbs) {
        value = withContext(Dispatchers.Default) {
            val r = spec.aspect.ratio
            val (w, h) = if (r < 1) (150 * r).toInt() to 150 else 150 to (150 / r).toInt()
            val s = spec.copy(templateId = t.id, style = if (spec.style.bg == CollageBg.BLUR) t.style.copy(bg = CollageBg.BLUR) else t.style, motion = t.motion)
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
                CollageRenderer.draw(Canvas(b), w, h, t, s, s.durationUs, true, { i, _ -> s.items.getOrNull(i)?.let { thumbs[it.uri] } }, face)
            }
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(enabled = enabled, onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(spec.aspect.ratio.coerceIn(0.5f, 1.4f)).clip(RoundedCornerShape(6.dp)).background(VV.Raised)
                .border(if (on) 2.dp else 0.dp, if (on) VV.Tally else Color.Transparent, RoundedCornerShape(6.dp)),
        ) { img?.let { Image(it.asImageBitmap(), contentDescription = t.name, modifier = Modifier.fillMaxSize()) } }
        Text(t.name, style = MaterialTheme.typography.labelSmall, color = if (on) VV.Tally else VV.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StylePanel(vm: EditorViewModel, spec: CollageSpec) {
    val st = spec.style
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = VV.Tally, unfocusedBorderColor = VV.Line, cursorColor = VV.Tally, focusedTextColor = VV.Text, unfocusedTextColor = VV.Text,
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PanelLabel("Sfondo")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CollageBg.entries.forEach { bg ->
                val on = bg == st.bg
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(58.dp).clickable { vm.updateCollage { it.copy(style = it.style.copy(bg = bg)) } }) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape)
                            .background(if (bg.blur) Color(0xFF555B66) else Color(bg.color.toInt()))
                            .border(if (on) 3.dp else 1.dp, if (on) VV.Tally else VV.Line, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        bg.color2?.let { c2 -> Box(Modifier.fillMaxWidth().fillMaxHeight(0.5f).align(Alignment.BottomCenter).background(Color(c2.toInt()))) }
                        if (bg.blur) Text("≈", color = Color.White)
                    }
                    Text(bg.label, style = MaterialTheme.typography.labelSmall, color = if (on) VV.Tally else VV.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        LabeledSlider("Spazio fra le foto", null, (st.gap * 1000).toInt(), range = 0..40, format = { if (it == 0) "nessuno" else "$it" }) { v ->
            vm.updateCollage { it.copy(style = it.style.copy(gap = v / 1000f)) }
        }
        LabeledSlider("Angoli arrotondati", null, (st.radius * 1000).toInt(), range = 0..60, format = { if (it == 0) "squadrati" else "$it" }) { v ->
            vm.updateCollage { it.copy(style = it.style.copy(radius = v / 1000f)) }
        }
        ToggleRow("Ombra sotto le foto", st.shadow) { on -> vm.updateCollage { it.copy(style = it.style.copy(shadow = on)) } }
        ToggleRow("Grana della pellicola", st.grain) { on -> vm.updateCollage { it.copy(style = it.style.copy(grain = on)) } }
        PanelLabel("Scritta (facoltativa)")
        var text by remember(spec.templateId) { mutableStateOf(spec.caption) }
        OutlinedTextField(
            value = text, onValueChange = { v -> text = v.take(60); vm.updateCollage { it.copy(caption = text) } }, singleLine = true, colors = colors,
            modifier = Modifier.fillMaxWidth(), placeholder = { Text("Es. Estate 2026", color = VV.Faint) },
        )
    }
}

@Composable
private fun MotionPanel(vm: EditorViewModel, spec: CollageSpec) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Hint("Il movimento si vede nel video; l'immagine salvata è il collage finito.")
        CollageMotion.entries.forEach { m ->
            val on = m == spec.motion
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (on) VV.Raised2 else VV.Raised)
                    .border(if (on) 2.dp else 0.dp, if (on) VV.Tally else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { vm.updateCollage { it.copy(motion = m) } }.padding(10.dp),
            ) {
                Text(m.label, style = MaterialTheme.typography.titleSmall, color = if (on) VV.Tally else VV.Text)
                Text(m.hint, style = MaterialTheme.typography.bodySmall)
            }
        }
        ToggleRow("Zoom lento dentro le foto", spec.kenBurns) { on -> vm.updateCollage { it.copy(kenBurns = on) } }
        PanelLabel("Durata nel video")
        PillRow(CollageSpec.DURATIONS, spec.durationUs, { "${it / 1_000_000} s" }) { d -> vm.updateCollage { it.copy(durationUs = d) } }
    }
}

@Composable
private fun PhotosPanel(vm: EditorViewModel, spec: CollageSpec, thumbs: Map<String, Bitmap>, onAdd: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PanelLabel("Formato")
        PillRow(CollageAspect.entries, spec.aspect, { it.label }) { vm.setCollageAspect(it) }
        PanelLabel("Foto e video (${spec.items.size} di ${CollageSpec.MAX_ITEMS}) · tocca × per togliere")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            spec.items.forEachIndexed { i, it ->
                Box(Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)).background(VV.Raised)) {
                    thumbs[it.uri]?.let { b -> Image(b.asImageBitmap(), contentDescription = "Foto ${i + 1}", modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                    if (it.video) Text("▶", color = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(4.dp))
                    if (spec.items.size > 1) Box(
                        Modifier.align(Alignment.TopEnd).padding(3.dp).size(22.dp).clip(CircleShape).background(Color(0xCC000000)).clickable { vm.removeCollageItem(i) },
                        contentAlignment = Alignment.Center,
                    ) { Text("×", color = Color.White) }
                }
            }
            if (spec.items.size < CollageSpec.MAX_ITEMS) Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)).background(VV.Raised).clickable(onClick = onAdd), contentAlignment = Alignment.Center,
            ) { Text("+", style = MaterialTheme.typography.titleLarge, color = VV.Tally) }
        }
        Hint("I video nelle celle si muovono davvero nel video finito; qui e nell'immagine si vede un fotogramma.")
        Spacer(Modifier.height(4.dp))
    }
}
