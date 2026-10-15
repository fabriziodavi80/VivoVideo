package it.fabriziodavi.vivovideo.ui

import it.fabriziodavi.vivovideo.engine.TextRenderer

import androidx.compose.runtime.rememberUpdatedState

import androidx.compose.ui.platform.LocalDensity

import androidx.compose.foundation.layout.BoxWithConstraints

import androidx.compose.ui.graphics.PathEffect

import androidx.compose.ui.graphics.drawscope.rotate

import androidx.compose.material3.OutlinedTextFieldDefaults

import it.fabriziodavi.vivovideo.model.TextAnim

import it.fabriziodavi.vivovideo.model.TextLook

import it.fabriziodavi.vivovideo.model.TextFont

import it.fabriziodavi.vivovideo.model.TextItem

import it.fabriziodavi.vivovideo.model.ClipSpeed
import it.fabriziodavi.vivovideo.model.Easing
import kotlin.math.roundToInt
import it.fabriziodavi.vivovideo.model.MusicTrack
import it.fabriziodavi.vivovideo.model.SpeedInterp
import it.fabriziodavi.vivovideo.model.SpeedLimits
import it.fabriziodavi.vivovideo.model.SpeedPreset
import it.fabriziodavi.vivovideo.model.Transform
import it.fabriziodavi.vivovideo.model.TransitionSpec
import it.fabriziodavi.vivovideo.model.TransitionType
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import it.fabriziodavi.vivovideo.Look
import it.fabriziodavi.vivovideo.engine.ExportState
import it.fabriziodavi.vivovideo.engine.OutputCodec
import it.fabriziodavi.vivovideo.engine.OutputFps
import it.fabriziodavi.vivovideo.engine.OutputSize
import kotlin.math.ln

private val ZOOMS = listOf(8f, 15f, 30f, 60f, 120f)

/** Strumenti della barra in basso. Quelli "futuri" hanno già il loro posto. */
enum class Tool(val label: String, val icon: () -> ImageVector, val future: Boolean = false) {
    ADD("Aggiungi", { VVIcons.Add }),
    SPLIT("Dividi", { VVIcons.Split }),
    SPEED("Velocità", { VVIcons.Speed }),
    AUDIO("Audio", { VVIcons.Volume }),
    COLOR("Colore", { VVIcons.Palette }),
    KEYFRAME("Keyframe", { VVIcons.Keyframe }),
    EFFECTS("Effetti", { VVIcons.Effects }, future = true),
    TRANSITION("Transizione", { VVIcons.Transition }),
    DURATION("Durata", { VVIcons.Speed }),
    FRAME("Inquadra", { VVIcons.Fullscreen }),
    TEXT("Testo", { VVIcons.Text }),
    MUSIC("Musica", { VVIcons.Music }),
    DUPLICATE("Duplica", { VVIcons.Copy }),
    MOVE_LEFT("Indietro", { VVIcons.MoveLeft }),
    MOVE_RIGHT("Avanti", { VVIcons.MoveRight }),
    DELETE("Elimina", { VVIcons.Delete }),
    EXPORT("Esporta", { VVIcons.Export }),
}

private val CLIP_TOOLS = listOf(
    Tool.SPLIT, Tool.SPEED, Tool.FRAME, Tool.TRANSITION, Tool.KEYFRAME, Tool.AUDIO, Tool.COLOR,
    Tool.DUPLICATE, Tool.MOVE_LEFT, Tool.MOVE_RIGHT, Tool.DELETE, Tool.EFFECTS,
)
private val PHOTO_TOOLS = listOf(
    Tool.SPLIT, Tool.DURATION, Tool.FRAME, Tool.TRANSITION, Tool.KEYFRAME, Tool.COLOR,
    Tool.DUPLICATE, Tool.MOVE_LEFT, Tool.MOVE_RIGHT, Tool.DELETE, Tool.EFFECTS,
)
private val PROJECT_TOOLS = listOf(Tool.ADD, Tool.SPLIT, Tool.TRANSITION, Tool.COLOR, Tool.TEXT, Tool.MUSIC, Tool.EFFECTS)

