package it.fabriziodavi.vivovideo.ui

import it.fabriziodavi.vivovideo.model.creditsRoll
import it.fabriziodavi.vivovideo.model.FadeColor
import it.fabriziodavi.vivovideo.model.AutoStyle
import it.fabriziodavi.vivovideo.model.Grade
import it.fabriziodavi.vivovideo.model.GradeMath
import it.fabriziodavi.vivovideo.model.Hsl
import it.fabriziodavi.vivovideo.model.HslBand
import it.fabriziodavi.vivovideo.model.Wheel
import androidx.compose.ui.graphics.Brush
import it.fabriziodavi.vivovideo.model.FrameMode
import it.fabriziodavi.vivovideo.model.VideoFormat
import it.fabriziodavi.vivovideo.model.Orientation
import it.fabriziodavi.vivovideo.model.frameMode
import it.fabriziodavi.vivovideo.model.withMode
import it.fabriziodavi.vivovideo.model.orientation

import it.fabriziodavi.vivovideo.model.FxCategory

import it.fabriziodavi.vivovideo.model.KeyColor

import it.fabriziodavi.vivovideo.model.LayerMask

import it.fabriziodavi.vivovideo.model.FxType

import it.fabriziodavi.vivovideo.model.TextPreset

import it.fabriziodavi.vivovideo.model.SlideshowOptions

import it.fabriziodavi.vivovideo.model.MotionStyle

import it.fabriziodavi.vivovideo.engine.LayerRenderer

import it.fabriziodavi.vivovideo.model.LayerBorder

import it.fabriziodavi.vivovideo.model.Layer

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
import java.io.File
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import it.fabriziodavi.vivovideo.model.ClipStyle
import it.fabriziodavi.vivovideo.model.StabMath
import it.fabriziodavi.vivovideo.model.PersonMode
import it.fabriziodavi.vivovideo.model.ClipAttr
import it.fabriziodavi.vivovideo.engine.ExportPreset
import kotlin.math.ln

private val ZOOMS = listOf(8f, 15f, 30f, 60f, 120f)

/** Le sei voci della barra principale, sempre visibili. */
enum class ToolGroup(val label: String, val icon: () -> ImageVector) {
    EDIT("Modifica", { VVIcons.Edit }),
    AUDIO("Audio", { VVIcons.Music }),
    TEXT("Testo", { VVIcons.Text }),
    LAYER("Livelli", { VVIcons.Layers }),
    EFFECTS("Effetti", { VVIcons.Effects }),
    COLOR("Colore", { VVIcons.Palette }),
}

/** Strumenti e pannelli. [group] è la voce della barra principale a cui appartengono. */
enum class Tool(val label: String, val icon: () -> ImageVector, val group: ToolGroup?, val needsClip: Boolean = false) {
    SPLIT("Dividi", { VVIcons.Split }, ToolGroup.EDIT),
    SPEED("Velocità", { VVIcons.Speed }, ToolGroup.EDIT, needsClip = true),
    DURATION("Durata", { VVIcons.Speed }, ToolGroup.EDIT, needsClip = true),
    FRAME("Inquadra", { VVIcons.Fullscreen }, ToolGroup.EDIT, needsClip = true),
    KEYFRAME("Keyframe", { VVIcons.Keyframe }, ToolGroup.EDIT, needsClip = true),
    TRANSITION("Transizione", { VVIcons.Transition }, ToolGroup.EDIT),
    SLIDESHOW("Slideshow", { VVIcons.Movie }, ToolGroup.EDIT),
    FORMAT("Formato", { VVIcons.Fullscreen }, ToolGroup.EDIT),
    MORE("Altro", { VVIcons.Tune }, ToolGroup.EDIT, needsClip = true),
    MUSIC("Audio", { VVIcons.Music }, ToolGroup.AUDIO),
    TEXT("Testo", { VVIcons.Text }, ToolGroup.TEXT),
    LAYER("Livelli", { VVIcons.Layers }, ToolGroup.LAYER),
    EFFECTS("Effetti", { VVIcons.Effects }, ToolGroup.EFFECTS, needsClip = true),
    COLOR("Colore", { VVIcons.Palette }, ToolGroup.COLOR),
    EXPORT("Esporta", { VVIcons.Export }, null),
}

/** Strumenti della seconda riga di "Modifica" (Velocità diventa Durata sulle foto). */
fun editTools(photo: Boolean) = listOf(
    Tool.SPLIT, if (photo) Tool.DURATION else Tool.SPEED, Tool.FRAME, Tool.KEYFRAME, Tool.TRANSITION, Tool.SLIDESHOW, Tool.MORE,
)

/** Pannello che si apre toccando una voce della barra principale (null = mostra la seconda riga). */
fun ToolGroup.panel(): Tool? = when (this) {
    ToolGroup.EDIT -> null
    ToolGroup.AUDIO -> Tool.MUSIC
    ToolGroup.TEXT -> Tool.TEXT
    ToolGroup.LAYER -> Tool.LAYER
    ToolGroup.EFFECTS -> Tool.EFFECTS
    ToolGroup.COLOR -> Tool.COLOR
}

/** Schede in cima ai pannelli più ricchi. */
val MUSIC_TABS = listOf("Brano", "Ritmo", "Battiti", "Voce", "Clip")
val TEXT_TABS = listOf("Stile", "Animazione", "Movimento")
val LAYER_TABS = listOf("Aspetto", "Forma", "Animazione")
/** Animazioni per i livelli (quelle lettera per lettera, glitch e luccichio sono solo per i testi). */
/** Le novità premium in testa alle liste: si vedono subito. */
// L'incrociata resta la prima (è la più usata), poi le novità.
// Prima le transizioni di tendenza (stacchi da velocity edit), poi le altre novità e le classiche.
val TRANSITIONS_NEW_FIRST: List<TransitionType> = listOf(TransitionType.CROSSFADE) + TransitionType.entries.filter { it.trend } +
    TransitionType.entries.filter { it.premium && !it.trend } + TransitionType.entries.filter { !it.premium && it != TransitionType.CROSSFADE }
val PRESETS_NEW_FIRST: List<TextPreset> = TextPreset.entries.filter { it.trend } +
    TextPreset.entries.filter { !it.trend && it.ordinal >= TextPreset.CINEMA26.ordinal } + TextPreset.entries.filter { it.ordinal < TextPreset.CINEMA26.ordinal }
val ANIMS_NEW_FIRST: List<TextAnim> = listOf(TextAnim.NONE) + TextAnim.entries.filter { it.ordinal >= TextAnim.BOUNCE.ordinal } +
    TextAnim.entries.filter { it != TextAnim.NONE && it.ordinal < TextAnim.BOUNCE.ordinal }
val LAYER_ANIMS = TextAnim.entries.filter { !it.inOnly && !it.letters && it != TextAnim.GLITCH }
val EFFECT_TABS = listOf("Effetti", "Dissolvenze", "Persona", "Privacy")
val COLOR_TABS = listOf("Base", "Ruote", "HSL", "Progetto")

