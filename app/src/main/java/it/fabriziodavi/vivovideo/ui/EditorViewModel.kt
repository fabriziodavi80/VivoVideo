package it.fabriziodavi.vivovideo.ui

import it.fabriziodavi.vivovideo.model.TextAnim
import it.fabriziodavi.vivovideo.model.TextItem
import it.fabriziodavi.vivovideo.model.TransitionSpec
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.SubStyle
import it.fabriziodavi.vivovideo.model.SubPosition
import it.fabriziodavi.vivovideo.model.Subtitles
import it.fabriziodavi.vivovideo.model.cutTimeline
import it.fabriziodavi.vivovideo.model.withCredits
import it.fabriziodavi.vivovideo.model.withoutCredits
import it.fabriziodavi.vivovideo.engine.Whisper
import it.fabriziodavi.vivovideo.engine.PreviewLoudness
import it.fabriziodavi.vivovideo.engine.CacheJanitor
import it.fabriziodavi.vivovideo.engine.Proxies
import it.fabriziodavi.vivovideo.engine.Decoders
import it.fabriziodavi.vivovideo.engine.DuckPreview
import it.fabriziodavi.vivovideo.engine.Meters
import it.fabriziodavi.vivovideo.model.Bus
import it.fabriziodavi.vivovideo.model.BusMix
import it.fabriziodavi.vivovideo.model.Mix
import it.fabriziodavi.vivovideo.model.bus
import it.fabriziodavi.vivovideo.engine.VoiceChain
import it.fabriziodavi.vivovideo.engine.VoiceRecorder
import it.fabriziodavi.vivovideo.engine.AudioDsp
import it.fabriziodavi.vivovideo.model.ClipAttr
import it.fabriziodavi.vivovideo.model.ClipStyle
import it.fabriziodavi.vivovideo.model.ClipTools
import it.fabriziodavi.vivovideo.model.Template
import it.fabriziodavi.vivovideo.model.TemplateStore
import it.fabriziodavi.vivovideo.model.Templates
import it.fabriziodavi.vivovideo.model.StabMath
import it.fabriziodavi.vivovideo.model.insertAt
import it.fabriziodavi.vivovideo.engine.Reverser
import it.fabriziodavi.vivovideo.engine.SmartAnalyzer
import it.fabriziodavi.vivovideo.engine.Backup
import it.fabriziodavi.vivovideo.engine.MediaRef
import it.fabriziodavi.vivovideo.engine.ProjectRefs
import it.fabriziodavi.vivovideo.engine.PersonMasks
import it.fabriziodavi.vivovideo.model.PersonMode
import it.fabriziodavi.vivovideo.model.withMode
import it.fabriziodavi.vivovideo.model.MomentScan
import it.fabriziodavi.vivovideo.model.MomentSample
import it.fabriziodavi.vivovideo.model.Moments
import it.fabriziodavi.vivovideo.model.JumpCuts
import it.fabriziodavi.vivovideo.model.JumpStyle
import it.fabriziodavi.vivovideo.model.Shuffle
import it.fabriziodavi.vivovideo.model.Reframe
import it.fabriziodavi.vivovideo.model.SpokenWord
import it.fabriziodavi.vivovideo.engine.Whoosh
import it.fabriziodavi.vivovideo.engine.Stabilizer
import it.fabriziodavi.vivovideo.engine.ExportEstimate
import it.fabriziodavi.vivovideo.engine.OutputCodec
import it.fabriziodavi.vivovideo.renderTimelineAudio
import it.fabriziodavi.vivovideo.model.Grade
import it.fabriziodavi.vivovideo.decodePhotoThumb
import it.fabriziodavi.vivovideo.model.FrameMode
import it.fabriziodavi.vivovideo.model.VideoFormat
import it.fabriziodavi.vivovideo.online.Library
import it.fabriziodavi.vivovideo.online.OnlineTrack
import it.fabriziodavi.vivovideo.model.AutoEdit
import it.fabriziodavi.vivovideo.model.AutoOptions
import it.fabriziodavi.vivovideo.model.AutoRecipe
import it.fabriziodavi.vivovideo.model.AutoSource
import it.fabriziodavi.vivovideo.model.AutoStyle
import it.fabriziodavi.vivovideo.media.GalleryItem
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
import android.os.SystemClock
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
import androidx.media3.transformer.Transformer
import it.fabriziodavi.vivovideo.engine.CrossfadePreview
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
import it.fabriziodavi.vivovideo.Diary
import it.fabriziodavi.vivovideo.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

private const val SNAP_US = 150_000L

