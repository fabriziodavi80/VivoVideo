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
    /** Tremolio della camera (quanto cambia lo spostamento da un fotogramma al successivo, in frazioni della larghezza). */
    val shake: Float = 0f,
    /** Nitidezza dentro il volto più grande (come [sharp]); 0 = nessun volto. */
    val faceSharp: Float = 0f,
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
    /**
     * Punteggio 0…1 di ogni istante, confrontato con gli altri dello stesso video (o della stessa raffica di foto):
     * nitidezza (del volto, se c'è), luce, volti (sorrisi, occhi aperti), un po' di vita e camera ferma.
     */
    fun scores(scan: MomentScan): FloatArray {
        val s = scan.samples
        if (s.isEmpty()) return FloatArray(0)
        // Dove c'è un volto conta la sua nitidezza (il fuoco sul soggetto), altrimenti quella di tutta l'immagine.
        val sharpOf = s.map { m -> if (m.faces > 0 && m.faceSharp > 0f) 0.6f * m.faceSharp + 0.4f * m.sharp else m.sharp }
        val sorted = sharpOf.sorted()
        val med = sorted[sorted.size / 2].coerceAtLeast(1e-3f)
        return FloatArray(s.size) { i ->
            val m = s[i]
            // Nitidezza: rispetto alla mediana; rispetto agli istanti vicini (±1,5 s: stessa scena, quindi la differenza
            // è il mosso); e in assoluto (penalità se è proprio impastata).
            var near = 0f
            s.forEachIndexed { j, o -> if (abs(o.tUs - m.tUs) <= 1_500_000) near = max(near, sharpOf[j]) }
            val sharpRel = (sharpOf[i] / (2f * med)).coerceIn(0f, 1f)
            val sharpLocal = if (near > 0f) (sharpOf[i] / near).coerceIn(0f, 1f) else 1f
            val sharpAbs = (sharpOf[i] / 200f).coerceIn(0f, 1f)
            val sharp = 0.4f * sharpRel + 0.4f * sharpLocal + 0.2f * sharpAbs
            val exposure = (1f - abs(m.luma - 0.47f) * 2.2f).coerceIn(0f, 1f) * (1f - m.clipped).coerceIn(0f, 1f)
            val face = if (m.faces > 0) {
                val size = (m.faceSize / 0.25f).coerceIn(0.3f, 1f)
                (0.45f + 0.35f * m.smile + 0.2f * m.eyes) * size
            } else 0f
            // Un po' di movimento è vita; troppo è confusione (o camera che sbanda).
            val mo = m.motion.coerceIn(0f, 1f)
            val motion = (4f * mo * (1f - mo)).coerceIn(0f, 1f)
            // Camera ferma: un leggero tremolio della mano va bene, gli scossoni no.
            val steady = (1f - (m.shake - 0.004f) / 0.02f).coerceIn(0f, 1f)
            (0.3f * sharp + 0.16f * exposure + 0.28f * face + 0.1f * motion + 0.16f * steady).coerceIn(0f, 1f)
        }
    }

    /**
     * I migliori [count] tratti lunghi [lenUs] (inizio nel file), che non si sovrappongono, dal migliore.
     * Il tratto deve stare tutto nel video. Si evitano i primi e gli ultimi istanti (il telefono che si alza
     * o si abbassa) e, se [calm], i tratti molto mossi.
     */
    fun bestStarts(scan: MomentScan, lenUs: Long, count: Int = 3, calm: Boolean = false): List<Long> {
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
            if (w <= 1e-4f) return 0f
            // Anche il momento peggiore dentro il tratto conta: mezzo secondo mosso in una clip di 3 si nota.
            val inside = ts.indices.filter { ts[it] in start..end }.map { sc[it] }.sorted()
            val worst = if (inside.size >= 3) inside[inside.size / 10] else acc / w
            var v = 0.75f * (acc / w) + 0.25f * worst
            // Bordi del video: inizio e fine spesso sono mossi o inquadrano a caso.
            if (maxStart > 2 * EDGE_US) {
                if (start < EDGE_US) v *= 0.85f
                if (end > dur - EDGE_US) v *= 0.85f
            }
            if (calm) {
                // Movimento medio dentro il tratto: nei temi calmi niente scene che sbandano.
                var mAcc = 0f; var mN = 0
                scan.samples.forEach { if (it.tUs in start..end) { mAcc += it.motion; mN++ } }
                if (mN > 0) v -= 0.35f * (mAcc / mN - 0.45f).coerceAtLeast(0f)
            }
            return v
        }
        val ranked = cands.map { it to value(it) }.sortedByDescending { it.second }
        val out = mutableListOf<Long>()
        for ((s, _) in ranked) {
            if (out.all { o -> abs(o - s) >= lenUs }) out += s
            if (out.size >= count) break
        }
        return out.ifEmpty { listOf(0L) }
    }

    /** Perché il tratto [start, start+[lenUs]) è buono, in poche parole ("nitido, camera ferma, volti sorridenti"). */
    fun describe(scan: MomentScan, start: Long, lenUs: Long): String {
        val inside = scan.samples.filter { it.tUs in start..start + lenUs }.ifEmpty { return "" }
        val all = scan.samples
        val words = mutableListOf<String>()
        val sharpIn = inside.map { it.sharp }.average(); val sharpAll = all.map { it.sharp }.average()
        if (sharpIn >= sharpAll * 1.05) words += "più nitido"
        if (all.any { it.shake > 0f }) {
            val shIn = inside.map { it.shake }.average(); val shAll = all.map { it.shake }.average()
            if (shIn <= shAll * 0.85 || shIn < 0.006) words += "camera ferma"
        }
        val faces = inside.count { it.faces > 0 }
        if (faces * 2 >= inside.size) words += if (inside.filter { it.faces > 0 }.map { it.smile }.average() > 0.5) "volti sorridenti" else "volti in vista"
        val lightIn = inside.map { abs(it.luma - 0.47f) }.average(); val lightAll = all.map { abs(it.luma - 0.47f) }.average()
        if (lightIn < lightAll * 0.85) words += "luce migliore"
        return words.joinToString(", ")
    }

    /** Primi e ultimi istanti di un video da evitare. */
    const val EDGE_US = 600_000L

    /**
     * Punteggi delle foto di una raffica, confrontate fra loro (nitidezza relativa al gruppo, del volto se c'è).
     * Null per le foto non analizzate.
     */
    fun burstScores(samples: List<MomentSample?>): List<Float?> {
        val known = samples.filterNotNull()
        if (known.isEmpty()) return samples.map { null }
        val sc = scores(MomentScan("", 1, known.mapIndexed { i, m -> m.copy(tUs = i * 10_000_000L) }))
        var k = 0
        return samples.map { if (it == null) null else sc[k++] }
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
    /** Fatto con il modello "Preciso" di Google (altrimenti quello veloce del telefono). */
    val precise: Boolean = false,
)
