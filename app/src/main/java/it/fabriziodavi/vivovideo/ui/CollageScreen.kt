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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import it.fabriziodavi.vivovideo.engine.CollageRenderer
import it.fabriziodavi.vivovideo.engine.DesignFonts
import it.fabriziodavi.vivovideo.media.PhotoPlace
import it.fabriziodavi.vivovideo.model.DOrient
import it.fabriziodavi.vivovideo.model.DText
import it.fabriziodavi.vivovideo.model.Design
import it.fabriziodavi.vivovideo.model.DesignMeta
import it.fabriziodavi.vivovideo.model.DesignTexts
import it.fabriziodavi.vivovideo.model.PrintFormat
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

/** Le famiglie in ordine: prima quelle grafiche (nuove), poi quelle a celle. */
private fun familyOrder(t: CollageTemplate): Int = t.design?.family?.ordinal ?: (100 + t.family.ordinal)

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
    val face = remember { DesignFonts.init(ctx); TextRenderer.typeface(ctx, TextFont.ELEGANTE) }
    var printing by remember { mutableStateOf(false) }
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
            CollagePreview(spec, template, thumbs, face, selected, playing, frames(), vm.collageMaskTick,
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
            Segmented(CollageTab.entries, tab, { if (it == CollageTab.STYLE && template.design != null) "Scritte" else it.label }, enabled = busy == null) { tab = it }
        }
        Box(Modifier.fillMaxWidth().height(250.dp).padding(horizontal = 12.dp)) {
            when (tab) {
                CollageTab.MODELS -> ModelsPanel(spec, template, thumbs, face, busy == null) { vm.chooseCollageTemplate(it); selected = -1 }
                CollageTab.STYLE -> if (template.design != null) DesignPanel(vm, spec, template.design) else StylePanel(vm, spec)
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
                SecondaryButton("Immagine", Modifier.weight(0.8f)) { vm.saveCollageImage() }
                SecondaryButton("Stampa", Modifier.weight(0.7f)) { playing = false; printing = true }
                PrimaryButton(when (vm.collageTarget) {
                    is EditorViewModel.CollageTarget.Edit -> "Aggiorna nel video"
                    EditorViewModel.CollageTarget.Add -> "Aggiungi al video"
                    EditorViewModel.CollageTarget.NewProject -> "Crea il video"
                }, Modifier.weight(1.2f)) { playing = false; vm.finishCollageVideo() }
            }
        }
    }
    if (printing) PrintDialog(spec, onDismiss = { printing = false }) { fmt, shop -> printing = false; vm.printCollage(fmt, shop) }
    // Esito del salvataggio: dove è finito il file, come stamparlo, e i pulsanti per mandarlo o vederlo.
    vm.collageResult?.let { res ->
        AlertDialog(
            onDismissRequest = { vm.collageResult = null },
            containerColor = VV.Raised,
            title = { Text(res.title, style = MaterialTheme.typography.titleSmall) },
            text = { Text(res.text, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "image/jpeg"; putExtra(android.content.Intent.EXTRA_STREAM, res.uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { ctx.startActivity(android.content.Intent.createChooser(send, "Condividi il collage")) }
                    }) { Text("Condividi", color = VV.Tally) }
                    TextButton(onClick = {
                        val view = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setDataAndType(res.uri, "image/jpeg"); addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { ctx.startActivity(view) }
                    }) { Text("Apri", color = VV.Tally) }
                }
            },
            dismissButton = { TextButton(onClick = { vm.collageResult = null }) { Text("OK", color = VV.Dim) } },
        )
    }
    // Avvisi ed errori (prima finivano solo nell'editor e qui non si vedevano).
    if (vm.collageResult == null) vm.message?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.message = null },
            containerColor = VV.Raised,
            text = { Text(msg, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { vm.message = null }) { Text("OK", color = VV.Tally) } },
        )
    }
}