@Composable
fun EditorScreen(vm: EditorViewModel, initialTool: Tool? = null) {
    val project = vm.project ?: return
    val context = LocalContext.current
    val running = vm.exportState is ExportState.Running
    var zoom by remember { mutableIntStateOf(2) }
    var renaming by remember { mutableStateOf(false) }
    var activeTool by remember { mutableStateOf(initialTool) }
    val clip = vm.selectedClip

    // Un pannello di clip si chiude se non c'è più una clip selezionata.
    if (clip == null && (activeTool == Tool.SPEED || activeTool == Tool.AUDIO || activeTool == Tool.KEYFRAME || activeTool == Tool.DURATION || activeTool == Tool.FRAME)) activeTool = null
    // Foto e video hanno strumenti diversi.
    if (clip != null && clip.isPhoto && (activeTool == Tool.SPEED || activeTool == Tool.AUDIO)) activeTool = Tool.DURATION
    if (clip != null && !clip.isPhoto && activeTool == Tool.DURATION) activeTool = Tool.SPEED

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
    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { vm.addMusic(uri); activeTool = Tool.MUSIC }
    }
    val pickMusic = { musicPicker.launch(arrayOf("audio/*")) }
    val pickVideos = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }

    fun onTool(t: Tool) {
        if (t.future) { vm.message = "\"${t.label}\" arriverà nelle prossime versioni: lo spazio è già pronto."; return }
        when (t) {
            Tool.ADD -> pickVideos()
            Tool.SPLIT -> if (vm.selectedMusicId != null) vm.splitMusicAtPlayhead() else vm.splitAtPlayhead()
            Tool.DUPLICATE -> vm.duplicateSelected()
            Tool.MOVE_LEFT -> vm.moveSelected(-1)
            Tool.MOVE_RIGHT -> vm.moveSelected(+1)
            Tool.DELETE -> vm.deleteSelected()
            Tool.TEXT -> {
                activeTool = Tool.TEXT
                if (project.texts.isEmpty()) vm.addText()
            }
            else -> activeTool = if (activeTool == t) null else t
        }
    }

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var live by remember { mutableStateOf(LiveTransform()) }
    var fullscreen by vm::fullscreen
    BackHandler(enabled = fullscreen) { fullscreen = false }

    val titleRow: @Composable (Boolean) -> Unit = { showExport ->
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(start = 2.dp, end = 8.dp),
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
                Spacer(Modifier.width(4.dp))
                ExportPill(enabled = project.clips.isNotEmpty()) { activeTool = Tool.EXPORT }
            }
        }
    }

    val playButton: @Composable (Dp) -> Unit = { size ->
        Box(
            Modifier.size(size).clip(CircleShape)
                .background(if (vm.player != null) VV.Text else VV.Raised)
                .clickable(enabled = vm.player != null, onClick = vm::togglePlay),
            contentAlignment = Alignment.Center,
        ) { Icon(if (vm.isPlaying) VVIcons.Pause else VVIcons.Play, "Riproduci", tint = VV.Canvas) }
    }
    val timecode: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatTime(vm.playheadUs), style = MaterialTheme.typography.labelLarge, color = Color.White)
            Text("  /  " + formatTime(project.durationUs), style = MaterialTheme.typography.labelMedium, color = VV.Dim)
        }
    }

    /** Anteprima a tutto spazio; con [overlay] i comandi stanno sopra al video. */
    val preview: @Composable (Modifier, Boolean) -> Unit = { mod, overlay ->
        Box(mod.background(Color.Black), contentAlignment = Alignment.Center) {
            val player = vm.player
            when {
                player != null -> {
                    // Con i pannelli Keyframe e Inquadra aperti si può pizzicare, trascinare e ruotare il video.
                    val framing = activeTool == Tool.FRAME
                    val gestures = (activeTool == Tool.KEYFRAME || framing) && clip != null && !running
                    AndroidView(
                        factory = { ctx -> PlayerView(ctx).apply { useController = false; setShutterBackgroundColor(android.graphics.Color.BLACK) } },
                        update = { it.player = player },
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = live.zoom; scaleY = live.zoom
                            translationX = live.pan.x; translationY = live.pan.y
                            rotationZ = live.rotation
                        },
                    )
                    // Testo selezionato: riquadro da trascinare, pizzicare e ruotare con le dita.
                    val txt = vm.selectedText
                    if (activeTool == Tool.TEXT && txt != null && !running) TextHandleOverlay(vm, txt)
                    if (gestures) {
                        val firstClip = project.clips.firstOrNull()
                        val videoAspect = if (firstClip != null && firstClip.height > 0) firstClip.width.toFloat() / firstClip.height else 16f / 9f
                        Box(
                            Modifier.fillMaxSize().pointerInput(clip?.id) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    var zoom = 1f; var pan = Offset.Zero; var rot = 0f
                                    do {
                                        val ev = awaitPointerEvent()
                                        zoom *= ev.calculateZoom(); pan += ev.calculatePan(); rot += ev.calculateRotation()
                                        live = LiveTransform(zoom, pan, rot)
                                        ev.changes.forEach { it.consume() }
                                    } while (ev.changes.any { it.pressed })
                                    if (zoom != 1f || pan != Offset.Zero || rot != 0f) {
                                        // Dimensione del video dentro l'anteprima (adattato senza tagli).
                                        val vw = minOf(size.width.toFloat(), size.height * videoAspect)
                                        val vh = vw / videoAspect
                                        val base = if (framing) vm.selectedClip?.frame ?: Transform() else vm.transformAtPlayhead()
                                        val next = base.copy(
                                            x = (base.x + pan.x / vw).coerceIn(-2f, 2f),
                                            y = (base.y + pan.y / vh).coerceIn(-2f, 2f),
                                            scale = (base.scale * zoom).coerceIn(0.2f, 5f),
                                            rotation = ((base.rotation + rot + 540f) % 360f) - 180f,
                                        )
                                        if (framing) vm.setFrame(next) else vm.setTransformAtPlayhead(next)
                                    }
                                    live = LiveTransform()
                                }
                            }
                        )
                    }
                }
                project.clips.isEmpty() -> EmptyState(onAdd = pickVideos)
                running -> Text("Anteprima in pausa durante l'esportazione", style = MaterialTheme.typography.bodySmall)
            }
            vm.previewError?.let {
                Text(it, color = VV.Danger, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopCenter).padding(8.dp))
            }
            if (overlay) {
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
                        // Mai sotto i tasti di navigazione di Android (se visibili).
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Box(Modifier.align(Alignment.CenterStart)) { timecode() }
                    Box(Modifier.align(Alignment.Center)) { playButton(40.dp) }
                    Box(Modifier.align(Alignment.CenterEnd)) {
                        IconBtn(if (fullscreen) VVIcons.FullscreenExit else VVIcons.Fullscreen, "Schermo intero", tint = Color.White) { fullscreen = !fullscreen }
                    }
                }
            } else if (project.clips.isNotEmpty()) {
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                    IconBtn(VVIcons.Fullscreen, "Schermo intero", tint = Color.White) { fullscreen = true }
                }
            }
        }
    }

    val transport: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 12.dp)) {
            Box(Modifier.align(Alignment.CenterStart)) { timecode() }
            Box(Modifier.align(Alignment.Center)) { playButton(38.dp) }
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
            onSelect = { vm.selectedClipId = it; if (it == null) { vm.selectedMusicId = null; vm.selectedTextId = null } },
            onTrim = { id, s, e -> vm.trimClip(id, s, e) },
            onAdd = pickVideos,
            selectedMusicId = vm.selectedMusicId,
            onSelectMusic = { vm.selectedMusicId = it; activeTool = Tool.MUSIC },
            onMusicChange = vm::setMusic,
            onAddMusic = pickMusic,
            selectedTextId = vm.selectedTextId,
            onSelectText = { vm.selectText(it); activeTool = Tool.TEXT },
            onTextChange = vm::setText,
        )
    }

    val tools = when { clip == null -> PROJECT_TOOLS; clip.isPhoto -> PHOTO_TOOLS; else -> CLIP_TOOLS }
    val toolbar: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().background(VV.Canvas).horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            tools.forEach { t -> ToolButton(t, active = activeTool == t, enabled = !running) { onTool(t) } }
        }
    }

    val panelBody: @Composable (Tool) -> Unit = { t ->
        when (t) {
            Tool.SPEED -> SpeedPanel(vm, running)
            Tool.KEYFRAME -> KeyframePanel(vm, running)
            Tool.TRANSITION -> TransitionPanel(vm, running)
            Tool.DURATION -> PhotoPanel(vm, running)
            Tool.FRAME -> FramePanel(vm, running)
            Tool.MUSIC -> MusicPanel(vm, running, pickMusic)
            Tool.TEXT -> TextPanel(vm, running)
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
    val panelHeader: @Composable (Tool) -> Unit = { t ->
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (clip != null && t != Tool.EXPORT && t != Tool.COLOR) {
                Text("Clip ${project.clips.indexOf(clip) + 1} di ${project.clips.size}", style = MaterialTheme.typography.bodySmall)
            }
            IconBtn(VVIcons.Check, "Chiudi", enabled = !running) { activeTool = null }
        }
    }

    Column(Modifier.fillMaxSize().background(if (fullscreen) Color.Black else VV.Canvas).then(if (landscape || fullscreen) Modifier else Modifier.safeDrawingPadding())) {
        vm.busy?.let { LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = VV.Tally, trackColor = VV.Canvas) }
        when {
            fullscreen -> preview(Modifier.fillMaxSize(), true)
            landscape -> {
                // Orizzontale: il video occupa tutta l'altezza disponibile; strumenti e pannelli a destra.
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    preview(Modifier.weight(1f).fillMaxHeight(), true)
                    Column(Modifier.width(300.dp).fillMaxHeight().background(VV.Panel)) {
                        titleRow(false)
                        val t = activeTool
                        if (t != null) {
                            panelHeader(t)
                            Column(
                                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) { panelBody(t) }
                        } else {
                            ToolGrid(tools, activeTool, !running, Modifier.weight(1f)) { onTool(it) }
                            Box(Modifier.fillMaxWidth().padding(10.dp), contentAlignment = Alignment.Center) {
                                ExportPill(enabled = project.clips.isNotEmpty()) { activeTool = Tool.EXPORT }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().background(VV.Panel), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { timeline() }
                    Column {
                        IconBtn(VVIcons.Add, "Aumenta zoom", tint = VV.Dim) { if (zoom < ZOOMS.lastIndex) zoom++ }
                        IconBtn(VVIcons.Remove, "Riduci zoom", tint = VV.Dim) { if (zoom > 0) zoom-- }
                    }
                }
            }
            else -> {
                titleRow(true)
                preview(Modifier.fillMaxWidth().weight(1f), false)
                transport()
                timeline()
                activeTool?.let { t ->
                    Column(Modifier.fillMaxWidth().heightIn(max = 340.dp).background(VV.Panel)) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(VV.Line))
                        panelHeader(t)
                        Column(
                            Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) { panelBody(t) }
                    }
                }
                toolbar()
            }
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

/** Strumenti a griglia (colonna destra in orizzontale). */
@Composable
private fun ToolGrid(tools: List<Tool>, active: Tool?, enabled: Boolean, modifier: Modifier, onClick: (Tool) -> Unit) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp)) {
        tools.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { t -> ToolButton(t, active == t, enabled) { onClick(t) } }
                repeat(4 - row.size) { Spacer(Modifier.width(64.dp)) }
            }
        }
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
        ) { Icon(VVIcons.Add, "Aggiungi video e foto", tint = VV.Text) }
        Spacer(Modifier.height(10.dp))
        Text("Aggiungi video e foto da montare", style = MaterialTheme.typography.bodySmall)
    }
}

