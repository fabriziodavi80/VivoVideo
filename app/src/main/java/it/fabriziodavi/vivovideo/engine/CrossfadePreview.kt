package it.fabriziodavi.vivovideo.engine

import android.content.Context
import android.net.Uri
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.Project
import java.io.File
import java.security.MessageDigest

/**
 * Dissolvenze incrociate vere anche in anteprima, senza sovrapporre due video in tempo reale
 * (sul telefono il lettore si blocca): ogni dissolvenza si prepara in anticipo con il motore
 * dell'esportazione come breve video, e l'anteprima lo riproduce al posto dei due pezzi di clip.
 */
object CrossfadePreview {
    private const val MIN_SIDE_US = 100_000L

    /** Un pezzo da preparare: le due mezze clip attorno al taglio, con la loro dissolvenza. */
    data class Job(val key: String, val sub: Project, val cf: Project.Crossfade, val durationUs: Long, val original: Project.Crossfade)

    fun dir(context: Context) = File(context.cacheDir, "dissolvenze").apply { mkdirs() }
    fun file(context: Context, key: String) = File(dir(context), "$key.mp4")

    private fun sha(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)

    /** Parte di una clip da [fromUs] a [toUs] del suo tempo nel video finale (taglio come "Dividi"). */
    private fun part(c: Clip, fromUs: Long, toUs: Long): Clip? {
        var p = Project(name = "", clips = listOf(c))
        var clip = c
        if (fromUs > 0) {
            p = p.splitAt(fromUs) ?: return null
            clip = p.clips[1]
            p = Project(name = "", clips = listOf(clip))
        }
        val len = toUs - fromUs
        if (len < clip.outputUs - 5_000) {
            p = p.splitAt(len) ?: return null
            clip = p.clips[0]
        }
        return clip
    }

    /** Pezzi da preparare per il progetto (misure dell'anteprima [w]×[h]). */
    fun jobs(p: Project, w: Int, h: Int): List<Job> = p.crossfades().mapNotNull { cf ->
        if (cf.beforeUs < MIN_SIDE_US || cf.afterUs < MIN_SIDE_US) return@mapNotNull null
        val a = p.clips[cf.index]
        val b = p.clips[cf.index + 1]
        val a2 = part(a, a.outputUs - cf.beforeUs, a.outputUs)?.copy(transitionOut = a.transitionOut) ?: return@mapNotNull null
        val b1 = part(b, 0, cf.afterUs)?.copy(transitionOut = null) ?: return@mapNotNull null
        // Niente musica, testi o livelli: restano sopra, nell'anteprima normale.
        // Identificativi fissi: dividere una clip ne crea uno casuale, e la chiave cambierebbe ogni volta.
        val sub = Project(name = "dissolvenza", clips = listOf(a2.copy(id = "a"), b1.copy(id = "b")), color = p.color)
        val local = Project.Crossfade(0, a2.outputUs, cf.beforeUs.coerceAtMost(a2.outputUs), cf.afterUs.coerceAtMost(b1.outputUs))
        val key = sha(Project.JSON.encodeToString(Project.serializer(), sub.copy(createdAt = 0, updatedAt = 0, id = "x")) + "|$local|${w}x$h")
        Job(key, sub, local, a2.outputUs + b1.outputUs, cf)
    }

    /**
     * Progetto per l'anteprima: dove la dissolvenza è pronta, le due mezze clip attorno al taglio
     * diventano il video preparato. Le durate restano le stesse, quindi testi, musica e livelli restano a tempo.
     */
    fun previewProject(context: Context, p: Project, w: Int, h: Int): Project {
        // Per ogni taglio pronto: indice della clip prima del taglio → lavoro.
        val byIndex = jobs(p, w, h).filter { file(context, it.key).exists() }.associateBy { it.original.index }
        if (byIndex.isEmpty()) return p
        val out = mutableListOf<Clip>()
        p.clips.forEachIndexed { k, c ->
            val head = byIndex[k - 1]?.original?.afterUs ?: 0L
            val tail = byIndex[k]?.original?.beforeUs ?: 0L
            val middle = if (head == 0L && tail == 0L) c else part(c, head, c.outputUs - tail) ?: return p
            out += if (tail > 0) middle.copy(transitionOut = null) else middle
            byIndex[k]?.let { job ->
                val d = job.original.beforeUs + job.original.afterUs
                out += Clip(
                    uri = Uri.fromFile(file(context, job.key)).toString(),
                    sourceDurationUs = d + 200_000,
                    trimEndUs = d,
                    width = w, height = h, fps = 30f, baked = true,
                )
            }
        }
        return p.copy(clips = out)
    }

    /** Tiene solo i pezzi più recenti. */
    fun trim(context: Context, keep: Int = 60) {
        dir(context).listFiles()?.sortedByDescending { it.lastModified() }?.drop(keep)?.forEach { it.delete() }
    }
}
