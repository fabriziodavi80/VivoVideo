package it.fabriziodavi.vivovideo.ui

import android.content.Intent
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import it.fabriziodavi.vivovideo.Look
import it.fabriziodavi.vivovideo.engine.ExportState
import it.fabriziodavi.vivovideo.engine.OutputCodec
import it.fabriziodavi.vivovideo.engine.OutputFps
import it.fabriziodavi.vivovideo.engine.OutputSize
import it.fabriziodavi.vivovideo.model.ClipSpeed
import kotlin.math.ln

private val ZOOMS = listOf(8f, 15f, 30f, 60f, 120f)

/** Strumenti della barra in basso. Quelli "futuri" hanno già il loro posto. */
enum class Tool(val label: String, val icon: () -> ImageVector, val future: Boolean = false) {
    ADD("Aggiungi", { VVIcons.Add }),
    SPLIT("Dividi", { VVIcons.Split }),
    SPEED("Velocità", { VVIcons.Speed }),
    AUDIO("Audio", { VVIcons.Volume }),
    COLOR("Colore", { VVIcons.Palette }),
    KEYFRAME("Keyframe", { VVIcons.Keyframe }, future = true),
    EFFECTS("Effetti", { VVIcons.Effects }, future = true),
    TRANSITION("Transizione", { VVIcons.Transition }, future = true),
    TEXT("Testo", { VVIcons.Text }, future = true),
    MUSIC("Musica", { VVIcons.Music }, future = true),
    DUPLICATE("Duplica", { VVIcons.Copy }),
    MOVE_LEFT("Indietro", { VVIcons.MoveLeft }),
    MOVE_RIGHT("Avanti", { VVIcons.MoveRight }),
    DELETE("Elimina", { VVIcons.Delete }),
    EXPORT("Esporta", { VVIcons.Export }),
}

private val CLIP_TOOLS = listOf(
    Tool.SPLIT, Tool.SPEED, Tool.AUDIO, Tool.COLOR, Tool.KEYFRAME, Tool.EFFECTS, Tool.TRANSITION,
    Tool.DUPLICATE, Tool.MOVE_LEFT, Tool.MOVE_RIGHT, Tool.DELETE,
)
private val PROJECT_TOOLS = listOf(Tool.ADD, Tool.SPLIT, Tool.COLOR, Tool.TEXT, Tool.MUSIC, Tool.EFFECTS)