@Composable
fun EditorScreen(vm: EditorViewModel, initialTool: Tool? = null, initialTab: Int = 0) {
    val project = vm.project ?: return
    val context = LocalContext.current
    val running = vm.exportState is ExportState.Running
    var zoom by remember { mutableIntStateOf(2) }
    var renaming by remember { mutableStateOf(false) }
    var activeTool by remember { mutableStateOf(initialTool) }
    // Voce della barra principale aperta (per "Modifica" mostra la seconda riga).
    var group by remember { mutableStateOf(initialTool?.group ?: if (vm.selectedClipId != null) ToolGroup.EDIT else null) }
    var tab by remember(activeTool) { mutableIntStateOf(if (activeTool == initialTool) initialTab else 0) }
    var clipMenu by remember { mutableStateOf(false) }
    var pasteDialog by remember { mutableStateOf(false) }
    val clip = vm.selectedClip

    // Un pannello di clip si chiude se non c'è più una clip selezionata.
    if (clip == null && activeTool?.needsClip == true) activeTool = null
    // Foto e video hanno strumenti diversi.
    if (clip != null && clip.isPhoto && activeTool == Tool.SPEED) activeTool = Tool.DURATION
    if (clip != null && !clip.isPhoto && activeTool == Tool.DURATION) activeTool = Tool.SPEED
    val shownGroup = activeTool?.group ?: group

    BackHandler {
        when {
            running -> vm.cancelExport()
            activeTool != null -> activeTool = null
            group != null -> group = null
            else -> vm.closeProject()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { vm.addClips(it) }
    val lutPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importLut(uri)
    }
    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { vm.addMusic(uri); activeTool = Tool.MUSIC }
    }
    val pickMusic = { musicPicker.launch(arrayOf("audio/*")) }
    val layerPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { vm.addLayer(uri); activeTool = Tool.LAYER }
    }
    val pickLayer = { layerPicker.launch(arrayOf("image/*", "video/*")) }
    var askAdd by remember { mutableStateOf(false) }
    val pickVideos = { askAdd = true }
    var projectMenu by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var relinkOpen by remember { mutableStateOf(false) }
    var reportOpen by remember { mutableStateOf(false) }
    var backupWithMedia by remember { mutableStateOf(false) }
    val backupSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.exportBackup(uri, backupWithMedia)
    }
    var relinkTarget by remember { mutableStateOf<String?>(null) }
    val relinkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val old = relinkTarget
        if (uri != null && old != null) vm.relinkManual(old, uri)
        relinkTarget = null
    }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.addFont(uri) }

    /** Clip su cui lavorare: quella selezionata o, se nessuna, quella sotto l'indicatore. */
    fun targetClip() = vm.selectedClip ?: project.locate(vm.playheadUs)?.let { (i, _) -> project.clips[i] }

    fun onTool(t: Tool) {
        if (t.needsClip && vm.selectedClip == null) {
            val c = targetClip()
            if (c == null) { vm.message = "Aggiungi prima un video o una foto."; return }
            vm.selectedClipId = c.id
        }
        when (t) {
            Tool.SPLIT -> if (vm.selectedMusicId != null) vm.splitMusicAtPlayhead() else vm.splitAtPlayhead()
            Tool.MORE -> clipMenu = true
            Tool.TEXT -> {
                activeTool = Tool.TEXT
                if (project.texts.isEmpty()) vm.addText()
            }
            Tool.LAYER -> {
                activeTool = Tool.LAYER
                if (project.layers.isEmpty()) pickLayer()
            }
            else -> activeTool = if (activeTool == t) null else t
        }
    }

    fun onGroup(g: ToolGroup) {
        if (shownGroup == g) { activeTool = null; group = null; return }
        group = g
        activeTool = null
        g.panel()?.let { onTool(it); if (activeTool == null && it != Tool.LAYER) group = null }
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
            // La guida si apre sull'argomento dello strumento aperto (senza strumenti, sull'indice).
            IconBtn(VVIcons.Help, "Guida") { vm.pause(); vm.guideTopic = GuideIndex.topicFor(activeTool); vm.showGuide = true }
            IconBtn(VVIcons.Tune, "Progetto", enabled = !running) { vm.pause(); projectMenu = true }
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
                    val lay = vm.selectedLayer
                    if (activeTool == Tool.LAYER && lay != null && !running) LayerHandleOverlay(vm, lay)
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
            if (vm.preparing > 0) {
                Text(
                    vm.preparingLabel.ifBlank { if (vm.preparing == 1) "Preparo un tratto dell'anteprima…" else "Preparo ${vm.preparing} tratti dell'anteprima…" },
                    color = Color.White, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        .background(Color(0x99000000), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            if (vm.missingFiles.isNotEmpty() && vm.clipWork == null) {
                Text(
                    if (vm.missingFiles.size == 1) "1 file non trovato · tocca per ricollegarlo" else "${vm.missingFiles.size} file non trovati · tocca per ricollegarli",
                    color = Color.White, fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)
                        .background(VV.Danger, RoundedCornerShape(8.dp)).clickable { relinkOpen = true }.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            vm.clipWork?.let { label ->
                Column(
                    Modifier.align(Alignment.Center).padding(24.dp).background(Color(0xCC000000), RoundedCornerShape(10.dp)).padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("$label  ${(vm.clipWorkProgress * 100).toInt()}%", color = Color.White, fontSize = 13.sp)
                    LinearProgressIndicator(
                        progress = { vm.clipWorkProgress },
                        modifier = Modifier.width(200.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = VV.Tally, trackColor = VV.Raised2,
                    )
                    TextButton(onClick = vm::cancelClipWork) { Text("Annulla", color = VV.Dim) }
                }
            }
            // Con l'avviso dei file mancanti l'errore tecnico non si mostra (sarebbero due scritte una sopra l'altra).
            vm.previewError?.takeIf { vm.missingFiles.isEmpty() }?.let {
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
                    Column(Modifier.fillMaxWidth()) {
                        if (project.durationUs > 0) SeekBar(vm, project.durationUs)
                        Box(Modifier.fillMaxWidth()) {
                            Box(Modifier.align(Alignment.CenterStart)) { timecode() }
                            Box(Modifier.align(Alignment.Center)) { playButton(40.dp) }
                            Box(Modifier.align(Alignment.CenterEnd)) {
                                IconBtn(if (fullscreen) VVIcons.FullscreenExit else VVIcons.Fullscreen, "Schermo intero", tint = Color.White) { fullscreen = !fullscreen }
                            }
                        }
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
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                // Formato del video: sempre a vista, apre le preimpostazioni.
                val fmtOn = activeTool == Tool.FORMAT
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(if (fmtOn) VV.Tally else VV.Raised)
                        .clickable(enabled = !running) { group = ToolGroup.EDIT; activeTool = if (fmtOn) null else Tool.FORMAT }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    val (fw, fh) = vm.previewFrameSize()
                    Text(
                        if (project.format == VideoFormat.AUTO) (if (fh > fw) "9:16" else if (fw > fh) "16:9" else "1:1") else project.format.short,
                        style = MaterialTheme.typography.labelMedium, color = if (fmtOn) VV.TallyInk else VV.Text,
                    )
                }
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
            onSeek = vm::scrubTo,
            onScrubStart = vm::pause,
            multiIds = vm.multiIds,
            onSelect = {
                if (it != null && vm.multiIds != null) { vm.toggleMulti(it); return@Timeline }
                vm.selectedClipId = it
                if (it == null) { vm.selectedMusicId = null; vm.selectedTextId = null; vm.selectedLayerId = null }
                else if (activeTool == null) group = ToolGroup.EDIT
            },
            onTrim = { id, s, e -> vm.trimClip(id, s, e) },
            onAdd = pickVideos,
            selectedMusicId = vm.selectedMusicId,
            onSelectMusic = { vm.selectedMusicId = it; activeTool = Tool.MUSIC },
            onMusicChange = vm::setMusic,
            onAddMusic = pickMusic,
            selectedTextId = vm.selectedTextId,
            onSelectText = { vm.selectText(it); activeTool = Tool.TEXT },
            onTextChange = vm::setText,
            selectedLayerId = vm.selectedLayerId,
            onSelectLayer = { vm.selectLayer(it); activeTool = Tool.LAYER },
            onLayerChange = vm::setLayerItem,
            onClipLongPress = { vm.selectedClipId = it; clipMenu = true },
            onReorder = { id, to -> vm.moveClipTo(id, to) },
            onCutTap = { i -> vm.selectedClipId = project.clips[i].id; group = ToolGroup.EDIT; activeTool = Tool.TRANSITION },
        )
    }

    val photo = targetClip()?.isPhoto == true
    val subTools = editTools(photo)
    val toolbar: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().background(VV.Canvas)) {
            val multi = vm.multiIds
            if (multi != null) {
                MultiSelectBar(vm, multi, onPaste = { pasteDialog = true })
            } else {
            if (shownGroup == ToolGroup.EDIT) {
                Row(
                    Modifier.fillMaxWidth().background(VV.Panel).padding(horizontal = 2.dp, vertical = 2.dp),
                ) {
                    subTools.forEach { t ->
                        ToolButton(t.label, t.icon(), active = activeTool == t, enabled = !running, small = true, modifier = Modifier.weight(1f)) { onTool(t) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp)) {
                ToolGroup.entries.forEach { g ->
                    ToolButton(g.label, g.icon(), active = shownGroup == g, enabled = !running, modifier = Modifier.weight(1f), underline = true) { onGroup(g) }
                }
            }
            }
        }
    }

    val tabs: List<String> = when (activeTool) {
        Tool.MUSIC -> MUSIC_TABS
        Tool.TEXT -> if (vm.selectedText != null) TEXT_TABS else emptyList()
        Tool.LAYER -> if (vm.selectedLayer != null) LAYER_TABS else emptyList()
        Tool.EFFECTS -> EFFECT_TABS
        Tool.COLOR -> COLOR_TABS
        else -> emptyList()
    }
    val panelBody: @Composable (Tool) -> Unit = { t ->
        when (t) {
            Tool.SPEED -> SpeedPanel(vm, running)
            Tool.KEYFRAME -> KeyframePanel(vm, running)
            Tool.TRANSITION -> TransitionPanel(vm, running)
            Tool.DURATION -> PhotoPanel(vm, running)
            Tool.FRAME -> FramePanel(vm, running)
            Tool.MUSIC -> MusicPanel(vm, running, tab, pickMusic)
            Tool.TEXT -> TextPanel(vm, running, tab, onPickFont = { fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream")) })
            Tool.LAYER -> LayerPanel(vm, running, tab, pickLayer)
            Tool.SLIDESHOW -> SlideshowPanel(vm, running)
            Tool.FORMAT -> FormatPanel(vm, running)
            Tool.EFFECTS -> EffectsPanel(vm, running, tab)
            Tool.COLOR -> ColorPanel(vm, running, tab) { lutPicker.launch(arrayOf("*/*")) }
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
            if (clip != null && (t.needsClip || t == Tool.TRANSITION || (t == Tool.MUSIC && tab == 4))) {
                Text("Clip ${project.clips.indexOf(clip) + 1} di ${project.clips.size}", style = MaterialTheme.typography.bodySmall)
            }
            IconBtn(VVIcons.Check, "Chiudi", enabled = !running) { activeTool = null }
        }
        if (tabs.isNotEmpty()) PanelTabs(tabs, tab) { tab = it }
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
                            val all = subTools + Tool.FORMAT + ToolGroup.entries.mapNotNull { it.panel() }
                            ToolGrid(all, activeTool, !running, Modifier.weight(1f)) { onTool(it) }
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
                if (activeTool == null && project.auto != null) AutoBar(vm, !running)
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
    if (askAdd) {
        AddMediaDialog(
            onPick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
            onAlbums = { vm.openAlbumPicker(newProject = false) },
            onDismiss = { askAdd = false },
        )
    }
    if (clipMenu) {
        val c = vm.selectedClip
        val idx = c?.let { project.clips.indexOf(it) } ?: -1
        AlertDialog(
            onDismissRequest = { clipMenu = false },
            containerColor = VV.Raised,
            title = { Text(if (c == null) "Clip" else "Clip ${idx + 1} di ${project.clips.size}", style = MaterialTheme.typography.titleSmall) },
            text = {
                Column {
                    fun act(f: () -> Unit) { f(); clipMenu = false }
                    if (c != null) {
                        MenuRow(VVIcons.MoveLeft, "Sposta prima", enabled = idx > 0) { act { vm.moveSelected(-1) } }
                        MenuRow(VVIcons.MoveRight, "Sposta dopo", enabled = idx < project.clips.lastIndex) { act { vm.moveSelected(+1) } }
                        MenuRow(VVIcons.Copy, "Duplica") { act { vm.duplicateSelected() } }
                        MenuRow(VVIcons.Split, "Dividi all'indicatore") { act { vm.splitAtPlayhead() } }
                        if (!c.isPhoto) {
                            MenuRow(VVIcons.Pause, "Fermo immagine all'indicatore") { act { vm.freezeFrame() } }
                            MenuRow(VVIcons.Effects, "Momento migliore") { act { vm.bestMoment() } }
                            MenuRow(VVIcons.MoveLeft, if (c.reversed != null) "Di nuovo nel verso normale" else "Al contrario") { act { vm.toggleReverse() } }
                        }
                        if (!c.isPhoto && !c.muted) MenuRow(VVIcons.Volume, "Stacca l'audio") { act { vm.detachAudio() } }
                        MenuRow(VVIcons.Copy, "Copia attributi") { act { vm.copyStyle() } }
                        if (vm.copiedStyle != null && vm.copiedStyle?.id != c.id) MenuRow(VVIcons.Copy, "Incolla attributi") { act { pasteDialog = true } }
                        MenuRow(VVIcons.Check, "Seleziona più clip") { act { vm.startMultiSelect() } }
                    }
                    if (project.clips.size >= 2) {
                        MenuRow(VVIcons.Shuffle, if (vm.canShuffleAgain) "Rimescola ancora" else "Rimescola tutte le clip") { act { vm.shuffleClips(null) } }
                    }
                    MenuRow(VVIcons.Add, "Aggiungi video e foto") { act { pickVideos() } }
                    if (c != null) MenuRow(VVIcons.Delete, "Elimina", tint = VV.Danger) { act { vm.deleteSelected() } }
                }
            },
            confirmButton = { TextButton(onClick = { clipMenu = false }) { Text("Chiudi", color = VV.Dim) } },
        )
    }
    if (projectMenu) {
        AlertDialog(
            onDismissRequest = { projectMenu = false },
            containerColor = VV.Raised,
            title = { Text("Progetto", style = MaterialTheme.typography.titleSmall) },
            text = {
                Column {
                    MenuRow(VVIcons.Copy, "Salva come modello") { projectMenu = false; naming = true }
                    MenuRow(VVIcons.Export, "Copia di sicurezza (leggera)") {
                        projectMenu = false; backupWithMedia = false; backupSaver.launch(project.name.ifBlank { "progetto" } + ".vivo")
                    }
                    MenuRow(VVIcons.Export, "Copia di sicurezza con tutti i video") {
                        projectMenu = false; backupWithMedia = true; backupSaver.launch(project.name.ifBlank { "progetto" } + ".vivo")
                    }
                    if (vm.missingFiles.isNotEmpty()) MenuRow(VVIcons.Movie, "Ricollega i file spariti") { projectMenu = false; relinkOpen = true }
                    MenuRow(VVIcons.Help, "Segnala un problema") { projectMenu = false; reportOpen = true }
                    MenuRow(VVIcons.Check, "Collaudo") { projectMenu = false; vm.showTestPlan = true }
                    Hint("Leggera: il progetto e i file creati dall'app (foto importate, voce, fermi immagine, brani scaricati); i video restano nella galleria. Con tutti i video: si apre anche su un altro telefono.")
                }
            },
            confirmButton = { TextButton(onClick = { projectMenu = false }) { Text("Chiudi", color = VV.Dim) } },
        )
    }
    if (reportOpen) ReportDialog(vm, onDismiss = { reportOpen = false })
    if (naming) {
        var name by remember { mutableStateOf(project.name) }
        AlertDialog(
            onDismissRequest = { naming = false },
            containerColor = VV.Raised,
            title = { Text("Nome del modello", style = MaterialTheme.typography.titleSmall) },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
                    Hint("Il modello tiene ${project.clips.size} spazi con durate, velocità, transizioni, effetti e colori, più testi, musica e livelli. Poi basta scegliere i video.")
                }
            },
            confirmButton = { TextButton(onClick = { vm.saveTemplate(name); naming = false }) { Text("Salva", color = VV.Tally) } },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Annulla", color = VV.Dim) } },
        )
    }
    if (relinkOpen) {
        AlertDialog(
            onDismissRequest = { relinkOpen = false },
            containerColor = VV.Raised,
            title = { Text("File non trovati", style = MaterialTheme.typography.titleSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (vm.missingFiles.isEmpty()) Hint("Tutti i file sono al loro posto.")
                    vm.missingFiles.forEach { u ->
                        val info = vm.missingInfo(u)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(info.name.ifBlank { "File senza nome" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            TextButton(onClick = { relinkTarget = u; relinkPicker.launch(arrayOf("video/*", "image/*", "audio/*")) }) { Text("Scegli", color = VV.Tally) }
                        }
                    }
                    if (vm.missingFiles.isNotEmpty()) {
                        vm.relinkNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = VV.Tally) }
                        SecondaryButton("Cerca di nuovo nel telefono", Modifier.fillMaxWidth(), onClick = { vm.autoRelink() })
                        Hint("L'app cerca da sola il file per nome e per peso. Se non lo trova, tocca «Scegli» e indicalo tu: tagli, effetti e tutto il resto restano.")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { relinkOpen = false }) { Text("Chiudi", color = VV.Dim) } },
        )
    }
    if (pasteDialog) {
        vm.copiedStyle?.let { src -> PasteStyleDialog(src, targets = (vm.multiIds ?: setOfNotNull(vm.selectedClipId)).count { it != src.id }, onPaste = { vm.pasteStyle(it); pasteDialog = false }, onDismiss = { pasteDialog = false }) }
            ?: run { pasteDialog = false }
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
            Row(Modifier.fillMaxWidth()) {
                row.forEach { t -> ToolButton(t.label, t.icon(), active == t, enabled, modifier = Modifier.weight(1f)) { onClick(t) } }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ToolButton(
    label: String,
    icon: ImageVector,
    active: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    underline: Boolean = false,
    onClick: () -> Unit,
) {
    val color = when {
        !enabled -> VV.Faint
        active -> VV.Tally
        else -> VV.Text
    }
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled, onClick = onClick).padding(top = if (small) 5.dp else 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = color, modifier = Modifier.size(if (small) 20.dp else 23.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            label, fontSize = if (small) 10.sp else 11.sp, fontFamily = Plex, maxLines = 1, softWrap = false,
            fontWeight = if (active && underline) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active) VV.Tally else if (!enabled) VV.Faint else VV.Dim,
        )
        Spacer(Modifier.height(if (small) 5.dp else 5.dp))
        Box(Modifier.width(18.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(if (active && underline) VV.Tally else Color.Transparent))
    }
}

/** Barra di avanzamento sopra al video (schermo intero e orizzontale): trascinandola si sposta l'indicatore. */
@Composable
private fun SeekBar(vm: EditorViewModel, durationUs: Long) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val pos = dragging ?: (vm.playheadUs.toFloat() / durationUs).coerceIn(0f, 1f)
    androidx.compose.material3.Slider(
        value = pos,
        onValueChange = { v ->
            if (dragging == null) vm.pause()
            dragging = v
            vm.scrubTo((v * durationUs).toLong())
        },
        onValueChangeFinished = { vm.endScrub(); dragging = null },
        modifier = Modifier.fillMaxWidth().height(28.dp).semantics { contentDescription = "Avanzamento" },
        colors = androidx.compose.material3.SliderDefaults.colors(
            thumbColor = VV.Tally, activeTrackColor = VV.Tally, inactiveTrackColor = Color(0x66FFFFFF),
        ),
    )
}

/**
 * Barra del video automatico: quale proposta è, "Altra proposta" (scene, tagli ed effetti nuovi)
 * e "Stile" per rifarla con un'altra regia. Annulla torna alla proposta di prima.
 */
@Composable
private fun AutoBar(vm: EditorViewModel, enabled: Boolean) {
    val r = vm.project?.auto ?: return
    var choose by remember { mutableStateOf(false) }
    val idle = enabled && vm.busy == null
    Row(
        Modifier.fillMaxWidth().background(VV.Panel).padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(VVIcons.Effects, null, tint = VV.Tally, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("Proposta ${r.round}", style = MaterialTheme.typography.labelLarge)
            Text(r.options.style.label, style = MaterialTheme.typography.bodySmall)
        }
        Box(
            Modifier.clip(RoundedCornerShape(999.dp)).background(VV.Raised).clickable(enabled = idle) { choose = true }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) { Text("Stile", style = MaterialTheme.typography.labelLarge) }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.clip(RoundedCornerShape(999.dp)).background(if (idle) VV.Tally else VV.Raised).clickable(enabled = idle) { vm.anotherProposal() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) { Text("Altra proposta", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = if (idle) VV.TallyInk else VV.Faint) }
    }
    if (choose) {
        AlertDialog(
            onDismissRequest = { choose = false },
            containerColor = VV.Raised,
            title = { Text("Rifai con lo stile…", style = MaterialTheme.typography.titleSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AutoStyle.entries.forEach { st ->
                        val on = st == r.options.style
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (on) VV.Raised2 else VV.Panel)
                                .border(if (on) 2.dp else 0.dp, if (on) VV.Tally else Color.Transparent, RoundedCornerShape(10.dp))
                                .clickable { choose = false; vm.anotherProposal(st) }.padding(10.dp),
                        ) {
                            Text(st.label, style = MaterialTheme.typography.labelLarge, color = if (on) VV.Tally else VV.Text)
                            Text(st.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choose = false }) { Text("Chiudi", color = VV.Dim) } },
        )
    }
}

/** Crediti dei brani presi in rete, da copiare nella descrizione del video. */
@Composable
private fun CreditsBox(credits: List<String>) {
    if (credits.isEmpty()) return
    val ctx = LocalContext.current
    val text = "Musica ed effetti:\n" + credits.joinToString("\n")
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(VV.Raised).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Crediti da mettere nella descrizione del video", style = MaterialTheme.typography.labelLarge, color = VV.Tally)
        SelectionContainer { Text(credits.joinToString("\n"), style = MaterialTheme.typography.bodySmall) }
        SecondaryButton("Copia i crediti", onClick = {
            val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
            cm?.setPrimaryClip(android.content.ClipData.newPlainText("Crediti", text))
        })
    }
}

/** Schede in cima a un pannello. */
@Composable
private fun PanelTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        tabs.forEachIndexed { i, label ->
            val on = i == selected
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { onSelect(i) }.padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                    color = if (on) VV.Text else VV.Dim, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                )
                Spacer(Modifier.height(7.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).background(if (on) VV.Tally else VV.Line))
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, enabled: Boolean = true, tint: Color = VV.Text, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (enabled) tint else VV.Faint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (enabled) tint else VV.Faint)
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
    val options: List<TransitionType?> = listOf(null) + TRANSITIONS_NEW_FIRST
    PillRow(options, spec?.type, { it?.label ?: "Nessuna" }, !running) { t ->
        vm.setTransition(t?.let { TransitionSpec(it, spec?.durationUs ?: 500_000L) })
    }
    if (spec != null) {
        val ms = (spec.durationUs / 1000).toInt()
        LabeledSlider(
            "Durata", null, ms / 100, !running, range = 2..20,
            format = { String.format(java.util.Locale.ITALIAN, "%.1f s", it / 10f) },
        ) { v -> vm.setTransition(spec.copy(durationUs = v * 100_000L)) }
        val cross = spec.type == TransitionType.CROSSFADE
        val real = if (cross) p.crossfades().firstOrNull { it.index == index }?.let { it.beforeUs + it.afterUs } ?: 0L
            else p.transitionHalfUs(index) * 2
        if (real < spec.durationUs) {
            Hint(
                if (cross && real == 0L) "Non c'è video oltre il taglio da usare: allunga le clip con le maniglie (o scegli un'altra transizione)."
                else String.format(java.util.Locale.ITALIAN, if (cross) "Poco video oltre il taglio: durata effettiva %.1f s." else "Clip corte: durata effettiva %.1f s.", real / 1_000_000f)
            )
        }
        SecondaryButton("Applica a tutti i tagli", selected = p.transitionOnAllCuts(spec), onClick = { vm.setTransitionAll(spec) })
    }
    Hint(
        if (spec?.type == TransitionType.CROSSFADE) "Incrociata: le due clip si sovrappongono usando i fotogrammi prima e dopo il taglio, senza cambiare la durata del video."
        else "Metà effetto alla fine di questa clip, metà all'inizio della successiva."
    )
}

@Composable
private fun MusicPanel(vm: EditorViewModel, running: Boolean, tab: Int, onAdd: () -> Unit) {
    val p = vm.project ?: return
    val enabled = !running
    // Mixer: tutti i suoni del progetto in una schermata (volume, bilanciamento, equalizzatore, compressore, uscita).
    PrimaryButton("Mixer audio", Modifier.fillMaxWidth(), enabled = enabled) { vm.openMixer() }
    if (tab == 4) { ClipAudioTab(vm, running); return }
    if (tab == 3) { VoiceTab(vm, running); return }
    // Nessun brano selezionato: prende quello sotto l'indicatore (o il primo).
    if (vm.selectedMusic == null && p.music.isNotEmpty()) {
        LaunchedEffect(Unit) {
            val m = p.music.firstOrNull { vm.playheadUs >= it.startUs && vm.playheadUs < it.endUs } ?: p.music.first()
            vm.selectedMusicId = m.id
        }
    }
    val t = vm.selectedMusic
    if (t == null) {
        Hint("Scegli un brano dal telefono (MP3, M4A, WAV, FLAC, anche MIDI) o dalla libreria di musica libera: parte dal punto dell'indicatore e si ferma alla fine del video.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Libreria online", Modifier.weight(1f), enabled = enabled) { vm.openLibrary(effects = false) }
            SecondaryButton("Dal telefono", Modifier.weight(1f), onClick = { if (enabled) onAdd() })
        }
        SecondaryButton("Effetti sonori", Modifier.fillMaxWidth(), onClick = { if (enabled) vm.openLibrary(effects = true) })
        return
    }
    val total = p.durationUs
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(VVIcons.Music, null, tint = VV.Music, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(t.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(formatTime(t.playUs(total)), style = MaterialTheme.typography.bodySmall)
    }
    when (tab) {
        0 -> {
            if (p.music.size > 1) PillRow(p.music, t, { it.name.take(18) }, enabled) { m -> vm.selectedMusicId = m.id }
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
            PanelLabel("Voci e musica")
            ToggleRow("Abbassa la musica quando si parla", p.duck, enabled) { vm.setDuck(on = it) }
            if (p.duck) LabeledSlider("Quanto abbassarla", "In anteprima sotto la voce fuori campo; nel video esportato anche dove si parla nelle clip.", p.duckDb, enabled, range = 4..24, format = { "–$it dB" }) { vm.setDuck(db = it) }
            PanelLabel("Aggiungi")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Libreria", Modifier.weight(1f), onClick = { if (enabled) vm.openLibrary(effects = false) })
                SecondaryButton("Effetti sonori", Modifier.weight(1f), onClick = { if (enabled) vm.openLibrary(effects = true) })
            }
            SecondaryButton("Brano dal telefono", Modifier.fillMaxWidth(), onClick = { if (enabled) onAdd() })
            CreditsBox(p.credits())
            Hint("Nella timeline: tocca il brano per selezionarlo, trascinalo per spostarlo, usa le maniglie per accorciarlo. Le dissolvenze si sentono anche in anteprima.")
        }
        1 -> {
            if (t.beatsUs.isEmpty()) {
                PrimaryButton("Rileva i battiti automaticamente", Modifier.fillMaxWidth(), enabled = enabled, onClick = vm::detectBeats)
                Hint("Dai file MIDI i battiti sono già esatti. Puoi anche segnarli a mano nella scheda Battiti.")
            } else {
                Text("${t.bpm.roundToInt()} BPM · ${t.timelineBeats(total).size} battiti nel video", style = MaterialTheme.typography.bodyMedium)
                Segmented(listOf(1, 2, 4), t.beatEvery, { if (it == 1) "Tutti" else "Ogni $it" }, enabled) { n ->
                    vm.updateBeats { it.copy(beatEvery = n) }
                }
                LabeledSlider(
                    "Sposta i battiti automatici", "Se cadono tutti un po' prima o dopo il colpo, correggili qui.",
                    (t.beatShiftUs / 10_000).toInt(), enabled, range = -30..30,
                    format = { (if (it > 0) "+" else "") + "${it * 10} ms" },
                ) { v -> vm.updateBeats { it.copy(beatShiftUs = v * 10_000L) } }
            }
            if (t.hasBeats) {
                PanelLabel("Tagli a ritmo")
                SecondaryButton("Allinea i tagli al ritmo", Modifier.fillMaxWidth(), onClick = { if (enabled) vm.alignCutsToBeats() })
                SecondaryButton("Dividi i video sui battiti", Modifier.fillMaxWidth(), onClick = { if (enabled) vm.splitOnBeats() })
                ToggleRow("Aggancia i tagli ai battiti", vm.snapToBeats, enabled) { vm.snapToBeats = it }
                SecondaryButton("Cancella tutti i battiti", onClick = { if (enabled) vm.clearBeats() })
            }
        }
        else -> {
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
            if (t.hasBeats) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("◀ Battito", onClick = { vm.jumpBeat(-1) })
                    SecondaryButton("Battito ▶", onClick = { vm.jumpBeat(+1) })
                }
            }
            ToggleRow("Taglia le clip a ogni tocco", vm.tapCuts, enabled) { vm.tapCuts = it }
            if (vm.pendingCutCount > 0) Text("${vm.pendingCutCount} tagli pronti: si fanno alla pausa", style = MaterialTheme.typography.labelMedium, color = VV.Tally)
            Hint("Segna: aggiunge un battito nel punto dell'indicatore, o toglie quello che c'è già (anche automatico). Tap: avvia l'ascolto e tocca il pulsante a ogni colpo; i battiti a mano sono bianchi. Con \"Taglia le clip\" acceso, quando metti in pausa il video viene tagliato in ogni punto toccato (un solo Annulla li toglie tutti).")
        }
    }
}