// ---------------------------------------------------------------- pannelli

@Composable
private fun SpeedPanel(vm: EditorViewModel, running: Boolean) {
    val clip = vm.selectedClip ?: return
    val project = vm.project ?: return
    Segmented(listOf(ClipSpeed.SLOW, ClipSpeed.NORMAL, ClipSpeed.FAST, ClipSpeed.RAMP), clip.speed, { it.label }, !running) { s ->
        vm.updateSelected { it.copy(speed = s) }
    }
    if (clip.speed == ClipSpeed.RAMP) {
        // Posizione dell'indicatore dentro questa clip (sul tempo originale), per orientarsi sulla curva.
        val start = project.clipStartsUs()[project.clips.indexOf(clip)]
        val inside = vm.playheadUs - start
        val frac = if (inside in 0..clip.outputUs) clip.speedCurve().sourceTimeAt(inside).toFloat() / clip.trimmedUs else null
        // Punti velocità sul fotogramma esatto: si sceglie il punto sulla timeline e lì si fissa la velocità.
        val here = vm.speedPointAtPlayhead()
        val f = vm.playheadFraction()
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBtn(VVIcons.MoveLeft, "Punto precedente") { vm.jumpSpeedPoint(-1) }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (here != null) VV.Tally else VV.Raised)
                    .clickable(enabled = !running) { if (f == null) vm.seekToSelectedStart() else vm.toggleSpeedPointAtPlayhead() }.padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        f == null -> "Vai all'inizio della clip"
                        here == null -> "Aggiungi punto velocità qui"
                        here.t == 0f || here.t == 1f -> "Punto di " + (if (here.t == 0f) "inizio" else "fine") + " clip"
                        else -> "Togli il punto"
                    },
                    style = MaterialTheme.typography.labelLarge, color = if (here != null) VV.TallyInk else VV.Text,
                )
            }
            IconBtn(VVIcons.MoveRight, "Punto successivo") { vm.jumpSpeedPoint(+1) }
        }
        if (f != null) {
            val now = here?.speed ?: SpeedInterp.valueAt(clip.curvePoints, f)
            val lnMin = kotlin.math.ln(SpeedLimits.MIN)
            val lnMax = kotlin.math.ln(SpeedLimits.MAX)
            fun toSlider(sp: Float) = ((kotlin.math.ln(sp) - lnMin) / (lnMax - lnMin) * 1000).toInt()
            fun fromSlider(v: Int): Float {
                val sp = kotlin.math.exp(lnMin + v / 1000f * (lnMax - lnMin))
                return if (kotlin.math.abs(sp - 1f) < 0.04f) 1f else sp
            }
            LabeledSlider(
                "Velocità in questo punto", null, toSlider(now), !running, range = 0..1000,
                format = { "%.2f×".format(fromSlider(it)).replace('.', ',') },
            ) { v -> vm.setSpeedAtPlayhead(fromSlider(v)) }
        }
        SpeedCurveEditor(
            points = clip.curvePoints,
            playFraction = frac,
            enabled = !running,
            onChange = { pts -> vm.updateSelected { it.copy(speedPoints = pts) } },
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpeedPreset.entries.forEach { preset ->
                val on = clip.curvePoints == preset.points
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(if (on) VV.Tally else VV.Raised)
                        .clickable(enabled = !running) { vm.updateSelected { it.copy(speedPoints = preset.points) } }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text(preset.label, style = MaterialTheme.typography.labelLarge, color = if (on) VV.TallyInk else VV.Text) }
            }
        }
        Hint("Scorri la timeline fino al fotogramma esatto e aggiungi lì un punto, oppure trascina i punti sul grafico (tocco su uno spazio vuoto per aggiungere, doppio tocco per togliere). Per partire da zero usa \"Piatta 1×\". Durata: " + formatTime(clip.outputUs))
    } else {
        Hint(
            when (clip.speed) {
                ClipSpeed.SLOW -> "Metà velocità per tutta la clip."
                ClipSpeed.FAST -> "Doppia velocità per tutta la clip."
                else -> "Velocità originale. Scegli \"Curva\" per disegnare uno speed ramp."
            } + "  Durata: " + formatTime(clip.outputUs)
        )
    }
}

