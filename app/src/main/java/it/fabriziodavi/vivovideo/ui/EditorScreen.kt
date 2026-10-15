@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.fabriziodavi.vivovideo.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.ClipSpeed

private val ZOOMS = listOf(8f, 15f, 30f, 60f, 120f)

@Composable
fun EditorScreen(vm: EditorViewModel) {
    val project = vm.project ?: return
    val context = LocalContext.current
    val running = vm.exportState is ExportState.Running
    var tab by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableIntStateOf(2) }
    var renaming by remember { mutableStateOf(false) }

    BackHandler { if (running) vm.cancelExport() else vm.closeProject() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        vm.addClips(uris)
    }
    val lutPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importLut(uri)
    }

    Column(
        Modifier.fillMaxSize().background(Bg).safeDrawingPadding()
    ) {
        // ---- Barra superiore ----
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.closeProject() }, enabled = !running) { Text("‹ Progetti", color = TextMain) }
            Text(
                project.name,
                color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            TextButton(onClick = { renaming = true }, enabled = !running) { Text("Rinomina", color = TextDim, fontSize = 12.sp) }
            TextButton(onClick = vm::undo, enabled = vm.canUndo && !running) { Text("↶", fontSize = 20.sp, color = if (vm.canUndo) TextMain else TextDim) }
            TextButton(onClick = vm::redo, enabled = vm.canRedo && !running) { Text("↷", fontSize = 20.sp, color = if (vm.canRedo) TextMain else TextDim) }
        }

        // ---- Anteprima ----
        val previewH = (LocalConfiguration.current.screenHeightDp * 0.34f).dp
        Box(
            Modifier.fillMaxWidth().height(previewH).padding(horizontal = 12.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val player = vm.player
            if (player != null) {
                AndroidView(
                    factory = { ctx -> PlayerView(ctx).apply { useController = false } },
                    update = { it.player = player },
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (project.clips.isEmpty()) {
                Text("Aggiungi dei video per iniziare", color = TextDim)
            } else if (running) {
                Text("Anteprima in pausa durante l'esportazione", color = TextDim, fontSize = 13.sp)
            }
            vm.previewError?.let {
                Text(it, color = Accent, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
            }
        }

        // ---- Riproduzione e tempo ----
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${formatTime(vm.playheadUs)} / ${formatTime(project.durationUs)}", color = TextMain, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = vm::togglePlay, enabled = vm.player != null) {
                Text(if (vm.isPlaying) "❚❚" else "▶", fontSize = 20.sp, color = Color.White)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { if (zoom > 0) zoom-- }) { Text("−", fontSize = 20.sp, color = TextMain) }
            TextButton(onClick = { if (zoom < ZOOMS.lastIndex) zoom++ }) { Text("+", fontSize = 20.sp, color = TextMain) }
        }

        // ---- Timeline ----
        Timeline(
            project = project,
            playheadUs = vm.playheadUs,
            isPlaying = vm.isPlaying,
            selectedClipId = vm.selectedClipId,
            dpPerSecond = ZOOMS[zoom],
            onSeek = vm::seekTo,
            onScrubStart = vm::pause,
            onSelect = { vm.selectedClipId = it },
        )

        // ---- Strumenti ----
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val sel = vm.selectedClip != null && !running
            Tool("+ Aggiungi", !running) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
            Tool("✂ Dividi", project.clips.isNotEmpty() && !running) { vm.splitAtPlayhead() }
            Tool("◀ Sposta", sel) { vm.moveSelected(-1) }
            Tool("Sposta ▶", sel) { vm.moveSelected(+1) }
            Tool("⧉ Duplica", sel) { vm.duplicateSelected() }
            Tool("🗑 Elimina", sel) { vm.deleteSelected() }
        }

        // ---- Pannelli ----
        TabRow(
            selectedTabIndex = tab,
            containerColor = Bg,
            contentColor = Color.White,
            indicator = { pos -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[tab]), color = Accent) },
        ) {
            listOf("Clip", "Colore", "Esporta").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
            }
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            vm.busy?.let { Text(it, color = TextDim) }
            when (tab) {
                0 -> ClipPanel(vm, running)
                1 -> ColorPanel(vm, running, onPickLut = { lutPicker.launch(arrayOf("*/*")) })
                2 -> ExportPanel(vm) { uri ->
                    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching { context.startActivity(intent) }
                }
            }
        }
    }

    vm.message?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.message = null },
            confirmButton = { TextButton(onClick = { vm.message = null }) { Text("OK") } },
            text = { Text(msg) },
        )
    }
    if (renaming) {
        var name by remember { mutableStateOf(project.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Nome del progetto") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.rename(name); renaming = false }) { Text("Salva") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun Tool(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(label, color = if (enabled) TextMain else TextDim.copy(alpha = 0.5f), fontSize = 13.sp)
    }
}

@Composable
private fun ClipPanel(vm: EditorViewModel, running: Boolean) {
    val clip = vm.selectedClip
    if (clip == null) {
        Hint("Tocca una clip nella timeline per modificarla.")
        return
    }
    val enabled = !running
    Section("Velocità") {
        ChipRow(ClipSpeed.entries, clip.speed, { it.label }, enabled) { s -> vm.updateSelected { it.copy(speed = s) } }
        if (clip.speed == ClipSpeed.RAMP) Hint("Veloce (2×) → lento (0,35×) al centro → veloce. Con video a 100/120 fps il lento è fluidissimo.")
    }
    Section("Taglio") { TrimControl(clip, enabled) { s, e -> vm.updateSelected { it.copy(trimStartUs = s, trimEndUs = e) } } }
    Section("Clip") {
        ToggleRow("Video LOG (converti in colori normali)", clip.log, enabled) { v -> vm.updateSelected { it.copy(log = v) } }
        ToggleRow("Senza audio", clip.muted, enabled) { v -> vm.updateSelected { it.copy(muted = v) } }
        Hint("${clip.width}×${clip.height} · ${"%.0f".format(clip.fps)} fps · originale ${formatTime(clip.sourceDurationUs)}")
    }
}

@Composable
private fun TrimControl(clip: Clip, enabled: Boolean, onChange: (Long, Long) -> Unit) {
    val total = clip.sourceDurationUs.toFloat()
    var range by remember(clip.id, clip.trimStartUs, clip.trimEndUs) {
        mutableStateOf(clip.trimStartUs / total..clip.trimEndUs / total)
    }
    Row {
        Text("Inizio ${formatTime((range.start * total).toLong())}", color = TextMain, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text("Fine ${formatTime((range.endInclusive * total).toLong())}", color = TextMain, fontSize = 13.sp)
    }
    RangeSlider(
        value = range,
        onValueChange = { r ->
            // Almeno 0,2 s di clip.
            val minFrac = 200_000f / total
            range = if (r.endInclusive - r.start >= minFrac) r else range
        },
        onValueChangeFinished = { onChange((range.start * total).toLong(), (range.endInclusive * total).toLong()) },
        enabled = enabled,
        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
    )
    Hint("Durata nel video: ${formatTime(clip.copy(trimStartUs = (range.start * total).toLong(), trimEndUs = (range.endInclusive * total).toLong()).outputUs)}")
}

@Composable
private fun ColorPanel(vm: EditorViewModel, running: Boolean, onPickLut: () -> Unit) {
    val c = vm.project?.color ?: return
    val enabled = !running
    Hint("Il colore vale per tutto il progetto. La conversione LOG si attiva per singola clip nel pannello Clip.")
    Section("Filtro") {
        val look = runCatching { Look.valueOf(c.look) }.getOrDefault(Look.NONE)
        ChipRow(Look.entries, look, { it.label }, enabled) { l -> vm.updateColor { it.copy(look = l.name) } }
    }
    Section("LUT") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onPickLut, enabled = enabled) { Text(if (c.lutPath == null) "Carica LUT .cube" else "Cambia LUT") }
            c.lutName?.let { name ->
                Text(name, color = TextMain, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                OutlinedButton(onClick = { vm.updateColor { it.copy(lutPath = null, lutName = null) } }, enabled = enabled) { Text("Togli") }
            }
        }
    }
    LabeledSlider("Alte luci / cielo", "Scurisce le zone chiare e ne recupera il colore.", c.highlights, enabled) { v -> vm.updateColor { it.copy(highlights = v) } }
    LabeledSlider("Riduzione rumore", "Leviga cielo e zone uniformi mantenendo i bordi. Utile sui LOG.", c.denoise, enabled) { v -> vm.updateColor { it.copy(denoise = v) } }
}