/** Scheda "Clip" del pannello Audio: volume della clip sotto l'indicatore. */
/** Voce fuori campo: si registra mentre l'anteprima scorre dall'indicatore. */
@Composable
private fun VoiceTab(vm: EditorViewModel, running: Boolean) {
    val ctx = LocalContext.current
    val p = vm.project ?: return
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.startVoice() else vm.message = "Per registrare la voce serve il permesso del microfono."
    }
    Hint("Porta l'indicatore dove vuoi iniziare, tocca Registra e parla mentre il video scorre; tocca di nuovo per fermare. La voce diventa una riga nella timeline, sincronizzata.")
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (vm.recording) VV.Danger else VV.Tally)
            .clickable(enabled = !running) {
                when {
                    vm.recording -> vm.stopVoice()
                    ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED -> vm.startVoice()
                    else -> perm.launch(android.Manifest.permission.RECORD_AUDIO)
                }
            }.padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (vm.recording) "■  Ferma la registrazione  ${formatTime(vm.playheadUs)}" else "●  Registra la voce",
            style = MaterialTheme.typography.titleSmall, color = if (vm.recording) Color.White else VV.TallyInk,
        )
    }
    ToggleRow("Silenzia l'anteprima mentre registro", vm.muteWhileRecording, !vm.recording) { vm.muteWhileRecording = it }
    Hint("Con le cuffie puoi lasciare l'audio acceso e sentire il video mentre parli; senza cuffie meglio silenziarlo, così il microfono registra solo la tua voce.")
    ToggleRow("Abbassa la musica quando si parla", p.duck, !running) { vm.setDuck(on = it) }
    val voices = p.music.filter { it.dialog }
    if (voices.isNotEmpty()) {
        PanelLabel("Voci e audio staccati")
        PillRow(voices, vm.selectedMusic?.takeIf { it.dialog }, { it?.name ?: "" }, !running) { v -> if (v != null) vm.selectedMusicId = v.id }
        Hint("Selezionata una voce, nella scheda Brano ne regoli volume, dissolvenze, divisione ed eliminazione.")
    }
}