@Composable
fun EditorScreen(vm: EditorViewModel) {
    val project = vm.project ?: return
    val context = LocalContext.current
    val running = vm.exportState is ExportState.Running
    var zoom by remember { mutableIntStateOf(2) }
    var renaming by remember { mutableStateOf(false) }
    var activeTool by remember { mutableStateOf<Tool?>(null) }
    val clip = vm.selectedClip

    // Un pannello di clip si chiude se non c'è più una clip selezionata.
    if (clip == null && (activeTool == Tool.SPEED || activeTool == Tool.AUDIO)) activeTool = null

    BackHandler {
        when {
            running -> vm.cancelExport()
            activeTool != null -> activeTool = null
            else -> vm.closeProject()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { vm.addClips(it) }
    val lutPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importLut(uri)
    }
    val pickVideos = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }

    fun onTool(t: Tool) {
        if (t.future) { vm.message = "\"${t.label}\" arriverà nelle prossime versioni: lo spazio è già pronto."; return }
        when (t) {
            Tool.ADD -> pickVideos()
            Tool.SPLIT -> vm.splitAtPlayhead()
            Tool.DUPLICATE -> vm.duplicateSelected()
            Tool.MOVE_LEFT -> vm.moveSelected(-1)
            Tool.MOVE_RIGHT -> vm.moveSelected(+1)
            Tool.DELETE -> vm.deleteSelected()
            else -> activeTool = if (activeTool == t) null else t
        }
    }

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val topBar: @Composable (Boolean) -> Unit = { showExport ->
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(start = 4.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBtn(VVIcons.Back, "Progetti", enabled = !running) { vm.closeProject() }
            Text(
                project.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clickable(enabled = !running) { renaming = true }.padding(horizontal = 4.dp),
            )
            IconBtn(VVIcons.Undo, "Annulla", enabled = vm.canUndo && !running, onClick = vm::undo)
            IconBtn(VVIcons.Redo, "Ripeti", enabled = vm.canRedo && !running, onClick = vm::redo)
            if (showExport) {
                Spacer(Modifier.width(6.dp))
                ExportPill(enabled = project.clips.isNotEmpty()) { activeTool = Tool.EXPORT }
            }
        }
    }

    val preview: @Composable (Modifier) -> Unit = { mod ->
        Box(mod.clip(RoundedCornerShape(8.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
            val player = vm.player
            when {
                player != null -> AndroidView(
                    factory = { ctx -> PlayerView(ctx).apply { useController = false; setShutterBackgroundColor(android.graphics.Color.BLACK) } },
                    update = { it.player = player },
                    modifier = Modifier.fillMaxSize(),
                )
                project.clips.isEmpty() -> EmptyState(onAdd = pickVideos)
                running -> Text("Anteprima in pausa durante l'esportazione", style = MaterialTheme.typography.bodySmall)
            }
            vm.previewError?.let {
                Text(it, color = VV.Danger, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
            }
        }
    }

    val transport: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 14.dp)) {
            Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.Bottom) {
                Text(formatTime(vm.playheadUs), style = MaterialTheme.typography.labelLarge)
                Text("  /  " + formatTime(project.durationUs), style = MaterialTheme.typography.labelMedium, color = VV.Dim)
            }
            Box(
                Modifier.align(Alignment.Center).size(42.dp).clip(CircleShape)
                    .background(if (vm.player != null) VV.Text else VV.Raised)
                    .clickable(enabled = vm.player != null, onClick = vm::togglePlay),
                contentAlignment = Alignment.Center,
            ) { Icon(if (vm.isPlaying) VVIcons.Pause else VVIcons.Play, "Riproduci", tint = VV.Canvas) }
            Row(Modifier.align(Alignment.CenterEnd)) {
                IconBtn(VVIcons.Remove, "Riduci zoom", tint = VV.Dim) { if (zoom > 0) zoom-- }
                IconBtn(VVIcons.Add, "Aumenta zoom", tint = VV.Dim) { if (zoom < ZOOMS.lastIndex) zoom++ }
            }
        }
    }

    val timeline: @Composable () -> Unit = {
        Timeline(
            project = project,
            playheadUs = vm.playheadUs,
            selectedClipId = vm.selectedClipId,
            dpPerSecond = ZOOMS[zoom],
            onSeek = vm::seekTo,
            onScrubStart = vm::pause,
            onSelect = { vm.selectedClipId = it },
            onTrim = { id, s, e -> vm.trimClip(id, s, e) },
            onAdd = pickVideos,
        )
    }

    val toolbar: @Composable () -> Unit = {
        val tools = if (clip != null) CLIP_TOOLS else PROJECT_TOOLS
        Row(
            Modifier.fillMaxWidth().background(VV.Canvas).horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            tools.forEach { t ->
                ToolButton(t, active = activeTool == t, enabled = !running) { onTool(t) }
            }
        }
    }

    val panel: @Composable (Modifier) -> Unit = { mod ->
        val t = activeTool
        Column(mod.background(VV.Panel)) {
            if (t == null) {
                Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                    Hint(if (clip == null) "Tocca una clip per modificarla, oppure scegli uno strumento qui sotto." else "Scegli uno strumento qui sotto.")
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (clip != null && t != Tool.EXPORT && t != Tool.COLOR) {
                        val i = project.clips.indexOf(clip) + 1
                        Text("Clip $i di ${project.clips.size}", style = MaterialTheme.typography.bodySmall)
                    }
                    IconBtn(VVIcons.Check, "Chiudi", enabled = !running) { activeTool = null }
                }
                Column(
                    Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (t) {
                        Tool.SPEED -> SpeedPanel(vm, running)
                        Tool.AUDIO -> AudioPanel(vm, running)
                        Tool.COLOR -> ColorPanel(vm, running) { lutPicker.launch(arrayOf("*/*")) }
                        Tool.EXPORT -> ExportPanel(vm) { uri ->
                            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            runCatching { context.startActivity(intent) }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(VV.Canvas).safeDrawingPadding()) {
        vm.busy?.let { LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = VV.Tally, trackColor = VV.Canvas) }
        if (landscape) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    topBar(false)
                    preview(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 10.dp))
                    transport()
                }
                Column(Modifier.width(340.dp).fillMaxHeight().background(VV.Panel)) {
                    Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                        ExportPill(enabled = project.clips.isNotEmpty()) { activeTool = Tool.EXPORT }
                    }
                    panel(Modifier.fillMaxWidth().weight(1f))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(VV.Line))
            timeline()
            toolbar()
        } else {
            topBar(true)
            preview(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 10.dp))
            transport()
            Box(Modifier.fillMaxWidth().height(1.dp).background(VV.Line))
            timeline()
            Box(Modifier.fillMaxWidth().height(1.dp).background(VV.Line))
            if (activeTool != null) panel(Modifier.fillMaxWidth().heightIn(max = 330.dp))
            toolbar()
        }
    }

    vm.message?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.message = null },
            containerColor = VV.Raised,
            confirmButton = { TextButton(onClick = { vm.message = null }) { Text("OK", color = VV.Tally) } },
            text = { Text(msg, style = MaterialTheme.typography.bodyMedium) },
        )
    }
    if (renaming) {
        var name by remember { mutableStateOf(project.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            containerColor = VV.Raised,
            title = { Text("Nome del progetto", style = MaterialTheme.typography.titleSmall) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.rename(name); renaming = false }) { Text("Salva", color = VV.Tally) } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Annulla", color = VV.Dim) } },
        )
    }
}