@Composable
private fun ExportPanel(vm: EditorViewModel, onOpen: (android.net.Uri) -> Unit) {
    val o = vm.exportOptions
    val state = vm.exportState
    val running = state is ExportState.Running
    Section("Risoluzione") { ChipRow(OutputSize.entries, o.size, { it.label }, !running) { vm.exportOptions = o.copy(size = it) } }
    Section("Fotogrammi al secondo") { ChipRow(OutputFps.entries, o.fps, { it.label }, !running) { vm.exportOptions = o.copy(fps = it) } }
    Section("Codec") { ChipRow(OutputCodec.entries, o.codec, { it.label }, !running) { vm.exportOptions = o.copy(codec = it) } }
    ToggleRow("Audio a tono naturale nei cambi di velocità", o.keepPitch, !running) { vm.exportOptions = o.copy(keepPitch = it) }
    when (state) {
        is ExportState.Running -> {
            Text(state.label + (state.percent?.let { " $it%" } ?: ""), color = Color.White)
            LinearProgressIndicator(progress = { (state.percent ?: 0) / 100f }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = vm::cancelExport, modifier = Modifier.fillMaxWidth()) { Text("Annulla") }
        }
        is ExportState.Done -> ResultCard("Fatto! Salvato in Film/VivoVideo", state.details) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onOpen(state.uri) }) { Text("Apri il video") }
                OutlinedButton(onClick = vm::dismissExportResult) { Text("Chiudi") }
            }
        }
        is ExportState.Failed -> ResultCard("Qualcosa non ha funzionato", state.message) {
            Hint("Fai uno screenshot di questo messaggio e mandalo a Claude.")
            OutlinedButton(onClick = vm::dismissExportResult) { Text("Chiudi") }
        }
        ExportState.Idle -> Button(
            onClick = vm::startExport,
            enabled = vm.project?.clips?.isNotEmpty() == true,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
        ) { Text("Esporta", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
fun ResultCard(title: String, body: String, actions: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(body, color = Color(0xFFB8BCC8), fontSize = 13.sp)
            }
            actions()
        }
    }
}