private const val SCRUB_MS = 120L

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
    /** Argomento su cui aprire la guida (null = indice). */
    var guideTopic by mutableStateOf<String?>(null)
    private var messageState by mutableStateOf<String?>(null)
    /** Messaggio in basso; finisce anche nel diario (per le segnalazioni). */
    var message: String?
        get() = messageState
        set(v) { messageState = v; if (v != null) Diary.log("Messaggio: $v") }
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
        multiIds = null
        missingFiles = emptyList()
        undoStack.clear(); redoStack.clear(); updateUndoFlags()
        selectedClipId = p.clips.firstOrNull()?.id
        exportState = ExportState.Idle
        if (startPreview) rebuildPreview(0)
        detectHdr(p)
    }

    /**
     * Controllo dei file all'apertura: quali video sono HDR (i progetti fatti prima non lo sapevano), nome e dimensione
     * dei file (per ritrovarli se si spostano) e quali file mancano.
     */
    private fun detectHdr(p: Project) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            data class Info(val hdr: Map<String, Int>, val names: Map<String, MediaRef>, val missing: List<String>)
            val info = withContext(Dispatchers.IO) {
                val missing = Backup.missing(ctx, p)
                val hdr = p.clips.filter { !it.isPhoto && !it.baked && it.hdr == 0 && it.uri !in missing }.associate { c ->
                    c.id to (runCatching { readVideoInfo(ctx, Uri.parse(c.uri)).hdrKind }.getOrNull() ?: 0)
                }.filterValues { it > 0 }
                val names = (p.clips.filter { it.fileName.isBlank() && !it.isPhoto }.map { it.uri } + p.layers.filter { it.fileName.isBlank() && it.isVideo }.map { it.uri })
                    .distinct().filter { it !in missing }.associateWith { Backup.describe(ctx, it) }.filterValues { it.name.isNotBlank() }
                Info(hdr, names, missing)
            }
            if (project?.id != p.id) return@launch
            missingFiles = info.missing
            if (info.missing.isNotEmpty()) autoRelink(quiet = true)
            if (info.hdr.isEmpty() && info.names.isEmpty()) return@launch
            project = project?.let { q ->
                q.copy(
                    clips = q.clips.map { c ->
                        var x = info.hdr[c.id]?.let { c.copy(hdr = it) } ?: c
                        info.names[x.uri]?.let { r -> if (x.fileName.isBlank()) x = x.copy(fileName = r.name, fileSize = r.size) }
                        x
                    },
                    layers = q.layers.map { l -> info.names[l.uri]?.let { r -> if (l.fileName.isBlank()) l.copy(fileName = r.name, fileSize = r.size) else l } ?: l },
                )
            }
            scheduleSave()
        }
    }

    // ---------------- Modelli e font ----------------

    val templateStore = TemplateStore(app)
    var templates by mutableStateOf(templateStore.list())
        private set

    fun saveTemplate(name: String) {
        val p = project ?: return
        if (p.clips.isEmpty()) { message = "Il modello prende la struttura delle clip: aggiungine almeno una."; return }
        templateStore.save(Templates.fromProject(p, name))
        templates = templateStore.list()
        message = "Modello salvato: lo trovi in Home → Da un modello."
    }

    fun deleteTemplate(id: String) { templateStore.delete(id); templates = templateStore.list() }

    /** Nuovo progetto dal modello [t] con i video e le foto [uris] (in ordine di scelta). */
    fun createFromTemplate(t: Template, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val clips = uris.mapIndexedNotNull { i, u ->
                busy = "Preparo foto e video… ${i + 1} di ${uris.size}"
                withContext(Dispatchers.IO) { importClip(ctx, u) }
            }
            if (clips.isEmpty()) { busy = null; message = "Non sono riuscito a leggere le foto e i video scelti."; return@launch }
            val scans = smartScans(ctx, clips.filter { !it.isPhoto }, true)
            busy = "Montaggio…"
            val p = withContext(Dispatchers.Default) {
                Templates.apply(t, clips, t.name) { c, len -> scans[c.uri]?.let { Moments.bestStarts(it, len, 1).first() } }
            }
            busy = null
            store.save(p)
            openProject(p)
            message = "Progetto dal modello \"${t.name}\": ${p.clips.size} clip. Ritocca quello che vuoi."
        }
    }

    /** Font aggiunti dall'utente (file nella cartella fonts). */
    private val fontDir get() = File(getApplication<Application>().filesDir, "fonts").apply { mkdirs() }
    var fonts by mutableStateOf(fontDir.listFiles().orEmpty().filter { it.extension.lowercase() in listOf("ttf", "otf") }.map { it.absolutePath }.sorted())
        private set

    /** Copia un font (.ttf/.otf) nell'app e lo mette sul testo selezionato. */
    fun addFont(uri: Uri) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val f = withContext(Dispatchers.IO) {
                runCatching {
                    val name = Backup.describe(ctx, uri.toString()).name.ifBlank { "font.ttf" }.replace(Regex("[^A-Za-z0-9._ -]"), "_")
                    val ext = name.substringAfterLast('.', "").lowercase()
                    require(ext == "ttf" || ext == "otf") { "Servono file .ttf o .otf." }
                    val out = File(fontDir, name)
                    ctx.contentResolver.openInputStream(uri)!!.use { s -> out.outputStream().use { s.copyTo(it) } }
                    // Prova: se Android non lo legge, meglio dirlo subito.
                    android.graphics.Typeface.createFromFile(out)
                    out
                }
            }
            f.onSuccess { file ->
                fonts = (fonts + file.absolutePath).distinct().sorted()
                updateText { it.copy(fontFile = file.absolutePath) }
                message = "Font aggiunto: ${file.nameWithoutExtension}."
            }.onFailure { message = "Font non leggibile: ${it.message}" }
        }
    }

    fun removeFont(path: String) {
        File(path).delete()
        fonts = fonts - path
        update { p -> p.copy(texts = p.texts.map { if (it.fontFile == path) it.copy(fontFile = null) else it }) }
    }

    // ---------------- File spariti, copie di sicurezza ----------------

    /** File del progetto che non si trovano più (spostati, rinominati o cancellati). */
    var missingFiles by mutableStateOf<List<String>>(emptyList())
        private set

    /** Come si chiamava un file sparito (dalle clip, dai livelli o dai brani). */
    fun missingInfo(uri: String): MediaRef {
        val p = project ?: return MediaRef(uri)
        p.clips.firstOrNull { it.uri == uri && it.fileName.isNotBlank() }?.let { return MediaRef(uri, it.fileName, it.fileSize) }
        p.layers.firstOrNull { it.uri == uri && it.fileName.isNotBlank() }?.let { return MediaRef(uri, it.fileName, it.fileSize) }
        p.music.firstOrNull { it.uri == uri }?.let { return MediaRef(uri, it.name) }
        // Senza nome registrato: si dice almeno quale elemento è.
        p.clips.indexOfFirst { it.uri == uri }.takeIf { it >= 0 }?.let { i ->
            return MediaRef(uri, (if (p.clips[i].isPhoto) "Foto" else "Video") + " della clip ${i + 1}")
        }
        p.layers.firstOrNull { it.uri == uri }?.let { return MediaRef(uri, "Livello «${it.name}»") }
        return MediaRef(uri)
    }

    /** Ultima risorsa: lo stesso video riconosciuto da durata e risoluzione (quando nome e peso non si conoscono). */
    private fun videoLookalike(ctx: android.content.Context, uri: String): String? {
        val p = project ?: return null
        p.clips.firstOrNull { it.uri == uri && !it.isPhoto }?.let { return Backup.findVideo(ctx, it.sourceDurationUs, it.width, it.height) }
        p.layers.firstOrNull { it.uri == uri && it.isVideo }?.let { return Backup.findVideo(ctx, it.sourceDurationUs, it.width, it.height) }
        return null
    }

    /** Nome che si cerca nel telefono: solo quello vero del file (non le etichette "Video della clip…"). */
    private fun searchInfo(uri: String): MediaRef {
        val p = project ?: return MediaRef(uri)
        p.clips.firstOrNull { it.uri == uri && it.fileName.isNotBlank() }?.let { return MediaRef(uri, it.fileName, it.fileSize) }
        p.layers.firstOrNull { it.uri == uri && it.fileName.isNotBlank() }?.let { return MediaRef(uri, it.fileName, it.fileSize) }
        val size = p.clips.firstOrNull { it.uri == uri }?.fileSize ?: p.layers.firstOrNull { it.uri == uri }?.fileSize ?: 0L
        p.music.firstOrNull { it.uri == uri }?.let { return MediaRef(uri, it.name) }
        return MediaRef(uri, "", size)
    }

    private fun relink(map: Map<String, String>) {
        if (map.isEmpty()) return
        val ctx = getApplication<Application>()
        // Nome e peso del file nuovo: servono per ritrovarlo se si sposterà ancora.
        val info = map.values.associateWith { runCatching { Backup.describe(ctx, it) }.getOrNull() }
        update { p ->
            ProjectRefs.replace(p) { r -> map[r] }.let { q ->
                q.copy(
                    clips = q.clips.map { c -> info[c.uri]?.takeIf { it.name.isNotBlank() }?.let { c.copy(fileName = it.name, fileSize = it.size) } ?: c },
                    layers = q.layers.map { l -> info[l.uri]?.takeIf { it.name.isNotBlank() }?.let { l.copy(fileName = it.name, fileSize = it.size) } ?: l },
                )
            }
        }
        missingFiles = missingFiles - map.keys
        unreadable.removeAll(map.keys)
    }

    /** Esito dell'ultima ricerca, mostrato nella finestra "File non trovati". */
    var relinkNote by mutableStateOf<String?>(null)
        private set

    /** Cerca nel telefono (per nome e per peso) i file spariti; [quiet] = automatica, parla solo se trova qualcosa. */
    fun autoRelink(quiet: Boolean = false) {
        val ctx = getApplication<Application>()
        val todo = missingFiles
        if (todo.isEmpty()) { if (!quiet) message = "Tutti i file sono al loro posto."; return }
        viewModelScope.launch {
            if (!quiet) busy = "Cerco i file nel telefono…"
            val found = withContext(Dispatchers.IO) { todo.mapNotNull { u -> (Backup.findByName(ctx, searchInfo(u)) ?: videoLookalike(ctx, u))?.let { u to it } }.toMap() }
            busy = null
            val names = found.keys.map { missingInfo(it).name.ifBlank { "il file" } }
            relink(found)
            // L'avviso tecnico dell'anteprima non serve più: o il file è tornato, o c'è il pulsante Ricollega.
            if (previewError?.startsWith("Un file del progetto") == true) previewError = null
            if (found.isNotEmpty() && player != null) rebuildPreview(playheadUs / 1000)
            val left = todo.filter { it !in found }.map { missingInfo(it).name.ifBlank { "un file senza nome" } }
            relinkNote = when {
                left.isEmpty() -> null
                !it.fabriziodavi.vivovideo.media.Gallery.fullAccess(ctx) ->
                    "Non trovo ${left.joinToString { "«$it»" }}: l'app vede solo le foto e i video che le hai permesso. Tocca «Scegli» e indicalo tu."
                else -> "Non trovo ${left.joinToString { "«$it»" }} nel telefono (forse è stato cancellato, o rinominato con un altro nome). Tocca «Scegli» e indicalo tu."
            }
            message = when {
                found.isNotEmpty() && left.isEmpty() -> "Ritrovat${if (names.size == 1) "o ${names[0].let { "«$it»" }}" else "i tutti i ${names.size} file"}: il progetto è a posto."
                found.isNotEmpty() -> "Ritrovati ${found.size} file; ${relinkNote}"
                quiet -> null
                else -> relinkNote
            } ?: message
        }
    }

    /** Il file sparito [old] ora è [new] (scelto dall'utente). */
    fun relinkManual(old: String, new: Uri) {
        val ctx = getApplication<Application>()
        runCatching { ctx.contentResolver.takePersistableUriPermission(new, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        // Indirizzo della galleria quando possibile: quello del documento può smettere di funzionare dopo un riavvio.
        val stable = Backup.stableUri(ctx, new)
        val name = missingInfo(old).name
        relink(mapOf(old to stable))
        if (missingFiles.isEmpty()) relinkNote = null
        previewError = null
        rebuildPreview(playheadUs / 1000)
        message = if (name.isNotBlank()) "«$name» ricollegato." else "File ricollegato."
    }

    /** Copia di sicurezza del progetto in [target] (file .vivo scelto dall'utente). */
    fun exportBackup(target: Uri, withMedia: Boolean) {
        val p = project ?: return
        store.save(p)
        val ctx = getApplication<Application>()
        runClipWork(if (withMedia) "Salvo il progetto con tutti i file…" else "Salvo il progetto…") { prog ->
            withContext(Dispatchers.IO) {
                ctx.contentResolver.openOutputStream(target, "wt")!!.use { out -> Backup.export(ctx, p, withMedia, out) { f -> prog(f) } }
            }
            message = "Copia di sicurezza salvata."
        }
    }

    /** Apre una copia di sicurezza (.vivo) come nuovo progetto. */
    fun importBackup(source: Uri) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            busy = "Apro la copia di sicurezza…"
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val imp = ctx.contentResolver.openInputStream(source)!!.use { Backup.import(ctx, it, store.list().map { p -> p.id }.toSet()) }
                    // Nomi dei file originali (per ritrovarli in questo telefono).
                    val byUri = imp.media.associateBy { it.uri }
                    imp.project.copy(
                        clips = imp.project.clips.map { c -> byUri[c.uri]?.takeIf { c.fileName.isBlank() }?.let { c.copy(fileName = it.name, fileSize = it.size) } ?: c },
                        layers = imp.project.layers.map { l -> byUri[l.uri]?.takeIf { l.fileName.isBlank() }?.let { l.copy(fileName = it.name, fileSize = it.size) } ?: l },
                    )
                }
            }
            busy = null
            r.onSuccess { p -> store.save(p); refreshProjects(); openProject(p); message = "Progetto aperto dalla copia di sicurezza." }
                .onFailure { message = "Non riesco ad aprire questa copia: ${it.message}" }
        }
    }

    /** C'è almeno un video HDR: si può esportare in HDR. */
    val hasHdr: Boolean get() = project?.clips?.any { it.hdr > 0 } == true

    fun closeProject() {
        cancelPrerender()
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

    /** Legge un video o una foto e ne fa una clip (null se il file non si legge). */
    private fun importClip(ctx: Application, uri: Uri): Clip? {
        // Il permesso resta valido anche dopo il riavvio del telefono.
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        if (ctx.contentResolver.getType(uri)?.startsWith("image/") == true) {
            return runCatching {
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
        return runCatching {
            val info = readVideoInfo(ctx, uri)
            Clip(
                uri = uri.toString(),
                sourceDurationUs = info.durationMs * 1000,
                log = info.tenBit && !info.isHdr,
                hdr = info.hdrKind,
                fileName = Backup.describe(ctx, uri.toString()).name,
                fileSize = Backup.describe(ctx, uri.toString()).size,
                width = info.displayWidth,
                height = info.displayHeight,
                fps = info.fps ?: 30f,
            )
        }.getOrNull()
    }

    /** Scelta degli album interi: per un nuovo progetto o da aggiungere a quello aperto. */
    var albumPick by mutableStateOf<Boolean?>(null) // true = nuovo progetto, false = aggiungi
    fun openAlbumPicker(newProject: Boolean) { pause(); albumPick = newProject }

    /** Elementi degli album scelti (in ordine di data) nel progetto. */
    fun addAlbumItems(items: List<GalleryItem>) {
        val forNew = albumPick ?: return
        albumPick = null
        val uris = items.map { it.uri }
        if (uris.isEmpty()) return
        if (forNew) newProject(uris) else addClips(uris)
    }

    fun addClips(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val clips = ArrayList<Clip>()
            uris.forEachIndexed { i, u ->
                busy = if (uris.size > 3) "Lettura di video e foto… ${i + 1} di ${uris.size}" else "Lettura di video e foto…"
                withContext(Dispatchers.IO) { importClip(ctx, u) }?.let { clips += it }
            }
            busy = null
            if (clips.size < uris.size) message = "${uris.size - clips.size} file non leggibili sono stati saltati."
            if (clips.isEmpty()) return@launch
            val p = project ?: return@launch
            // Inserisce dopo la clip selezionata, o in fondo.
            val at = p.clips.indexOfFirst { it.id == selectedClipId }.let { if (it < 0) p.clips.size else it + 1 }
            // Le nuove clip seguono le preimpostazioni di formato (riempi / sfondo sfocato secondo l'orientamento).
            val ids = clips.map { it.id }.toSet()
            update { it.addClips(clips, at).applyFramePresets(ids) }
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
    /** Il testo selezionato inizia (si sposta) o finisce all'indicatore: per piazzarlo preciso senza trascinare. */
    fun textToPlayhead(start: Boolean) {
        val p = project ?: return
        val t = selectedText ?: return
        val (st, du) = p.snapToPlayhead(t.startUs, t.durationUs, playheadUs, start, TextItem.MIN_US)
        update { it.replaceText(t.copy(startUs = st, durationUs = du)) }
    }

    /** Lo stesso per il livello selezionato. */
    fun layerToPlayhead(start: Boolean) {
        val p = project ?: return
        val l = selectedLayer ?: return
        val (st, du) = p.snapToPlayhead(l.startUs, l.durationUs, playheadUs, start, Layer.MIN_US)
        update { it.replaceLayer(l.copy(startUs = st, durationUs = if (l.isVideo) minOf(du, l.sourceDurationUs - l.sourceStartUs).coerceAtLeast(Layer.MIN_US) else du)) }
    }

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
                            fileName = Backup.describe(ctx, uri.toString()).name,
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
    // ---------------- Libreria in rete ----------------

    /** Libreria aperta: per musica o effetti, e se il brano va al video automatico invece che al progetto. */
    data class LibraryRequest(val effects: Boolean, val forAuto: Boolean = false)
    var library by mutableStateOf<LibraryRequest?>(null)
    /** Brano scelto in libreria per il video automatico (lo raccoglie la sua schermata). */
    var autoMusicPicked by mutableStateOf<Uri?>(null)
    /** Nome e crediti dei file scaricati, per indirizzo: li usa la lettura del brano. */
    private val onlineInfo = HashMap<String, Pair<String, String>>()
    var downloading by mutableStateOf<String?>(null)
        private set

    fun openLibrary(effects: Boolean, forAuto: Boolean = false) {
        pause()
        library = LibraryRequest(effects, forAuto)
    }

    /** Scarica il brano scelto e lo mette nel progetto (dall'indicatore) o lo passa al video automatico. */
    fun useOnline(t: OnlineTrack) {
        val req = library ?: return
        val ctx = getApplication<Application>()
        if (downloading != null) return
        viewModelScope.launch {
            downloading = t.id
            val f = runCatching { Library.download(ctx, t) }
            downloading = null
            val file = f.getOrElse { message = "Non sono riuscito a scaricare \"${t.title}\": ${it.message}"; return@launch }
            val uri = Uri.fromFile(file)
            onlineInfo[uri.toString()] = (if (t.artist.isBlank()) t.title else "${t.title} – ${t.artist}") to t.credit
            library = null
            if (req.forAuto) autoMusicPicked = uri else addMusic(uri)
        }
    }

    /** Legge un brano (anche MIDI, convertito in WAV con i battiti esatti). Dall'inizio, a tutta lunghezza. */
    private suspend fun readMusic(ctx: Application, uri: Uri): MusicTrack? = withContext(Dispatchers.IO) {
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        runCatching {
            val (name, infoUs) = readAudioInfo(ctx, uri)
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
                    uri = Uri.fromFile(wav).toString(), name = name, sourceDurationUs = durUs, lengthUs = durUs,
                    beatsUs = midi?.beatsUs?.filter { it < durUs }.orEmpty(),
                    bpm = midi?.bpm ?: 0f,
                )
            } else {
                MusicTrack(uri = uri.toString(), name = name, sourceDurationUs = infoUs, lengthUs = infoUs)
            }
        }.getOrNull()?.takeIf { it.sourceDurationUs > 0 }?.let { t ->
            onlineInfo[uri.toString()]?.let { (name, credit) -> t.copy(name = name, credit = credit) } ?: t
        }
    }

    /** Battiti di un brano (null se non c'è un ritmo chiaro). */
    private suspend fun findBeats(ctx: Application, t: MusicTrack): BeatDetector.Result? = findBeatsAndEnergy(ctx, t)?.first

    /** Battiti e, per la regia automatica, l'energia del brano a ogni battito. */
    private suspend fun findBeatsAndEnergy(ctx: Application, t: MusicTrack): Pair<BeatDetector.Result, List<Float>>? = withContext(Dispatchers.Default) {
        runCatching {
            val stereo = decodeAudioRange(ctx, Uri.parse(t.uri), 0, t.sourceDurationUs) ?: return@runCatching null
            val mono = BeatDetector.toMono(stereo)
            val r = BeatDetector.detect(mono, 48_000).takeIf { it.beatsUs.size >= 4 } ?: return@runCatching null
            r to BeatDetector.energy(mono, 48_000, r.beatsUs)
        }.getOrNull()
    }

    // ---------------- Video automatico ----------------

    var showAuto by mutableStateOf(false)

    /**
     * Crea un progetto completo dagli elementi della galleria scelti: seleziona, importa, analizza il ritmo
     * del brano e monta con la regia dello stile scelto. [fitMusic] = durata come il brano.
     */
    /** [handPicked] = foto e video scelti uno per uno: si usano tutti (niente scarto delle raffiche né del verso). */
    fun autoCreate(items: List<GalleryItem>, musicUri: Uri?, options: AutoOptions, fitMusic: Boolean, handPicked: Boolean = false) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            var music: MusicTrack? = null
            if (musicUri != null) {
                busy = "Lettura del brano…"
                music = readMusic(ctx, musicUri)
                if (music == null) { busy = null; message = "Questo file audio non si può leggere."; return@launch }
            }
            // Prima il ritmo: serve a sapere quante scene ci stanno nel brano.
            val m1 = music
            if (m1 != null && (m1.beatsUs.isEmpty() || m1.energy.isEmpty())) {
                busy = "Analisi del ritmo…"
                val r = findBeatsAndEnergy(ctx, m1)
                if (r != null) music = m1.copy(beatsUs = r.first.beatsUs, bpm = r.first.bpm, energy = r.second)
            }
            val o = music?.let { m -> if (fitMusic) options.copy(targetUs = m.sourceDurationUs) else null } ?: options
            // Le fonti si ricordano (al massimo 1500, distribuite nel tempo) per poter fare altre proposte.
            val all = if (items.size <= 1500) items else (0 until 1500).map { items[(it.toLong() * (items.size - 1) / 1499).toInt()] }
            val raw = all.map { AutoSource(it.uri.toString(), it.isVideo, it.durationMs * 1000, it.dateMs, it.width, it.height) }
            val sources = if (handPicked) raw.sortedBy { it.dateMs } else chooseSources(ctx, raw, o, music)
            val picked = AutoEdit.pick(sources.map { it.item() }, o.style, o.targetUs, slotUs = AutoEdit.beatSlotUs(o.style, music)).map { sources[it] }
            if (picked.isEmpty()) { busy = null; message = "Fra quelli scelti non ci sono foto o video."; return@launch }
            val imported = importSources(ctx, picked, emptyMap())
            val clips = picked.mapNotNull { imported[it.uri] }
            if (clips.isEmpty()) { busy = null; message = "Non sono riuscito a leggere le foto e i video scelti."; return@launch }
            val m0 = music
            if (m0 != null && m0.beatsUs.isEmpty()) {
                busy = "Analisi del ritmo…"
                val r = findBeats(ctx, m0)
                if (r != null) music = m0.copy(beatsUs = r.beatsUs, bpm = r.bpm)
            }
            val scans = smartScans(ctx, clips, o.smart)
            busy = "Montaggio…"
            val name = options.title.ifBlank { "Video automatico" }.take(40)
            // Data vera di ogni elemento (per i temi con la data della videocamera), per indirizzo della clip.
            val dates = picked.mapNotNull { s -> imported[s.uri]?.let { it.uri to s.dateMs } }.toMap()
            val built = withContext(Dispatchers.Default) { AutoEdit.build(name, clips, music, o, scans, dates) }
            var p = built.copy(auto = AutoRecipe(sources, o, music, round = 1, imported = imported))
            if (clips.any { it.log }) p = p.copy(color = p.color.copy(denoise = 40))
            busy = null
            showAuto = false
            store.save(p)
            openProject(p)
            message = "Video pronto: ${p.clips.size} scene, ${formatTime(p.durationUs)}." +
                (if (clips.size < picked.size) " ${picked.size - clips.size} file non leggibili saltati." else "") +
                " Guardalo in anteprima, ritocca quello che vuoi ed esporta."
        }
    }

    /**
     * Fonti del video automatico: delle raffiche si tiene la foto migliore, e si preferiscono foto e video
     * nello stesso verso del video finale.
     */
    private suspend fun chooseSources(ctx: Application, raw: List<AutoSource>, o: AutoOptions, music: MusicTrack?): List<AutoSource> {
        val photos = raw.filter { !it.isVideo }.sortedBy { it.dateMs }
        // Solo le foto che hanno una vicina a pochi secondi (le raffiche) vanno valutate.
        val inBurst = photos.filterIndexed { i, s ->
            (i > 0 && s.dateMs - photos[i - 1].dateMs <= 4_000) || (i < photos.size - 1 && photos[i + 1].dateMs - s.dateMs <= 4_000)
        }.take(240)
        val samples = HashMap<String, MomentSample>()
        if (o.smart) inBurst.forEachIndexed { i, s ->
            busy = "Scelgo le foto migliori… ${i + 1} di ${inBurst.size}"
            runCatching { SmartAnalyzer.scan(ctx, s.uri, true, 0L) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                .getOrNull()?.samples?.firstOrNull()?.let { samples[s.uri] = it }
        }
        // Le foto di una raffica si confrontano fra loro (stessa scena): vince la più nitida, con occhi aperti e sorrisi.
        val deduped = AutoEdit.dedupeBurstsBy(raw, { g -> Moments.burstScores(g.map { samples[it.uri] }) })
        val slot = AutoEdit.beatSlotUs(o.style, music) ?: o.style.photoUs
        val wanted = (o.targetUs / slot.coerceAtLeast(500_000L)).toInt().coerceAtLeast(4)
        return AutoEdit.preferOrientation(deduped, o.format, wanted)
    }

    /** Regia intelligente: analisi di foto e video (momenti migliori, volti); quelle già fatte sono immediate. */
    private suspend fun smartScans(ctx: Application, clips: List<Clip>, smart: Boolean): Map<String, MomentScan> {
        if (!smart) return emptyMap()
        val out = HashMap<String, MomentScan>()
        clips.distinctBy { it.uri }.forEachIndexed { i, c ->
            busy = "Cerco i momenti migliori… ${i + 1} di ${clips.size}"
            runCatching { SmartAnalyzer.scan(ctx, c.uri, c.isPhoto, c.sourceDurationUs) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                .getOrNull()?.let { out[c.uri] = it }
        }
        return out
    }

    /** Importa le fonti che mancano (con avanzamento) e restituisce tutte quelle importate, per indirizzo. */
    private suspend fun importSources(ctx: Application, picked: List<AutoSource>, known: Map<String, Clip>): Map<String, Clip> {
        val out = HashMap(known)
        val missing = picked.filter { it.uri !in out }
        missing.forEachIndexed { i, src ->
            busy = "Preparo foto e video… ${i + 1} di ${missing.size}"
            withContext(Dispatchers.IO) { importClip(ctx, Uri.parse(src.uri)) }?.let { out[src.uri] = it }
        }
        return out
    }

    /**
     * Altra proposta del video automatico (anche con un altro stile): scene, tagli, transizioni ed effetti nuovi.
     * È una modifica come le altre: Annulla torna alla proposta di prima.
     */
    fun anotherProposal(style: AutoStyle? = null) {
        val ctx = getApplication<Application>()
        val p = project ?: return
        val r = p.auto ?: return
        if (busy != null) return
        viewModelScope.launch {
            val round = r.round + 1
            val o = r.options.copy(style = style ?: r.options.style, seed = r.options.seed + 7919 * round, variant = round)
            val picked = AutoEdit.pick(r.sources.map { it.item() }, o.style, o.targetUs, variant = round, slotUs = AutoEdit.beatSlotUs(o.style, r.music)).map { r.sources[it] }
            val imported = importSources(ctx, picked, r.imported)
            val clips = picked.mapNotNull { imported[it.uri] }
            val scans = smartScans(ctx, clips, o.smart)
            busy = "Nuova proposta…"
            val dates = picked.mapNotNull { s -> imported[s.uri]?.let { it.uri to s.dateMs } }.toMap()
            val built = withContext(Dispatchers.Default) { AutoEdit.build(p.name, clips, r.music, o, scans, dates) }
            busy = null
            if (built.clips.isEmpty()) { message = "Non sono riuscito a leggere le foto e i video degli album."; return@launch }
            pause()
            update { cur ->
                cur.copy(clips = built.clips, texts = built.texts, music = built.music, format = built.format,
                    auto = r.copy(options = o, round = round, imported = imported))
            }
            selectedClipId = null
            seekTo(0)
        }
    }

    fun addMusic(uri: Uri) {
        val ctx = getApplication<Application>()
        val p = project ?: return
        if (p.clips.isEmpty()) { message = "Aggiungi prima un video."; return }
        viewModelScope.launch {
            busy = "Lettura del brano…"
            val total = p.durationUs
            val start = if (total - playheadUs < 1_000_000) 0L else playheadUs
            val track = readMusic(ctx, uri)?.let { it.copy(startUs = start, lengthUs = minOf(it.sourceDurationUs, total - start)) }
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
            val r = findBeats(ctx, t)
            busy = null
            if (r == null) { message = "Non ho trovato un ritmo chiaro in questo brano."; return@launch }
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
    /** "TAP a tempo" taglia anche le clip (i tagli si fanno tutti insieme alla pausa, per non far scattare l'anteprima). */
    var tapCuts by mutableStateOf(true)
    private val pendingCuts = mutableListOf<Long>()
    val pendingCutCount: Int get() = pendingCutsState
    private var pendingCutsState by mutableStateOf(0)

    fun tapBeat() {
        val t = selectedMusic ?: return
        val now = player?.currentPosition?.let { it * 1000 } ?: playheadUs
        if (tapCuts) { pendingCuts += now; pendingCutsState = pendingCuts.size }
        val src = musicSrcAt(t, now) ?: return
        update(keepUndo = false, rebuild = false) { p ->
            val cur = p.music.firstOrNull { it.id == t.id } ?: return@update null
            p.replaceMusic(cur.addMarker(src))
        }
    }

    /** Fa in un colpo solo (un solo Annulla) i tagli segnati con i tocchi a tempo. */
    fun applyTapCuts() {
        if (pendingCuts.isEmpty()) return
        val cuts = pendingCuts.sorted()
        pendingCuts.clear(); pendingCutsState = 0
        val p = project ?: return
        var q = p
        var n = 0
        cuts.forEach { c -> q.splitAt(c)?.let { q = it; n++ } }
        if (n == 0) return
        update { q }
        message = "$n ${if (n == 1) "taglio fatto" else "tagli fatti"} sui tuoi tocchi a tempo. Annulla li toglie tutti insieme."
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

    /**
     * Divide i video sui battiti di tutti i brani del progetto (non solo di quello selezionato): i brani di cui
     * non si conoscono ancora i battiti vengono analizzati prima. Un solo Annulla toglie tutti i tagli.
     */
    fun splitOnBeats() {
        val p0 = project ?: return
        val songs = p0.music.filter { !it.dialog }
        if (songs.isEmpty()) { message = "Aggiungi prima un brano."; return }
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            var unclear = 0
            val todo = songs.filter { !it.hasBeats }
            todo.forEachIndexed { i, t ->
                busy = "Analisi del ritmo… brano ${i + 1} di ${todo.size}"
                val r = findBeats(ctx, t)
                if (r == null) unclear++
                else update(keepUndo = false, rebuild = false) { p ->
                    val cur = p.music.firstOrNull { it.id == t.id } ?: return@update null
                    p.replaceMusic(cur.copy(beatsUs = r.beatsUs, bpm = r.bpm))
                }
            }
            busy = null
            val p = project ?: return@launch
            val before = p.clips.size
            val skipped = if (unclear > 0) " ($unclear ${if (unclear == 1) "brano senza" else "brani senza"} un ritmo chiaro: lì niente tagli)" else ""
            val r = p.splitAtBeats()
            if (r == null) { message = "Nessun battito dentro i video$skipped."; return@launch }
            update { r }
            val brani = p.music.count { !it.dialog && it.hasBeats }
            message = "Tagliato su ogni battito di $brani ${if (brani == 1) "brano" else "brani"}: da $before a ${r.clips.size} clip$skipped. " +
                "Elimina o sostituisci i pezzi che non ti servono; un Annulla toglie tutti i tagli." +
                (if (r.clips.size > 600) " Sono tanti: con «Ogni 2» o «Ogni 4» nella scheda Ritmo i tagli si diradano." else "")
        }
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

    // ---------------- Fermo immagine, al contrario, stabilizzazione ----------------

    /** Lavoro lungo su una clip (al contrario, stabilizzazione): testo e avanzamento 0…1. */
    var clipWork by mutableStateOf<String?>(null)
        private set
    var clipWorkProgress by mutableStateOf(0f)
        private set
    private var clipJob: kotlinx.coroutines.Job? = null

    /** Clip su cui lavorano gli strumenti: quella selezionata, altrimenti quella sotto l'indicatore. */
    private fun workClip(): Clip? = selectedClip ?: project?.let { p -> p.locate(playheadUs)?.let { p.clips[it.first] } }

    fun cancelClipWork() { clipJob?.cancel() }

    private fun runClipWork(label: String, block: suspend (onProgress: (Float) -> Unit) -> Unit) {
        if (clipWork != null) return
        pause()
        cancelPrerender()
        val pos = playheadUs
        // Decoder ed encoder servono al lavoro: l'anteprima si ferma e riparte alla fine.
        releasePlayer()
        playheadUs = pos
        clipWork = label; clipWorkProgress = 0f
        clipJob = viewModelScope.launch {
            try {
                block { p -> viewModelScope.launch { clipWorkProgress = p } }
            } catch (e: kotlinx.coroutines.CancellationException) {
                message = "Annullato."
            } catch (e: Throwable) {
                message = "Non riuscito: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                clipWork = null; clipJob = null
                rebuildPreview(playheadUs / 1000)
            }
        }
    }

    /** Fermo immagine all'indicatore: il fotogramma diventa una foto di 2 secondi in mezzo alla clip. */
    fun freezeFrame() {
        val p = project ?: return
        val (i, off) = p.locate(playheadUs) ?: return
        val c = p.clips[i]
        if (c.isPhoto) { message = "Il fermo immagine si fa sui video."; return }
        val ctx = getApplication<Application>()
        val at = playheadUs
        viewModelScope.launch {
            val made = withContext(Dispatchers.IO) {
                runCatching {
                    val src = c.trimStartUs + c.speedCurve().sourceTimeAt(off)
                    val r = android.media.MediaMetadataRetriever()
                    val bmp = (try {
                        r.setDataSource(ctx, Uri.parse(c.uri))
                        val long = maxOf(c.width, c.height).coerceAtLeast(1)
                        val k = minOf(1f, 3840f / long)
                        r.getScaledFrameAtTime(src, android.media.MediaMetadataRetriever.OPTION_CLOSEST, (c.width * k).toInt().coerceAtLeast(2), (c.height * k).toInt().coerceAtLeast(2))
                    } finally { r.release() }) ?: error("fotogramma non leggibile")
                    val f = File(File(ctx.filesDir, "fermi").apply { mkdirs() }, "fermo_${System.currentTimeMillis()}.jpg")
                    f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it) }
                    Triple(f, bmp.width, bmp.height).also { bmp.recycle() }
                }
            }
            made.onSuccess { (f, w, h) ->
                val photo = ClipTools.freezeClip(c, Uri.fromFile(f).toString(), w, h)
                update { it.insertAt(at, photo) }
                selectedClipId = photo.id
                message = "Fermo immagine aggiunto (2 s): allungalo o accorcialo come una foto."
            }.onFailure { message = "Fermo immagine non riuscito: ${it.message}" }
        }
    }

    /** Al contrario (o di nuovo nel verso normale) la clip selezionata. */
    fun toggleReverse() {
        val c = workClip() ?: return
        if (c.isPhoto) { message = "Le foto non hanno un verso."; return }
        if (c.reversed != null) {
            update { it.replaceClip(ClipTools.unreversed(c)) }
            message = "Clip di nuovo nel verso normale."
            return
        }
        if (c.trimmedUs > Reverser.MAX_US) { message = "Al contrario si possono mettere clip fino a 60 secondi: accorciala prima."; return }
        val ctx = getApplication<Application>()
        runClipWork("Metto la clip al contrario…") { prog ->
            val r = Reverser.reverse(ctx, c, prog)
            val now = project?.clips?.firstOrNull { it.id == c.id }
            if (now == null || now.uri != c.uri) { r.file.delete(); return@runClipWork }
            update { it.replaceClip(ClipTools.reversed(now, Uri.fromFile(r.file).toString(), r.durationUs, r.width, r.height)) }
            message = "Clip al contrario. Si torna indietro da qui o con Annulla."
        }
    }

    /** Misura il movimento della clip e lo leviga. */
    fun stabilize(strength: Float = 0.6f) {
        val c = workClip() ?: return
        if (c.isPhoto) { message = "La stabilizzazione è per i video."; return }
        val ctx = getApplication<Application>()
        runClipWork("Misuro il movimento della camera…") { prog ->
            val s = Stabilizer.analyze(ctx, c, strength, prog)
            val now = project?.clips?.firstOrNull { it.id == c.id } ?: return@runClipWork
            if (now.uri != c.uri || now.trimStartUs != c.trimStartUs || now.trimEndUs != c.trimEndUs) { message = "La clip è cambiata: riprova."; return@runClipWork }
            update { it.replaceClip(now.copy(stab = s)) }
            message = "Clip stabilizzata (zoom ${"%.0f".format((StabMath.plan(s).zoom - 1) * 100)}%)."
        }
    }

    // ---------------- Funzioni intelligenti ----------------

    /** Tiene la stessa durata ma sposta il taglio sul tratto migliore del video; ripetuto, propone il successivo. */
    fun bestMoment() {
        val c = workClip() ?: return
        if (c.isPhoto) { message = "Il momento migliore si cerca nei video."; return }
        if (c.trimmedUs >= c.sourceDurationUs - 100_000) { message = "La clip usa già tutto il video: accorciala prima, poi cerco il tratto migliore di quella durata."; return }
        val ctx = getApplication<Application>()
        runClipWork("Cerco il momento migliore…") { prog ->
            prog(0.02f)
            val scan = SmartAnalyzer.scan(ctx, c.uri, false, c.sourceDurationUs) { prog(0.02f + 0.95f * it) }
            val best = Moments.bestStarts(scan, c.trimmedUs, 3)
            val now = project?.clips?.firstOrNull { it.id == c.id } ?: return@runClipWork
            val cur = best.indexOfFirst { kotlin.math.abs(it - now.trimStartUs) < 250_000 }
            val k = (cur + 1) % best.size
            val start = best[k]
            update { it.replaceClip(now.copy(trimStartUs = start, trimEndUs = start + now.trimmedUs)) }
            val why = Moments.describe(scan, start, now.trimmedUs)
            message = "Tratto ${k + 1} di ${best.size}" + (if (why.isNotBlank()) ": $why." else " per qualità (nitidezza, luce, volti, camera ferma).") +
                (if (best.size > 1) " Tocca di nuovo per il successivo." else "")
        }
    }

    /** Segui il soggetto: riempie il quadro e sposta l'inquadratura seguendo volti o soggetto. */
    fun followSubject() {
        val c = workClip() ?: return
        val ctx = getApplication<Application>()
        runClipWork("Cerco il soggetto…") { prog ->
            prog(0.1f)
            val scan = SmartAnalyzer.scan(ctx, c.uri, c.isPhoto, c.sourceDurationUs)
            val path = Moments.subjectPath(scan)
            val now = project?.clips?.firstOrNull { it.id == c.id } ?: return@runClipWork
            if (path == null) { message = "Non ho trovato un soggetto chiaro da seguire."; return@runClipWork }
            update { it.replaceClip(now.withMode(FrameMode.FILL).copy(reframe = path)) }
            message = if (scan.hasFaces) "Inquadratura che segue i volti." else "Nessun volto: seguo la zona con più dettagli."
        }
    }

    fun stopFollow() = updateSelected { it.copy(reframe = null) }

    /** Scontorno "Preciso" (modello di Google) invece di quello veloce del telefono. */
    var personPrecise by mutableStateOf(false)

    private fun personMessage(fx: it.fabriziodavi.vivovideo.model.PersonFx, ok: String) =
        if (personPrecise && !fx.precise) "Il modello preciso di Google non è disponibile ora (servono i servizi Google e internet la prima volta): ho usato quello veloce. $ok" else ok

    /** Scontorna la persona della clip (maschere calcolate una volta) e tratta lo sfondo come [mode]. */
    fun cutPerson(mode: PersonMode = PersonMode.BLUR) {
        val c = workClip() ?: return
        if (c.isPhoto) { message = "Lo scontorno della persona è per i video."; return }
        val ctx = getApplication<Application>()
        runClipWork(if (personPrecise) "Scontorno preciso…" else "Scontorno la persona…") { prog ->
            val from = (c.trimStartUs - 500_000).coerceAtLeast(0)
            val to = (c.trimEndUs + 500_000).coerceAtMost(c.sourceDurationUs)
            val fx = PersonMasks.analyze(ctx, c.uri, from, to, mode, prog, personPrecise) { st -> viewModelScope.launch { if (clipWork != null) clipWork = st } }
            val now = project?.clips?.firstOrNull { it.id == c.id } ?: return@runClipWork
            update { it.replaceClip(now.copy(person = fx)) }
            message = personMessage(fx, "Persona scontornata: scegli cosa fare dello sfondo.")
        }
    }

    // ---------------- Privacy: oscura volti, targhe, oggetti ----------------

    var redactFaces by mutableStateOf(true)
    var redactPlates by mutableStateOf(true)
    var redactStyle by mutableStateOf(it.fabriziodavi.vivovideo.model.RedactStyle.PIXEL)
    /** Oggetto scelto toccandolo nel fotogramma (null = nessuno) e tempo del file in cui è stato scelto. */
    var redactSeed by mutableStateOf<it.fabriziodavi.vivovideo.model.RBox?>(null)
    private var redactSeedSrcUs = 0L
    private var redactSeedClipId: String? = null
    /** Scelta dell'oggetto: fotogramma all'indicatore e oggetti trovati dentro. */
    var objectPick by mutableStateOf<android.graphics.Bitmap?>(null)
        private set
    var objectPickBoxes by mutableStateOf(listOf<it.fabriziodavi.vivovideo.model.RBox>())
        private set

    /** Apre la scelta dell'oggetto: prende il fotogramma all'indicatore e cerca gli oggetti. */
    fun openObjectPick() {
        val p = project ?: return
        val (i, off) = p.locate(playheadUs) ?: return
        val c = p.clips[i]
        val ctx = getApplication<Application>()
        pause()
        viewModelScope.launch {
            busy = "Cerco gli oggetti nel fotogramma…"
            val bmp = withContext(Dispatchers.IO) {
                runCatching {
                    if (c.isPhoto) it.fabriziodavi.vivovideo.decodePhotoThumb(Uri.parse(c.uri).path ?: "", 1080)
                    else {
                        val r = android.media.MediaMetadataRetriever()
                        try {
                            r.setDataSource(ctx, Uri.parse(c.uri))
                            val k = minOf(1f, 1080f / maxOf(c.width, c.height).coerceAtLeast(1))
                            r.getScaledFrameAtTime(c.trimStartUs + c.speedCurve().sourceTimeAt(off), android.media.MediaMetadataRetriever.OPTION_CLOSEST,
                                (c.width * k).toInt().coerceAtLeast(2), (c.height * k).toInt().coerceAtLeast(2))
                        } finally { r.release() }
                    }
                }.getOrNull()
            }
            if (bmp == null) { busy = null; message = "Non riesco a leggere il fotogramma."; return@launch }
            objectPickBoxes = runCatching { it.fabriziodavi.vivovideo.engine.Redactor.objectsIn(bmp) }.getOrDefault(emptyList())
            busy = null
            redactSeedSrcUs = if (c.isPhoto) 0L else c.trimStartUs + c.speedCurve().sourceTimeAt(off)
            redactSeedClipId = c.id
            selectedClipId = c.id
            objectPick = bmp
        }
    }

    /** Tocco sul fotogramma ([x], [y] fra 0 e 1): l'oggetto trovato lì, oppure un riquadro attorno al dito. */
    fun pickObjectAt(x: Float, y: Float) {
        val hit = objectPickBoxes.filter { it.contains(x, y) }.minByOrNull { it.w * it.h }
        redactSeed = hit ?: it.fabriziodavi.vivovideo.model.RBox((x - 0.1f).coerceIn(0f, 0.8f), (y - 0.1f).coerceIn(0f, 0.8f), 0.2f, 0.2f)
    }

    fun closeObjectPick() { objectPick = null }

    fun clearObject() { redactSeed = null }

    /** Analizza la clip e oscura ciò che è stato scelto (volti, targhe, oggetto). */
    fun redactClip() {
        val c = workClip() ?: return
        val seed = redactSeed?.takeIf { redactSeedClipId == c.id }
        if (!redactFaces && !redactPlates && seed == null) { message = "Scegli almeno cosa oscurare."; return }
        val ctx = getApplication<Application>()
        val want = it.fabriziodavi.vivovideo.model.Redaction(faces = redactFaces, plates = redactPlates, objectSeed = seed, style = redactStyle)
        runClipWork(if (c.isPhoto) "Cerco cosa oscurare…" else "Cerco cosa oscurare in ogni fotogramma…") { prog ->
            val r = it.fabriziodavi.vivovideo.engine.Redactor.analyze(ctx, c, want, redactSeedSrcUs, prog)
            val now = project?.clips?.firstOrNull { it.id == c.id } ?: return@runClipWork
            update { it.replaceClip(now.copy(redact = r)) }
            message = when {
                r.found == 0 -> "Non ho trovato volti, targhe o l'oggetto in questa clip."
                c.isPhoto -> "Oscurati ${r.found} elementi. Cambia lo stile qui sotto; Annulla per togliere."
                else -> "Fatto: qualcosa da oscurare in ${r.found} fotogrammi su ${r.count}. Guarda l'anteprima; Annulla per togliere."
            }
        }
    }

    fun changeRedactStyle(s: it.fabriziodavi.vivovideo.model.RedactStyle) {
        redactStyle = s
        updateSelected { c -> c.redact?.let { c.copy(redact = it.copy(style = s)) } ?: c }
    }

    fun removeRedact() = updateSelected { it.copy(redact = null) }

    fun setPersonMode(m: PersonMode) = updateSelected { c -> c.person?.let { c.copy(person = it.copy(mode = m)) } ?: c }
    fun setPersonColor(color: Int) = updateSelected { c -> c.person?.let { c.copy(person = it.copy(color = color)) } ?: c }
    fun removePerson() = updateSelected { it.copy(person = null) }

    /** Video sovrapposto: solo la persona (sfondo trasparente), per metterla sopra a un altro video. */
    fun cutLayerPerson() {
        val l = selectedLayer ?: return
        if (!l.isVideo) { message = "Lo scontorno della persona è per i video sovrapposti."; return }
        val ctx = getApplication<Application>()
        runClipWork(if (personPrecise) "Scontorno preciso…" else "Scontorno la persona…") { prog ->
            val fx = PersonMasks.analyze(ctx, l.uri, l.sourceStartUs, l.sourceStartUs + l.durationUs, PersonMode.REMOVE, prog, personPrecise) { st ->
                viewModelScope.launch { if (clipWork != null) clipWork = st }
            }
            val now = project?.layers?.firstOrNull { it.id == l.id } ?: return@runClipWork
            update { it.replaceLayer(now.copy(person = fx)) }
            message = personMessage(fx, "Persona scontornata: in anteprima si vede appena il tratto è pronto, nel video esportato sempre.")
        }
    }

    fun setStabStrength(v: Float) = updateSelected { c -> c.stab?.let { c.copy(stab = it.copy(strength = v.coerceIn(0f, 1f))) } ?: c }
    fun removeStab() = updateSelected { it.copy(stab = null) }

    // ---------------- Selezione multipla e copia/incolla degli attributi ----------------

    /** Clip scelte con la selezione multipla (null = selezione normale, una clip alla volta). */
    var multiIds by mutableStateOf<Set<String>?>(null)
        private set
    /** Clip da cui si sono copiati gli attributi. */
    var copiedStyle by mutableStateOf<Clip?>(null)
        private set

    fun startMultiSelect() { multiIds = setOfNotNull(selectedClipId) }
    fun endMultiSelect() { multiIds = null }
    fun selectAllClips() { multiIds = project?.clips?.map { it.id }?.toSet() }

    // ---------------- Rimescola clip ----------------

    /** Progetto prima dell'ultimo rimescolamento e quello uscito: "Rimescola ancora" riparte dal primo (un solo Annulla). */
    private var shuffleBase: Project? = null
    private var shuffleResult: Project? = null
    private var shuffleIds: Set<String>? = null

    /** Il progetto è ancora quello appena rimescolato: si può proporre un altro ordine. */
    val canShuffleAgain: Boolean get() = shuffleResult != null && project === shuffleResult

    /**
     * Rimescola le clip [ids] (null = tutte). Con le clip tagliate a ritmo cambia il contenuto e restano le durate.
     * Se il progetto è quello appena rimescolato, propone un altro ordine partendo da com'era prima.
     */
    fun shuffleClips(ids: Set<String>?) {
        val p = project ?: return
        if (busy != null || exportState is ExportState.Running) return
        val again = canShuffleAgain
        val base = if (again) shuffleBase ?: p else p
        val useIds = if (again) shuffleIds else ids
        if (useIds != null && useIds.size < 2) { message = "Scegli almeno due clip da rimescolare."; return }
        val r = Shuffle.apply(base, useIds, kotlin.random.Random.nextLong())
            ?: run { message = "Servono almeno due clip da rimescolare."; return }
        pause()
        // La seconda volta non si aggiunge un passo di Annulla: si torna sempre all'ordine di partenza.
        update(keepUndo = !again) { r.project }
        shuffleBase = base
        shuffleIds = useIds
        shuffleResult = project
        Diary.log("Rimescola: ${r.moved} clip spostate su ${useIds?.size ?: base.clips.size}, a ritmo ${r.onBeat}, video uguali vicini ${r.sameNeighbours}.")
        if (!again) message = buildString {
            append("Clip rimescolate (${r.moved} cambiate di posto). ")
            if (r.onBeat) append("Erano tagliate a ritmo: le durate restano, i tagli sono ancora sul battito. ")
            if (r.sameNeighbours > 0) append("Qualche pezzo dello stesso video è rimasto vicino: non c'erano abbastanza video diversi. ")
            append("«Rimescola ancora» per un altro ordine, Annulla per tornare com'era.")
        }
    }

    /** Tocco su una clip: con la selezione multipla la aggiunge o la toglie. */
    fun toggleMulti(id: String) {
        val m = multiIds ?: return
        multiIds = if (id in m) m - id else m + id
        selectedClipId = id
    }

    fun copyStyle() {
        val c = selectedClip ?: return
        copiedStyle = c
        // Si entra subito nella scelta delle clip: basta toccarle nella timeline e poi "Incolla attributi".
        if (multiIds == null) multiIds = emptySet()
        message = "Attributi copiati. Ora tocca nella timeline le clip su cui incollarli (o «Tutte»), poi «Incolla attributi»."
    }

    /** Clip su cui si incolla: quelle della selezione multipla, altrimenti quella selezionata. */
    private fun targetIds(): Set<String> = multiIds ?: setOfNotNull(selectedClipId)

    fun pasteStyle(attrs: Set<ClipAttr>) {
        val src = copiedStyle ?: return
        val ids = targetIds() - src.id
        if (ids.isEmpty() || attrs.isEmpty()) return
        update { p -> p.copy(clips = p.clips.map { if (it.id in ids) ClipStyle.paste(src, it, attrs) else it }) }
        message = "Attributi incollati su ${ids.size} clip."
    }

    fun deleteMulti() {
        val ids = multiIds ?: return
        if (ids.isEmpty()) return
        update { p -> p.copy(clips = p.clips.filterNot { it.id in ids }).takeIf { it.clips.isNotEmpty() } ?: run { message = "Deve restare almeno una clip."; null } }
        multiIds = emptySet()
        selectedClipId = project?.clips?.firstOrNull()?.id
    }

    /** Clip trascinata col dito in una nuova posizione (testi e livelli dentro la clip la seguono). */
    fun moveClipTo(id: String, to: Int) {
        update { p -> p.moveClipTo(id, to).takeIf { it != p } }
        selectedClipId = id
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

    /** Il video si apre dal nero sulla prima clip e si chiude al nero sull'ultima. */
    fun fadeVideoEnds(inUs: Long = 1_000_000, outUs: Long = 1_500_000) = update { p ->
        if (p.clips.isEmpty()) return@update null
        val first = p.clips.first().id
        val last = p.clips.last().id
        p.copy(clips = p.clips.map { c ->
            var x = c
            if (c.id == first) x = x.copy(fadeInUs = minOf(inUs, c.outputUs / 2))
            if (c.id == last) x = x.copy(fadeOutUs = minOf(outUs, c.outputUs / 2))
            x
        })
    }

    /** "Apri e chiudi il video" come interruttore: lo accende o toglie le due dissolvenze. */
    fun toggleVideoEnds() {
        val p = project ?: return
        if (!p.videoEndsFaded()) { fadeVideoEnds(); return }
        update { q ->
            val first = q.clips.first().id
            val last = q.clips.last().id
            q.copy(clips = q.clips.map { c ->
                var x = c
                if (c.id == first) x = x.copy(fadeInUs = 0)
                if (c.id == last) x = x.copy(fadeOutUs = 0)
                x
            })
        }
    }

    fun applyFxToAll() {
        val c = selectedClip ?: return
        update { p -> p.copy(clips = p.clips.map { it.copy(fx = c.fx) }) }
    }

    /** Copia le dissolvenze della clip selezionata (entrata, uscita, colore) su tutte le clip. */
    fun applyFadesToAll() {
        val c = selectedClip ?: return
        update { p -> p.copy(clips = p.clips.map { it.copy(fadeInUs = minOf(c.fadeInUs, it.outputUs / 2), fadeOutUs = minOf(c.fadeOutUs, it.outputUs / 2), fadeColor = c.fadeColor, fadeAudio = c.fadeAudio) }) }
    }

    /** Formato del video e preimpostazioni: si applicano subito a tutte le clip. */
    fun setFormat(format: VideoFormat? = null, same: FrameMode? = null, other: FrameMode? = null) = update { p ->
        p.copy(format = format ?: p.format, sameMode = same ?: p.sameMode, otherMode = other ?: p.otherMode).applyFramePresets()
    }

    // ---------------- Sottotitoli automatici ----------------

    var showSubtitles by mutableStateOf(false)
    private var subtitleJob: kotlinx.coroutines.Job? = null

    /**
     * Ascolta l'audio del video (clip, voci, audio staccato; la musica no) e crea i sottotitoli,
     * sostituendo quelli automatici di prima. Il modello si scarica la prima volta.
     */
    fun autoSubtitles(model: Whisper.Model, lang: String, style: SubStyle, pos: SubPosition) {
        val ctx = getApplication<Application>()
        val p = project ?: return
        if (p.clips.isEmpty() || busy != null) return
        pause()
        subtitleJob = viewModelScope.launch {
            try {
                if (!Whisper.isDownloaded(ctx, model)) {
                    busy = "Scarico il modello ${model.label} (${model.sizeMb} MB, solo la prima volta)…"
                    Whisper.download(ctx, model) { f -> handler.post { busy = "Scarico il modello ${model.label}… ${(f * 100).toInt()}%" } }
                }
                busy = "Preparo l'audio…"
                val voiceOnly = p.copy(music = p.music.filter { it.dialog })
                val pcm = withContext(Dispatchers.Default) {
                    val wav = renderTimelineAudio(ctx, voiceOnly, loudness = false)
                    try { Whisper.wavTo16kMono(wav) } finally { wav.delete() }
                }
                busy = "Ascolto il video… 0%"
                val poll = launch { while (true) { kotlinx.coroutines.delay(400); busy = "Ascolto il video… ${Whisper.progress()}%" } }
                val words = try { Whisper.transcribe(Whisper.modelFile(ctx, model), pcm, lang) } finally { poll.cancel() }
                busy = null
                val subs = Subtitles.build(words, style, pos, p.durationUs)
                if (subs.isEmpty()) { message = "Non ho sentito parole nel video (la musica non viene ascoltata, solo l'audio delle clip e le voci)."; return@launch }
                update { it.copy(texts = it.texts.filterNot { t -> t.subtitle } + subs) }
                showSubtitles = false
                message = "${subs.size} sottotitoli creati. Tocca un sottotitolo nella timeline per correggere una parola, spostarlo o cambiarne lo stile; Annulla li toglie tutti."
            } catch (e: kotlinx.coroutines.CancellationException) {
                busy = null; throw e
            } catch (e: Exception) {
                busy = null
                message = "Sottotitoli non riusciti: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun cancelSubtitles() {
        Whisper.abort()
        subtitleJob?.cancel()
        busy = null
    }

    /** Toglie tutti i sottotitoli automatici. */
    fun removeSubtitles() = update { it.copy(texts = it.texts.filterNot { t -> t.subtitle }) }

    // ---------------- Tutorial: zoom su un punto, evidenziazioni, passi ----------------

    /**
     * Zoom morbido sulla zona [spot] della clip sotto l'indicatore: arriva, resta [holdUs] e torna
     * (si vede in anteprima; i keyframe si ritoccano dallo strumento Keyframe).
     */
    fun zoomOnSpot(spot: it.fabriziodavi.vivovideo.model.Tutorial.Spot, scale: Float, holdUs: Long) {
        val p = project ?: return
        val (i, offsetOut) = p.locate(playheadUs) ?: return
        val clip = p.clips[i]
        val local = clip.speedCurve().sourceTimeAt(offsetOut)
        val keys = it.fabriziodavi.vivovideo.model.Tutorial.zoomKeyframes(clip.keyframes, local, clip.trimmedUs, spot.x, spot.y, scale, holdUs)
        update { it.replaceClip(clip.copy(keyframes = keys)) }
        selectedClipId = clip.id
        message = "Zoom aggiunto: guarda l'anteprima. Si ritocca dallo strumento Keyframe; Annulla lo toglie."
    }

    /** Freccia, cerchio, riquadro o numero come livello dall'indicatore (3 s), al centro: poi lo sposti con le dita. */
    fun addTutorialMark(mark: it.fabriziodavi.vivovideo.model.Tutorial.Mark) {
        val ctx = getApplication<Application>()
        val p = project ?: return
        if (p.clips.isEmpty()) { message = "Aggiungi prima un video o una foto."; return }
        val n = it.fabriziodavi.vivovideo.model.Tutorial.nextStep(p)
        val (f, w, h) = runCatching { it.fabriziodavi.vivovideo.engine.TutorialMarks.file(ctx, mark, n) }.getOrElse { message = "Non riesco a preparare l'immagine."; return }
        val total = p.durationUs
        val start = if (total - playheadUs < Layer.MIN_US) 0L else playheadUs
        val scale = when (mark) {
            it.fabriziodavi.vivovideo.model.Tutorial.Mark.ARROW -> 0.18f
            it.fabriziodavi.vivovideo.model.Tutorial.Mark.CIRCLE -> 0.24f
            it.fabriziodavi.vivovideo.model.Tutorial.Mark.BOX -> 0.38f
            it.fabriziodavi.vivovideo.model.Tutorial.Mark.NUMBER -> 0.1f
        }
        val name = if (mark == it.fabriziodavi.vivovideo.model.Tutorial.Mark.NUMBER) "Numero $n" else mark.label
        val layer = Layer(
            uri = android.net.Uri.fromFile(f).toString(), name = name, width = w, height = h,
            startUs = start, durationUs = minOf(3_000_000L, total - start).coerceAtLeast(Layer.MIN_US),
            pose = Transform(scale = scale), animIn = TextAnim.POP, animOut = TextAnim.FADE,
        )
        update { it.copy(layers = it.layers + layer) }
        selectedLayerId = layer.id
    }

    /** Passo numerato ("1. …") dall'indicatore, in alto a sinistra: si scrive il testo nel pannello. */
    fun addStep() {
        val p = project ?: return
        if (p.clips.isEmpty()) { message = "Aggiungi prima un video o una foto."; return }
        val total = p.durationUs
        val start = if (total - playheadUs < TextItem.MIN_US) 0L else playheadUs
        val t = it.fabriziodavi.vivovideo.model.Tutorial.stepText(it.fabriziodavi.vivovideo.model.Tutorial.nextStep(p), start, minOf(4_000_000L, total - start).coerceAtLeast(TextItem.MIN_US))
        update { it.copy(texts = it.texts + t) }
        selectedTextId = t.id
    }

    // ---------------- Titoli di coda ----------------

    var showCredits by mutableStateOf(false)
    /** Ultime scelte nella schermata dei titoli (si ritrovano riaprendola). */
    var creditsTitle by mutableStateOf("")
    var creditsRows by mutableStateOf(listOf(it.fabriziodavi.vivovideo.model.Credits.Row("Riprese e montaggio", "")))
    var creditsMusic by mutableStateOf(true)
    var creditsOnBlack by mutableStateOf(true)
    var creditsSeconds by mutableStateOf(0)

    /** Testo dei titoli con le scelte attuali. */
    fun creditsText(): String = it.fabriziodavi.vivovideo.model.Credits.text(
        creditsTitle, creditsRows, if (creditsMusic) project?.credits().orEmpty() else emptyList(),
    )

    /** Mette (o rifà) i titoli di coda; un Annulla li toglie. */
    fun applyCredits() {
        val ctx = getApplication<Application>()
        val p = project ?: return
        val text = creditsText()
        if (text.isBlank()) { message = "Scrivi almeno un titolo o una riga."; return }
        val dur = if (creditsSeconds > 0) creditsSeconds * 1_000_000L else it.fabriziodavi.vivovideo.model.Credits.suggestedUs(text)
        val black = if (creditsOnBlack) blackClip(ctx, p) else null
        update { it.withCredits(text, dur, creditsOnBlack, black) }
        showCredits = false
        message = "Titoli di coda pronti (${dur / 1_000_000} s). Nella timeline sono un testo che si può spostare; Annulla li toglie."
    }

    fun removeCredits() { update { it.withoutCredits() }; showCredits = false }

    /** Una foto nera grande quanto il video (per i titoli "su nero"). */
    private fun blackClip(ctx: Application, p: Project): Clip? = runCatching {
        val (w, h) = factory.outputSize(p, 1080)
        val f = File(File(ctx.filesDir, "titoli").apply { mkdirs() }, "nero_${w}x$h.jpg")
        if (!f.exists()) {
            val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLACK) }
            f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
            bmp.recycle()
        }
        Clip(uri = android.net.Uri.fromFile(f).toString(), sourceDurationUs = Clip.PHOTO_MAX_US, trimEndUs = 5_000_000, isPhoto = true, width = w, height = h, fill = true)
    }.getOrNull()

    // ---------------- Pulizia del parlato: pause ed "ehm" ----------------

    var showSpeechCut by mutableStateOf(false)
    /** La schermata è aperta per i jump cut (Audio → Clip → Jump cut) invece che per la sola pulizia. */
    var jumpMode by mutableStateOf(false)
    /** Parole sentite nell'ultima analisi (tempi della timeline), per i sottotitoli dei jump cut Social. */
    var speechWords by mutableStateOf<List<SpokenWord>>(emptyList())
        private set
    /** Ciò che l'analisi propone di togliere (null finché non si analizza). */
    var speechPlan by mutableStateOf<it.fabriziodavi.vivovideo.model.SpeechCuts.Result?>(null)

    /**
     * Ascolta l'audio delle clip: pause più lunghe di [minPauseUs] (0 = non cercarle) e, con [fillers],
     * le parole di riempimento (serve il riconoscimento vocale). Non cambia nulla finché non si conferma.
     */
    fun analyzeSpeech(model: Whisper.Model, lang: String, minPauseUs: Long, fillers: Boolean, transcribe: Boolean = false) {
        val ctx = getApplication<Application>()
        val p = project ?: return
        if (p.clips.isEmpty() || busy != null) return
        pause()
        speechPlan = null
        speechWords = emptyList()
        subtitleJob = viewModelScope.launch {
            try {
                busy = "Preparo l'audio…"
                // Solo l'audio delle clip: voci fuori campo e musica non si tagliano.
                val pcm = withContext(Dispatchers.Default) {
                    val wav = renderTimelineAudio(ctx, p.copy(music = emptyList()), loudness = false)
                    try { Whisper.wavTo16kMono(wav) } finally { wav.delete() }
                }
                var words = emptyList<SpokenWord>()
                var subtitleWords = emptyList<SpokenWord>()
                if (fillers || transcribe) {
                    if (!Whisper.isDownloaded(ctx, model)) {
                        busy = "Scarico il modello ${model.label} (${model.sizeMb} MB, solo la prima volta)…"
                        Whisper.download(ctx, model) { f -> handler.post { busy = "Scarico il modello ${model.label}… ${(f * 100).toInt()}%" } }
                    }
                    // Due ascolti diversi: per gli ehm con l'esempio che li fa scrivere (veloce); per i sottotitoli senza
                    // esempio e con la ricerca a fascio (l'esempio degli ehm faceva sbagliare parole ai sottotitoli).
                    suspend fun listen(label: String, prompt: String, beam: Int): List<SpokenWord> {
                        busy = "$label… 0%"
                        val poll = launch { while (true) { kotlinx.coroutines.delay(400); busy = "$label… ${Whisper.progress()}%" } }
                        return try { Whisper.transcribe(Whisper.modelFile(ctx, model), pcm, lang, prompt, beam) } finally { poll.cancel() }
                    }
                    val fillerWords = if (fillers) listen("Ascolto gli ehm", Whisper.FILLER_PROMPT, 1) else emptyList()
                    subtitleWords = if (transcribe) listen("Ascolto le parole per i sottotitoli", "", Whisper.SUBTITLE_BEAM) else emptyList()
                    words = fillerWords
                }
                busy = "Cerco le pause…"
                val plan = withContext(Dispatchers.Default) { it.fabriziodavi.vivovideo.model.SpeechCuts.plan(p, pcm, words, minPauseUs, fillers = fillers) }
                busy = null
                speechPcm = pcm
                speechWords = subtitleWords
                speechPlan = plan
                // Per le segnalazioni: cosa ha sentito il riconoscimento vocale e cosa ha trovato il suono.
                if (fillers) Diary.log(
                    "Analisi del parlato: ${words.size} parole riconosciute, ${plan.fillerCuts.count { it.word != null && !it.doubtful }} ehm sentiti e ritrovati nel suono, " +
                        "${plan.fillerCuts.count { it.doubtful }} da controllare, ${plan.fillerCuts.count { it.word == null }} trovati solo dal suono, ${plan.pauses} pause. " +
                        "Testo: " + words.joinToString(" ") { it.text }.take(400)
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                busy = null; throw e
            } catch (e: Exception) {
                busy = null
                message = "Analisi non riuscita: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    /** Audio delle clip analizzato (16 kHz mono, tempi del montaggio), per riascoltare gli ehm trovati. */
    private var speechPcm: FloatArray? = null
    private var sampleTrack: android.media.AudioTrack? = null

    /** Spunta o toglie l'ehm numero [i] della lista. */
    fun toggleFiller(i: Int) { speechPlan = speechPlan?.toggle(i) }

    /**
     * Fa sentire l'ehm numero [i] col suo contorno (un secondo prima, mezzo dopo): così com'è ([after] = false)
     * o come sarà dopo il taglio.
     */
    fun playFiller(i: Int, after: Boolean) {
        val pcm = speechPcm ?: return
        val f = speechPlan?.fillerCuts?.getOrNull(i) ?: return
        pause()
        val r = 16_000
        fun idx(us: Long) = (us * r / 1_000_000).toInt().coerceIn(0, pcm.size)
        val a = idx(f.span.startUs - 1_000_000); val s = idx(f.span.startUs); val e = idx(f.span.endUs); val b = idx(f.span.endUs + 600_000)
        val clip = if (after) pcm.copyOfRange(a, s) + pcm.copyOfRange(e, b) else pcm.copyOfRange(a, b)
        if (clip.isEmpty()) return
        // L'audio delle clip è quello grezzo del telefono (spesso basso): si porta a un volume chiaro, senza distorcere.
        var peak = 1e-6f; var en = 0.0
        for (v in clip) { peak = maxOf(peak, kotlin.math.abs(v)); en += v * v }
        val rms = kotlin.math.sqrt(en / clip.size).toFloat().coerceAtLeast(1e-6f)
        val gain = minOf(0.15f / rms, 0.9f / peak, 30f)
        for (k in clip.indices) clip[k] *= gain
        // Brevi dissolvenze ai bordi (e sul taglio) per non sentire scatti.
        val fade = r / 200
        for (k in 0 until minOf(fade, clip.size)) { val g = k.toFloat() / fade; clip[k] *= g; clip[clip.size - 1 - k] *= g }
        if (after) { val j = s - a; for (k in -fade until fade) { val q = j + k; if (q in clip.indices) clip[q] *= kotlin.math.abs(k).toFloat() / fade } }
        runCatching {
            sampleTrack?.run { runCatching { stop() }; release() }
            val t = android.media.AudioTrack.Builder()
                .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(android.media.AudioFormat.Builder().setEncoding(android.media.AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(r).setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(android.media.AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(clip.size * 4)
                .build()
            t.write(clip, 0, clip.size, android.media.AudioTrack.WRITE_BLOCKING)
            t.play()
            sampleTrack = t
        }
    }

    /** Chiude la pulizia del parlato (e libera l'audio analizzato). */
    fun closeSpeechCut() {
        showSpeechCut = false
        jumpMode = false
        speechPlan = null
        speechPcm = null
        speechWords = emptyList()
        sampleTrack?.run { runCatching { stop() }; release() }
        sampleTrack = null
    }

    /** Taglia quello che l'analisi ha trovato (un solo Annulla torna indietro). */
    fun applySpeechCuts() {
        val plan = speechPlan ?: return
        if (plan.isEmpty) return
        val before = project?.durationUs ?: return
        update { it.cutTimeline(plan.spans) }
        val after = project?.durationUs ?: before
        closeSpeechCut()
        selectedClipId = null
        message = "Tolti ${formatSeconds(before - after)} di pause e parole di riempimento. Annulla per tornare indietro."
    }

    private fun formatSeconds(us: Long): String = String.format(java.util.Locale.ITALIAN, "%.1f s", us / 1e6)

    // ---------------- Jump cut pronti ----------------

    /** Proporzioni del video finale (larghezza/altezza). */
    private fun outAspect(p: Project): Float = runCatching { factory.outputSize(p, null) }.getOrNull()
        ?.let { (w, h) -> w.toFloat() / h.coerceAtLeast(1) } ?: (9f / 16f)

    /** Riepilogo dei jump cut prima di confermare (veloce: i volti contano solo per centrare l'inquadratura). */
    fun jumpPreview(style: JumpStyle, whoosh: Boolean): JumpCuts.Summary? {
        val p = project ?: return null
        val plan = speechPlan ?: return null
        if (plan.isEmpty) return null
        return runCatching {
            JumpCuts.apply(p, plan.spans, style, speechPcm, speechWords, whoosh, outAspect(p)).summary
        }.getOrNull()
    }

    /**
     * Jump cut: toglie pause ed ehm trovati e a ogni taglio cambia l'inquadratura (centrata sul volto, che si cerca
     * con la stessa analisi di "Momento migliore"); con Social anche lampi, whoosh e sottotitoli. Un solo Annulla.
     */
    fun applyJumpCuts(style: JumpStyle, whoosh: Boolean) {
        val plan = speechPlan ?: return
        val p0 = project ?: return
        if (plan.isEmpty || busy != null) return
        val ctx = getApplication<Application>()
        val pcm = speechPcm
        val words = speechWords
        subtitleJob = viewModelScope.launch {
            try {
                // Volti: un'analisi per video (si ricorda, la seconda volta è immediata).
                val videos = p0.clips.filter { !it.isPhoto }.distinctBy { it.uri }
                val paths = HashMap<String, Reframe>()
                videos.forEachIndexed { i, c ->
                    busy = if (videos.size == 1) "Cerco il volto…" else "Cerco i volti… ${i + 1} di ${videos.size}"
                    val scan = runCatching { SmartAnalyzer.scan(ctx, c.uri, false, c.sourceDurationUs) }
                        .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
                    if (scan != null && scan.hasFaces) Moments.subjectPath(scan)?.let { paths[c.uri] = it }
                }
                busy = "Preparo i jump cut…"
                val now = project ?: return@launch
                val outcome = withContext(Dispatchers.Default) {
                    JumpCuts.apply(now, plan.spans, style, pcm, words, whoosh, outAspect(now)) { uri, srcUs ->
                        paths[uri]?.at(srcUs)
                    }
                }
                val sfx = if (outcome.whooshAtUs.isEmpty()) null else withContext(Dispatchers.IO) {
                    runCatching { android.net.Uri.fromFile(Whoosh.file(ctx)).toString() }.getOrNull()
                }
                busy = null
                val result = if (sfx == null) outcome.project else outcome.project.copy(
                    music = outcome.project.music + outcome.whooshAtUs.map { t -> JumpCuts.whooshTrack(sfx, t) },
                )
                update { result }
                closeSpeechCut()
                selectedClipId = null
                val sm = outcome.summary
                message = buildString {
                    append("Jump cut fatti: ${sm.cuts} tagli, tolti ${formatSeconds(sm.removedUs)}")
                    if (sm.reframes > 0) append(", ${sm.reframes} cambi d'inquadratura nelle frasi lunghe")
                    if (sm.punches > 0) append(", ${sm.punches} punch-in")
                    if (sm.flashes > 0) append(", ${sm.flashes} lampi")
                    if (sm.subtitles > 0) append(", ${sm.subtitles} sottotitoli")
                    append(". ")
                    append(if (paths.isEmpty()) "Nessun volto trovato: l'inquadratura stretta è al centro. " else "Inquadratura stretta centrata sul volto. ")
                    append("Annulla per tornare indietro.")
                }
                Diary.log("Jump cut ${style.name}: ${sm.cuts} tagli, ${sm.reframes} cambi senza taglio, ${sm.zoomed} stretti, ${sm.punches} punch-in, ${sm.flashes} lampi, ${sm.whooshes} whoosh, ${sm.subtitles} sottotitoli, volti in ${paths.size} video su ${videos.size}.")
            } catch (e: kotlinx.coroutines.CancellationException) {
                busy = null; throw e
            } catch (e: Exception) {
                busy = null
                message = "Jump cut non riusciti: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    // ---------------- Voce fuori campo, audio staccato, ducking ----------------

    private var recorder: VoiceRecorder? = null
    private var recordFile: File? = null
    private var recordStartUs = 0L
    var recording by mutableStateOf(false)
        private set
    /** Silenzia l'anteprima mentre si registra (senza cuffie il microfono la risentirebbe). */
    var muteWhileRecording by mutableStateOf(true)

    /** Registra la voce dal punto dell'indicatore, con l'anteprima che scorre per andare a tempo. */
    fun startVoice() {
        val ctx = getApplication<Application>()
        val p = project ?: return
        if (p.clips.isEmpty() || recording) return
        val dir = File(ctx.filesDir, "voice").apply { mkdirs() }
        val f = File(dir, "voce_${System.currentTimeMillis()}.pcm")
        val r = VoiceRecorder(f)
        if (!runCatching { r.start() }.getOrDefault(false)) {
            f.delete()
            message = "Non riesco a usare il microfono (forse lo usa un'altra app)."
            return
        }
        recorder = r; recordFile = f; recording = true
        recordStartUs = playheadUs.coerceAtMost(p.durationUs - 200_000).coerceAtLeast(0)
        player?.let { pl -> if (muteWhileRecording) runCatching { pl.volume = 0f }; pl.play() }
    }

    fun stopVoice() {
        val r = recorder ?: return
        val raw = recordFile
        recorder = null; recording = false
        val n = runCatching { r.stop() }.getOrDefault(0L)
        pause()
        player?.let { pl -> runCatching { pl.volume = 1f } }
        val ctx = getApplication<Application>()
        val p = project ?: return
        if (raw == null || n < VoiceChain.RATE / 2) { raw?.delete(); message = "Registrazione troppo breve."; return }
        message = "Pulisco la voce…"
        viewModelScope.launch {
            // Catena da podcast: niente rimbombo né fruscio, volume uniforme e chiaro (WAV senza perdite).
            val wav = withContext(Dispatchers.IO) {
                runCatching {
                    val clean = VoiceChain.process(VoiceRecorder.read(raw))
                    val out = File(raw.parentFile, raw.nameWithoutExtension + ".wav")
                    writeWavFile(out, VoiceChain.toStereo(clean))
                    out
                }.getOrNull().also { raw.delete() }
            }
            if (wav == null) { message = "Registrazione non leggibile."; return@launch }
            val durUs = n * 1_000_000 / VoiceChain.RATE
            val q = project ?: p
            val count = q.music.count { it.dialog && it.uri.contains("/voice/") } + 1
            val t = MusicTrack(
                uri = Uri.fromFile(wav).toString(), name = "Voce $count", sourceDurationUs = durUs,
                startUs = recordStartUs, lengthUs = minOf(durUs, q.durationUs - recordStartUs), dialog = true,
            )
            // Prima voce sopra un brano: mix automatico come in un podcast (musica un po' più bassa e ducking acceso).
            var autoMix = false
            update {
                val hasSong = it.music.any { m -> !m.dialog }
                val firstVoice = it.music.none { m -> m.bus() == Bus.VOICE }
                val mix = if (hasSong && firstVoice && it.mix.bus(Bus.MUSIC).neutral) { autoMix = true; it.mix.with(Bus.MUSIC, BusMix(gainDb = -6f)) } else it.mix
                it.copy(music = it.music + t, duck = if (hasSong) true else it.duck, mix = mix)
            }
            selectedMusicId = t.id
            message = if (autoMix) "Voce aggiunta e pulita. Musica abbassata di 6 dB e ducking acceso: regola tutto in Audio → Mixer audio."
                else "Voce aggiunta: pulita e a volume uniforme."
        }
    }

    /** L'audio della clip diventa una riga a sé (si può allungare prima/dopo il taglio: tagli J e L). */
    fun detachAudio() {
        val p = project ?: return
        val c = selectedClip ?: return
        if (c.isPhoto || c.muted) { message = "Questa clip non ha audio da staccare."; return }
        if (c.speedCurve().speeds.any { it != 1f }) { message = "Per staccare l'audio la clip deve essere a velocità normale."; return }
        val i = p.clips.indexOf(c)
        val t = MusicTrack(
            uri = c.uri, name = "Audio clip ${i + 1}", sourceDurationUs = c.sourceDurationUs,
            startUs = p.clipStartsUs()[i], offsetUs = c.trimStartUs, lengthUs = c.outputUs, volume = c.volume, dialog = true,
        )
        update { q -> q.replaceClip(c.copy(muted = true)).copy(music = q.music + t) }
        selectedMusicId = t.id
        message = "Audio staccato: ora è una riga nella timeline. Trascinalo o allungalo con le maniglie per farlo iniziare prima o finire dopo il taglio."
    }

    // ---------------- Mixer ----------------
    var showMixer by mutableStateOf(false)
        private set

    fun openMixer() { Meters.reset(); showMixer = true; factory.metering = true; rebuildPreview(playheadUs / 1000) }
    fun closeMixer() { showMixer = false; factory.metering = false; rebuildPreview(playheadUs / 1000) }

    /** Canale del mixer cambiato: si rifà l'anteprima per sentirlo subito. */
    fun setBus(b: Bus, m: BusMix) = update { it.copy(mix = it.mix.with(b, m)) }
    fun setMix(change: (Mix) -> Mix) = update { it.copy(mix = change(it.mix)) }
    fun resetMix() = update { it.copy(mix = Mix()) }

    // Il ducking ora si sente anche in anteprima: si rifà l'anteprima.
    fun setDuck(on: Boolean? = null, db: Int? = null) = update { it.copy(duck = on ?: it.duck, duckDb = db ?: it.duckDb) }

    /** Voci di cui si stanno misurando i livelli per il ducking in anteprima. */
    private var duckPreparing: String? = null

    private fun prepareDuckPreview(p: Project) {
        if (!DuckPreview.needs(p)) return
        val k = DuckPreview.key(p) ?: return
        if (duckPreparing == k) return
        duckPreparing = k
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { DuckPreview.prepare(ctx, p) } }
            duckPreparing = null
            val now = project ?: return@launch
            if (DuckPreview.key(now) == k && player != null) rebuildPreview(playheadUs / 1000)
        }
    }

    private var loudPreparing: String? = null
    private var loudJob: kotlinx.coroutines.Job? = null

    /**
     * Misura in secondo piano il volume del mix, così l'anteprima suona forte come il video esportato.
     * Si aspetta un attimo dopo le modifiche; quando la misura è pronta e il guadagno cambia di almeno 1 dB,
     * l'anteprima si ricarica (solo da ferma, per non interrompere l'ascolto).
     */
    private fun preparePreviewLoudness(p: Project) {
        if (!exportOptions.loudness || !PreviewLoudness.needs(p)) return
        val k = PreviewLoudness.key(p) ?: return
        if (loudPreparing == k) return
        loudJob?.cancel()
        loudPreparing = k
        val ctx = getApplication<Application>()
        loudJob = viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
            val ok = withContext(Dispatchers.Default) {
                runCatching { PreviewLoudness.prepare(ctx, p, isCancelled = { !isActive }) }.isSuccess
            }
            if (!isActive) return@launch
            if (loudPreparing == k) loudPreparing = null
            val now = project ?: return@launch
            if (!ok || PreviewLoudness.key(now) != k || player == null) return@launch
            val g = PreviewLoudness.gainFor(now)
            Diary.log("Volume dell'anteprima: ${"%+.1f".format(java.util.Locale.ITALIAN, PreviewLoudness.db(g))} dB, come nel video esportato.")
            if (kotlin.math.abs(PreviewLoudness.db(g) - PreviewLoudness.db(factory.previewGain)) >= 1.0) {
                if (isPlaying) pendingLoudRebuild = true else rebuildPreview(playheadUs / 1000)
            }
        }
    }
    private var pendingLoudRebuild = false

    /** Correzione colore copiata (per "Incolla" su un'altra clip). */
    var copiedGrade by mutableStateOf<Grade?>(null)

    fun applyGradeToAll() {
        val c = selectedClip ?: return
        update { p -> p.copy(clips = p.clips.map { it.copy(grade = c.grade) }) }
    }

    /** Piccola immagine (160 px) del fotogramma della clip sotto l'indicatore, per l'istogramma. */
    suspend fun sampleFrame(ctx: android.content.Context, clip: Clip): IntArray? = withContext(Dispatchers.IO) {
        runCatching {
            val p = project ?: return@runCatching null
            val i = p.clips.indexOfFirst { it.id == clip.id }.takeIf { it >= 0 } ?: return@runCatching null
            val start = p.clipStartsUs()[i]
            val outOff = (playheadUs - start).let { if (it in 0..clip.outputUs) it else clip.outputUs / 2 }
            val bmp: android.graphics.Bitmap = if (clip.isPhoto) {
                decodePhotoThumb(Uri.parse(clip.uri).path ?: return@runCatching null, 120) ?: return@runCatching null
            } else {
                val src = clip.trimStartUs + clip.speedCurve().sourceTimeAt(outOff)
                val r = android.media.MediaMetadataRetriever()
                try {
                    r.setDataSource(ctx, Uri.parse(clip.uri))
                    r.getScaledFrameAtTime(src, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 160, 160) ?: return@runCatching null
                } finally { r.release() }
            }
            val w = bmp.width; val h = bmp.height
            IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
        }.getOrNull()
    }

    /** Inquadratura fissa della clip selezionata. */
    fun setFrame(f: Transform) = updateSelected { it.copy(frame = f) }

    /** Copia inquadratura e Adatta/Riempi della clip selezionata su tutte le clip. */
    fun applyFrameToAll() {
        val c = selectedClip ?: return
        update { p -> p.copy(clips = p.clips.map { it.copy(frame = c.frame, fill = c.fill, blurBg = c.blurBg) }) }
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
    private var previewErrorState by mutableStateOf<String?>(null)
    var previewError: String?
        get() = previewErrorState
        private set(v) { previewErrorState = v; if (v != null) Diary.log("Anteprima: $v") }
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            player?.let { p ->
                playheadUs = p.currentPosition * 1000
                isPlaying = p.isPlaying
                watchStall(p)
                if (!p.playWhenReady && pendingCuts.isNotEmpty()) applyTapCuts()
                if (!p.playWhenReady && pendingLoudRebuild) { pendingLoudRebuild = false; rebuildPreview(playheadUs / 1000) }
            }
            handler.postDelayed(this, 50)
        }
    }

    // Guardiano dell'anteprima: se in riproduzione il tempo resta fermo troppo a lungo
    // (raro inceppamento del motore video), si ricarica il lettore da quel punto e si riparte.
    private var stallPos = -1L
    private var stallSince = 0L
    private var stallRecoveries = 0
    var previewRecovered by mutableStateOf(0)
        private set

    private fun watchStall(p: CompositionPlayer) {
        val wantsToPlay = p.playWhenReady && p.playbackState != Player.STATE_ENDED && p.playbackState != Player.STATE_IDLE
        val now = android.os.SystemClock.elapsedRealtime()
        val pos = p.currentPosition
        if (!wantsToPlay || pos != stallPos) { stallPos = pos; stallSince = now; return }
        if (now - stallSince < 4_000) return
        stallSince = now
        if (stallRecoveries >= 3) return // ripetuto: si lascia com'è (l'utente vede l'errore o riprova)
        stallRecoveries++
        previewRecovered++
        releasePlayer()
        rebuildPreview(pos)
        player?.play()
        // Dopo un po' di riproduzione regolare il conteggio si azzera.
        handler.postDelayed({ if (stallPos != pos) stallRecoveries = 0 }, 20_000)
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
    fun onAppStart() {
        if (playerMulti && player != null) Safety.pendingPreview(getApplication(), true)
        // Al ritorno nell'app: i file potrebbero essere stati spostati o cancellati nel frattempo.
        recheckFiles()
        cleanCacheOnce()
    }

    private var cacheCleaned = false

    /** Una volta per avvio, in secondo piano: temporanei rimasti e copie leggere che nessun progetto usa più. */
    private fun cleanCacheOnce() {
        if (cacheCleaned) return
        cacheCleaned = true
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val freed = withContext(Dispatchers.IO) {
                runCatching { CacheJanitor.run(ctx, store.list() + listOfNotNull(project)) }.getOrDefault(0L)
            }
            if (freed > 50L * 1024 * 1024) {
                Diary.log("Pulizia della cache: liberati ${"%.0f".format(java.util.Locale.ITALIAN, freed / 1_048_576.0)} MB.")
            }
        }
    }

    // Galleria osservata mentre l'app è aperta: uno spostamento o una cancellazione si nota subito,
    // e un file appena spostato si ritrova appena il telefono lo registra nella nuova cartella.
    private val mediaObserver = object : android.database.ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            handler.removeCallbacks(mediaChanged)
            handler.postDelayed(mediaChanged, 1200)
        }
    }
    private val mediaChanged = Runnable { if (project != null) recheckFiles(force = true) }

    init {
        runCatching {
            getApplication<Application>().contentResolver.registerContentObserver(
                android.provider.MediaStore.Files.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL), true, mediaObserver,
            )
        }
    }

    /** File che l'anteprima non è riuscita a leggere (anche se Android dice ancora che c'è). */
    private val unreadable = mutableSetOf<String>()
    private var retryJob: kotlinx.coroutines.Job? = null

    /**
     * Ricontrolla quali file del progetto mancano (veloce, in sottofondo) e, se ne manca qualcuno, lo cerca;
     * finché non lo trova riprova ogni pochi secondi per un minuto (il telefono registra con calma i file spostati).
     */
    fun recheckFiles(force: Boolean = false) {
        val p = project ?: return
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val missing = withContext(Dispatchers.IO) {
                val m = runCatching { Backup.missing(ctx, p) }.getOrDefault(emptyList())
                // Quelli che l'anteprima non leggeva contano come mancanti finché non si leggono davvero.
                (m + unreadable.filter { u -> !Backup.readable(ctx, u) }).distinct()
            }
            if (project?.id != p.id) return@launch
            unreadable.retainAll(missing.toSet())
            val appeared = missingFiles.isNotEmpty() && missing.isEmpty()
            val newlyMissing = missing.any { it !in missingFiles }
            missingFiles = missing
            // Appena manca qualcosa si cerca subito da soli (per nome e per peso): spesso non serve fare altro.
            if (newlyMissing || (force && missing.isNotEmpty())) autoRelink(quiet = true)
            if (missing.isNotEmpty() && retryJob?.isActive != true) {
                retryJob = viewModelScope.launch {
                    repeat(15) {
                        kotlinx.coroutines.delay(4000)
                        if (missingFiles.isEmpty() || project?.id != p.id) return@launch
                        autoRelink(quiet = true)
                    }
                }
            }
            // Un file ricomparso (per esempio la fine di uno spostamento): l'anteprima riparte.
            if (appeared) { previewError = null; if (player != null) rebuildPreview(playheadUs / 1000) }
            // L'anteprima non leggeva un file, ma tutti i file risultano a posto: si riprova una volta.
            if (missing.isEmpty() && previewError?.startsWith("Un file del progetto") == true) {
                previewError = null
                if (player != null) rebuildPreview(playheadUs / 1000)
            }
        }
    }
    /** Il compositore a più ingressi ha dato errore in anteprima: si usa il ripiego. */
    private var multiFailed = false

    private fun rebuildPreview(positionMs: Long) {
        val p = project ?: return
        if (exportState is ExportState.Running) return
        if (p.clips.isEmpty()) { releasePlayer(); playheadUs = 0; return }
        val allowMulti = false // l'anteprima con più video insieme si blocca sul telefono: dissolvenze preparate in anticipo
        val (fw, fh) = previewFrameSize()
        val shown = runCatching { CrossfadePreview.previewProject(getApplication(), previewSource(p), fw, fh) }.getOrDefault(p)
        prepareDuckPreview(p)
        preparePreviewLoudness(p)
        factory.previewGain = if (exportOptions.loudness) PreviewLoudness.gainFor(p) else 1f
        val composition = runCatching { factory.build(shown, exportOptions, forPreview = true, nativeAudio = null, allowMulti = allowMulti) }
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
                override fun onPlaybackStateChanged(state: Int) {
                    // L'anteprima è pronta: i file si leggono. Un vecchio "illeggibile" non vale più.
                    if (state == Player.STATE_READY && player === np && (unreadable.isNotEmpty() || missingFiles.isNotEmpty())) {
                        unreadable.clear()
                        recheckFiles()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    // Un lettore già chiuso (o in chiusura) non conta: per esempio "release timeout" su un telefono lento.
                    if (player !== np) return
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
                    if (error.errorCode in 2000..2999) {
                        // L'indirizzo del file che non si legge, preso dal messaggio d'errore.
                        val bad = Regex("(content|file)://[^\\s]+").findAll(generateSequence(error as Throwable) { it.cause }.joinToString(" ") { it.message ?: "" })
                            .map { it.value.trimEnd('.', ',', ')') }.toSet()
                        project?.let { q -> bad.filter { b -> q.clips.any { it.uri == b } || q.layers.any { it.uri == b } || q.music.any { it.uri == b } } }?.let { unreadable += it }
                        // Un file non si legge più (spostato, cancellato, permesso scaduto): niente errore tecnico,
                        // si ricontrolla, si cerca da soli e, se serve, si offre Ricollega.
                        previewError = "Un file del progetto non si legge più. Lo sto cercando…"
                        handler.post { recheckFiles() }
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
        schedulePrerender()
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
        val old = player
        player = null
        old?.release()
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

    /** Trascinando (barra o timeline): l'indicatore segue subito il dito, il lettore riceve un punto ogni [SCRUB_MS]. */
    private var lastScrubAt = 0L
    private var scrubTarget = -1L
    private val scrubSeek = Runnable { if (scrubTarget >= 0) { player?.seekTo(scrubTarget / 1000); lastScrubAt = SystemClock.uptimeMillis() } }

    fun scrubTo(us: Long) {
        val d = project?.durationUs ?: return
        val t = us.coerceIn(0, d)
        playheadUs = t
        scrubTarget = t
        handler.removeCallbacks(scrubSeek)
        val wait = SCRUB_MS - (SystemClock.uptimeMillis() - lastScrubAt)
        // Troppe richieste di fila si annullano a vicenda e l'immagine resta ferma: se ne manda una ogni SCRUB_MS,
        // e comunque l'ultima arriva (anche quando il dito si ferma).
        if (wait <= 0) scrubSeek.run() else handler.postDelayed(scrubSeek, wait)
    }

    /** Fine del trascinamento: il punto esatto dove si è lasciato il dito. */
    fun endScrub() {
        handler.removeCallbacks(scrubSeek)
        if (scrubTarget >= 0) seekTo(scrubTarget)
        scrubTarget = -1
    }

    // ---------------- Export ----------------

    var exportOptions by mutableStateOf(ExportOptions())

    /** Misure e peso stimato del video che uscirà con le opzioni scelte ("1080×1920 · 30 fps · circa 85 MB"). */
    fun exportEstimate(): String? {
        val p = project ?: return null
        if (p.clips.isEmpty()) return null
        val o = exportOptions
        val (w, h) = factory.outputSize(p, o.size.shortSide)
        val maxFps = p.clips.maxOfOrNull { it.fps } ?: 30f
        val fps = o.fps.max?.let { minOf(it.toFloat(), maxFps) } ?: maxFps
        val bytes = ExportEstimate.bytes(w, h, fps, if (o.hdr) OutputCodec.H265 else o.codec, p.durationUs, o.maxBitrate, hdr = o.hdr && p.clips.any { it.hdr > 0 })
        return "${w}×$h · ${"%.0f".format(fps)} fps" + (if (o.hdr && hasHdr) " · HDR" else "") + " · " + ExportEstimate.label(bytes)
    }
    var exportState by mutableStateOf<ExportState>(ExportState.Idle)
        private set

    private val exporter = Exporter(app, viewModelScope, factory) { s ->
        when (s) {
            is ExportState.Done -> Diary.log("Esportazione riuscita: ${s.details}")
            is ExportState.Failed -> Diary.log("Esportazione fallita: ${s.message}")
            else -> {}
        }
        exportState = s
    }

    // ---------------- Collaudo e segnalazioni ----------------

    var showTestPlan by mutableStateOf(false)

    /** Prepara il rapporto (con il progetto aperto, se [withProject]) e apre la condivisione per mandarlo. */
    /** Rapporto compatto negli appunti: si incolla nella chat con Claude. */
    fun copyReport(note: String, withProject: Boolean = true, extra: String = "") {
        val ctx = getApplication<Application>()
        val p = if (withProject) project else null
        viewModelScope.launch {
            busy = "Preparo la segnalazione…"
            val text = withContext(Dispatchers.IO) { runCatching { Diagnostics.report(ctx, p, note, extra, compact = true) } }
            busy = null
            text.onSuccess { t ->
                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("VivoVideo: segnalazione", t))
                message = "Segnalazione copiata (${t.length / 1000} mila caratteri). Apri la chat con Claude, tieni premuto nel campo del messaggio e scegli Incolla."
            }.onFailure { message = "Segnalazione non riuscita: ${it.message}" }
        }
    }

    /** Rapporto completo nella cartella Download (da allegare alla chat come documento). */
    fun saveReportToDownloads(note: String, withProject: Boolean = true, extra: String = "") {
        val ctx = getApplication<Application>()
        val p = if (withProject) project else null
        viewModelScope.launch {
            busy = "Preparo la segnalazione…"
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val text = Diagnostics.report(ctx, p, note, extra)
                    val name = "VivoVideo_segnalazione_" + java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.ITALY).format(java.util.Date()) + ".txt"
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
                        put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain")
                        put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = ctx.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("cartella Download non disponibile")
                    ctx.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) }
                    name
                }
            }
            busy = null
            r.onSuccess { name -> message = "Salvata in Download: $name. Nella chat con Claude tocca la graffetta (o +), scegli File e cerca in Download." }
                .onFailure { message = "Non riesco a salvare in Download: ${it.message}" }
        }
    }

    fun shareReport(note: String, withProject: Boolean = true, extra: String = "") {
        val ctx = getApplication<Application>()
        val p = if (withProject) project else null
        viewModelScope.launch {
            busy = "Preparo la segnalazione…"
            val f = withContext(Dispatchers.IO) { runCatching { Diagnostics.write(ctx, Diagnostics.report(ctx, p, note, extra)) } }
            busy = null
            f.onSuccess { file ->
                val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "VivoVideo: segnalazione")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching {
                    ctx.startActivity(Intent.createChooser(send, "Manda la segnalazione").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }.onFailure { message = "Non riesco ad aprire la condivisione: ${it.message}" }
            }.onFailure { message = "Segnalazione non riuscita: ${it.message}" }
        }
    }

    // ---------------- Dissolvenze preparate per l'anteprima ----------------

    /** Dissolvenze in preparazione (0 = nessuna). */
    var preparing by mutableStateOf(0)
        private set
    private val segQueue = ArrayDeque<CrossfadePreview.Job>()
    /** Tratti che servono al progetto aperto: non si cancellano per fare spazio. */
    private var segNeeded: Set<String> = emptySet()
    private var segTransformer: Transformer? = null
    private var segSchedule: Job? = null
    private val failedSegments = mutableSetOf<String>()
    private var segProduced = false

    // ---------------- Copie leggere (anteprima fluida con 4K120 / 8K) ----------------

    private val prefs get() = getApplication<Application>().getSharedPreferences("anteprima", android.content.Context.MODE_PRIVATE)
    var useProxies by mutableStateOf(prefs.getBoolean("copie_leggere", true))
        private set
    fun setProxies(on: Boolean) {
        useProxies = on
        prefs.edit().putBoolean("copie_leggere", on).apply()
        rebuildPreview(playheadUs / 1000)
    }
    /** Cosa si sta preparando in sottofondo (per la scritta sull'anteprima). */
    var preparingLabel by mutableStateOf("")
        private set
    private var proxyTransformer: Transformer? = null
    private val failedProxies = mutableSetOf<String>()

    /** Il progetto come lo vede l'anteprima: copie leggere al posto dei video pesanti (se pronte). */
    private fun previewSource(p: Project): Project =
        if (useProxies) runCatching { Proxies.forPreview(getApplication(), p) }.getOrDefault(p) else p

    /** Prepara una copia leggera (720p, stessa durata e audio) del video [uri]; poi la successiva. */
    private fun nextProxy(queue: ArrayDeque<String>) {
        val ctx = getApplication<Application>()
        val uri = queue.firstOrNull()
        val p = project
        if (uri == null || p == null) {
            proxyTransformer = null
            preparing = 0
            rebuildPreview(playheadUs / 1000)
            schedulePrerender()
            return
        }
        preparing = queue.size
        preparingLabel = if (queue.size == 1) "Preparo la copia leggera…" else "Preparo ${queue.size} copie leggere…"
        val (w, h) = p.clips.firstOrNull { it.uri == uri }?.let { it.width to it.height }
            ?: p.layers.firstOrNull { it.uri == uri }?.let { it.width to it.height } ?: (1920 to 1080)
        val (pw, ph) = Proxies.size(w, h)
        val out = Proxies.file(ctx, uri)
        val tmp = File(out.parentFile, out.name + ".tmp.mp4")
        val item = androidx.media3.transformer.EditedMediaItem.Builder(androidx.media3.common.MediaItem.fromUri(Uri.parse(uri)))
            .setEffects(androidx.media3.transformer.Effects(emptyList(), listOf(
                androidx.media3.effect.Presentation.createForWidthAndHeight(pw, ph, androidx.media3.effect.Presentation.LAYOUT_SCALE_TO_FIT),
            )))
            .build()
        fun next(ok: Boolean) {
            proxyTransformer = null
            if (ok) tmp.renameTo(out) else { tmp.delete(); failedProxies += uri }
            queue.removeFirstOrNull()
            nextProxy(queue)
        }
        val t = Transformer.Builder(ctx)
            .setVideoMimeType(androidx.media3.common.MimeTypes.VIDEO_H264)
            .setMaxDelayBetweenMuxerSamplesMs(30_000)
            // Fotogrammi chiave ogni mezzo secondo (prima uno al secondo): con centinaia di tagli a ritmo l'anteprima
            // salta da una clip all'altra senza dover decodificare fino a un secondo di video prima del punto giusto.
            .setEncoderFactory(
                androidx.media3.transformer.DefaultEncoderFactory.Builder(ctx).setRequestedVideoEncoderSettings(
                    androidx.media3.transformer.VideoEncoderSettings.Builder().setiFrameIntervalSeconds(Proxies.KEYFRAME_SECONDS).build(),
                ).build(),
            )
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: androidx.media3.transformer.ExportResult) = next(true)
                override fun onError(
                    composition: androidx.media3.transformer.Composition,
                    exportResult: androidx.media3.transformer.ExportResult,
                    exportException: androidx.media3.transformer.ExportException,
                ) = next(false)
            })
            .build()
        proxyTransformer = t
        runCatching { t.start(item, tmp.absolutePath) }.onFailure { next(false) }
    }

    /** Dopo una modifica (con calma) prepara le copie leggere che mancano, poi i tratti (dissolvenze, video sovrapposti). */
    private fun schedulePrerender() {
        segSchedule?.cancel()
        segSchedule = viewModelScope.launch {
            delay(900)
            val p0 = project ?: return@launch
            if (exportState is ExportState.Running) return@launch
            val ctx = getApplication<Application>()
            if (useProxies) {
                // Foto grandi: copie leggere (pochi istanti), poi l'anteprima le usa.
                val photos = runCatching { Proxies.missingPhotos(ctx, p0) }.getOrDefault(emptyList())
                if (photos.isNotEmpty()) {
                    withContext(Dispatchers.IO) { photos.forEach { u -> runCatching { Proxies.makePhoto(ctx, u) } } }
                    if (project === p0) rebuildPreview(playheadUs / 1000)
                }
            }
            if (useProxies && proxyTransformer == null && segTransformer == null) {
                val missing = runCatching { Proxies.missing(ctx, p0) }.getOrDefault(emptyList()).filter { it !in failedProxies }
                if (missing.isNotEmpty()) { nextProxy(ArrayDeque(missing)); return@launch }
            }
            if (proxyTransformer != null) return@launch
            val p = previewSource(p0)
            val (w, h) = previewFrameSize()
            preparingLabel = ""
            val all = runCatching { CrossfadePreview.jobs(p, w, h) }.getOrDefault(emptyList())
            segNeeded = all.map { it.key }.toSet()
            // Prima i tratti vicini all'indicatore (quelli che si stanno per guardare), poi gli altri.
            val here = playheadUs
            val jobs = all.filter { !CrossfadePreview.file(ctx, it.key).exists() && it.key !in failedSegments }
                .sortedBy { j -> if (j.endUs < here) Long.MAX_VALUE / 2 + (here - j.endUs) else (j.startUs - here).coerceAtLeast(0) }
            segQueue.clear()
            segQueue.addAll(jobs)
            preparing = segQueue.size
            if (segQueue.isNotEmpty() && segTransformer == null) nextSegment(retry = false)
        }
    }

    private fun nextSegment(retry: Boolean) {
        val ctx = getApplication<Application>()
        val job = segQueue.firstOrNull()
        if (job == null) {
            preparing = 0
            segTransformer = null
            if (segProduced) { segProduced = false; rebuildPreview(playheadUs / 1000) }
            return
        }
        val (w, h) = previewFrameSize()
        val tmp = File(CrossfadePreview.dir(ctx), "${job.key}.tmp.mp4")
        val composition = runCatching {
            factory.build(job.sub, ExportOptions(), forPreview = true, nativeAudio = null, allowMulti = true,
                sizeOverride = w to h, crossfadesOverride = job.cf?.let { listOf(it) })
        }.getOrElse { failedSegments += job.key; segQueue.removeFirstOrNull(); nextSegment(false); return }
        val t = Transformer.Builder(ctx)
            // Un pezzo dura un secondo: se per 20 s non esce nulla si è inceppato e si riprova.
            .setMaxDelayBetweenMuxerSamplesMs(20_000)
            .setVideoMimeType(androidx.media3.common.MimeTypes.VIDEO_H264)
            // Al secondo tentativo con il decodificatore software: non si guasta se quelli hardware sono esauriti.
            .setAssetLoaderFactory(Decoders.assetLoader(ctx, softwareOnly = retry))
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: androidx.media3.transformer.ExportResult) {
                    tmp.renameTo(CrossfadePreview.file(ctx, job.key))
                    CrossfadePreview.trim(ctx, segNeeded)
                    segProduced = true
                    segTransformer = null
                    if (segQueue.firstOrNull()?.key == job.key) segQueue.removeFirst()
                    preparing = segQueue.size
                    nextSegment(false)
                }

                override fun onError(
                    composition: androidx.media3.transformer.Composition,
                    exportResult: androidx.media3.transformer.ExportResult,
                    exportException: androidx.media3.transformer.ExportException,
                ) {
                    tmp.delete()
                    segTransformer = null
                    if (!retry) {
                        // Forse mancano decoder: si libera l'anteprima e si riprova una volta.
                        releasePlayer()
                        nextSegment(true)
                        return
                    }
                    failedSegments += job.key
                    if (segQueue.firstOrNull()?.key == job.key) segQueue.removeFirst()
                    preparing = segQueue.size
                    if (segQueue.isEmpty()) rebuildPreview(playheadUs / 1000) else nextSegment(false)
                }
            })
            .build()
        segTransformer = t
        runCatching { t.start(composition, tmp.absolutePath) }.onFailure {
            segTransformer = null; failedSegments += job.key; segQueue.removeFirstOrNull(); nextSegment(false)
        }
    }

    private fun cancelPrerender() {
        proxyTransformer?.cancel(); proxyTransformer = null
        segSchedule?.cancel()
        segQueue.clear()
        segTransformer?.cancel()
        segTransformer = null
        preparing = 0
    }

    fun startExport() {
        val p = project ?: return
        if (p.clips.isEmpty() || exportState is ExportState.Running) return
        cancelPrerender()
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
        runCatching { getApplication<Application>().contentResolver.unregisterContentObserver(mediaObserver) }
        handler.removeCallbacks(mediaChanged)
        runCatching { recorder?.stop() }
        sampleTrack?.run { runCatching { stop() }; release() }
        project?.let { store.save(it) }
        releasePlayer()
    }
}