// ---------------------------------------------------------------- pezzi comuni

@Composable
fun IconBtn(icon: ImageVector, desc: String, enabled: Boolean = true, tint: Color = VV.Text, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = if (enabled) tint else VV.Faint, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun ExportPill(enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(999.dp)).background(if (enabled) VV.Tally else VV.Raised)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(VVIcons.Export, null, tint = if (enabled) VV.TallyInk else VV.Faint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Esporta", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = if (enabled) VV.TallyInk else VV.Faint)
    }
}

@Composable
private fun ToolButton(t: Tool, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val color = when {
        !enabled -> VV.Faint
        active -> VV.Tally
        t.future -> VV.Faint
        else -> VV.Text
    }
    Column(
        Modifier.width(64.dp).clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(t.icon(), t.label, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(5.dp))
        Text(
            t.label, fontSize = 11.sp, fontFamily = Plex, maxLines = 1,
            color = if (active) VV.Tally else if (t.future || !enabled) VV.Faint else VV.Dim,
        )
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).border(1.dp, VV.Line, CircleShape).clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) { Icon(VVIcons.Add, "Aggiungi video", tint = VV.Text) }
        Spacer(Modifier.height(10.dp))
        Text("Aggiungi i video da montare", style = MaterialTheme.typography.bodySmall)
    }
}

// ---------------------------------------------------------------- pannelli

@Composable
private fun SpeedPanel(vm: EditorViewModel, running: Boolean) {
    val clip = vm.selectedClip ?: return
    Segmented(listOf(ClipSpeed.SLOW, ClipSpeed.NORMAL, ClipSpeed.FAST, ClipSpeed.RAMP), clip.speed, { it.label }, !running) { s ->
        vm.updateSelected { it.copy(speed = s) }
    }
    // Curva della velocità lungo la clip (scala logaritmica: 1× al centro).
    val curve = remember(clip.speed, clip.trimStartUs, clip.trimEndUs) { clip.speedCurve() }
    Canvas(Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(8.dp)).background(VV.Raised)) {
        val pad = 8.dp.toPx()
        val h = size.height - 2 * pad
        fun y(speed: Float): Float {
            val v = (ln(speed) - ln(0.25f)) / (ln(4f) - ln(0.25f))
            return pad + h * (1 - v)
        }
        drawLine(VV.Line, Offset(0f, y(1f)), Offset(size.width, y(1f)), 1.dp.toPx())
        val path = Path()
        val n = 80
        for (i in 0..n) {
            val t = curve.durationUs * i / n
            val x = pad + (size.width - 2 * pad) * i / n
            val yy = y(curve.speedAt(t.coerceAtMost(curve.durationUs - 1)))
            if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
        }
        drawPath(path, VV.Tally, style = Stroke(2.dp.toPx()))
    }
    Hint(
        when (clip.speed) {
            ClipSpeed.RAMP -> "2× all'inizio, 0,35× al centro, 2× alla fine. Con video a 100/120 fps il lento resta fluido."
            ClipSpeed.SLOW -> "Metà velocità per tutta la clip."
            ClipSpeed.FAST -> "Doppia velocità per tutta la clip."
            ClipSpeed.NORMAL -> "Velocità originale."
        } + "  Durata: " + formatTime(clip.outputUs)
    )
}