/** Formato di stampa e dove si stampa. */
@Composable
private fun PrintDialog(spec: CollageSpec, onDismiss: () -> Unit, onPrint: (PrintFormat, Boolean) -> Unit) {
    val formats = remember(spec.aspect) { PrintFormat.forAspect(spec.aspect) }
    var fmt by remember { mutableStateOf(formats.firstOrNull { it == PrintFormat.A4 || it == PrintFormat.S20 || it == PrintFormat.L15x10 } ?: formats.first()) }
    var shop by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VV.Raised,
        title = { Text("Pronto da stampare", style = MaterialTheme.typography.titleSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PanelLabel("Formato")
                Row(Modifier.horizontalScroll(rememberScrollState())) { PillRow(formats, fmt, { it.label }) { fmt = it } }
                PanelLabel("Dove lo stampi")
                Segmented(listOf(true, false), shop, { if (it) "Negozio o sito" else "Stampante di casa" }) { shop = it }
                val (w, h, dpi) = fmt.pixels(shop)
                Hint(if (shop) "$w×$h pixel a $dpi dpi, con 3 mm di margine di taglio per lato: al negozio scegli «al vivo» o «senza bordo»."
                    else "$w×$h pixel a $dpi dpi, con un bordo bianco di 4 mm: la stampante di casa non arriva al bordo del foglio.")
                if (spec.aspect.orient == DOrient.PORTRAIT && spec.aspect != CollageAspect.PHOTO && spec.aspect != CollageAspect.PAPER)
                    Hint("Il modello si adatta al foglio: per vedere prima com'è, scegli il formato 10×15 o A4 in «Foto e formato».")
            }
        },
        confirmButton = { TextButton(onClick = { onPrint(fmt, shop) }) { Text("Salva per la stampa", color = VV.Tally) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla", color = VV.Dim) } },
    )
}

/** Scritte (tutte modificabili, con data e luogo già presi dalle foto), colori e persona davanti al titolo. */
@Composable
private fun DesignPanel(vm: EditorViewModel, spec: CollageSpec, d: Design) {
    val ctx = LocalContext.current
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = VV.Tally, unfocusedBorderColor = VV.Line, cursorColor = VV.Tally, focusedTextColor = VV.Text, unfocusedTextColor = VV.Text,
    )
    val meta = remember(spec.items) { DesignMeta.of(spec.items) }
    var locTick by remember { mutableIntStateOf(0) }
    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> locTick++; if (ok) vm.refreshCollagePlaces() }
    val wantsPlace = remember(d) { d.els.any { e -> (e is DText && e.text.contains("{luogo")) } }
    val locMissing = remember(locTick) { !PhotoPlace.locationAllowed(ctx) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (d.hint.isNotEmpty()) Hint(d.hint)
        if (wantsPlace && locMissing) {
            SecondaryButton("Usa il luogo delle foto (GPS)", Modifier.fillMaxWidth()) { locLauncher.launch(android.Manifest.permission.ACCESS_MEDIA_LOCATION) }
        }
        PanelLabel("Colore")
        ColorDots(d.accents, spec.accent ?: d.accents.first()) { vm.setCollageAccent(it) }
        if (d.accents2.isNotEmpty()) {
            PanelLabel("Secondo colore")
            ColorDots(d.accents2, spec.accent2 ?: d.accents2.first()) { vm.setCollageAccent(it, second = true) }
        }
        if (d.hasBehind) ToggleRow("La persona davanti al titolo", spec.behind) { vm.setCollageBehind(it) }
        ToggleRow("Grana della pellicola", spec.style.grain || d.grain, enabled = !d.grain) { on -> vm.updateCollage { it.copy(style = it.style.copy(grain = on)) } }
        PanelLabel("Scritte · vuota = torna quella proposta")
        d.words.forEach { w ->
            var text by remember(spec.templateId, w.id) { mutableStateOf(spec.texts[w.id] ?: DesignTexts.word(w, spec, meta)) }
            OutlinedTextField(value = text, onValueChange = { v -> text = v.take(9); vm.setCollageText(w.id, text) }, singleLine = true, colors = colors,
                modifier = Modifier.fillMaxWidth(), label = { Text("${w.label} (una foto per lettera)", color = VV.Dim) })
        }
        d.extraTexts.forEach { (id, label, def) ->
            val proposed = DesignTexts.fill(def, meta)
            var text by remember(spec.templateId, id) { mutableStateOf(spec.texts[id] ?: proposed) }
            OutlinedTextField(
                value = text, onValueChange = { v -> text = v.take(60); vm.setCollageText(id, if (text == proposed) "" else text) },
                singleLine = false, colors = colors, modifier = Modifier.fillMaxWidth(), label = { Text(label, color = VV.Dim) },
            )
        }
        d.texts.forEach { t ->
            val proposed = DesignTexts.fill(t.text, meta)
            var text by remember(spec.templateId, t.id) { mutableStateOf(spec.texts[t.id] ?: proposed) }
            OutlinedTextField(
                value = text, onValueChange = { v -> text = v.take(120); vm.setCollageText(t.id, if (text == proposed) "" else text) },
                singleLine = !t.text.contains('\n') && !t.wrap, colors = colors, modifier = Modifier.fillMaxWidth(),
                label = { Text(t.label, color = VV.Dim) },
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ColorDots(options: List<Long>, current: Long, onPick: (Long) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { c ->
            val on = c == current
            Box(Modifier.size(36.dp).clip(CircleShape).background(Color(c.toInt())).border(if (on) 3.dp else 1.dp, if (on) VV.Tally else VV.Line, CircleShape)
                .clickable { onPick(c) }.semantics { contentDescription = "Colore" })
        }
    }
}

/** L'anteprima disegnata con lo stesso codice dell'immagine e del video (i video qui sono fermi sul primo fotogramma). */
@Composable
private fun CollagePreview(
    spec: CollageSpec, template: CollageTemplate, thumbs: Map<String, Bitmap>, face: android.graphics.Typeface?, selected: Int, playing: Boolean,
    frames: CollageRenderer.Frames, maskTick: Int, onTap: (Int) -> Unit, onDrag: (Float, Float) -> Unit,
) {
    var box by remember { mutableStateOf(IntSize.Zero) }
    val ratio = spec.aspect.ratio
    val curSpec by rememberUpdatedState(spec)
    val curTemplate by rememberUpdatedState(template)
    val curOnTap by rememberUpdatedState(onTap)
    val curOnDrag by rememberUpdatedState(onDrag)
    // Immagine ferma, rifatta a ogni modifica.
    val still by produceState<Bitmap?>(null, spec, template, thumbs, selected, box, maskTick) {
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
    val all = remember(spec.items.size, spec.aspect) { CollageTemplates.forCount(spec.items.size, spec.aspect).sortedBy { familyOrder(it) } }
    val families = remember(all) { all.map { it.familyLabel }.distinct() }
    var fam by remember(families) { mutableStateOf(if (current.familyLabel in families) current.familyLabel else families.first()) }
    val shown = all.filter { it.familyLabel == fam }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PillRow(families, fam, { f -> "$f ${all.count { it.familyLabel == f }}" }, enabled) { fam = it }
        Hint(shown.firstOrNull()?.design?.family?.hint ?: shown.firstOrNull()?.family?.hint ?: "")
        LazyVerticalGrid(GridCells.Adaptive(76.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { t ->
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
            val s = spec.copy(templateId = t.id, style = if (spec.style.bg == CollageBg.BLUR && t.design == null) t.style.copy(bg = CollageBg.BLUR) else t.style,
                motion = t.motion, accent = null, accent2 = null)
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