@Composable
private fun ClipAudioTab(vm: EditorViewModel, running: Boolean) {
    val p = vm.project ?: return
    if (vm.selectedClip == null) {
        LaunchedEffect(Unit) { p.locate(vm.playheadUs)?.let { (i, _) -> vm.selectedClipId = p.clips[i].id } }
    }
    val clip = vm.selectedClip
    if (clip == null) { Hint("Nessuna clip nel progetto."); return }
    // Per i video parlati: toglie silenzi e "ehm" da tutto il progetto.
    SecondaryButton("Taglia pause e ehm", Modifier.fillMaxWidth(), onClick = { if (!running) { vm.pause(); vm.speechPlan = null; vm.jumpMode = false; vm.showSpeechCut = true } })
    // Jump cut pronti: come sopra, e a ogni taglio cambia l'inquadratura (stili Pulito, Dinamico, Social).
    SecondaryButton("Jump cut", Modifier.fillMaxWidth(), onClick = { if (!running) { vm.pause(); vm.speechPlan = null; vm.jumpMode = true; vm.showSpeechCut = true } })
    if (clip.isPhoto) { Hint("Le foto non hanno audio."); return }
    ToggleRow("Disattiva l'audio della clip", clip.muted, !running) { v -> vm.updateSelected { it.copy(muted = v) } }
    LabeledSlider(
        "Volume", null, (clip.volume * 100).toInt(), enabled = !running && !clip.muted, range = 0..200,
        format = { "$it%" },
    ) { v -> vm.updateSelected { it.copy(volume = v / 100f) } }
    SecondaryButton("Stacca l'audio", Modifier.fillMaxWidth(), onClick = { if (!running) vm.detachAudio() })
    Hint("Stacca l'audio: diventa una riga a sé nella timeline; allungandola o spostandola, la voce può iniziare prima dell'immagine o continuare dopo il taglio (tagli a J e a L).")
    Hint("Tocca un'altra clip nella timeline per regolarne il volume. Nei cambi di velocità l'audio esportato mantiene il tono naturale (impostazione in Esporta).")
}

@Composable
private fun PhotoPanel(vm: EditorViewModel, running: Boolean) {
    val clip = vm.selectedClip ?: return
    val enabled = !running
    LabeledSlider(
        "Durata della foto", null, (clip.trimmedUs / 100_000).toInt(), enabled, range = 2..150,
        format = { String.format(java.util.Locale.ITALIAN, "%.1f s", it / 10f) },
    ) { v -> vm.setPhotoDuration(v * 100_000L) }
    PanelLabel("Movimento")
    var allPhotos by remember { mutableStateOf(false) }
    PillRow(MotionStyle.entries, MotionStyle.matching(clip.keyframes, clip.trimmedUs), { it?.label ?: "" }, enabled) { m -> if (m != null) vm.applyMotion(m, allPhotos) }
    ToggleRow("Applica a tutte le foto", allPhotos, enabled) { allPhotos = it }
    Hint("I movimenti usano due keyframe: li personalizzi dallo strumento Keyframe. Zoom fisso, posizione e Adatta/Riempi sono in Inquadra. Con i battiti, la maniglia destra aggancia la durata al ritmo.")
}

/** Zoom su un punto, per i tutorial: si sceglie la zona del quadro, quanto ingrandire e per quanto. */
@Composable
private fun TutorialZoom(vm: EditorViewModel, enabled: Boolean) {
    var zoom by remember { mutableStateOf(2f) }
    var hold by remember { mutableStateOf(2_500_000L) }
    PanelLabel("Zoom su un punto (tutorial)")
    Hint("Porta l'indicatore dove vuoi lo zoom e tocca la zona del quadro: l'immagine ci arriva morbida, resta e torna.")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            it.fabriziodavi.vivovideo.model.Tutorial.Spot.entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { s ->
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(VV.Raised).clickable(enabled = enabled) { vm.zoomOnSpot(s, zoom, hold) },
                            contentAlignment = Alignment.Center,
                        ) { Text(s.label, color = VV.Tally, style = MaterialTheme.typography.titleSmall) }
                    }
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PillRow(listOf(1.5f, 2f, 2.5f), zoom, { String.format(java.util.Locale.ITALIAN, "%.1f×", it).replace(",0×", "×") }, enabled) { zoom = it }
            PillRow(listOf(1_500_000L, 2_500_000L, 4_000_000L), hold, { String.format(java.util.Locale.ITALIAN, "%.1f s", it / 1e6).replace(",0 s", " s") }, enabled) { hold = it }
        }
    }
}

@Composable
private fun FramePanel(vm: EditorViewModel, running: Boolean) {
    val clip = vm.selectedClip ?: return
    val enabled = !running
    val f = clip.frame
    Segmented(FrameMode.entries, clip.frameMode, { if (it == FrameMode.BLUR) "Sfocato" else it.label }, enabled) { m ->
        vm.updateSelected { it.withMode(m) }
    }
    if (clip.frameMode == FrameMode.BLUR) Hint("Sfondo sfocato: l'immagine intera, con dietro una sua copia sfocata al posto delle bande nere.")
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
        SecondaryButton("Reimposta", onClick = { if (enabled) { vm.setFrame(Transform()); vm.project?.let { p -> vm.updateSelected { it.withMode(p.presetModeFor(it)) } } } })
    }
    SecondaryButton("Applica a tutte le clip", selected = vm.project?.frameOnAll(clip) == true, onClick = { if (enabled) vm.applyFrameToAll() })
    TutorialZoom(vm, enabled)
    PanelLabel("Segui il soggetto")
    if (clip.reframe == null) {
        SecondaryButton("Segui il soggetto", Modifier.fillMaxWidth(), onClick = { if (enabled && vm.clipWork == null) vm.followSubject() })
        Hint("Riempie il quadro e sposta l'inquadratura per tenere al centro volti o soggetto: perfetto per mettere un video orizzontale in un progetto verticale.")
    } else {
        Hint(if (clip.fill) "L'inquadratura segue il soggetto." else "Per seguire il soggetto serve \"Riempi\" (in alto).")
        SecondaryButton("Smetti di seguire", onClick = { if (enabled) vm.stopFollow() })
    }
    if (!clip.isPhoto) {
        PanelLabel("Stabilizzazione")
        val st = clip.stab
        if (st == null) {
            SecondaryButton("Stabilizza la clip", Modifier.fillMaxWidth(), onClick = { if (enabled && vm.clipWork == null) vm.stabilize() })
            Hint("Misura il tremolio della camera e lo leviga, con un leggero zoom per nascondere i bordi. Video lunghi o in 4K/8K richiedono un po' di tempo.")
        } else {
            LabeledSlider("Forza", null, (st.strength * 100).toInt(), enabled, range = 0..100, format = { "$it%" }) { vm.setStabStrength(it / 100f) }
            Hint("Zoom usato: ${"%.0f".format((StabMath.plan(st).zoom - 1) * 100)}%. Più forza = movimento più fermo, ma più zoom.")
            SecondaryButton("Togli la stabilizzazione", selected = false, onClick = { if (enabled) vm.removeStab() })
        }
    }
}

private val TEXT_COLORS = listOf(
    0xFFFFFFFF, 0xFF111111, 0xFFF2B33D, 0xFFFFE14D, 0xFFFF5A4E, 0xFFFF7AB6, 0xFF5BC0FF, 0xFF4ADE80, 0xFFB794F6,
).map { it.toInt() }

