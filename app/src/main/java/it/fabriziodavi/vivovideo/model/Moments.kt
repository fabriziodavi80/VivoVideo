package it.fabriziodavi.vivovideo.model

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Un istante analizzato di un video (o una foto). Valori 0…1 salvo dove indicato.
 * [sharp] nitidezza (varianza del laplaciano, non normalizzata), [luma] luminosità media,
 * [clipped] parte bruciata o nera, [motion] differenza dall'istante prima, [faces] volti trovati,
 * [smile] e [eyes] del volto più grande, [faceSize] sua larghezza rispetto all'immagine,
 * [subjectX]/[subjectY] centro del soggetto (volti, altrimenti la zona più ricca di dettagli).
 */
@Serializable
data class MomentSample(
    val tUs: Long,
    val sharp: Float,
    val luma: Float,
    val clipped: Float = 0f,
    val motion: Float = 0f,
    val faces: Int = 0,
    val smile: Float = 0f,
    val eyes: Float = 0f,
    val faceSize: Float = 0f,
    val subjectX: Float = 0.5f,
    val subjectY: Float = 0.5f,
    val hasSubject: Boolean = false,
)

@Serializable
data class MomentScan(val uri: String, val durationUs: Long, val samples: List<MomentSample>) {
    val hasFaces: Boolean get() = samples.any { it.faces > 0 }
}

/** Percorso del soggetto nel tempo del file: centro (0…1) a ogni istante [times]. */
@Serializable
data class Reframe(val times: List<Long> = emptyList(), val xs: List<Float> = emptyList(), val ys: List<Float> = emptyList()) {
    /** Centro al tempo [tUs] del file (interpolato, fermo prima del primo e dopo l'ultimo istante). */
    fun at(tUs: Long): Pair<Float, Float> {
        if (times.isEmpty()) return 0.5f to 0.5f
        if (tUs <= times.first()) return xs.first() to ys.first()
        if (tUs >= times.last()) return xs.last() to ys.last()
        var i = 0
        while (i < times.size - 2 && times[i + 1] < tUs) i++
        val u = ((tUs - times[i]).toFloat() / (times[i + 1] - times[i]).coerceAtLeast(1)).coerceIn(0f, 1f)
        // Movimento morbido (come un operatore che segue il soggetto).
        val s = u * u * (3 - 2 * u)
        return (xs[i] + (xs[i + 1] - xs[i]) * s) to (ys[i] + (ys[i + 1] - ys[i]) * s)
    }
}

object Moments {
    /** Punteggio 0…1 di ogni istante, confrontato con gli altri dello stesso video (nitidezza relativa). */
    fun scores(scan: MomentScan): FloatArray {
        val s = scan.samples
        if (s.isEmpty()) return FloatArray(0)
        val sorted = s.map { it.sharp }.sorted()
        val med = sorted[sorted.size / 2].coerceAtLeast(1e-3f)
        return FloatArray(s.size) { i ->
            val m = s[i]
            // Nitidezza: rispetto alla mediana del video, e penalità forte se è proprio mossa (valore assoluto basso).
            val sharpRel = (m.sharp / (2f * med)).coerceIn(0f, 1f)
            val sharpAbs = (m.sharp / 200f).coerceIn(0f, 1f)
            val sharp = 0.6f * sharpRel + 0.4f * sharpAbs
            val exposure = (1f - abs(m.luma - 0.47f) * 2.2f).coerceIn(0f, 1f) * (1f - m.clipped).coerceIn(0f, 1f)
            val face = if (m.faces > 0) {
                val size = (m.faceSize / 0.25f).coerceIn(0.3f, 1f)
                (0.45f + 0.35f * m.smile + 0.2f * m.eyes) * size
            } else 0f
            // Un po' di movimento è vita; troppo è confusione (o camera che sbanda).
            val mo = m.motion.coerceIn(0f, 1f)
            val motion = (4f * mo * (1f - mo)).coerceIn(0f, 1f)
            (0.36f * sharp + 0.2f * exposure + 0.3f * face + 0.14f * motion).coerceIn(0f, 1f)
        }
    }

