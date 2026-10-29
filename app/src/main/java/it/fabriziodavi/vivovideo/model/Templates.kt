package it.fabriziodavi.vivovideo.model

import android.content.Context
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

/**
 * Un modello di montaggio: un progetto senza i suoi video. Ogni clip è uno "spazio" con durata, velocità,
 * transizione, effetti, colore, inquadratura e movimento; testi, musica, livelli e formato restano.
 */
@Serializable
data class Template(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val project: Project,
) {
    val slots: Int get() = project.clips.size
    val durationUs: Long get() = project.durationUs
}

object Templates {
    fun fromProject(p: Project, name: String): Template =
        Template(name = name.ifBlank { p.name }.take(40), project = p.copy(id = "modello", auto = null, originals = emptyList()))

    /**
     * Nuovo progetto dal modello con le clip [media] (in ordine): la clip i va nello spazio i
     * (se le clip sono di più, gli spazi si ripetono). [bestStart] sceglie l'inizio di un video per una lunghezza (o null = al centro).
     */
    fun apply(t: Template, media: List<Clip>, name: String = t.name, bestStart: (Clip, Long) -> Long? = { _, _ -> null }): Project {
        val slots = t.project.clips
        if (slots.isEmpty() || media.isEmpty()) return t.project.copy(id = UUID.randomUUID().toString(), name = name, clips = emptyList(), originals = emptyList())
        val n = maxOf(slots.size, media.size)
        val all = ClipAttr.entries.toSet()
        // I video originali sono quelli scelti per il modello (non quelli del progetto da cui è nato).
        var p = t.project.copy(id = UUID.randomUUID().toString(), name = name, createdAt = System.currentTimeMillis(), auto = null, originals = media)
        val clips = (0 until n).map { i ->
            val slot = slots[i % slots.size]
            val src = media[i % media.size]
            val base = if (src.isPhoto) {
                src.copy(trimStartUs = 0, trimEndUs = slot.outputUs.coerceIn(300_000L, Clip.PHOTO_MAX_US))
            } else {
                // Il video deve dare la stessa durata con la velocità dello spazio: serve [need] di materiale.
                val need = (if (slot.isPhoto) slot.outputUs else slot.trimmedUs).coerceAtMost(src.sourceDurationUs)
                val start = (bestStart(src, need) ?: ((src.sourceDurationUs - need) / 2)).coerceIn(0, (src.sourceDurationUs - need).coerceAtLeast(0))
                src.copy(trimStartUs = start, trimEndUs = start + need)
            }
            var c = ClipStyle.paste(slot, base.copy(id = UUID.randomUUID().toString()), all)
            if (slot.isPhoto && !src.isPhoto) c = c.copy(speed = ClipSpeed.NORMAL, speedPoints = emptyList())
            // Orientamento diverso da quello dello spazio: inquadratura secondo le preimpostazioni del progetto.
            if (src.orientation != slot.orientation) c = c.withMode(p.copy(clips = slots).presetModeFor(c)).copy(frame = Transform())
            c
        }
        p = p.copy(clips = clips)
        // L'ultima clip non ha transizione verso il nulla.
        return p.copy(clips = p.clips.mapIndexed { i, c -> if (i == p.clips.lastIndex) c.copy(transitionOut = null) else c })
    }
}

/** I modelli salvati (cartella "modelli" dell'app). */
class TemplateStore(context: Context) {
    private val dir = File(context.filesDir, "modelli").apply { mkdirs() }

    fun list(): List<Template> = dir.listFiles { f -> f.extension == "json" }.orEmpty()
        .mapNotNull { runCatching { Project.JSON.decodeFromString(Template.serializer(), it.readText()) }.getOrNull() }
        .sortedByDescending { it.createdAt }

    fun save(t: Template) = File(dir, "${t.id}.json").writeText(Project.JSON.encodeToString(Template.serializer(), t))
    fun delete(id: String) { File(dir, "$id.json").delete() }
}