@Composable
private fun TextPanel(vm: EditorViewModel, running: Boolean, tab: Int, onPickFont: () -> Unit = {}) {
    val p = vm.project ?: return
    val t = vm.selectedText
    val enabled = !running
    if (t == null) {
        if (p.texts.isNotEmpty()) {
            PanelLabel("Testi nel progetto")
            PillRow(p.texts, null as TextItem?, { it?.text?.lines()?.firstOrNull()?.take(18) ?: "" }, enabled) { x -> if (x != null) vm.selectText(x.id) }
        }
        PrimaryButton("Aggiungi testo", Modifier.fillMaxWidth(), enabled = enabled, onClick = vm::addText)
        SecondaryButton("Sottotitoli automatici", Modifier.fillMaxWidth(), onClick = { if (enabled) { vm.pause(); vm.showSubtitles = true } })
        SecondaryButton(if (p.creditsRoll != null) "Titoli di coda · modifica" else "Titoli di coda", Modifier.fillMaxWidth(), onClick = { if (enabled) { vm.pause(); vm.showCredits = true } })
        SecondaryButton("Passo numerato (tutorial)", Modifier.fillMaxWidth(), onClick = { if (enabled) vm.addStep() })
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
    when (tab) {
        0 -> {
            PanelLabel("Stili pronti")
            PillRow(PRESETS_NEW_FIRST, TextPreset.matching(t), { it?.label ?: "" }, enabled) { pr -> if (pr != null) vm.applyTextPreset(pr) }
            PanelLabel("Carattere")
            PillRow(TextFont.entries.map<TextFont, TextFont?> { it }, if (t.fontFile == null) t.font else null, { it?.label ?: "" }, enabled) { f -> if (f != null) vm.updateText { it.copy(font = f, fontFile = null) } }
            PanelLabel("I tuoi font")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                vm.fonts.forEach { path ->
                    SecondaryButton(File(path).nameWithoutExtension.take(18), selected = t.fontFile == path, onClick = { if (enabled) vm.updateText { it.copy(fontFile = path) } })
                }
                SecondaryButton("+ Aggiungi font", onClick = { if (enabled) onPickFont() })
            }
            if (t.fontFile != null) {
                TextButton(onClick = { t.fontFile?.let { vm.removeFont(it) } }) { Text("Togli questo font dall'app", color = VV.Danger) }
            }
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
            PanelLabel("Sfumatura verso")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.clip(RoundedCornerShape(15.dp)).background(if (t.gradientTo == null) VV.Tally else VV.Raised)
                        .clickable(enabled = enabled) { vm.updateText { it.copy(gradientTo = null) } }.padding(horizontal = 12.dp, vertical = 6.dp)
                ) { Text("Nessuna", style = MaterialTheme.typography.labelMedium, color = if (t.gradientTo == null) VV.TallyInk else VV.Text) }
                TEXT_COLORS.forEach { c ->
                    Box(
                        Modifier.size(30.dp).clip(RoundedCornerShape(15.dp)).background(Color(c))
                            .border(if (c == t.gradientTo) 3.dp else 1.dp, if (c == t.gradientTo) VV.Tally else VV.Line, RoundedCornerShape(15.dp))
                            .clickable(enabled = enabled) { vm.updateText { it.copy(gradientTo = c) } }
                    )
                }
            }
            PanelLabel("Contorno e ombra")
            Segmented(TextLook.entries, t.look, { it.label }, enabled) { l -> vm.updateText { it.copy(look = l) } }
            LabeledSlider("Dimensione", null, (t.size * 1000).toInt(), enabled, range = 15..250, format = { "${it / 10}" }) { v ->
                vm.updateText(typing = true) { it.copy(size = v / 1000f) }
            }
            LabeledSlider("Spaziatura lettere", null, (t.spacing * 100).toInt(), enabled, range = -5..60) { v ->
                vm.updateText(typing = true) { it.copy(spacing = v / 100f) }
            }
            ToggleRow("Tutto maiuscolo", t.upper, enabled) { v -> vm.updateText { it.copy(upper = v) } }
        }
        1 -> {
            PanelLabel("In entrata")
            PillRow(ANIMS_NEW_FIRST, t.animIn, { it.label }, enabled) { a -> vm.updateText { it.copy(animIn = a) } }
            PanelLabel("In uscita")
            PillRow(ANIMS_NEW_FIRST.filter { !it.inOnly }, t.animOut, { it.label }, enabled) { a -> vm.updateText { it.copy(animOut = a) } }
        }
        else -> {
            PanelLabel("Posizione")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Alto", selected = t.x == 0f && t.y == -0.36f, onClick = { vm.updateText { it.copy(x = 0f, y = -0.36f) } })
                SecondaryButton("Centro", selected = t.x == 0f && t.y == 0f, onClick = { vm.updateText { it.copy(x = 0f, y = 0f) } })
                SecondaryButton("Basso", selected = t.x == 0f && t.y == 0.36f, onClick = { vm.updateText { it.copy(x = 0f, y = 0.36f) } })
            }
            Hint("Oppure trascina, pizzica e ruota il riquadro sull'anteprima con le dita.")
            MotionSection(vm, t.endPose != null, enabled, t.startUs, t.durationUs) { vm.toggleTextMotion() }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("Inizia qui", Modifier.weight(1f), onClick = { if (enabled) vm.textToPlayhead(start = true) })
        SecondaryButton("Finisci qui", Modifier.weight(1f), onClick = { if (enabled) vm.textToPlayhead(start = false) })
    }
    Hint("Inizia qui sposta il testo all'indicatore; Finisci qui lo fa finire lì. Nella timeline: tocca il testo, poi trascinalo; le maniglie ai lati cambiano la durata.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("Nuovo", onClick = { if (enabled) vm.addText() })
        SecondaryButton("Duplica", onClick = { if (enabled) vm.duplicateText() })
        SecondaryButton("Elimina", onClick = { if (enabled) vm.deleteText() })
    }
    SecondaryButton("Stile a tutti i testi", selected = p.textStyleOnAll(t), onClick = { if (enabled) vm.applyTextStyleToAll() })
    SecondaryButton("Sottotitoli automatici", Modifier.fillMaxWidth(), onClick = { if (enabled) { vm.pause(); vm.showSubtitles = true } })
    SecondaryButton(if (p.creditsRoll != null) "Titoli di coda · modifica" else "Titoli di coda", Modifier.fillMaxWidth(), onClick = { if (enabled) { vm.pause(); vm.showCredits = true } })
}

/**
 * Riquadro tratteggiato sopra all'anteprima per un elemento posizionabile (testo, immagine):
 * si trascina, si pizzica (misura) e si ruota con le dita. Le misure sono in pixel del fotogramma.
 */
@Composable
private fun PoseHandleOverlay(
    key: Any,
    frameW: Int,
    frameH: Int,
    boxW: Float,
    boxH: Float,
    x: Float,
    y: Float,
    rotation: Float,
    onGesture: (dx: Float, dy: Float, zoom: Float, rot: Float) -> Unit,
) {
    var live by remember { mutableStateOf(LiveTransform()) }
    val onG by rememberUpdatedState(onGesture)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val aspect = frameW.toFloat() / frameH
        val vw = minOf(wPx, hPx * aspect)
        val vh = vw / aspect
        val k = vw / frameW
        val cx = wPx / 2 + x * vw + live.pan.x
        val cy = hPx / 2 + y * vh + live.pan.y
        val bw = boxW * k * live.zoom
        val bh = boxH * k * live.zoom
        Canvas(Modifier.fillMaxSize()) {
            rotate(rotation + live.rotation, Offset(cx, cy)) {
                drawRect(
                    VV.Tally, topLeft = Offset(cx - bw / 2, cy - bh / 2), size = androidx.compose.ui.geometry.Size(bw, bh),
                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
            }
        }
        Box(
            Modifier.fillMaxSize().pointerInput(key, vw) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var zoom = 1f; var pan = Offset.Zero; var rot = 0f
                    do {
                        val ev = awaitPointerEvent()
                        zoom *= ev.calculateZoom(); pan += ev.calculatePan(); rot += ev.calculateRotation()
                        live = LiveTransform(zoom, pan, rot)
                        ev.changes.forEach { it.consume() }
                    } while (ev.changes.any { it.pressed })
                    if (zoom != 1f || pan != Offset.Zero || rot != 0f) onG(pan.x / vw, pan.y / vh, zoom, rot)
                    live = LiveTransform()
                }
            }
        )
    }
}

private fun wrapDeg(d: Float) = ((d + 540f) % 360f) - 180f

/** Riquadro del testo selezionato (posizione iniziale o finale del movimento). */
@Composable
private fun TextHandleOverlay(vm: EditorViewModel, t: TextItem) {
    val context = LocalContext.current
    val (outW, outH) = vm.previewFrameSize()
    val (bw, bh) = remember(t.text, t.font, t.look, t.size, outW, outH) { TextRenderer.measure(context, t, outW, outH) }
    val end = if (vm.editEnd) t.endPose else null
    val mul = end?.scale ?: 1f
    PoseHandleOverlay(
        key = t.id to (end != null), frameW = outW, frameH = outH,
        boxW = bw * mul, boxH = bh * mul,
        x = end?.x ?: t.x, y = end?.y ?: t.y, rotation = end?.rotation ?: t.rotation,
    ) { dx, dy, zoom, rot ->
        vm.updateText { cur ->
            val e = cur.endPose
            if (vm.editEnd && e != null) cur.copy(endPose = e.copy(
                x = (e.x + dx).coerceIn(-0.5f, 0.5f), y = (e.y + dy).coerceIn(-0.5f, 0.5f),
                scale = (e.scale * zoom).coerceIn(0.2f, 5f), rotation = wrapDeg(e.rotation + rot),
            )) else cur.copy(
                x = (cur.x + dx).coerceIn(-0.5f, 0.5f), y = (cur.y + dy).coerceIn(-0.5f, 0.5f),
                size = (cur.size * zoom).coerceIn(0.015f, 0.25f), rotation = wrapDeg(cur.rotation + rot),
            )
        }
    }
}

/** Riquadro dell'immagine selezionata (posizione iniziale o finale del movimento). */
@Composable
private fun LayerHandleOverlay(vm: EditorViewModel, l: Layer) {
    val (outW, outH) = vm.previewFrameSize()
    val pose = vm.editedLayerPose() ?: l.pose
    val (cw, ch) = LayerRenderer.containSize(l, outW, outH)
    PoseHandleOverlay(
        key = l.id to vm.editEnd, frameW = outW, frameH = outH,
        boxW = cw * pose.scale, boxH = ch * pose.scale, x = pose.x, y = pose.y, rotation = pose.rotation,
    ) { dx, dy, zoom, rot ->
        val p = vm.editedLayerPose() ?: return@PoseHandleOverlay
        vm.setLayerPose(p.copy(
            x = (p.x + dx).coerceIn(-1f, 1f), y = (p.y + dy).coerceIn(-1f, 1f),
            scale = (p.scale * zoom).coerceIn(0.05f, 1.5f), rotation = wrapDeg(p.rotation + rot),
        ))
    }
}

/** Interruttore del movimento e scelta della posizione da modificare (inizio/fine), come in ProShow. */
@Composable
private fun MotionSection(vm: EditorViewModel, on: Boolean, enabled: Boolean, startUs: Long, durationUs: Long, onToggle: () -> Unit) {
    PanelLabel("Movimento")
    ToggleRow("Muovi da una posizione a un'altra", on, enabled) { onToggle() }
    if (on) {
        Segmented(listOf(false, true), vm.editEnd, { if (it) "Posizione finale" else "Posizione iniziale" }, enabled) { e ->
            vm.showPoseEnd(e, startUs, durationUs)
        }
        Hint("Scegli quale posizione modificare e sistemala con le dita sull'anteprima: il movimento va dall'una all'altra in modo morbido.")
    }
}