/** Gesto in corso sul video, mostrato subito; il keyframe si salva quando si alzano le dita. */
private data class LiveTransform(val zoom: Float = 1f, val pan: Offset = Offset.Zero, val rotation: Float = 0f)

@Composable
private fun KeyframePanel(vm: EditorViewModel, running: Boolean) {
    val clip = vm.selectedClip ?: return
    val inside = vm.playheadInSelected() != null
    val here = vm.keyframeAtPlayhead()
    val v = vm.transformAtPlayhead()
    val enabled = !running && inside
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBtn(VVIcons.MoveLeft, "Keyframe precedente", enabled = clip.keyframes.isNotEmpty()) { vm.jumpKeyframe(-1) }
        Box(
            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (here != null) VV.Tally else VV.Raised)
                .clickable(enabled = enabled) { vm.toggleKeyframeAtPlayhead() }.padding(vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(VVIcons.Keyframe, null, tint = if (here != null) VV.TallyInk else VV.Text, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (here != null) "Togli il keyframe" else "Aggiungi keyframe qui",
                    style = MaterialTheme.typography.labelLarge, color = if (here != null) VV.TallyInk else VV.Text,
                )
            }
        }
        IconBtn(VVIcons.MoveRight, "Keyframe successivo", enabled = clip.keyframes.isNotEmpty()) { vm.jumpKeyframe(+1) }
    }
    if (!inside) {
        Hint("Porta l'indicatore dentro la clip selezionata.")
        return
    }
    Hint(
        if (clip.keyframes.isEmpty()) "Pizzica, trascina o ruota il video con due dita, oppure usa i cursori: il keyframe si crea da solo."
        else "${clip.keyframes.size} keyframe in questa clip. Sposta l'indicatore e cambia i valori: ogni modifica crea o aggiorna un keyframe."
    )
    LabeledSlider("Zoom", null, (v.scale * 100).toInt(), enabled, range = 20..400, format = { "$it%" }) {
        vm.setTransformAtPlayhead(v.copy(scale = it / 100f))
    }
    LabeledSlider("Orizzontale", null, (v.x * 100).toInt(), enabled, range = -100..100) {
        vm.setTransformAtPlayhead(v.copy(x = it / 100f))
    }
    LabeledSlider("Verticale", null, (v.y * 100).toInt(), enabled, range = -100..100) {
        vm.setTransformAtPlayhead(v.copy(y = it / 100f))
    }
    LabeledSlider("Rotazione", null, v.rotation.toInt(), enabled, range = -180..180, format = { "$it°" }) {
        vm.setTransformAtPlayhead(v.copy(rotation = it.toFloat()))
    }
    LabeledSlider("Opacità", "Sotto il video c'è il nero: abbassandola si ottiene una dissolvenza.", (v.opacity * 100).toInt(), enabled, format = { "$it%" }) {
        vm.setTransformAtPlayhead(v.copy(opacity = it / 100f))
    }
    if (clip.keyframes.size >= 2) {
        PanelLabel("Andamento verso il keyframe successivo")
        val t = vm.playheadInSelected() ?: 0L
        val current = clip.keyframes.filter { it.timeUs <= t + 40_000 }.maxByOrNull { it.timeUs }?.easing
        Segmented(Easing.entries, current ?: Easing.SMOOTH, { it.label }, !running && current != null) {
            vm.setEasingAtPlayhead(it)
        }
    }
    if (clip.keyframes.isNotEmpty()) {
        SecondaryButton("Togli tutti i keyframe della clip", onClick = vm::clearKeyframes)
    }
}