    /**
     * I migliori [count] tratti lunghi [lenUs] (inizio nel file), che non si sovrappongono, dal migliore.
     * Il tratto deve stare tutto nel video.
     */
    fun bestStarts(scan: MomentScan, lenUs: Long, count: Int = 3): List<Long> {
        val dur = scan.durationUs
        if (lenUs >= dur || scan.samples.isEmpty()) return listOf(0L)
        val sc = scores(scan)
        val ts = scan.samples.map { it.tUs }
        val maxStart = dur - lenUs
        // Candidati: ogni campione come inizio, centro o fine del tratto, più passi regolari.
        val step = max(100_000L, maxStart / 40)
        val cands = (ts.flatMap { listOf(it, it - lenUs / 2, it - lenUs) } + (0..maxStart step step)).map { it.coerceIn(0, maxStart) }.distinct()
        fun value(start: Long): Float {
            val end = start + lenUs
            var acc = 0f; var w = 0f
            ts.forEachIndexed { i, t ->
                // Peso pieno dentro il tratto, che sfuma fuori (i campioni sono radi).
                val d = if (t < start) start - t else if (t > end) t - end else 0L
                val k = exp(-(d / 700_000f) * (d / 700_000f))
                acc += sc[i] * k; w += k
            }
            return if (w <= 1e-4f) 0f else acc / w
        }
        val ranked = cands.map { it to value(it) }.sortedByDescending { it.second }
        val out = mutableListOf<Long>()
        for ((s, _) in ranked) {
            if (out.all { o -> abs(o - s) >= lenUs }) out += s
            if (out.size >= count) break
        }
        return out.ifEmpty { listOf(0L) }
    }

    /** Quanto è riuscita una foto (o un istante), 0…1, senza confronti: per scartare le foto mosse. */
    fun photoScore(m: MomentSample): Float = scores(MomentScan("", 1, listOf(m))).first()

    /** Percorso del soggetto levigato: segue i volti (o i dettagli), senza scatti. */
    fun subjectPath(scan: MomentScan): Reframe? {
        val s = scan.samples.filter { it.hasSubject }
        if (s.isEmpty()) return null
        val xs = s.map { it.subjectX }.toFloatArray(); val ys = s.map { it.subjectY }.toFloatArray()
        // Media pesata con i vicini (circa 1,5 s): niente salti da un volto all'altro.
        val sx = smoothTimes(s.map { it.tUs }, xs, 1_500_000f)
        val sy = smoothTimes(s.map { it.tUs }, ys, 1_500_000f)
        return Reframe(s.map { it.tUs }, sx.map { it.coerceIn(0f, 1f) }, sy.map { it.coerceIn(0f, 1f) })
    }

    private fun smoothTimes(t: List<Long>, v: FloatArray, sigmaUs: Float): List<Float> = t.indices.map { i ->
        var acc = 0f; var w = 0f
        t.indices.forEach { j ->
            val d = (t[j] - t[i]) / sigmaUs
            val k = exp(-d * d / 2)
            acc += v[j] * k; w += k
        }
        acc / w
    }

    /**
     * Spostamento (coordinate normalizzate, -1…1) che porta il soggetto in centro, quanto basta e senza mostrare bordi:
     * [srcAspect] e [outAspect] = larghezza/altezza della sorgente e del video finale (con riempimento).
     */
    fun panFor(cx: Float, cy: Float, srcAspect: Float, outAspect: Float): Pair<Float, Float> {
        // Parte visibile della sorgente dopo il riempimento (frazione di larghezza o di altezza).
        val fw = min(1f, outAspect / srcAspect)
        val fh = min(1f, srcAspect / outAspect)
        val tx = (-(2f * cx - 1f)).coerceIn(-(1f - fw), 1f - fw)
        val ty = (2f * cy - 1f).coerceIn(-(1f - fh), 1f - fh)
        return tx to ty
    }
}

/** Cosa fare dello sfondo dietro alla persona. */
enum class PersonMode(val label: String) { BLUR("Sfoca lo sfondo"), COLOR("Sfondo a colore"), MONO("Sfondo in bianco e nero"), REMOVE("Solo la persona") }

/** Scontorno della persona: [maskFile] = maschere già calcolate (dal tempo [fromUs] del file, [rateHz] al secondo). */
@Serializable
data class PersonFx(
    val mode: PersonMode = PersonMode.BLUR,
    val color: Int = 0xFF101418.toInt(),
    val maskFile: String = "",
    val fromUs: Long = 0,
    val rateHz: Int = 15,
    val count: Int = 0,
)