@Composable
private fun LayerPanel(vm: EditorViewModel, running: Boolean, tab: Int, onAdd: () -> Unit) {
    val p = vm.project ?: return
    val l = vm.selectedLayer
    val enabled = !running
    if (l == null) {
        if (p.layers.isNotEmpty()) {
            PanelLabel("Livelli nel progetto")
            PillRow(p.layers, null as Layer?, { it?.name?.take(18) ?: "" }, enabled) { x -> if (x != null) vm.selectLayer(x.id) }
        }
        PrimaryButton("Aggiungi immagine o video", Modifier.fillMaxWidth(), enabled = enabled, onClick = onAdd)
        Hint("Foto, loghi, adesivi PNG con trasparenza o altri video (picture-in-picture) sopra al video, dall'indicatore. Nella timeline li sposti e ne cambi la durata con le maniglie.")
        PanelLabel("Evidenziazioni per tutorial")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            it.fabriziodavi.vivovideo.model.Tutorial.Mark.entries.forEach { m ->
                SecondaryButton(m.label, onClick = { if (enabled) vm.addTutorialMark(m) })
            }
        }
        Hint("Compaiono al centro dall'indicatore per 3 s: trascinale dove serve, pizzica per la misura, ruota la freccia con due dita. I numeri vanno avanti da soli (1, 2, 3…).")
        return
    }
    val pose = vm.editedLayerPose() ?: l.pose
    Text(l.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("Inizia qui", Modifier.weight(1f), onClick = { if (enabled) vm.layerToPlayhead(start = true) })
        SecondaryButton("Finisci qui", Modifier.weight(1f), onClick = { if (enabled) vm.layerToPlayhead(start = false) })
    }
    when (tab) {
        0 -> {
            Hint("Trascina, pizzica e ruota il riquadro sull'anteprima con due dita.")
            LabeledSlider("Dimensione", null, (pose.scale * 100).toInt(), enabled, range = 5..150, format = { "$it%" }) {
                vm.setLayerPose(pose.copy(scale = it / 100f))
            }
            LabeledSlider("Opacità", null, (pose.opacity * 100).toInt(), enabled, format = { "$it%" }) {
                vm.setLayerPose(pose.copy(opacity = it / 100f))
            }
            LabeledSlider("Rotazione", null, pose.rotation.toInt(), enabled, range = -180..180, format = { "$it°" }) {
                vm.setLayerPose(pose.copy(rotation = it.toFloat()))
            }
            if (l.isVideo) {
                Hint("In anteprima il video sovrapposto si vede come immagine ferma; nel video esportato si muove.")
                PanelLabel("Audio del video sovrapposto")
                ToggleRow("Disattiva l'audio", l.muted, enabled) { v -> vm.updateLayer { it.copy(muted = v) } }
                if (!l.muted) LabeledSlider("Volume", null, (l.volume * 100).toInt(), enabled, range = 0..200, format = { "$it%" }) { v ->
                    vm.updateLayer { it.copy(volume = v / 100f) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Porta sopra", onClick = { if (enabled) vm.moveLayerZ(+1) })
                SecondaryButton("Porta sotto", onClick = { if (enabled) vm.moveLayerZ(-1) })
            }
        }
        1 -> {
            PanelLabel("Forma")
            PillRow(LayerMask.entries, l.shape, { it.label }, enabled) { m -> vm.updateLayer { it.copy(mask = m, rounded = false) } }
            PanelLabel(if (l.isVideo) "Cornice" else "Cornice e ombra")
            Segmented(LayerBorder.entries, l.border, { it.label }, enabled) { b -> vm.updateLayer { it.copy(border = b) } }
            if (!l.isVideo) ToggleRow("Ombra", l.shadow, enabled) { v -> vm.updateLayer { it.copy(shadow = v) } }
            if (l.isVideo) {
                PanelLabel("Solo la persona")
                if (l.person == null) {
                    PersonQuality(vm, enabled)
                    SecondaryButton("Scontorna la persona", Modifier.fillMaxWidth(), onClick = { if (enabled && vm.clipWork == null) vm.cutLayerPerson() })
                    Hint("Toglie lo sfondo dietro alla persona, senza telo verde: resta solo lei sopra al video principale.")
                } else {
                    Hint("Si vede solo la persona.")
                    SecondaryButton("Rimetti lo sfondo", onClick = { if (enabled) vm.updateLayer { it.copy(person = null) } })
                }
            }
            PanelLabel("Scontorno (togli lo sfondo)")
            PillRow(KeyColor.entries, l.key, { it.label }, enabled) { k -> vm.updateLayer { it.copy(key = k) } }
            if (l.key != KeyColor.NONE) {
                LabeledSlider("Tolleranza", "Alzala se resta un alone di sfondo, abbassala se sparisce anche il soggetto.", (l.keyTolerance * 100).toInt(), enabled, format = { "$it%" }) { v ->
                    vm.updateLayer { it.copy(keyTolerance = v / 100f) }
                }
            }
        }
        else -> {
            MotionSection(vm, l.endPose != null, enabled, l.startUs, l.durationUs) { vm.toggleLayerMotion() }
            PanelLabel("Animazione in entrata")
            PillRow(LAYER_ANIMS, l.animIn, { it.label }, enabled) { a -> vm.updateLayer { it.copy(animIn = a) } }
            PanelLabel("Animazione in uscita")
            PillRow(LAYER_ANIMS, l.animOut, { it.label }, enabled) { a -> vm.updateLayer { it.copy(animOut = a) } }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("Aggiungi", onClick = { if (enabled) onAdd() })
        SecondaryButton("Duplica", onClick = { if (enabled) vm.duplicateLayer() })
        SecondaryButton("Elimina", onClick = { if (enabled) vm.deleteLayer() })
    }
}

/** Formato del video e preimpostazioni per foto e video verticali/orizzontali. */
@Composable
private fun FormatPanel(vm: EditorViewModel, running: Boolean) {
    val p = vm.project ?: return
    val enabled = !running
    PanelLabel("Formato del video")
    Segmented(VideoFormat.entries, p.format, { if (it == VideoFormat.AUTO) "Auto" else it.short }, enabled) { vm.setFormat(format = it) }
    Hint(
        when (p.format) {
            VideoFormat.AUTO -> "Automatico: come la prima clip (ora " + (if (p.outputOrientation() == Orientation.PORTRAIT) "verticale" else if (p.outputOrientation() == Orientation.SQUARE) "quadrato" else "orizzontale") + ")."
            VideoFormat.VERTICAL -> "Verticale 9:16: per storie, reel e telefono."
            VideoFormat.HORIZONTAL -> "Orizzontale 16:9: per TV, computer e YouTube."
            VideoFormat.SQUARE -> "Quadrato 1:1: per i post."
        }
    )
    PanelLabel("Foto e video con lo stesso orientamento")
    Segmented(FrameMode.entries, p.sameMode, { if (it == FrameMode.BLUR) "Sfocato" else it.label }, enabled) { vm.setFormat(same = it) }
    PanelLabel("Foto e video con l'orientamento opposto")
    Segmented(FrameMode.entries, p.otherMode, { if (it == FrameMode.BLUR) "Sfocato" else it.label }, enabled) { vm.setFormat(other = it) }
    val v = p.clips.count { it.orientation == Orientation.PORTRAIT }
    val o = p.clips.count { it.orientation == Orientation.LANDSCAPE }
    Hint("Nel progetto: $v verticali, $o orizzontali. Riempi taglia i bordi, Adatta mostra tutto con bande nere, Sfocato mostra tutto con dietro una copia sfocata.")
    SecondaryButton("Applica a tutte le clip", Modifier.fillMaxWidth(), selected = p.followsPresets(), onClick = { if (enabled) vm.setFormat() })
    Hint("Le preimpostazioni valgono anche per le foto e i video che aggiungi dopo. Una singola clip la cambi in Inquadra.")
}

@Composable
private fun SlideshowPanel(vm: EditorViewModel, running: Boolean) {
    val p = vm.project ?: return
    val enabled = !running
    val hasBeats = p.beatTimes().isNotEmpty()
    var seconds by remember { mutableStateOf(30) }          // decimi di secondo
    var rhythm by remember { mutableStateOf(hasBeats) }
    var beats by remember { mutableStateOf(4) }
    var motion by remember { mutableStateOf<MotionStyle?>(null) }
    var transition by remember { mutableStateOf<TransitionType?>(null) }
    var noTransitions by remember { mutableStateOf(false) }
    var fill by remember { mutableStateOf(true) }
    Hint("Prepara in un colpo tutte le foto del progetto (${p.clips.count { it.isPhoto }}): durata, movimento e transizioni. Si può annullare.")
    PanelLabel("Durata di ogni foto")
    if (hasBeats) ToggleRow("A ritmo della musica", rhythm, enabled) { rhythm = it }
    if (rhythm && hasBeats) {
        Segmented(listOf(1, 2, 4, 8), beats, { "$it battit" + if (it == 1) "o" else "i" }, enabled) { beats = it }
    } else {
        LabeledSlider("Secondi", null, seconds, enabled, range = 10..100, format = { String.format(java.util.Locale.ITALIAN, "%.1f s", it / 10f) }) { seconds = it }
        if (!hasBeats) Hint("Con una musica e i suoi battiti, le foto possono cambiare a tempo.")
    }
    PanelLabel("Movimento delle foto")
    val motions: List<MotionStyle?> = listOf(null) + MotionStyle.entries
    PillRow(motions, motion, { it?.label ?: "Vario" }, enabled) { motion = it }
    PanelLabel("Transizioni")
    val trans: List<TransitionType?> = listOf(null) + TRANSITIONS_NEW_FIRST
    PillRow(listOf<Any?>("none") + trans, if (noTransitions) "none" else transition, { a ->
        when (a) { "none" -> "Nessuna"; null -> "Varie"; else -> (a as TransitionType).label }
    }, enabled) { a ->
        noTransitions = a == "none"
        transition = a as? TransitionType
    }
    ToggleRow("Foto a tutto schermo (riempi il quadro)", fill, enabled) { fill = it }
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(VV.Tally)
            .clickable(enabled = enabled) {
                vm.createSlideshow(SlideshowOptions(
                    photoUs = seconds * 100_000L,
                    beatsPerPhoto = if (rhythm && hasBeats) beats else 0,
                    motion = motion, transition = transition, noTransitions = noTransitions, fill = fill,
                ))
            }.padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) { Text("Crea slideshow", style = MaterialTheme.typography.labelLarge, color = VV.TallyInk) }
}

@Composable
private fun EffectsPanel(vm: EditorViewModel, running: Boolean, tab: Int) {
    val clip = vm.selectedClip ?: return
    val p = vm.project ?: return
    val enabled = !running
    if (tab == 0) { FxTab(vm, clip, p, enabled); return }
    if (tab == 2) { PersonTab(vm, clip, enabled); return }
    if (tab == 3) { PrivacyTab(vm, clip, enabled); return }
    val secs = { v: Int -> if (v == 0) "No" else String.format(java.util.Locale.ITALIAN, "%.1f s", v / 10f) }
    val maxTenths = (clip.outputUs / 2 / 100_000).toInt().coerceIn(1, 50)
    PanelLabel("Dissolvenze della clip")
    LabeledSlider("In entrata", null, (clip.fadeInUs / 100_000).toInt().coerceAtMost(maxTenths), enabled, range = 0..maxTenths, format = secs) { v ->
        vm.updateSelected { it.copy(fadeInUs = v * 100_000L) }
    }
    LabeledSlider("In uscita", null, (clip.fadeOutUs / 100_000).toInt().coerceAtMost(maxTenths), enabled, range = 0..maxTenths, format = secs) { v ->
        vm.updateSelected { it.copy(fadeOutUs = v * 100_000L) }
    }
    if (clip.fadeInUs > 0 || clip.fadeOutUs > 0) {
        Segmented(FadeColor.entries, clip.fadeColor, { "Dal/al " + it.label.lowercase() }, enabled) { c -> vm.updateSelected { it.copy(fadeColor = c) } }
        if (!clip.isPhoto) ToggleRow("Anche l'audio (nel video esportato)", clip.fadeAudio, enabled) { v -> vm.updateSelected { it.copy(fadeAudio = v) } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("Apri e chiudi il video", selected = p.videoEndsFaded(), onClick = { if (enabled) vm.toggleVideoEnds() })
        if (clip.fadeInUs > 0 || clip.fadeOutUs > 0) SecondaryButton("A tutte le clip", selected = p.fadesOnAll(clip), onClick = { if (enabled) vm.applyFadesToAll() })
    }
    Hint("\"Apri e chiudi il video\": dissolvenza dal nero sulla prima clip (1 s) e al nero sull'ultima (1,5 s). Acceso è in ambra; toccalo di nuovo per toglierle.")
}

@Composable
private fun FxTab(vm: EditorViewModel, clip: it.fabriziodavi.vivovideo.model.Clip, p: it.fabriziodavi.vivovideo.model.Project, enabled: Boolean) {
    var category by remember { mutableStateOf(FxCategory.TREND) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FxCategory.entries.forEach { cat ->
            val on = cat == category
            val count = clip.fx.count { if (cat == FxCategory.TREND) it.type.trendy else it.type.category == cat }
            Box(
                Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) VV.Raised2 else VV.Canvas)
                    .border(1.dp, if (on) VV.Tally else VV.Line, RoundedCornerShape(10.dp))
                    .clickable { category = cat }.padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(cat.label + if (count > 0) " · $count" else "", style = MaterialTheme.typography.labelMedium, color = if (on) VV.Tally else VV.Dim)
            }
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FxType.entries.filter { if (category == FxCategory.TREND) it.trendy else it.category == category }.forEach { t ->
            val on = clip.fx.any { it.type == t }
            Box(
                Modifier.clip(RoundedCornerShape(18.dp)).background(if (on) VV.Tally else VV.Raised)
                    .clickable(enabled = enabled) { vm.toggleFx(t) }.padding(horizontal = 14.dp, vertical = 9.dp)
            ) { Text(t.label, style = MaterialTheme.typography.labelLarge, color = if (on) VV.TallyInk else VV.Text) }
        }
    }
    clip.fx.filter { !it.type.toggle }.forEach { f ->
        LabeledSlider(f.type.label, null, (f.amount * 100).toInt(), enabled, format = { "$it%" }) { v -> vm.setFxAmount(f.type, v / 100f) }
    }
    if (clip.fx.any { it.type.onBeats } && p.beatTimes().isEmpty()) {
        Hint("\"Pulsa a ritmo\", \"Lampi a ritmo\" e \"Colpo a ritmo\" usano i battiti della musica: aggiungi un brano e rileva i battiti dal pannello Musica.")
    }
    if (clip.fx.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Applica a tutte le clip", selected = p.fxOnAll(clip), onClick = { if (enabled) vm.applyFxToAll() })
            SecondaryButton("Togli tutti", onClick = { if (enabled) vm.updateSelected { it.copy(fx = emptyList()) } })
        }
    } else {
        Hint("Tocca un effetto per accenderlo o spegnerlo; si possono combinare anche di categorie diverse.")
    }
}

