package it.fabriziodavi.vivovideo.engine

import android.content.Context
import android.net.Uri
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.Layer
import it.fabriziodavi.vivovideo.model.Project
import it.fabriziodavi.vivovideo.model.TransitionType
import java.io.File
import java.security.MessageDigest

/**
 * Anteprima fedele senza sovrapporre più video in tempo reale (sul telefono il lettore si blocca):
 * i tratti con una dissolvenza incrociata o con video sovrapposti si preparano in anticipo con il motore
 * dell'esportazione come brevi video, e l'anteprima li riproduce al posto dei pezzi di clip.
 */
object CrossfadePreview {
    private const val MIN_SIDE_US = 100_000L
    /** Un confine a meno di così da un taglio si sposta sul taglio (niente pezzi minuscoli). */
    private const val SNAP_US = 120_000L

    /**
     * Un tratto da preparare, da [startUs] a [endUs] del video. [cf]/[original] ci sono solo per le
     * dissolvenze semplici (le due mezze clip attorno al taglio); [sub] è il mini-progetto da esportare.
     */
    data class Job(
        val key: String, val sub: Project, val cf: Project.Crossfade?, val durationUs: Long,
        val original: Project.Crossfade?, val startUs: Long, val endUs: Long,
    )

    fun dir(context: Context) = File(context.cacheDir, "dissolvenze").apply { mkdirs() }
    fun file(context: Context, key: String) = File(dir(context), "$key.mp4")

    private fun sha(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)

    private fun keyOf(sub: Project, extra: String, w: Int, h: Int) =
        sha(Project.JSON.encodeToString(Project.serializer(), sub.copy(createdAt = 0, updatedAt = 0, id = "x")) + "|$extra|${w}x$h")

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

    /** Dissolvenze semplici: le due mezze clip attorno a ogni taglio. */
    private fun crossfadeJobs(p: Project, w: Int, h: Int): List<Job> {
        val starts = p.clipStartsUs()
        return p.crossfades().mapNotNull { cf ->
            // Il passaggio con maschera sta tutto dopo il taglio (prima non c'è nulla sopra).
            val afterOnly = cf.type == TransitionType.MASK_PASS
            if ((cf.beforeUs < MIN_SIDE_US && !afterOnly) || cf.afterUs < MIN_SIDE_US) return@mapNotNull null
            val a = p.clips[cf.index]
            val b = p.clips[cf.index + 1]
            // Della clip prima serve almeno un pezzetto, anche quando la transizione è tutta dopo il taglio.
            val lead = maxOf(cf.beforeUs, MIN_SIDE_US).coerceAtMost(a.outputUs)
            val a2 = part(a, a.outputUs - lead, a.outputUs)?.copy(transitionOut = a.transitionOut) ?: return@mapNotNull null
            val b1 = part(b, 0, cf.afterUs)?.copy(transitionOut = null) ?: return@mapNotNull null
            // Niente musica, testi o livelli: restano sopra, nell'anteprima normale.
            // Identificativi fissi: dividere una clip ne crea uno casuale, e la chiave cambierebbe ogni volta.
            val sub = Project(name = "dissolvenza", clips = listOf(a2.copy(id = "a"), b1.copy(id = "b")), color = p.color)
            val local = Project.Crossfade(0, a2.outputUs, cf.beforeUs.coerceAtMost(a2.outputUs), cf.afterUs.coerceAtMost(b1.outputUs), cf.type)
            val cut = starts[cf.index + 1]
            Job(keyOf(sub, local.toString(), w, h), sub, local, a2.outputUs + b1.outputUs, cf, cut - a2.outputUs, cut + b1.outputUs)
        }
    }

    /** Divide il progetto nel punto [t] se cade dentro una clip (se è già un taglio, o troppo vicino, lo lascia). */
    private fun splitSafe(p: Project, t: Long): Project = p.splitAt(t) ?: p

    /** Sposta [t] sul taglio più vicino se ci sta entro SNAP_US. */
    private fun snap(p: Project, t: Long): Long {
        val cuts = p.clipStartsUs() + p.durationUs
        return cuts.minByOrNull { kotlin.math.abs(it - t) }?.takeIf { kotlin.math.abs(it - t) <= SNAP_US } ?: t
    }

