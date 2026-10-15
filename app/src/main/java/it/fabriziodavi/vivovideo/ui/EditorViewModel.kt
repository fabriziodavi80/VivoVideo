package it.fabriziodavi.vivovideo.ui

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
import it.fabriziodavi.vivovideo.engine.CompositionFactory
import it.fabriziodavi.vivovideo.engine.ExportOptions
import it.fabriziodavi.vivovideo.engine.ExportState
import it.fabriziodavi.vivovideo.engine.Exporter
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.ColorSettings
import it.fabriziodavi.vivovideo.model.Project
import it.fabriziodavi.vivovideo.model.ProjectStore
import it.fabriziodavi.vivovideo.readVideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    var selectedClipId by mutableStateOf<String?>(null)
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

    fun newProject(uris: List<Uri>) {
        val n = projects.size + 1
        val p = Project(name = "Progetto $n")
        openProject(p)
        addClips(uris)
    }

    fun openProject(p: Project, startPreview: Boolean = true) {
        project = p
        undoStack.clear(); redoStack.clear(); updateUndoFlags()
        selectedClipId = p.clips.firstOrNull()?.id
        exportState = ExportState.Idle
        if (startPreview) rebuildPreview(0)
    }

    fun closeProject() {
        project?.let { store.save(it) }
        releasePlayer()
        project = null
        refreshProjects()
    }

    /** Ogni modifica passa da qui: salva, aggiorna l'anteprima e prepara l'annulla. */
    private fun update(keepUndo: Boolean = true, change: (Project) -> Project?) {
        val old = project ?: return
        val new = change(old)?.touch() ?: return
        if (keepUndo) {
            undoStack.addLast(old); if (undoStack.size > 50) undoStack.removeFirst()
            redoStack.clear(); updateUndoFlags()
        }
        project = new
        scheduleSave()
        rebuildPreview(playheadUs / 1000)
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
            busy = "Lettura dei video…"
            val clips = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    // Il permesso resta valido anche dopo il riavvio del telefono.
                    runCatching {
                        ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
            if (clips.size < uris.size) message = "${uris.size - clips.size} video non leggibili sono stati saltati."
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

    fun splitAtPlayhead() {
        val p = project ?: return
        val located = p.locate(playheadUs) ?: return
        if (p.clips[located.first].speed == it.fabriziodavi.vivovideo.model.ClipSpeed.RAMP) {
            message = "Le clip con la rampa non si possono dividere: prima imposta una velocità fissa."
            return
        }
        val result = p.splitAt(playheadUs)
        if (result == null) { message = "Sposta l'indicatore un po' più lontano dal bordo della clip."; return }
        update { result }
        selectedClipId = result.clips[located.first + 1].id
    }

    fun trimClip(id: String, startUs: Long, endUs: Long) {
        val c = project?.clips?.firstOrNull { it.id == id } ?: return
        if (c.trimStartUs == startUs && c.trimEndUs == endUs) return
        update { it.replaceClip(c.copy(trimStartUs = startUs, trimEndUs = endUs)) }
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

    private fun rebuildPreview(positionMs: Long) {
        val p = project ?: return
        if (exportState is ExportState.Running) return
        if (p.clips.isEmpty()) { releasePlayer(); playheadUs = 0; return }
        val composition = runCatching { factory.build(p, exportOptions, forPreview = true, nativeAudio = null) }
            .getOrElse { previewError = "Anteprima non disponibile: ${it.message}"; return }
        val pl = player ?: CompositionPlayer.Builder(getApplication()).build().also { np ->
            np.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    previewError = "Errore anteprima: ${error.errorCodeName} ${error.message ?: ""}"
                }
            })
            np.repeatMode = Player.REPEAT_MODE_ALL
            player = np
            handler.removeCallbacks(ticker)
            handler.post(ticker)
        }
        previewError = null
        val wasPlaying = pl.isPlaying
        pl.setComposition(composition, positionMs.coerceIn(0, p.durationUs / 1000))
        pl.prepare()
        if (wasPlaying) pl.play()
    }

    private fun releasePlayer() {
        handler.removeCallbacks(ticker)
        player?.release()
        player = null
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