@Composable
private fun ColorPanel(vm: EditorViewModel, running: Boolean, tab: Int, onPickLut: () -> Unit) {
    val p = vm.project ?: return
    val c = p.color
    val enabled = !running
    if (tab == 3) {
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
        return
    }
    // Le schede della clip lavorano sulla clip selezionata (o su quella sotto l'indicatore).
    if (vm.selectedClip == null) {
        LaunchedEffect(Unit) { p.locate(vm.playheadUs)?.let { (i, _) -> vm.selectedClipId = p.clips[i].id } }
    }
    val clip = vm.selectedClip ?: return
    val g = clip.grade
    Histogram(vm, clip)
    fun set(change: (Grade) -> Grade) { vm.updateSelected { it.copy(grade = change(it.grade)) } }
    fun pct(v: Float) = (v * 100).roundToInt()
    val signed = { v: Int -> if (v > 0) "+$v" else "$v" }
    when (tab) {
        0 -> {
            LabeledSlider("Esposizione", null, pct(g.exposure), enabled, range = -200..200,
                format = { String.format(java.util.Locale.ITALIAN, "%+.1f EV", it / 100f) }) { v -> set { it.copy(exposure = v / 100f) } }
            LabeledSlider("Contrasto", null, pct(g.contrast), enabled, range = -100..100, format = signed) { v -> set { it.copy(contrast = v / 100f) } }
            LabeledSlider("Alte luci", null, pct(g.highlights), enabled, range = -100..100, format = signed) { v -> set { it.copy(highlights = v / 100f) } }
            LabeledSlider("Ombre", null, pct(g.shadows), enabled, range = -100..100, format = signed) { v -> set { it.copy(shadows = v / 100f) } }
            LabeledSlider("Temperatura", "Verso il blu (–) o verso l'arancio (+).", pct(g.temperature), enabled, range = -100..100, format = signed) { v -> set { it.copy(temperature = v / 100f) } }
            LabeledSlider("Tinta", "Verso il verde (–) o verso il magenta (+).", pct(g.tint), enabled, range = -100..100, format = signed) { v -> set { it.copy(tint = v / 100f) } }
            LabeledSlider("Saturazione", null, pct(g.saturation), enabled, range = -100..100, format = signed) { v -> set { it.copy(saturation = v / 100f) } }
            LabeledSlider("Vividezza", "Ravviva i colori spenti lasciando com'è la pelle.", pct(g.vibrance), enabled, range = -100..100, format = signed) { v -> set { it.copy(vibrance = v / 100f) } }
        }
        1 -> {
            Hint("Trascina il punto verso un colore per spingere ombre, toni medi o luci in quella direzione; più lontano dal centro, più forte.")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ColorWheel("Ombre", g.shadowWheel, enabled) { w -> set { it.copy(shadowWheel = w) } }
                ColorWheel("Toni medi", g.midWheel, enabled) { w -> set { it.copy(midWheel = w) } }
                ColorWheel("Luci", g.highWheel, enabled) { w -> set { it.copy(highWheel = w) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Look cinema", Modifier.weight(1f), onClick = {
                    if (enabled) set { it.copy(shadowWheel = Wheel(190f, 0.5f), highWheel = Wheel(30f, 0.4f), contrast = maxOf(it.contrast, 0.15f)) }
                })
                SecondaryButton("Azzera ruote", Modifier.weight(1f), onClick = { if (enabled) set { it.copy(shadowWheel = Wheel(), midWheel = Wheel(), highWheel = Wheel()) } })
            }
        }
        else -> {
            var band by remember { mutableStateOf(HslBand.ORANGE) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HslBand.entries.forEach { b ->
                    val on = b == band
                    val changed = g.band(b) != Hsl()
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(Color(b.argb))
                            .border(if (on) 3.dp else if (changed) 2.dp else 0.dp, if (on) VV.Text else VV.Tally, CircleShape)
                            .clickable { band = b },
                    )
                }
            }
            val h = g.band(band)
            Text(band.label, style = MaterialTheme.typography.labelLarge, color = VV.Tally)
            LabeledSlider("Tonalità", null, pct(h.hue), enabled, range = -100..100, format = signed) { v -> set { it.withBand(band, h.copy(hue = v / 100f)) } }
            LabeledSlider("Saturazione", null, pct(h.sat), enabled, range = -100..100, format = signed) { v -> set { it.withBand(band, h.copy(sat = v / 100f)) } }
            LabeledSlider("Luminosità", null, pct(h.lum), enabled, range = -100..100, format = signed) { v -> set { it.withBand(band, h.copy(lum = v / 100f)) } }
            Hint("Cambia un solo colore: per esempio il cielo più blu (Blu: saturazione +) o il verde dei prati meno acceso, senza toccare il resto.")
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("Copia", Modifier.weight(1f), onClick = { vm.copiedGrade = g })
        SecondaryButton("Incolla", Modifier.weight(1f), onClick = { vm.copiedGrade?.let { cg -> if (enabled) set { cg } } })
        SecondaryButton("Reimposta", Modifier.weight(1f), onClick = { if (enabled) set { Grade() } })
    }
    SecondaryButton("Applica a tutte le clip", Modifier.fillMaxWidth(), selected = p.clips.size > 1 && p.clips.all { it.grade == g }, onClick = { if (enabled) vm.applyGradeToAll() })
}

/** Istogramma del fotogramma sotto l'indicatore, già corretto: luminosità (grigio) e canali rosso, verde, blu. */
@Composable
private fun Histogram(vm: EditorViewModel, clip: it.fabriziodavi.vivovideo.model.Clip) {
    val ctx = LocalContext.current
    val pixels by androidx.compose.runtime.produceState<IntArray?>(null, clip.id, clip.trimStartUs, vm.playheadUs / 500_000) {
        value = vm.sampleFrame(ctx, clip)
    }
    val px = pixels
    val bins = remember(px, clip.grade) { px?.let { GradeMath.histogram(it, clip.grade) } }
    Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0B0C0F))) {
        if (bins == null) {
            Text("Istogramma", style = MaterialTheme.typography.labelSmall, color = VV.Faint, modifier = Modifier.align(Alignment.Center))
        } else {
            Canvas(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 4.dp)) {
                val n = bins[0].size
                val maxV = (bins.flatMap { it.toList() }.sortedDescending().getOrNull(n / 8) ?: 1).coerceAtLeast(1).toFloat()
                fun path(b: IntArray): Path = Path().apply {
                    moveTo(0f, size.height)
                    for (i in 0 until n) lineTo(i * size.width / (n - 1), size.height - (b[i] / maxV).coerceAtMost(1f) * size.height)
                    lineTo(size.width, size.height); close()
                }
                drawPath(path(bins[0]), Color(0x55FFFFFF))
                drawPath(path(bins[1]), Color(0xFFFF4D4D), style = Stroke(1.2.dp.toPx()))
                drawPath(path(bins[2]), Color(0xFF4DFF6A), style = Stroke(1.2.dp.toPx()))
                drawPath(path(bins[3]), Color(0xFF4D8BFF), style = Stroke(1.2.dp.toPx()))
            }
            val total = bins[0].sum().coerceAtLeast(1)
            val clipHi = bins[0].last() * 100 / total
            val clipLo = bins[0].first() * 100 / total
            if (clipHi >= 3 || clipLo >= 3) {
                Text(
                    listOfNotNull(if (clipHi >= 3) "Luci bruciate $clipHi%" else null, if (clipLo >= 3) "Ombre chiuse $clipLo%" else null).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = VV.Tally,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            }
        }
    }
}

/** Ruota colore: il punto indica la direzione (tinta) e la forza della spinta. */
@Composable
private fun ColorWheel(label: String, value: Wheel, enabled: Boolean, onChange: (Wheel) -> Unit) {
    var live by remember(value) { mutableStateOf(value) }
    val onCh by rememberUpdatedState(onChange)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier.size(92.dp).pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun at(p: Offset): Wheel {
                        val cx = size.width / 2f; val cy = size.height / 2f
                        val dx = p.x - cx; val dy = p.y - cy
                        val r = kotlin.math.sqrt(dx * dx + dy * dy) / (minOf(cx, cy))
                        val deg = ((Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()).toFloat()) + 360f) % 360f
                        return Wheel(deg, r.coerceIn(0f, 1f).let { if (it < 0.06f) 0f else it })
                    }
                    live = at(down.position)
                    do {
                        val ev = awaitPointerEvent()
                        ev.changes.forEach { ch -> ch.consume(); live = at(ch.position) }
                    } while (ev.changes.any { it.pressed })
                    onCh(live)
                }
            }
        ) {
            val r = size.minDimension / 2f
            drawCircle(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)), r)
            drawCircle(Brush.radialGradient(listOf(Color(0xFF8A8F98), Color(0x008A8F98)), center, r), r)
            drawCircle(Color(0x55000000), r, style = Stroke(1.dp.toPx()))
            val a = Math.toRadians(live.hue.toDouble())
            val p = Offset(center.x + (kotlin.math.cos(a) * live.amount * r).toFloat(), center.y + (kotlin.math.sin(a) * live.amount * r).toFloat())
            drawCircle(Color.White, 7.dp.toPx(), p)
            drawCircle(Color.Black, 7.dp.toPx(), p, style = Stroke(1.5.dp.toPx()))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
        Text(if (value.amount > 0f) "${(value.amount * 100).roundToInt()}%" else "—", style = MaterialTheme.typography.labelSmall, color = VV.Dim,
            modifier = Modifier.clickable(enabled = enabled) { onChange(Wheel()) })
    }
}