    /** Tratti con video sovrapposti (uniti a quelli delle dissolvenze che toccano): tutto quello che c'è sotto, più i video. */
    private fun layerJobs(p: Project, cfJobs: List<Job>, w: Int, h: Int): Pair<List<Job>, Set<String>> {
        val total = p.durationUs
        val vl = p.layers.filter { it.isVideo && it.startUs < total && it.endUs > 0 }
        if (vl.isEmpty()) return emptyList<Job>() to emptySet()
        var ranges = vl.map { snap(p, it.startUs.coerceAtLeast(0)) to snap(p, it.endUs.coerceAtMost(total)) }
        fun merge(r: List<Pair<Long, Long>>): List<Pair<Long, Long>> {
            val out = mutableListOf<Pair<Long, Long>>()
            r.sortedBy { it.first }.forEach { x ->
                val last = out.lastOrNull()
                if (last != null && x.first <= last.second + 200_000) out[out.lastIndex] = last.first to maxOf(last.second, x.second) else out += x
            }
            return out
        }
        ranges = merge(ranges)
        // Le dissolvenze che toccano un tratto con video sovrapposti si preparano insieme a lui.
        val absorbed = mutableSetOf<String>()
        repeat(2) {
            ranges = merge(ranges.map { r ->
                var a = r.first; var b = r.second
                cfJobs.forEach { j -> if (j.startUs < b && j.endUs > a) { a = minOf(a, j.startUs); b = maxOf(b, j.endUs); absorbed += j.key } }
                a to b
            })
        }
        val jobs = ranges.mapNotNull { (a, b) ->
            if (b - a < 300_000) return@mapNotNull null
            var q = p.copy(texts = emptyList(), music = emptyList(), layers = emptyList(), auto = null)
            q = splitSafe(splitSafe(q, a), b)
            val starts = q.clipStartsUs()
            val inside = q.clips.filterIndexed { i, c -> starts[i] >= a - 2_000 && starts[i] + c.outputUs <= b + 2_000 }
            if (inside.isEmpty() || kotlin.math.abs(inside.sumOf { it.outputUs } - (b - a)) > 20_000) return@mapNotNull null
            val layers = vl.filter { it.startUs < b && it.endUs > a }.mapIndexed { i, l ->
                var x = l.copy(id = "l$i", startUs = l.startUs - a)
                if (x.startUs < 0) x = x.copy(sourceStartUs = x.sourceStartUs - x.startUs, durationUs = x.durationUs + x.startUs, startUs = 0)
                x.copy(durationUs = minOf(x.durationUs, (b - a) - x.startUs))
            }.filter { it.durationUs > Layer.MIN_US }
            val clips = inside.mapIndexed { i, c -> c.copy(id = "c$i", transitionOut = if (i == inside.lastIndex) null else c.transitionOut) }
            val sub = Project(name = "tratto", clips = clips, layers = layers, color = p.color, format = p.format)
            Job(keyOf(sub, "tratto", w, h), sub, null, b - a, null, a, b)
        }
        return jobs to absorbed
    }

    /** Tratti da preparare per il progetto (misure dell'anteprima [w]×[h]). */
    fun jobs(p: Project, w: Int, h: Int): List<Job> {
        val cf = crossfadeJobs(p, w, h)
        val (layer, absorbed) = layerJobs(p, cf, w, h)
        return cf.filter { it.key !in absorbed } + layer
    }

    /**
     * Progetto per l'anteprima: dove un tratto è pronto, i pezzi di clip lì sotto diventano il video preparato
     * (e i video sovrapposti di quel tratto spariscono, perché sono già dentro). Le durate restano le stesse:
     * testi, musica e livelli restano a tempo.
     */
    fun previewProject(context: Context, p: Project, w: Int, h: Int): Project {
        val ready = jobs(p, w, h).filter { file(context, it.key).exists() }.sortedBy { it.startUs }
        if (ready.isEmpty()) return p
        var q = p
        ready.forEach { j -> q = splitSafe(splitSafe(q, j.startUs), j.endUs) }
        val starts = q.clipStartsUs()
        val out = mutableListOf<Clip>()
        val used = mutableSetOf<String>()
        q.clips.forEachIndexed { i, c ->
            val s = starts[i]; val e = s + c.outputUs
            val job = ready.firstOrNull { s >= it.startUs - 2_000 && e <= it.endUs + 2_000 }
            if (job == null) {
                // La clip subito prima di un tratto pronto non fa più la sua metà di dissolvenza incrociata.
                val next = ready.firstOrNull { kotlin.math.abs(it.startUs - e) < 2_000 }
                out += if (next != null && c.transitionOut?.type?.composite == true) c.copy(transitionOut = null) else c
                return@forEachIndexed
            }
            if (job.key in used) return@forEachIndexed
            used += job.key
            out += Clip(
                uri = Uri.fromFile(file(context, job.key)).toString(),
                sourceDurationUs = job.durationUs + 200_000,
                trimEndUs = job.durationUs,
                width = w, height = h, fps = 30f, baked = true,
            )
        }
        // Se qualcosa non torna (durate diverse) meglio l'anteprima normale.
        val result = p.copy(
            clips = out,
            layers = p.layers.filterNot { l -> l.isVideo && ready.any { j -> j.cf == null && l.startUs >= j.startUs - SNAP_US && l.endUs <= j.endUs + SNAP_US } },
        )
        return if (kotlin.math.abs(result.durationUs - p.durationUs) > 30_000) p else result
    }

    /** Tiene solo i pezzi più recenti. */
    /**
     * Fa spazio: tiene tutti i tratti che servono al progetto aperto ([needed], le chiavi) e, degli altri,
     * i [keep] più recenti. Prima teneva solo gli ultimi 60 in tutto: con più di 60 dissolvenze l'app cancellava
     * i primi tratti per fare gli ultimi, poi rifaceva i primi… e non finiva mai.
     */
    fun trim(context: Context, needed: Set<String> = emptySet(), keep: Int = 60) {
        dir(context).listFiles()?.filter { f -> needed.none { f.name.startsWith(it) } }
            ?.sortedByDescending { it.lastModified() }?.drop(keep)?.forEach { it.delete() }
    }
}
