package it.fabriziodavi.vivovideo.ui

import it.fabriziodavi.vivovideo.model.TextItem
import it.fabriziodavi.vivovideo.model.TransitionSpec
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.ClipSpeed
import it.fabriziodavi.vivovideo.model.ClipFx
import it.fabriziodavi.vivovideo.model.ColorSettings
import it.fabriziodavi.vivovideo.model.FxType
import it.fabriziodavi.vivovideo.model.Easing
import it.fabriziodavi.vivovideo.model.Keyframe
import it.fabriziodavi.vivovideo.model.Keyframes
import it.fabriziodavi.vivovideo.model.Layer
import it.fabriziodavi.vivovideo.model.MotionStyle
import it.fabriziodavi.vivovideo.model.MusicTrack
import it.fabriziodavi.vivovideo.model.SlideshowOptions
import it.fabriziodavi.vivovideo.model.TextPreset
import it.fabriziodavi.vivovideo.model.Project
import it.fabriziodavi.vivovideo.model.ProjectStore
import it.fabriziodavi.vivovideo.model.SpeedInterp
import it.fabriziodavi.vivovideo.model.SpeedPoint
import it.fabriziodavi.vivovideo.model.SpeedPoints
import it.fabriziodavi.vivovideo.model.Transform
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.transformer.CompositionPlayer
import it.fabriziodavi.vivovideo.CubeLut
import it.fabriziodavi.vivovideo.decodeAudioRange
import it.fabriziodavi.vivovideo.engine.BeatDetector
import it.fabriziodavi.vivovideo.engine.Midi
import it.fabriziodavi.vivovideo.writeWavFile
import it.fabriziodavi.vivovideo.engine.CompositionFactory
import it.fabriziodavi.vivovideo.engine.ExportOptions
import it.fabriziodavi.vivovideo.engine.ExportState
import it.fabriziodavi.vivovideo.engine.Exporter
import it.fabriziodavi.vivovideo.importPhoto
import it.fabriziodavi.vivovideo.Safety
import it.fabriziodavi.vivovideo.readAudioInfo
import it.fabriziodavi.vivovideo.readVideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val SNAP_US = 150_000L

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    val store = ProjectStore(app)
    private val factory = CompositionFactory(app)

    // ---------------- Elenco progetti ----------------
    var projects by mutableStateOf(store.list())
        private set

    fun refreshProjects() { projects = store.list() }

    fun deleteProject(id: String) {
        store.delete(id); refreshProjects()
    }

    // ---------------- Progetto aperto ----------------
    var project by mutableStateOf<Project?>(null)
        private set
    private var selectedClipState by mutableStateOf<String?>(null)
    /** Clip selezionata; selezionarne una toglie la selezione dal brano musicale. */
    var selectedClipId: String?
        get() = selectedClipState
        set(v) { selectedClipState = v; if (v != null) { selectedMusicState = null; selectedTextState = null; selectedLayerState = null } }
    /** Brano musicale selezionato; selezionarlo toglie le altre selezioni. */
    private var selectedMusicState by mutableStateOf<String?>(null)
    var selectedMusicId: String?
        get() = selectedMusicState
        set(v) { selectedMusicState = v; if (v != null) { selectedClipState = null; selectedTextState = null; selectedLayerState = null } }
    /** Testo selezionato; selezionarlo toglie le altre selezioni. */
    private var selectedTextState by mutableStateOf<String?>(null)
    var selectedTextId: String?
        get() = selectedTextState
        set(v) { selectedTextState = v; if (v != null) { selectedClipState = null; selectedMusicState = null; selectedLayerState = null } }
    /** Livello immagine selezionato; selezionarlo toglie le altre selezioni. */
    private var selectedLayerState by mutableStateOf<String?>(null)
    var selectedLayerId: String?
        get() = selectedLayerState
        set(v) { selectedLayerState = v; if (v != null) { selectedClipState = null; selectedMusicState = null; selectedTextState = null } }
    /** Con il movimento attivo: si modifica la posizione finale invece di quella iniziale. */
    var editEnd by mutableStateOf(false)
    /** Anteprima a schermo intero (nasconde anche le barre di sistema). */
    var fullscreen by mutableStateOf(false)
    /** Guida aperta. */
    var showGuide by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var busy by mutableStateOf<String?>(null)
        private set

    private val undoStack = ArrayDeque<Project>()
    private val redoStack = ArrayDeque<Project>()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    val selectedClip: Clip? get() = project?.clips?.firstOrNull { it.id == selectedClipId }
    val selectedMusic: MusicTrack? get() = project?.music?.firstOrNull { it.id == selectedMusicId }
    val selectedText: TextItem? get() = project?.texts?.firstOrNull { it.id == selectedTextId }
    val selectedLayer: Layer? get() = project?.layers?.firstOrNull { it.id == selectedLayerId }

    fun newProject(uris: List<Uri>) {
        val n = projects.size + 1
        val p = Project(name = "Progetto $n")
        openProject(p)
        addClips(uris)
    }

    fun openProject(p: Project, startPreview: Boolean = true) {
        project = p
        multiFailed = false
        undoStack.clear(); redoStack.clear(); updateUndoFlags()
        selectedClipId = p.clips.firstOrNull()?.id
        exportState = ExportState.Idle
        if (startPreview) rebuildPreview(0)
    }

    fun closeProject() {
        fullscreen = false
        project?.let { store.save(it) }
        releasePlayer()
        project = null
        refreshProjects()
    }

    /** Ogni modifica passa da qui: salva, aggiorna l'anteprima e prepara l'annulla. */
    private fun update(keepUndo: Boolean = true, rebuild: Boolean = true, change: (Project) -> Project?) {
        val old = project ?: return
        val new = change(old)?.touch() ?: return
        if (keepUndo) {
            undoStack.addLast(old); if (undoStack.size > 50) undoStack.removeFirst()
            redoStack.clear(); updateUndoFlags()
        }
        project = new
        scheduleSave()
        // I battiti non cambiano audio e video: l'anteprima continua senza interruzioni.
        if (rebuild) rebuildPreview(playheadUs / 1000)
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        project?.let { redoStack.addLast(it) }
        project = prev; updateUndoFlags(); scheduleSave(); rebuildPreview(playheadUs / 1000)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        project?.let { undoStack.addLast(it) }
        project = next; updateUndoFlags(); scheduleSave(); rebuildPreview(playheadUs / 1000)
    }

    private fun updateUndoFlags() { canUndo = undoStack.isNotEmpty(); canRedo = redoStack.isNotEmpty() }

    private var saveJob: Job? = null
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            project?.let { p -> withContext(Dispatchers.IO) { store.save(p) } }
        }
    }

    fun rename(name: String) = update(keepUndo = false) { it.copy(name = name.ifBlank { it.name }) }

    // ---------------- Clip ----------------

    fun addClips(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            busy = "Lettura di video e foto…"
            val clips = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    // Il permesso resta valido anche dopo il riavvio del telefono.
                    runCatching {
                        ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    if (ctx.contentResolver.getType(uri)?.startsWith("image/") == true) {
                        return@mapNotNull runCatching {
                            val photo = importPhoto(ctx, uri)
                            Clip(
                                uri = Uri.fromFile(photo.file).toString(),
                                sourceDurationUs = Clip.PHOTO_MAX_US,
                                trimEndUs = Clip.PHOTO_DEFAULT_US,
                                isPhoto = true,
                                width = photo.width, height = photo.height, fps = 30f,
                            )
                        }.getOrNull()
                    }
                    runCatching {
                        val info = readVideoInfo(ctx, uri)
                        Clip(
                            uri = uri.toString(),
                            sourceDurationUs = info.durationMs * 1000,
                            log = info.tenBit && !info.isHdr,
                            width = info.displayWidth,
                            height = info.displayHeight,
                            fps = info.fps ?: 30f,
                        )
                    }.getOrNull()
                }
            }
            busy = null
            if (clips.size < uris.size) message = "${uris.size - clips.size} file non leggibili sono stati saltati."
            if (clips.isEmpty()) return@launch
            val p = project ?: return@launch
            // Inserisce dopo la clip selezionata, o in fondo.
            val at = p.clips.indexOfFirst { it.id == selectedClipId }.let { if (it < 0) p.clips.size else it + 1 }
            update { it.addClips(clips, at) }
            selectedClipId = clips.first().id
            // Primo video LOG: un po' di riduzione rumore di base.
            if (p.clips.isEmpty() && clips.any { it.log } && project!!.color.denoise == 0) {
                updateColor(keepUndo = false) { it.copy(denoise = 40) }
            }
        }
    }

    // ---------------- Testi ----------------

    /** Nuovo testo dall'indicatore: 3 secondi (o fino alla fine del video). */
    fun addText() {
        val p = project ?: return
        if (p.clips.isEmpty()) { message = "Aggiungi prima un video o una foto."; return }
        val total = p.durationUs
        val start = if (total - playheadUs < TextItem.MIN_US) 0L else playheadUs
        val last = p.texts.lastOrNull()
        // Il nuovo testo riprende lo stile dell'ultimo, così una serie di didascalie resta coerente.
        val t = (last?.copy(id = java.util.UUID.randomUUID().toString(), text = "Testo") ?: TextItem())
            .copy(startUs = start, durationUs = minOf(3_000_000L, total - start).coerceAtLeast(TextItem.MIN_US))
        update { it.copy(texts = it.texts + t) }
        selectedTextId = t.id
    }

    /** Seleziona un testo e porta l'indicatore dove si vede (dopo l'animazione di entrata). */
    fun selectText(id: String) {
        selectedTextId = id
        editEnd = false
        val t = selectedText ?: return
        if (playheadUs < t.startUs || playheadUs >= t.endUs) {
            pause(); seekTo(t.startUs + minOf(500_000L, t.durationUs / 2))
        }
    }

    /** Dimensioni del fotogramma dell'anteprima (per disegnare i riquadri dei testi). */
    fun previewFrameSize(): Pair<Int, Int> = project?.let { factory.outputSize(it, 1080) } ?: (1080 to 1920)

    private var rebuildJob: Job? = null
    /** Aggiorna l'anteprima poco dopo l'ultima modifica (mentre si scrive non la ricarica a ogni lettera). */
    private fun scheduleRebuild() {
        rebuildJob?.cancel()
        rebuildJob = viewModelScope.launch { delay(350); rebuildPreview(playheadUs / 1000) }
    }

    /** Modifica del testo selezionato; [typing] = scrittura in corso (senza annulla per ogni lettera). */
    fun updateText(typing: Boolean = false, change: (TextItem) -> TextItem) {
        val t = selectedText ?: return
        update(keepUndo = !typing, rebuild = false) { it.replaceText(change(t)) }
        scheduleRebuild()
    }

    /** Modifica di un testo qualsiasi (trascinamento sulla timeline). */
    fun setText(t: TextItem) = update { it.replaceText(t) }

    /** Movimento del testo: posizione finale (x, y, rotazione assolute; scala relativa). */
    fun toggleTextMotion() = updateText { t ->
        if (t.endPose != null) t.copy(endPose = null)
        else t.copy(endPose = Transform(x = t.x, y = t.y, scale = 1.2f, rotation = t.rotation))
    }.also { editEnd = false }

    fun deleteText() {
        val id = selectedTextId ?: return
        update { p -> p.copy(texts = p.texts.filterNot { it.id == id }) }
        selectedTextId = null
    }

    fun duplicateText() {
        val t = selectedText ?: return
        val copy = t.copy(id = java.util.UUID.randomUUID().toString(), startUs = t.endUs)
        update { it.copy(texts = it.texts + copy) }
        selectedTextId = copy.id
    }

    /** Applica lo stile del testo selezionato (font, colore, effetto, misura, animazioni) a tutti i testi. */
    fun applyTextStyleToAll() {
        val t = selectedText ?: return
        update { p -> p.copy(texts = p.texts.map {
            it.copy(font = t.font, look = t.look, color = t.color, size = t.size, animIn = t.animIn, animOut = t.animOut)
        }) }
    }

    // ---------------- Livelli immagine ----------------

    fun addLayer(uri: Uri) {
        val ctx = getApplication<Application>()
        val p = project ?: return
        if (p.clips.isEmpty()) { message = "Aggiungi prima un video o una foto."; return }
        viewModelScope.launch {
            busy = "Preparazione dell'immagine…"
            val layer = withContext(Dispatchers.IO) {
                runCatching {
                    val mime = ctx.contentResolver.getType(uri).orEmpty()
                    val (name, _) = runCatching { readAudioInfo(ctx, uri) }.getOrDefault("Immagine" to 0L)
                    val total = p.durationUs
                    val start = if (total - playheadUs < Layer.MIN_US) 0L else playheadUs
                    if (mime.startsWith("video/")) {
                        // Video sovrapposto: si usa il file originale.
                        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                        val info = readVideoInfo(ctx, uri)
                        val src = info.durationMs * 1000
                        return@runCatching Layer(
                            uri = uri.toString(), name = name, width = info.displayWidth, height = info.displayHeight,
                            isVideo = true, sourceDurationUs = src,
                            startUs = start, durationUs = minOf(src, total - start).coerceAtLeast(Layer.MIN_US),
                        )
                    }
                    val alpha = mime.contains("png") || mime.contains("webp") || mime.contains("gif")
                    val img = importPhoto(ctx, uri, "layers", keepAlpha = alpha)
                    Layer(
                        uri = Uri.fromFile(img.file).toString(), name = name, width = img.width, height = img.height,
                        startUs = start, durationUs = minOf(4_000_000L, total - start).coerceAtLeast(Layer.MIN_US),
                    )
                }.getOrNull()
            }
            busy = null
            if (layer == null) { message = "Questa immagine non si può leggere."; return@launch }
            update { it.copy(layers = it.layers + layer) }
            editEnd = false
            selectedLayerId = layer.id
        }
    }

    fun selectLayer(id: String) {
        selectedLayerId = id
        editEnd = false
        val l = selectedLayer ?: return
        if (playheadUs < l.startUs || playheadUs >= l.endUs) { pause(); seekTo(l.startUs + minOf(500_000L, l.durationUs / 2)) }
    }

    fun updateLayer(change: (Layer) -> Layer) {
        val l = selectedLayer ?: return
        update(rebuild = false) { it.replaceLayer(change(l)) }
        scheduleRebuild()
    }

    /** Posizione in modifica (iniziale o finale) del livello selezionato. */
    fun editedLayerPose(): Transform? = selectedLayer?.let { l -> if (editEnd && l.endPose != null) l.endPose else l.pose }

    fun setLayerPose(t: Transform) = updateLayer { l -> if (editEnd && l.endPose != null) l.copy(endPose = t) else l.copy(pose = t) }

    fun setLayerItem(l: Layer) = update { it.replaceLayer(l) }

    /** Attiva/disattiva il movimento: la fine parte un po' più grande dell'inizio (zoom lento). */
    fun toggleLayerMotion() = updateLayer { l ->
        if (l.endPose != null) l.copy(endPose = null) else l.copy(endPose = l.pose.copy(scale = l.pose.scale * 1.25f))
    }.also { editEnd = false }

    fun deleteLayer() {
        val id = selectedLayerId ?: return
        update { p -> p.copy(layers = p.layers.filterNot { it.id == id }) }
        selectedLayerId = null
    }

    fun duplicateLayer() {
        val l = selectedLayer ?: return
        val c = l.copy(id = java.util.UUID.randomUUID().toString(), startUs = l.endUs)
        update { it.copy(layers = it.layers + c) }
        selectedLayerId = c.id
    }

    /** Porta il livello sopra (+1) o sotto (-1) agli altri. */
    fun moveLayerZ(dir: Int) {
        val id = selectedLayerId ?: return
        update { p ->
            val i = p.layers.indexOfFirst { it.id == id }
            val j = i + dir
            if (i < 0 || j !in p.layers.indices) return@update null
            p.copy(layers = p.layers.toMutableList().apply { add(j, removeAt(i)) })
        }
    }

    /** Porta l'indicatore all'inizio o alla fine del movimento, per vedere la posizione che si modifica. */
    fun showPoseEnd(end: Boolean, startUs: Long, durationUs: Long) {
        editEnd = end
        pause()
        seekTo(if (end) startUs + durationUs - minOf(400_000L, durationUs / 3) else startUs + minOf(500_000L, durationUs / 3))
    }

    // ---------------- Musica ----------------

    /** Aggiunge un brano che parte dall'indicatore (o dall'inizio, se l'indicatore è in fondo). */
    fun addMusic(uri: Uri) {
        val ctx = getApplication<Application>()
        val p = project ?: return
        if (p.clips.isEmpty()) { message = "Aggiungi prima un video."; return }
        viewModelScope.launch {
            busy = "Lettura del brano…"
            val track = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                runCatching {
                    val (name, infoUs) = readAudioInfo(ctx, uri)
                    val total = p.durationUs
                    val start = if (total - playheadUs < 1_000_000) 0L else playheadUs
                    val head = ctx.contentResolver.openInputStream(uri)?.use { s -> ByteArray(16).also { s.read(it) } }
                    val bytes = if (head != null && Midi.isMidi(head)) ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } else null
                    if (bytes != null) {
                        // MIDI: lo suona il sintetizzatore di Android e lo si salva come WAV;
                        // tempo e battiti si leggono esatti dal file.
                        withContext(Dispatchers.Main) { busy = "Conversione del MIDI…" }
                        val pcm = decodeAudioRange(ctx, uri, 0, Long.MAX_VALUE / 4) ?: error("MIDI non riproducibile")
                        if (pcm.size < 2 * 4800) error("MIDI vuoto")
                        val dir = File(ctx.filesDir, "music").apply { mkdirs() }
                        val wav = File(dir, "midi_${System.currentTimeMillis()}.wav")
                        writeWavFile(wav, pcm)
                        val durUs = pcm.size / 2 * 1_000_000L / 48_000
                        val midi = Midi.parse(bytes)
                        MusicTrack(
                            uri = Uri.fromFile(wav).toString(), name = name, sourceDurationUs = durUs,
                            startUs = start, lengthUs = minOf(durUs, total - start),
                            beatsUs = midi?.beatsUs?.filter { it < durUs }.orEmpty(),
                            bpm = midi?.bpm ?: 0f,
                        )
                    } else {
                        MusicTrack(
                            uri = uri.toString(), name = name, sourceDurationUs = infoUs,
                            startUs = start, lengthUs = minOf(infoUs, total - start),
                        )
                    }
                }.getOrNull()
            }
            busy = null
            if (track == null || track.sourceDurationUs <= 0) { message = "Questo file audio non si può leggere."; return@launch }
            update { it.copy(music = it.music + track) }
            selectedMusicId = track.id
        }
    }

    fun updateMusic(keepUndo: Boolean = true, change: (MusicTrack) -> MusicTrack) {
        val t = selectedMusic ?: return
        update(keepUndo) { it.replaceMusic(change(t)) }
    }

    /** Modifica di un brano qualsiasi (trascinamento sulla timeline). */
    fun setMusic(t: MusicTrack) = update { it.replaceMusic(t) }

    internal fun setMusicForTest(list: List<MusicTrack>) = update { it.copy(music = list) }
    internal fun setLayersForTest(list: List<Layer>) = update { it.copy(layers = list) }

    fun deleteMusic() {
        val id = selectedMusicId ?: return
        update { p -> p.copy(music = p.music.filterNot { it.id == id }) }
        selectedMusicId = null
    }

    /** Analizza il brano selezionato e ne segna i battiti. */
    fun detectBeats() {
        val t = selectedMusic ?: return
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            busy = "Analisi del ritmo…"
            val r = withContext(Dispatchers.Default) {
                runCatching {
                    val stereo = decodeAudioRange(ctx, Uri.parse(t.uri), 0, t.sourceDurationUs) ?: return@runCatching null
                    BeatDetector.detect(BeatDetector.toMono(stereo), 48_000)
                }.getOrNull()
            }
            busy = null
            if (r == null || r.beatsUs.size < 4) { message = "Non ho trovato un ritmo chiaro in questo brano."; return@launch }
            update { p ->
                val cur = p.music.firstOrNull { it.id == t.id } ?: return@update null
                p.replaceMusic(cur.copy(beatsUs = r.beatsUs, bpm = r.bpm))
            }
        }
    }

    /** Punto del brano selezionato sotto l'indicatore (o null se l'indicatore è fuori dal brano). */
    private fun musicSrcAt(t: MusicTrack, timelineUs: Long): Long? {
        val total = project?.durationUs ?: return null
        val rel = timelineUs - t.startUs
        if (rel < 0 || rel > t.playUs(total)) return null
        return t.offsetUs + rel
    }

    /** Segna o toglie un battito nel punto dell'indicatore. */
    fun toggleBeatAtPlayhead() {
        val t = selectedMusic ?: return
        val src = musicSrcAt(t, playheadUs) ?: run { message = "Porta l'indicatore sopra il brano."; return }
        update(rebuild = false) { it.replaceMusic(t.toggleBeatAt(src)) }
    }

    /** Tap a tempo durante l'ascolto: segna un battito nel punto che si sta sentendo. */
    fun tapBeat() {
        val t = selectedMusic ?: return
        val now = player?.currentPosition?.let { it * 1000 } ?: playheadUs
        val src = musicSrcAt(t, now) ?: return
        update(keepUndo = false, rebuild = false) { p ->
            val cur = p.music.firstOrNull { it.id == t.id } ?: return@update null
            p.replaceMusic(cur.addMarker(src))
        }
    }

    fun clearBeats() = updateMusic { it.copy(beatsUs = emptyList(), bpm = 0f, markersUs = emptyList(), hiddenUs = emptyList(), beatShiftUs = 0) }

    /** Modifica dei battiti del brano selezionato senza ricaricare l'anteprima. */
    fun updateBeats(change: (MusicTrack) -> MusicTrack) {
        val t = selectedMusic ?: return
        update(rebuild = false) { it.replaceMusic(change(t)) }
    }

    /** Sposta l'indicatore sul battito precedente o successivo. */
    fun jumpBeat(dir: Int) {
        val beats = project?.beatTimes() ?: return
        val target = if (dir > 0) beats.firstOrNull { it > playheadUs + 10_000 } else beats.lastOrNull { it < playheadUs - 10_000 }
        target?.let { pause(); seekTo(it) }
    }

    fun alignCutsToBeats() {
        val p = project ?: return
        val r = p.alignCutsToBeats()
        if (r == null) { message = "I tagli sono già sul ritmo (o le clip non hanno abbastanza video per arrivare al battito)."; return }
        update { r }
    }

    /** Divide i video su ogni battito del brano selezionato. */
    fun splitOnBeats() {
        val p = project ?: return
        val t = selectedMusic ?: return
        val r = p.splitAtBeats(t.startUs + 1, t.startUs + t.playUs(p.durationUs) - 1)
        if (r == null) { message = "Nessun battito dentro i video."; return }
        update { r }
        message = "Video divisi in ${r.clips.size} clip a ritmo: elimina o sostituisci i pezzi che non ti servono."
    }

    /** Divide il brano selezionato nel punto dell'indicatore. */
    fun splitMusicAtPlayhead() {
        val t = selectedMusic ?: return
        val cut = playheadUs - t.startUs
        if (cut < MusicTrack.MIN_US || t.lengthUs - cut < MusicTrack.MIN_US) {
            message = "Porta l'indicatore dentro il brano, lontano dai bordi."; return
        }
        val a = t.copy(lengthUs = cut, fadeOutUs = 0)
        val b = t.copy(id = java.util.UUID.randomUUID().toString(), startUs = t.startUs + cut, offsetUs = t.offsetUs + cut, lengthUs = t.lengthUs - cut, fadeInUs = 0)
        update { p -> p.copy(music = p.music.flatMap { if (it.id == t.id) listOf(a, b) else listOf(it) }) }
        selectedMusicId = b.id
    }

    fun splitAtPlayhead() {
        val p = project ?: return
        val located = p.locate(playheadUs) ?: return
        val result = p.splitAt(playheadUs)
        if (result == null) { message = "Sposta l'indicatore un po' più lontano dal bordo della clip."; return }
        update { result }
        selectedClipId = result.clips[located.first + 1].id
    }

    fun trimClip(id: String, startUs: Long, endUs: Long) {
        val c = project?.clips?.firstOrNull { it.id == id } ?: return
        if (c.trimStartUs == startUs && c.trimEndUs == endUs) return
        update { p ->
            val trimmed = p.replaceClip(c.copy(trimStartUs = startUs, trimEndUs = endUs))
            snapClipEnd(trimmed, id, moveEnd = endUs != c.trimEndUs) ?: trimmed
        }
    }

    /** Aggancia i tagli ai battiti della musica. */
    var snapToBeats by mutableStateOf(true)

    /** Se la fine della clip cade vicino a un battito, la porta esattamente sul battito. */
    private fun snapClipEnd(p: Project, id: String, moveEnd: Boolean): Project? {
        if (!snapToBeats) return null
        val beats = p.beatTimes().ifEmpty { return null }
        val i = p.clips.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return null
        val start = p.clipStartsUs()[i]
        val end = start + p.clips[i].outputUs
        val b = beats.minByOrNull { kotlin.math.abs(it - end) } ?: return null
        if (kotlin.math.abs(b - end) > SNAP_US || b - start < 200_000) return null
        return p.fitClipOutput(i, b - start, moveEnd)
    }

    // ---------------- Keyframe ----------------

    /** Tempo (sul video originale, dall'inizio della clip) dell'indicatore dentro la clip selezionata. */
    fun playheadInSelected(): Long? {
        val p = project ?: return null
        val c = selectedClip ?: return null
        val start = p.clipStartsUs()[p.clips.indexOf(c)]
        val out = playheadUs - start
        if (out < 0 || out > c.outputUs) return null
        return c.speedCurve().sourceTimeAt(out)
    }

    fun transformAtPlayhead(): Transform {
        val c = selectedClip ?: return Transform()
        val t = playheadInSelected() ?: 0L
        return Keyframes.valueAt(c.keyframes, t)
    }

    /** Cambia la trasformazione nel punto dell'indicatore: crea il keyframe se non c'è. */
    fun setTransformAtPlayhead(value: Transform) {
        val t = playheadInSelected()
        if (t == null) { message = "Porta l'indicatore dentro la clip selezionata per mettere un keyframe."; return }
        updateSelected { c -> c.copy(keyframes = Keyframes.set(c.keyframes, t, value)) }
    }

    fun keyframeAtPlayhead(): Keyframe? {
        val c = selectedClip ?: return null
        val t = playheadInSelected() ?: return null
        return c.keyframes.firstOrNull { kotlin.math.abs(it.timeUs - t) <= 40_000 }
    }

    fun toggleKeyframeAtPlayhead() {
        val c = selectedClip ?: return
        val k = keyframeAtPlayhead()
        if (k != null) updateSelected { it.copy(keyframes = it.keyframes - k) }
        else setTransformAtPlayhead(transformAtPlayhead())
    }

    fun setEasingAtPlayhead(e: Easing) {
        val c = selectedClip ?: return
        val t = playheadInSelected() ?: return
        val k = c.keyframes.filter { it.timeUs <= t + 40_000 }.maxByOrNull { it.timeUs } ?: return
        updateSelected { cl -> cl.copy(keyframes = cl.keyframes.map { if (it == k) it.copy(easing = e) else it }) }
    }

    // ---------------- Punti velocità sull'indicatore ----------------

    /** Posizione (0..1 lungo la clip, sul video originale) dell'indicatore nella clip selezionata. */
    fun playheadFraction(): Float? {
        val c = selectedClip ?: return null
        val t = playheadInSelected() ?: return null
        return (t.toFloat() / c.trimmedUs).coerceIn(0f, 1f)
    }

    fun speedPointAtPlayhead(): SpeedPoint? {
        val c = selectedClip ?: return null
        val f = playheadFraction() ?: return null
        return SpeedPoints.nearest(c.curvePoints, f)
    }

    /**
     * Cambia la curva tenendo fermo il fotogramma sotto l'indicatore:
     * con la nuova velocità lo stesso fotogramma cade in un altro istante del video finale.
     */
    private fun changeCurveKeepingFrame(newPoints: List<SpeedPoint>) {
        val p = project ?: return
        val c = selectedClip ?: return
        val src = playheadInSelected()
        val idx = p.clips.indexOf(c)
        updateSelected { it.copy(speed = ClipSpeed.RAMP, speedPoints = newPoints) }
        if (src != null) {
            val np = project ?: return
            val nc = np.clips[idx]
            seekTo(np.clipStartsUs()[idx] + nc.speedCurve().outputTimeAt(src))
        }
    }

    fun setSpeedAtPlayhead(speed: Float) {
        val c = selectedClip ?: return
        val f = playheadFraction()
        if (f == null) { message = "Porta l'indicatore dentro la clip selezionata."; return }
        changeCurveKeepingFrame(SpeedPoints.set(c.curvePoints, f, speed))
    }

    fun toggleSpeedPointAtPlayhead() {
        val c = selectedClip ?: return
        val f = playheadFraction() ?: run { message = "Porta l'indicatore dentro la clip selezionata."; return }
        val near = SpeedPoints.nearest(c.curvePoints, f)
        if (near != null && near.t > 0f && near.t < 1f) {
            changeCurveKeepingFrame(c.curvePoints - near)
        } else if (near == null) {
            val now = SpeedInterp.valueAt(c.curvePoints, f)
            changeCurveKeepingFrame(SpeedPoints.set(c.curvePoints, f, now))
        }
    }

    /** Porta l'indicatore all'inizio della clip selezionata. */
    fun seekToSelectedStart() {
        val p = project ?: return
        val c = selectedClip ?: return
        pause(); seekTo(p.clipStartsUs()[p.clips.indexOf(c)] + 1_000)
    }

    fun jumpSpeedPoint(dir: Int) {
        val p = project ?: return
        val c = selectedClip ?: return
        val start = p.clipStartsUs()[p.clips.indexOf(c)]
        val times = c.curvePoints.map { start + c.speedCurve().outputTimeAt((it.t * c.trimmedUs).toLong()) }.sorted()
        val target = if (dir > 0) times.firstOrNull { it > playheadUs + 20_000 } else times.lastOrNull { it < playheadUs - 20_000 }
        target?.let { pause(); seekTo(it) }
    }

    fun clearKeyframes() = updateSelected { it.copy(keyframes = emptyList()) }

    /** Salta al keyframe precedente (-1) o successivo (+1) della clip selezionata. */
    fun jumpKeyframe(dir: Int) {
        val p = project ?: return
        val c = selectedClip ?: return
        val start = p.clipStartsUs()[p.clips.indexOf(c)]
        val times = c.keyframes.map { start + c.speedCurve().outputTimeAt(it.timeUs) }.sorted()
        val target = if (dir > 0) times.firstOrNull { it > playheadUs + 20_000 } else times.lastOrNull { it < playheadUs - 20_000 }
        target?.let { pause(); seekTo(it) }
    }

    fun deleteSelected() {
        val id = selectedClipId ?: return
        val p = project ?: return
        val i = p.clips.indexOfFirst { it.id == id }
        update { it.removeClip(id) }
        selectedClipId = project?.clips?.getOrNull(i.coerceAtMost((project?.clips?.size ?: 1) - 1))?.id
    }

    fun duplicateSelected() {
        val c = selectedClip ?: return
        val p = project ?: return
        val copy = c.copy(id = java.util.UUID.randomUUID().toString())
        update { it.addClips(listOf(copy), p.clips.indexOf(c) + 1) }
        selectedClipId = copy.id
    }

    fun moveSelected(delta: Int) {
        val id = selectedClipId ?: return
        update { p -> p.moveClip(id, delta).takeIf { it != p } }
    }

    fun updateSelected(change: (Clip) -> Clip) {
        val c = selectedClip ?: return
        update { it.replaceClip(change(c)) }
    }

    // ---------------- Effetti ----------------

    fun toggleFx(type: FxType) = updateSelected { c ->
        if (c.fx.any { it.type == type }) c.copy(fx = c.fx.filterNot { it.type == type }) else c.copy(fx = c.fx + ClipFx(type))
    }

    fun setFxAmount(type: FxType, amount: Float) = updateSelected { c ->
        c.copy(fx = c.fx.map { if (it.type == type) it.copy(amount = amount) else it })
    }

    fun applyFxToAll() {
        val c = selectedClip ?: return
        update { p -> p.copy(clips = p.clips.map { it.copy(fx = c.fx) }) }
    }

    /** Inquadratura fissa della clip selezionata. */
    fun setFrame(f: Transform) = updateSelected { it.copy(frame = f) }

    /** Copia inquadratura e Adatta/Riempi della clip selezionata su tutte le clip. */
    fun applyFrameToAll() {
        val c = selectedClip ?: return
        update { p -> p.copy(clips = p.clips.map { it.copy(frame = c.frame, fill = c.fill) }) }
    }

    /** Durata di una foto. */
    fun setPhotoDuration(us: Long) = updateSelected { c ->
        val d = us.coerceIn(200_000, Clip.PHOTO_MAX_US)
        // I keyframe seguono la nuova durata in proporzione.
        val k = if (c.trimmedUs > 0) c.keyframes.map { it.copy(timeUs = it.timeUs * d / c.trimmedUs) } else c.keyframes
        c.copy(trimStartUs = 0, trimEndUs = d, keyframes = k)
    }

    /** Movimento pronto sulla clip selezionata, o su tutte le foto. */
    fun applyMotion(style: MotionStyle, allPhotos: Boolean) {
        val c = selectedClip ?: return
        if (!allPhotos) { updateSelected { it.copy(keyframes = style.keyframes(it.trimmedUs)) }; return }
        update { p -> p.copy(clips = p.clips.map { if (it.isPhoto || it.id == c.id) it.copy(keyframes = style.keyframes(it.trimmedUs)) else it }) }
    }

    fun createSlideshow(o: SlideshowOptions) {
        val p = project ?: return
        if (p.clips.none { it.isPhoto } && o.noTransitions) { message = "Aggiungi qualche foto per lo slideshow."; return }
        update { it.slideshow(o, seed = (System.currentTimeMillis() % 10_000).toInt()) }
        message = "Slideshow pronto: ${p.clips.count { it.isPhoto }} foto" +
            (if (o.beatsPerPhoto > 0) " a ritmo di musica." else ".") + " Puoi sempre ritoccare ogni clip o annullare."
    }

    fun applyTextPreset(preset: TextPreset) = updateText { preset.applyTo(it) }

    /** Zoom lento su tutta la foto (effetto Ken Burns), o lo toglie. */
    fun toggleKenBurns() = updateSelected { c ->
        if (c.keyframes.isNotEmpty()) c.copy(keyframes = emptyList())
        else c.copy(keyframes = listOf(
            Keyframe(0, Transform(), Easing.LINEAR),
            Keyframe(c.trimmedUs, Transform(scale = 1.18f), Easing.LINEAR),
        ))
    }

    fun setTransition(spec: TransitionSpec?) = updateSelected { it.copy(transitionOut = spec) }

    /** Stessa transizione su tutti i tagli del progetto. */
    fun setTransitionAll(spec: TransitionSpec?) = update { p ->
        p.copy(clips = p.clips.mapIndexed { i, c -> if (i < p.clips.lastIndex) c.copy(transitionOut = spec) else c })
    }

    fun updateColor(keepUndo: Boolean = true, change: (ColorSettings) -> ColorSettings) =
        update(keepUndo) { it.copy(color = change(it.color)) }

    /** Copia il .cube nella cartella dell'app, così resta disponibile per il progetto. */
    fun importLut(uri: Uri) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val lut = CubeLut.fromUri(ctx, uri) // valida il file
                    val dest = File(store.lutDir, "${System.currentTimeMillis()}.cube")
                    ctx.contentResolver.openInputStream(uri)!!.use { i -> dest.outputStream().use { i.copyTo(it) } }
                    dest.absolutePath to lut.name
                }
            }
            r.onSuccess { (path, name) -> updateColor { it.copy(lutPath = path, lutName = name) } }
                .onFailure { message = "LUT non valida: ${it.message}" }
        }
    }

    // ---------------- Anteprima ----------------

    var player by mutableStateOf<CompositionPlayer?>(null)
        private set
    var playheadUs by mutableStateOf(0L)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var previewError by mutableStateOf<String?>(null)
        private set
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            player?.let { p ->
                playheadUs = p.currentPosition * 1000
                isPlaying = p.isPlaying
            }
            handler.postDelayed(this, 50)
        }
    }

    private var playerMulti = false

    /** Anteprima con dissolvenze incrociate e video sovrapposti (sperimentale). */
    var multiPreviewEnabled by mutableStateOf(Safety.multiPreview(app))
        private set
    var multiExportEnabled by mutableStateOf(Safety.multiExport(app))
        private set

    fun setMultiPreview(on: Boolean) {
        multiPreviewEnabled = on
        Safety.setMultiPreview(getApplication(), on)
        multiFailed = false
        rebuildPreview(playheadUs / 1000)
    }

    fun setMultiExport(on: Boolean) {
        multiExportEnabled = on
        Safety.setMultiExport(getApplication(), on)
    }

    /** In secondo piano non c'è rischio di crash dell'anteprima: si toglie il segnale; al ritorno si rimette. */
    fun onAppStop() { if (playerMulti) Safety.pendingPreview(getApplication(), false) }
    fun onAppStart() { if (playerMulti && player != null) Safety.pendingPreview(getApplication(), true) }
    /** Il compositore a più ingressi ha dato errore in anteprima: si usa il ripiego. */
    private var multiFailed = false

    private fun rebuildPreview(positionMs: Long) {
        val p = project ?: return
        if (exportState is ExportState.Running) return
        if (p.clips.isEmpty()) { releasePlayer(); playheadUs = 0; return }
        val allowMulti = multiPreviewEnabled && !multiFailed
        val composition = runCatching { factory.build(p, exportOptions, forPreview = true, nativeAudio = null, allowMulti = allowMulti) }
            .getOrElse { previewError = "Anteprima non disponibile: ${it.message}"; return }
        // Con video sovrapposti o dissolvenze incrociate serve il compositore a più ingressi:
        // si ricrea il lettore se cambia modalità.
        val multi = allowMulti && (p.layers.any { it.isVideo } || p.crossfades().isNotEmpty())
        if (player != null && playerMulti != multi) releasePlayer()
        playerMulti = multi
        // Se l'app cadesse con l'anteprima avanzata attiva, al riavvio la si spegne da sola.
        Safety.pendingPreview(getApplication(), multi)
        val pl = player ?: CompositionPlayer.Builder(getApplication())
            .apply { if (multi) setVideoGraphFactory(androidx.media3.effect.MultipleInputVideoGraph.Factory()) }
            .build().also { np ->
            np.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    // Catena delle cause: serve per capire cosa non va sul telefono.
                    val chain = generateSequence(error.cause) { it.cause }.take(3)
                        .joinToString(" ← ") { "${it.javaClass.simpleName}: ${it.message?.take(120) ?: ""}" }
                    val detail = "${error.errorCodeName}${if (chain.isNotEmpty()) "\n$chain" else ""}"
                    if (playerMulti && !multiFailed) {
                        // Ripiego: anteprima senza compositore a più ingressi.
                        multiFailed = true
                        Safety.pendingPreview(getApplication(), false)
                        handler.post {
                            rebuildPreview(playheadUs / 1000)
                            previewError = "Anteprima semplificata (dissolvenza incrociata mostrata come passaggio al nero, video sovrapposti nascosti). Dettaglio: $detail"
                        }
                        return
                    }
                    previewError = "Errore anteprima: $detail"
                }
            })
            np.repeatMode = Player.REPEAT_MODE_ALL
            player = np
            handler.removeCallbacks(ticker)
            handler.post(ticker)
        }
        previewError = null
        val wasPlaying = pl.isPlaying
        try {
            pl.setComposition(composition, positionMs.coerceIn(0, p.durationUs / 1000))
            pl.prepare()
        } catch (e: Exception) {
            // Errore immediato: con l'anteprima avanzata si ripiega su quella semplice.
            if (multi && !multiFailed) {
                multiFailed = true
                releasePlayer()
                rebuildPreview(positionMs)
                previewError = "Anteprima semplificata. Dettaglio: ${e.javaClass.simpleName}: ${e.message?.take(160)}"
            } else {
                previewError = "Anteprima non disponibile: ${e.javaClass.simpleName}: ${e.message?.take(160)}"
            }
            return
        }
        if (wasPlaying) pl.play()
    }

    private fun releasePlayer() {
        handler.removeCallbacks(ticker)
        player?.release()
        player = null
        if (playerMulti) Safety.pendingPreview(getApplication(), false)
        playerMulti = false
        isPlaying = false
    }

    fun togglePlay() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else {
            if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
            p.play()
        }
    }

    fun pause() { player?.pause() }

    fun seekTo(us: Long) {
        val d = project?.durationUs ?: return
        val t = us.coerceIn(0, d)
        playheadUs = t
        player?.seekTo(t / 1000)
    }

    // ---------------- Export ----------------

    var exportOptions by mutableStateOf(ExportOptions())
    var exportState by mutableStateOf<ExportState>(ExportState.Idle)
        private set

    private val exporter = Exporter(app, viewModelScope, factory) { exportState = it }

    fun startExport() {
        val p = project ?: return
        if (p.clips.isEmpty() || exportState is ExportState.Running) return
        store.save(p)
        // Libera il decoder dell'anteprima: con 4K/8K non ci sono risorse per entrambi.
        val pos = playheadUs
        releasePlayer()
        playheadUs = pos
        exportState = ExportState.Running(null, "Preparazione…")
        exporter.start(p, exportOptions)
    }

    fun cancelExport() {
        exporter.cancel()
        rebuildPreview(playheadUs / 1000)
    }

    fun dismissExportResult() {
        exportState = ExportState.Idle
        rebuildPreview(playheadUs / 1000)
    }

    override fun onCleared() {
        project?.let { store.save(it) }
        releasePlayer()
    }
}