@Composable
private fun ExportPanel(vm: EditorViewModel, onOpen: (android.net.Uri) -> Unit) {
    val o = vm.exportOptions
    val state = vm.exportState
    val running = state is ExportState.Running
    PanelLabel("Pronto per")
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ExportPreset.entries.forEach { pr ->
            SecondaryButton(pr.label, selected = pr.matches(o)) { if (!running) vm.exportOptions = pr.applyTo(o) }
        }
    }
    ExportPreset.entries.firstOrNull { it.matches(o) }?.let { Hint(it.hint) }
    vm.exportEstimate()?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = VV.Tally) }
    PanelLabel("Risoluzione")
    Segmented(OutputSize.entries, o.size, { it.label }, !running) { vm.exportOptions = o.copy(size = it, maxBitrate = null) }
    PanelLabel("Fotogrammi al secondo")
    Segmented(OutputFps.entries, o.fps, { it.label }, !running) { vm.exportOptions = o.copy(fps = it, maxBitrate = null) }
    PanelLabel("Codec")
    Segmented(OutputCodec.entries, if (o.hdr && vm.hasHdr) OutputCodec.H265 else o.codec, { it.label }, !running) {
        vm.exportOptions = o.copy(codec = it, maxBitrate = null, hdr = o.hdr && it == OutputCodec.H265)
    }
    if (vm.hasHdr) {
        ToggleRow("HDR 10 bit, come l'hai girato", o.hdr, !running) { vm.exportOptions = o.copy(hdr = it, codec = if (it) OutputCodec.H265 else o.codec) }
        Hint(if (o.hdr) "Luci e colori pieni sugli schermi HDR (YouTube, Instagram, galleria del telefono). Si usa H.265. Se qualcosa non lo regge, l'app esporta in SDR e te lo scrive nel risultato."
            else "Nel progetto ci sono video HDR: ora vengono convertiti in SDR, il formato che si vede bene ovunque.")
        // Dettaglio: un video 4K esportato in 1080p perde finezza (le luci HDR restano).
        val src4k = vm.project?.clips?.any { !it.isPhoto && minOf(it.width, it.height) >= 2000 } == true
        if (src4k && o.size != OutputSize.ORIGINAL && o.size != OutputSize.P2160) {
            Hint("I video del progetto sono in 4K: in ${o.size.label} si perde un po' di dettaglio. Per avere la stessa nitidezza dell'originale scegli Risoluzione Originale o 4K.")
        }
    }
    ToggleRow("Audio a tono naturale nei cambi di velocità", o.keepPitch, !running) { vm.exportOptions = o.copy(keepPitch = it) }
    ToggleRow("Volume uniformato: ${vm.project?.mix?.target?.label ?: "–14 LUFS"} (si cambia in Audio → Mixer)", o.loudness, !running) { vm.exportOptions = o.copy(loudness = it) }
    PanelLabel("Sperimentale: dissolvenze incrociate e video sovrapposti")
    ToggleRow("Nel video esportato", vm.multiExportEnabled, !running) { vm.setMultiExport(it) }
    Hint("In anteprima dissolvenze incrociate e video sovrapposti si preparano in anticipo (pochi secondi dopo una modifica). Se l'app si chiude per un errore, questa opzione si spegne da sola.")
    PanelLabel("Anteprima")
    ToggleRow("Copie leggere per i video 4K, 8K e 120 fps", vm.useProxies, !running) { vm.setProxies(it) }
    Hint("Solo per l'anteprima, che scorre più fluida: il video esportato usa sempre gli originali.")
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
        is ExportState.Done -> {
            ResultBox("Salvato in Film/VivoVideo", state.details) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Apri il video") { onOpen(state.uri) }
                    SecondaryButton("Chiudi", onClick = vm::dismissExportResult)
                }
            }
            CreditsBox(vm.project?.credits().orEmpty())
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

/** Pulsante secondario; con [selected] si illumina in ambra (per le scelte che restano attive). */
@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(if (selected) VV.Tally else VV.Raised).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) VV.TallyInk else VV.Text, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }
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


/** Barra della selezione multipla, al posto degli strumenti. */
@Composable
private fun MultiSelectBar(vm: EditorViewModel, ids: Set<String>, onPaste: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(VV.Panel).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    ids.isNotEmpty() -> "${ids.size} clip scelte"
                    vm.copiedStyle != null -> "Tocca le clip su cui incollare"
                    else -> "Tocca le clip da scegliere"
                },
                style = MaterialTheme.typography.titleSmall, color = VV.Text, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = vm::selectAllClips) { Text("Tutte", color = VV.Tally) }
            TextButton(onClick = vm::endMultiSelect) { Text("Fine", color = VV.Dim) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (vm.copiedStyle != null) SecondaryButton("Incolla attributi", Modifier.weight(1f), onClick = { if (ids.isNotEmpty()) onPaste() })
            else SecondaryButton("Copia attributi", Modifier.weight(1f), onClick = vm::copyStyle)
            SecondaryButton("Elimina", Modifier.weight(1f), onClick = vm::deleteMulti)
        }
        // Rimescola: le clip scelte (almeno due); subito dopo, un altro ordine.
        if (vm.copiedStyle == null) SecondaryButton(
            if (vm.canShuffleAgain) "Rimescola ancora" else "Rimescola le clip scelte", Modifier.fillMaxWidth(),
            onClick = { if (vm.canShuffleAgain || ids.size >= 2) vm.shuffleClips(ids) else vm.message = "Scegli almeno due clip da rimescolare." },
        )
        if (vm.copiedStyle == null) Hint("Copia gli attributi dalla clip evidenziata, poi scegli le clip su cui incollarli.")
    }
}

/** Quali attributi incollare: di serie quelli che la clip copiata ha davvero. */
@Composable
private fun PasteStyleDialog(src: Clip, targets: Int, onPaste: (Set<ClipAttr>) -> Unit, onDismiss: () -> Unit) {
    val present = remember(src) { ClipStyle.present(src) }
    var chosen by remember(src) { mutableStateOf(present.ifEmpty { ClipAttr.entries.toSet() }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VV.Raised,
        title = { Text("Incolla attributi su $targets clip", style = MaterialTheme.typography.titleSmall) },
        text = {
            Column {
                ClipAttr.entries.forEach { a ->
                    ToggleRow(a.label + if (a in present) "" else " (normale)", a in chosen) { on -> chosen = if (on) chosen + a else chosen - a }
                }
                Hint("I keyframe si adattano alla durata di ogni clip. Velocità e volume non cambiano le foto.")
            }
        },
        confirmButton = { TextButton(onClick = { onPaste(chosen) }, enabled = chosen.isNotEmpty() && targets > 0) { Text("Incolla", color = VV.Tally) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla", color = VV.Dim) } },
    )
}


private val PERSON_COLORS = listOf(0xFF101418, 0xFFFFFFFF, 0xFF1F6FEB, 0xFF2DA44E, 0xFFF2B33D, 0xFFE5484D, 0xFF8E4EC6).map { it.toInt() }

/** Scontorno della persona: cosa fare dello sfondo. */
/** Privacy: oscura volti, targhe e un oggetto scelto, in tutta la clip (riconosciuti sul telefono). */
@Composable
private fun PrivacyTab(vm: EditorViewModel, clip: Clip, enabled: Boolean) {
    val r = clip.redact
    val busy = vm.clipWork != null
    if (r != null) {
        PanelLabel("Oscurato")
        val what = listOfNotNull(if (r.faces) "volti" else null, if (r.plates) "targhe" else null, if (r.objectSeed != null) "un oggetto" else null).joinToString(", ")
        Hint("In questa clip: $what. " + if (clip.isPhoto) "${r.found} elementi coperti." else "Coperti in ${r.found} fotogrammi su ${r.count}.")
        PillRow(it.fabriziodavi.vivovideo.model.RedactStyle.entries, r.style, { it.label }, enabled) { vm.changeRedactStyle(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Rifai", onClick = { if (enabled && !busy) vm.redactClip() })
            SecondaryButton("Togli", onClick = { if (enabled) vm.removeRedact() })
        }
        Hint("Se accorci o allunghi molto la clip, o cambi cosa oscurare qui sotto, tocca Rifai.")
    } else {
        PanelLabel("Oscura volti, targhe e oggetti")
        Hint("L'app trova cosa coprire in ogni fotogramma della clip, sul telefono e senza internet, e lo segue mentre si muove.")
    }
    ToggleRow("Volti", vm.redactFaces, enabled) { vm.redactFaces = it }
    ToggleRow("Targhe", vm.redactPlates, enabled) { vm.redactPlates = it }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (vm.redactSeed != null) "Oggetto scelto" else "Un oggetto", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        SecondaryButton(if (vm.redactSeed != null) "Cambia" else "Toccalo nell'immagine", onClick = { if (enabled && !busy) vm.openObjectPick() })
        if (vm.redactSeed != null) IconBtn(VVIcons.Close, "Nessun oggetto") { vm.clearObject() }
    }
    if (r == null) {
        PillRow(it.fabriziodavi.vivovideo.model.RedactStyle.entries, vm.redactStyle, { it.label }, enabled) { vm.redactStyle = it }
        PrimaryButton(if (clip.isPhoto) "Oscura" else "Analizza e oscura", Modifier.fillMaxWidth(), enabled = enabled && !busy) { vm.redactClip() }
        if (!clip.isPhoto) Hint("Ci vuole circa il tempo della clip. Per l'oggetto: porta l'indicatore dove si vede bene, poi toccalo.")
    }
}

/** Scelta del modello di scontorno: veloce (sul telefono) o preciso (Google). */
@Composable
private fun PersonQuality(vm: EditorViewModel, enabled: Boolean) {
    Segmented(listOf(false, true), vm.personPrecise, { if (it) "Preciso (Google)" else "Veloce" }, enabled) { vm.personPrecise = it }
    Hint(if (vm.personPrecise) "Bordo più fedele anche su capelli e oggetti in mano; più lento (circa 2–4 volte il tempo della clip). La prima volta Google scarica il modello: serve internet."
        else "Sul telefono, senza internet, circa il tempo della clip.")
}

@Composable
private fun PersonTab(vm: EditorViewModel, clip: Clip, enabled: Boolean) {
    if (clip.isPhoto) { Hint("Lo scontorno della persona funziona sui video."); return }
    val fx = clip.person
    if (fx == null) {
        PanelLabel("Persona e sfondo")
        Hint("L'app riconosce la persona in ogni fotogramma e ti lascia sfocare lo sfondo, cambiarlo con un colore o metterlo in bianco e nero.")
        PersonQuality(vm, enabled)
        PrimaryButton("Scontorna la persona", Modifier.fillMaxWidth(), enabled = enabled && vm.clipWork == null) { vm.cutPerson() }
        return
    }
    PanelLabel("Sfondo")
    PillRow(PersonMode.entries.filter { it != PersonMode.REMOVE }, fx.mode, { it.label }, enabled) { vm.setPersonMode(it) }
    if (fx.mode == PersonMode.COLOR) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PERSON_COLORS.forEach { c ->
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(15.dp)).background(Color(c))
                        .border(if (c == fx.color) 3.dp else 1.dp, if (c == fx.color) VV.Tally else VV.Line, RoundedCornerShape(15.dp))
                        .clickable(enabled = enabled) { vm.setPersonColor(c) }
                )
            }
        }
    }
    Hint((if (fx.precise) "Scontorno preciso (Google). " else "Scontorno veloce. ") + "Se accorci o allunghi molto la clip, rifai lo scontorno.")
    PersonQuality(vm, enabled)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("Rifai lo scontorno", onClick = { if (enabled && vm.clipWork == null) vm.cutPerson(fx.mode) })
        SecondaryButton("Togli", onClick = { if (enabled) vm.removePerson() })
    }
}