@Composable
private fun TransitionPanel(vm: EditorViewModel, running: Boolean) {
    val p = vm.project ?: return
    // Nessuna clip selezionata: prende quella sotto l'indicatore.
    if (vm.selectedClip == null) {
        LaunchedEffect(Unit) {
            p.locate(vm.playheadUs)?.let { (i, _) -> vm.selectedClipId = p.clips[i].id }
        }
    }
    val clip = vm.selectedClip ?: return
    val index = p.clips.indexOfFirst { it.id == clip.id }
    if (index < 0 || index == p.clips.lastIndex) {
        Hint("Nessuna clip dopo questa: la transizione si applica tra la clip selezionata e la successiva.")
        return
    }
    val spec = clip.transitionOut
    PanelLabel("Verso la clip ${index + 2}")
    val options: List<TransitionType?> = listOf(null) + TransitionType.entries
    PillRow(options, spec?.type, { it?.label ?: "Nessuna" }, !running) { t ->
        vm.setTransition(t?.let { TransitionSpec(it, spec?.durationUs ?: 500_000L) })
    }
    if (spec != null) {
        val ms = (spec.durationUs / 1000).toInt()
        LabeledSlider(
            "Durata", null, ms / 100, !running, range = 2..20,
            format = { String.format(java.util.Locale.ITALIAN, "%.1f s", it / 10f) },
        ) { v -> vm.setTransition(spec.copy(durationUs = v * 100_000L)) }
        val real = p.transitionHalfUs(index) * 2
        if (real < spec.durationUs) {
            Hint(String.format(java.util.Locale.ITALIAN, "Clip corte: durata effettiva %.1f s.", real / 1_000_000f))
        }
        SecondaryButton("Applica a tutti i tagli", onClick = { vm.setTransitionAll(spec) })
    }
    Hint("Metà effetto alla fine di questa clip, metà all'inizio della successiva.")
}