@Composable
private fun AudioPanel(vm: EditorViewModel, running: Boolean) {
    val clip = vm.selectedClip ?: return
    ToggleRow("Disattiva l'audio della clip", clip.muted, !running) { v -> vm.updateSelected { it.copy(muted = v) } }
    LabeledSlider(
        "Volume", null, (clip.volume * 100).toInt(), enabled = !running && !clip.muted, range = 0..200,
        format = { "$it%" },
    ) { v -> vm.updateSelected { it.copy(volume = v / 100f) } }
    Hint("Nei cambi di velocità l'audio esportato mantiene il tono naturale (impostazione in Esporta).")
}

@Composable
private fun ColorPanel(vm: EditorViewModel, running: Boolean, onPickLut: () -> Unit) {
    val c = vm.project?.color ?: return
    val enabled = !running
    vm.selectedClip?.let { clip ->
        PanelLabel("Questa clip")
        ToggleRow("Girata in LOG: converti in colori normali", clip.log, enabled) { v -> vm.updateSelected { it.copy(log = v) } }
    }
    PanelLabel("Tutto il progetto")
    val look = runCatching { Look.valueOf(c.look) }.getOrDefault(Look.NONE)
    PillRow(Look.entries, look, { it.label }, enabled) { l -> vm.updateColor { it.copy(look = l.name) } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(c.lutName ?: "Nessuna LUT", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = onPickLut, enabled = enabled) { Text(if (c.lutPath == null) "Carica .cube" else "Cambia", color = VV.Tally) }
        if (c.lutPath != null) TextButton(onClick = { vm.updateColor { it.copy(lutPath = null, lutName = null) } }, enabled = enabled) { Text("Togli", color = VV.Dim) }
    }
    LabeledSlider("Alte luci / cielo", null, c.highlights, enabled) { v -> vm.updateColor { it.copy(highlights = v) } }
    LabeledSlider("Riduzione rumore", "Leviga cielo e zone uniformi mantenendo i bordi.", c.denoise, enabled) { v -> vm.updateColor { it.copy(denoise = v) } }
}

@Composable
private fun ExportPanel(vm: EditorViewModel, onOpen: (android.net.Uri) -> Unit) {
    val o = vm.exportOptions
    val state = vm.exportState
    val running = state is ExportState.Running
    PanelLabel("Risoluzione")
    Segmented(OutputSize.entries, o.size, { it.label }, !running) { vm.exportOptions = o.copy(size = it) }
    PanelLabel("Fotogrammi al secondo")
    Segmented(OutputFps.entries, o.fps, { it.label }, !running) { vm.exportOptions = o.copy(fps = it) }
    PanelLabel("Codec")
    Segmented(OutputCodec.entries, o.codec, { it.label }, !running) { vm.exportOptions = o.copy(codec = it) }
    ToggleRow("Audio a tono naturale nei cambi di velocità", o.keepPitch, !running) { vm.exportOptions = o.copy(keepPitch = it) }
    when (state) {
        is ExportState.Running -> {
            Text(state.label + (state.percent?.let { "  $it%" } ?: ""), style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(
                progress = { (state.percent ?: 0) / 100f },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = VV.Tally, trackColor = VV.Raised2,
            )
            SecondaryButton("Annulla", onClick = vm::cancelExport)
        }
        is ExportState.Done -> ResultBox("Salvato in Film/VivoVideo", state.details) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Apri il video") { onOpen(state.uri) }
                SecondaryButton("Chiudi", onClick = vm::dismissExportResult)
            }
        }
        is ExportState.Failed -> ResultBox("L'esportazione non è riuscita", state.message) {
            Hint("Fai uno screenshot di questo messaggio e mandalo a Claude.")
            SecondaryButton("Chiudi", onClick = vm::dismissExportResult)
        }
        ExportState.Idle -> PrimaryButton("Esporta il video", Modifier.fillMaxWidth(), enabled = vm.project?.clips?.isNotEmpty() == true, onClick = vm::startExport)
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(if (enabled) VV.Tally else VV.Raised)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = if (enabled) VV.TallyInk else VV.Faint) }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(VV.Raised).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun ResultBox(title: String, body: String, actions: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(VV.Raised).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        SelectionContainer { Text(body, style = MaterialTheme.typography.bodySmall) }
        actions()
    }
}