@Composable
private fun MusicPanel(vm: EditorViewModel, running: Boolean, onAdd: () -> Unit) {
    val p = vm.project ?: return
    val t = vm.selectedMusic
    val enabled = !running
    if (t == null) {
        if (p.music.isEmpty()) {
            Hint("Scegli un brano dal telefono (MP3, M4A, WAV, FLAC…): parte dal punto dell'indicatore e si ferma alla fine del video.")
        } else {
            PanelLabel("Brani nel progetto")
            PillRow(p.music, null as MusicTrack?, { it?.name ?: "" }, enabled) { m -> if (m != null) vm.selectedMusicId = m.id }
        }
        SecondaryButton(if (p.music.isEmpty()) "Aggiungi musica" else "Aggiungi un altro brano", onClick = { if (enabled) onAdd() })
        return
    }
    val total = p.durationUs
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(VVIcons.Music, null, tint = VV.Music, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(t.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(formatTime(t.playUs(total)), style = MaterialTheme.typography.bodySmall)
    }
    PanelLabel("Ritmo")
    if (t.beatsUs.isEmpty()) {
        SecondaryButton("Rileva i battiti automaticamente", onClick = { if (enabled) vm.detectBeats() })
    } else {
        Text(
            "${t.bpm.roundToInt()} BPM · ${t.timelineBeats(total).size} battiti nel video",
            style = MaterialTheme.typography.bodyMedium,
        )
        Segmented(listOf(1, 2, 4), t.beatEvery, { if (it == 1) "Tutti" else "Ogni $it" }, enabled) { n ->
            vm.updateBeats { it.copy(beatEvery = n) }
        }
        LabeledSlider(
            "Sposta i battiti automatici", "Se cadono tutti un po' prima o dopo il colpo, correggili qui.",
            (t.beatShiftUs / 10_000).toInt(), enabled, range = -30..30,
            format = { (if (it > 0) "+" else "") + "${it * 10} ms" },
        ) { v -> vm.updateBeats { it.copy(beatShiftUs = v * 10_000L) } }
    }
    PanelLabel("Battiti a mano")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        SecondaryButton("Segna / togli qui", onClick = { if (enabled) vm.toggleBeatAtPlayhead() })
        // Tap: si preme play e si tocca a tempo di musica.
        Box(
            Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                .background(if (vm.isPlaying) VV.Tally else VV.Raised)
                .clickable(enabled = enabled) { if (vm.isPlaying) vm.tapBeat() else vm.togglePlay() }
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (vm.isPlaying) "TAP a tempo" else "▶ Ascolta e tocca",
                style = MaterialTheme.typography.labelLarge,
                color = if (vm.isPlaying) VV.TallyInk else VV.Text,
            )
        }
    }
    Hint("Segna: aggiunge un battito nel punto dell'indicatore, o toglie quello che c'è già (anche automatico). Tap: avvia l'ascolto e tocca il pulsante a ogni colpo; i battiti a mano sono bianchi.")
    if (t.hasBeats) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("◀ Battito", onClick = { vm.jumpBeat(-1) })
            SecondaryButton("Battito ▶", onClick = { vm.jumpBeat(+1) })
        }
        SecondaryButton("Allinea i tagli al ritmo", onClick = { if (enabled) vm.alignCutsToBeats() })
        SecondaryButton("Dividi i video sui battiti", onClick = { if (enabled) vm.splitOnBeats() })
        ToggleRow("Aggancia i tagli ai battiti", vm.snapToBeats, enabled) { vm.snapToBeats = it }
        SecondaryButton("Cancella tutti i battiti", onClick = { if (enabled) vm.clearBeats() })
    }
    PanelLabel("Brano")
    LabeledSlider("Volume", null, (t.volume * 100).toInt(), enabled, range = 0..200, format = { "$it%" }) { v ->
        vm.updateMusic { it.copy(volume = v / 100f) }
    }
    val secs = { v: Int -> String.format(java.util.Locale.ITALIAN, "%.1f s", v / 10f) }
    LabeledSlider("Dissolvenza in entrata", null, (t.fadeInUs / 100_000).toInt(), enabled, range = 0..100, format = secs) { v ->
        vm.updateMusic { it.copy(fadeInUs = v * 100_000L) }
    }
    LabeledSlider("Dissolvenza in uscita", null, (t.fadeOutUs / 100_000).toInt(), enabled, range = 0..100, format = secs) { v ->
        vm.updateMusic { it.copy(fadeOutUs = v * 100_000L) }
    }
    val maxOffset = ((t.sourceDurationUs - MusicTrack.MIN_US) / 100_000).toInt().coerceAtLeast(0)
    LabeledSlider(
        "Parti dal punto del brano", "Per far partire la musica dal ritornello o da un altro punto.",
        (t.offsetUs / 100_000).toInt().coerceAtMost(maxOffset), enabled && maxOffset > 0, range = 0..maxOf(maxOffset, 1),
        format = { formatTime(it * 100_000L) },
    ) { v ->
        vm.updateMusic { m ->
            val off = v * 100_000L
            m.copy(offsetUs = off, lengthUs = minOf(m.lengthUs, m.sourceDurationUs - off).coerceAtLeast(MusicTrack.MIN_US))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("Inizia da qui", onClick = {
            if (enabled) vm.updateMusic { it.copy(startUs = vm.playheadUs.coerceIn(0, (total - MusicTrack.MIN_US).coerceAtLeast(0))) }
        })
        SecondaryButton("Fino alla fine", onClick = {
            if (enabled) vm.updateMusic { it.copy(lengthUs = (it.sourceDurationUs - it.offsetUs).coerceAtLeast(MusicTrack.MIN_US)) }
        })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("Dividi qui", onClick = { if (enabled) vm.splitMusicAtPlayhead() })
        SecondaryButton("Elimina brano", onClick = { if (enabled) vm.deleteMusic() })
    }
    Hint("Tocca il brano nella timeline per selezionarlo: trascinalo per spostarlo, usa le maniglie per accorciarlo. Le dissolvenze si sentono nel video esportato.")
}

@Composable
private fun PhotoPanel(vm: EditorViewModel, running: Boolean) {
    val clip = vm.selectedClip ?: return
    val enabled = !running
    LabeledSlider(
        "Durata della foto", null, (clip.trimmedUs / 100_000).toInt(), enabled, range = 2..150,
        format = { String.format(java.util.Locale.ITALIAN, "%.1f s", it / 10f) },
    ) { v -> vm.setPhotoDuration(v * 100_000L) }
    ToggleRow("Zoom lento (effetto Ken Burns)", clip.keyframes.isNotEmpty(), enabled) { vm.toggleKenBurns() }
    Hint("Zoom, posizione, rotazione e Adatta/Riempi sono nello strumento Inquadra. Lo zoom lento usa due keyframe: lo personalizzi dallo strumento Keyframe. Con i battiti, trascinando la maniglia destra la durata si aggancia al ritmo.")
}

@Composable
private fun FramePanel(vm: EditorViewModel, running: Boolean) {
    val clip = vm.selectedClip ?: return
    val enabled = !running
    val f = clip.frame
    Segmented(listOf(false, true), clip.fill, { if (it) "Riempi" else "Adatta" }, enabled) { v ->
        vm.updateSelected { it.copy(fill = v) }
    }
    Hint("Pizzica, trascina o ruota l'anteprima con due dita, oppure usa i cursori.")
    LabeledSlider("Zoom", null, (f.scale * 100).toInt(), enabled, range = 20..400, format = { "$it%" }) {
        vm.setFrame(f.copy(scale = it / 100f))
    }
    LabeledSlider("Orizzontale", null, (f.x * 100).toInt(), enabled, range = -100..100) { vm.setFrame(f.copy(x = it / 100f)) }
    LabeledSlider("Verticale", null, (f.y * 100).toInt(), enabled, range = -100..100) { vm.setFrame(f.copy(y = it / 100f)) }
    LabeledSlider("Rotazione", null, f.rotation.toInt(), enabled, range = -180..180, format = { "$it°" }) {
        vm.setFrame(f.copy(rotation = it.toFloat()))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("Ruota 90°", onClick = {
            if (enabled) vm.setFrame(f.copy(rotation = ((f.rotation + 90f + 540f) % 360f) - 180f))
        })
        SecondaryButton("Reimposta", onClick = { if (enabled) { vm.setFrame(Transform()); vm.updateSelected { it.copy(fill = false) } } })
    }
    SecondaryButton("Applica a tutte le clip", onClick = { if (enabled) vm.applyFrameToAll() })
}

private val TEXT_COLORS = listOf(
    0xFFFFFFFF, 0xFF111111, 0xFFF2B33D, 0xFFFFE14D, 0xFFFF5A4E, 0xFFFF7AB6, 0xFF5BC0FF, 0xFF4ADE80, 0xFFB794F6,
).map { it.toInt() }

@Composable
private fun TextPanel(vm: EditorViewModel, running: Boolean) {
    val p = vm.project ?: return
    val t = vm.selectedText
    val enabled = !running
    if (t == null) {
        if (p.texts.isNotEmpty()) {
            PanelLabel("Testi nel progetto")
            PillRow(p.texts, null as TextItem?, { it?.text?.lines()?.firstOrNull()?.take(18) ?: "" }, enabled) { x -> if (x != null) vm.selectText(x.id) }
        }
        SecondaryButton("Aggiungi testo", onClick = { if (enabled) vm.addText() })
        Hint("Il testo parte dall'indicatore. Nella timeline lo sposti trascinandolo e ne cambi la durata con le maniglie.")
        return
    }
    OutlinedTextField(
        value = t.text,
        onValueChange = { v -> vm.updateText(typing = true) { it.copy(text = v) } },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyLarge,
        minLines = 1, maxLines = 4,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = VV.Tally, unfocusedBorderColor = VV.Line, cursorColor = VV.Tally,
            focusedTextColor = VV.Text, unfocusedTextColor = VV.Text,
        ),
    )
    PanelLabel("Carattere")
    PillRow(TextFont.entries, t.font, { it.label }, enabled) { f -> vm.updateText { it.copy(font = f) } }
    PanelLabel("Colore")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TEXT_COLORS.forEach { c ->
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(15.dp)).background(Color(c))
                    .border(if (c == t.color) 3.dp else 1.dp, if (c == t.color) VV.Tally else VV.Line, RoundedCornerShape(15.dp))
                    .clickable(enabled = enabled) { vm.updateText { it.copy(color = c) } }
            )
        }
    }
    PanelLabel("Stile")
    Segmented(TextLook.entries, t.look, { it.label }, enabled) { l -> vm.updateText { it.copy(look = l) } }
    LabeledSlider("Dimensione", null, (t.size * 1000).toInt(), enabled, range = 15..250, format = { "${it / 10}" }) { v ->
        vm.updateText(typing = true) { it.copy(size = v / 1000f) }
    }
    PanelLabel("Posizione")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("Alto", onClick = { vm.updateText { it.copy(x = 0f, y = -0.36f) } })
        SecondaryButton("Centro", onClick = { vm.updateText { it.copy(x = 0f, y = 0f) } })
        SecondaryButton("Basso", onClick = { vm.updateText { it.copy(x = 0f, y = 0.36f) } })
    }
    Hint("Oppure trascina, pizzica e ruota il riquadro sull'anteprima con le dita.")
    PanelLabel("Animazione in entrata")
    PillRow(TextAnim.entries, t.animIn, { it.label }, enabled) { a -> vm.updateText { it.copy(animIn = a) } }
    PanelLabel("Animazione in uscita")
    PillRow(TextAnim.entries.filter { it != TextAnim.TYPE }, t.animOut, { it.label }, enabled) { a -> vm.updateText { it.copy(animOut = a) } }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("Duplica", onClick = { if (enabled) vm.duplicateText() })
        SecondaryButton("Elimina", onClick = { if (enabled) vm.deleteText() })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("Nuovo testo", onClick = { if (enabled) vm.addText() })
        SecondaryButton("Stile a tutti", onClick = { if (enabled) vm.applyTextStyleToAll() })
    }
}

/** Riquadro del testo selezionato sopra all'anteprima: si trascina, si pizzica (misura) e si ruota. */
@Composable
private fun TextHandleOverlay(vm: EditorViewModel, t: TextItem) {
    val context = LocalContext.current
    val (outW, outH) = vm.previewFrameSize()
    val (bw, bh) = remember(t.text, t.font, t.look, t.size, outW, outH) {
        TextRenderer.measure(context, t, outW, outH)
    }
    var live by remember { mutableStateOf(LiveTransform()) }
    val current by rememberUpdatedState(t)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val aspect = outW.toFloat() / outH
        val vw = minOf(wPx, hPx * aspect)
        val vh = vw / aspect
        val k = vw / outW
        val cx = wPx / 2 + t.x * vw + live.pan.x
        val cy = hPx / 2 + t.y * vh + live.pan.y
        val boxW = bw * k * live.zoom
        val boxH = bh * k * live.zoom
        Canvas(Modifier.fillMaxSize()) {
            rotate(t.rotation + live.rotation, Offset(cx, cy)) {
                drawRect(
                    VV.Tally, topLeft = Offset(cx - boxW / 2, cy - boxH / 2), size = androidx.compose.ui.geometry.Size(boxW, boxH),
                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
            }
        }
        Box(
            Modifier.fillMaxSize().pointerInput(t.id, vw) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var zoom = 1f; var pan = Offset.Zero; var rot = 0f
                    do {
                        val ev = awaitPointerEvent()
                        zoom *= ev.calculateZoom(); pan += ev.calculatePan(); rot += ev.calculateRotation()
                        live = LiveTransform(zoom, pan, rot)
                        ev.changes.forEach { it.consume() }
                    } while (ev.changes.any { it.pressed })
                    if (zoom != 1f || pan != Offset.Zero || rot != 0f) {
                        val b = current
                        vm.updateText { it.copy(
                            x = (b.x + pan.x / vw).coerceIn(-0.5f, 0.5f),
                            y = (b.y + pan.y / vh).coerceIn(-0.5f, 0.5f),
                            size = (b.size * zoom).coerceIn(0.015f, 0.25f),
                            rotation = ((b.rotation + rot + 540f) % 360f) - 180f,
                        ) }
                    }
                    live = LiveTransform()
                }
            }
        )
    }
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
