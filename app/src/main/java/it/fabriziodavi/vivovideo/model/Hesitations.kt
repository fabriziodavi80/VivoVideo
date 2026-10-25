package it.fabriziodavi.vivovideo.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * "Ehm" riconosciuti dal suono, non solo dalle parole del riconoscimento vocale (che spesso li salta o ne sbaglia i tempi).
 * Un ehm è un suono tenuto: voce continua, tono quasi fermo, timbro fermo, di vocale "e" o di mormorio a bocca chiusa
 * ("mmm"), fra due pause. Le parole vere invece cambiano timbro di continuo (consonanti, passaggi fra vocali).
 *
 * Lavora a 8 kHz, un fotogramma ogni 10 ms.
 */
object Hesitations {
    const val HOP_US = 10_000L
    private const val RATE = 8_000
    private const val HOP = 80
    private const val NFFT = 256

    /** Timbro del fotogramma: E = vocale "e" (o "i"), N = mormorio basso ("mmm", "uhm"), O = altro, ' ' = senza voce. */
    data class Frame(val db: Float, val voiced: Boolean, val semitone: Float, val kind: Char, val flux: Float)

    /** Un tratto di parlato fra due pause, in fotogrammi [from, to). */
    data class Island(val from: Int, val to: Int, val gapBefore: Int, val gapAfter: Int) {
        val frames: Int get() = to - from
    }

    /** Analisi dell'audio mono a 16 kHz. */
    fun analyze(pcm16: FloatArray): List<Frame> {
        val n8 = pcm16.size / 2
        val x = FloatArray(n8) { (pcm16[2 * it] + pcm16[2 * it + 1]) * 0.5f }
        val frames = (n8 - 280) / HOP + 1
        if (frames <= 2) return emptyList()
        val window = FloatArray(NFFT) { (0.5 - 0.5 * cos(2 * PI * it / NFFT)).toFloat() }
        val re = FloatArray(NFFT); val im = FloatArray(NFFT)
        val bands = 16 // 250 Hz l'una
        var prev2: FloatArray? = null
        var prev1: FloatArray? = null
        val out = ArrayList<Frame>(frames)
        for (f in 0 until frames) {
            val o = f * HOP
            var e = 0.0
            for (i in 0 until NFFT) { val v = x[o + i]; e += v * v; re[i] = v * window[i]; im[i] = 0f }
            val db = (10 * log10(e / NFFT + 1e-12)).toFloat()
            fft(re, im)
            val pow = FloatArray(NFFT / 2) { re[it] * re[it] + im[it] * im[it] }
            fun band(lo: Double, hi: Double): Double {
                var s = 1e-12
                for (b in (lo / 31.25).toInt() until (hi / 31.25).toInt().coerceAtMost(NFFT / 2)) s += pow[b]
                return 10 * log10(s)
            }
            val bandDb = FloatArray(bands) { b -> band(b * 250.0, (b + 1) * 250.0).toFloat() }
            // Stabilità del timbro: differenza media (dB) con 20 ms prima, sulle bande che contano.
            val ref = prev2
            val flux = if (ref == null) 99f else {
                val top = bandDb.max()
                var s = 0f; var k = 0
                for (b in 0 until bands) if (bandDb[b] > top - 35 || ref[b] > top - 35) { s += abs(bandDb[b] - ref[b]); k++ }
                if (k == 0) 99f else s / k
            }
            prev2 = prev1; prev1 = bandDb
            // Voce: autocorrelazione normalizzata fra 70 e 400 Hz (finestra di 20 ms). Si prende il ritardo più corto
            // vicino al massimo, così il tono non salta all'ottava sotto da un fotogramma all'altro.
            val w = 160
            val r = DoubleArray(115)
            var best = 0.0
            for (l in 20..114) {
                var a = 0.0; var b = 0.0; var c = 0.0
                for (i in 0 until w) { val p = x[o + i]; val q = x[o + i + l]; a += p * q; b += p * p; c += q * q }
                r[l] = if (b > 0 && c > 0) a / sqrt(b * c) else 0.0
                if (r[l] > best) best = r[l]
            }
            var lag = 0
            for (l in 21..113) if (r[l] >= 0.92 * best && r[l] >= r[l - 1] && r[l] >= r[l + 1]) { lag = l; break }
            val voiced = best > 0.6 && lag > 0
            val st = if (voiced) (12 * ln(RATE.toDouble() / lag / 100.0) / ln(2.0)).toFloat() else 0f
            val kind = if (!voiced) ' ' else {
                val low = band(94.0, 440.0); val b = band(800.0, 1400.0); val c = band(1500.0, 2700.0)
                when {
                    c < low - 28 && low - b > 6 -> 'N'
                    c - b > 0 -> 'E'
                    else -> 'O'
                }
            }
            out += Frame(db, voiced, st, kind, flux)
        }
        return out
    }

    /** Tratti di parlato fra le pause (buchi sotto la soglia lunghi almeno 80 ms). */
    fun islands(frames: List<Frame>): List<Island> {
        if (frames.size < 10) return emptyList()
        val sorted = frames.map { it.db }.sorted()
        val floor = sorted[(sorted.size * 0.1).toInt()]
        val speech = sorted[(sorted.size * 0.9).toInt().coerceAtMost(sorted.size - 1)]
        if (speech - floor < 10f) return emptyList()
        val thr = floor + 0.3f * (speech - floor)
        val runs = ArrayList<IntArray>()
        var s = -1
        for (f in 0..frames.size) {
            val loud = f < frames.size && frames[f].db >= thr
            if (loud && s < 0) s = f
            if (!loud && s >= 0) { runs += intArrayOf(s, f); s = -1 }
        }
        // Buchi più corti di 80 ms (una consonante, un'occlusiva) non separano.
        val merged = ArrayList<IntArray>()
        runs.forEach { r -> val l = merged.lastOrNull(); if (l != null && r[0] - l[1] < 8) l[1] = r[1] else merged += r }
        return merged.mapIndexed { i, r ->
            Island(r[0], r[1], r[0] - (merged.getOrNull(i - 1)?.get(1) ?: 0), (merged.getOrNull(i + 1)?.get(0) ?: frames.size) - r[1])
        }
    }

    /** Quanto un tratto [from, to) somiglia a un ehm. */
    data class Stats(val voiced: Float, val steady: Float, val en: Float, val rangeSt: Float, val longestUnvoiced: Int, val eAfterN: Float)

    fun stats(frames: List<Frame>, from: Int, to: Int): Stats {
        val seg = frames.subList(from, to)
        val v = seg.filter { it.voiced }
        if (v.isEmpty()) return Stats(0f, 0f, 0f, 99f, seg.size, 0f)
        var steady = 0
        for (i in from until to) {
            val f = frames[i]
            if (!f.voiced) continue
            val p = frames.getOrNull(i - 1)
            val stable = f.flux < 4f && (p == null || !p.voiced || abs(p.semitone - f.semitone) < 1f)
            if (stable) steady++
        }
        val tones = v.map { it.semitone }.sorted()
        val range = tones[(tones.size * 0.9).toInt().coerceAtMost(tones.size - 1)] - tones[(tones.size * 0.1).toInt()]
        var longest = 0; var run = 0
        seg.forEach { if (!it.voiced) { run++; longest = max(longest, run) } else run = 0 }
        // "ehm" va da "e" a "m": dopo il mormorio non torna la "e" (altrimenti è una parola come "nemmeno").
        val firstN = (from until to).firstOrNull { i -> (i until min(to, i + 5)).all { frames[it].kind == 'N' } }
        val eAfter = if (firstN == null) 0f else (firstN until to).count { frames[it].kind == 'E' }.toFloat() / v.size
        return Stats(
            voiced = v.size.toFloat() / seg.size,
            steady = steady.toFloat() / v.size,
            en = v.count { it.kind == 'E' || it.kind == 'N' }.toFloat() / v.size,
            rangeSt = range,
            longestUnvoiced = longest,
            eAfterN = eAfter,
        )
    }

    /** Regole severe: si usano quando il riconoscimento vocale non ha sentito nulla. */
    fun strict(s: Stats, frames: Int): Boolean =
        frames in 30..180 && s.voiced >= 0.85f && s.longestUnvoiced <= 4 && s.steady >= 0.75f && s.en >= 0.85f && s.rangeSt <= 3f && s.eAfterN <= 0.1f

    /** Regole larghe: il riconoscimento vocale ha già detto "ehm", qui si cerca solo dove sta di preciso. */
    fun relaxed(s: Stats, frames: Int): Boolean =
        frames in 12..250 && s.voiced >= 0.7f && s.steady >= 0.55f && s.en >= 0.65f && s.rangeSt <= 4f && s.eAfterN <= 0.2f

    private fun islandSpan(i: Island): Span {
        // Un filo di margine dentro le pause ai lati (mai più di metà pausa).
        val a = i.from - min(3, i.gapBefore / 2)
        val b = i.to + min(3, i.gapAfter / 2)
        return Span(a.coerceAtLeast(0) * HOP_US, b * HOP_US)
    }

    /** Ehm isolati (fra due pause) trovati solo dal suono. */
    fun detect(frames: List<Frame>, islands: List<Island> = islands(frames)): List<Span> =
        islands.filter { it.gapBefore >= 10 && it.gapAfter >= 10 && strict(stats(frames, it.from, it.to), it.frames) }.map(::islandSpan)

    /**
     * Dove sta davvero l'ehm che il riconoscimento vocale ha messo in [heard]: il tratto di parlato vicino che suona come
     * un ehm, oppure il suono tenuto attaccato alla fine (o all'inizio) di una parola ("allora-ehm").
     * Null se nei paraggi non c'è niente che suoni come un ehm.
     */
    fun locate(frames: List<Frame>, islands: List<Island>, heard: Span): Span? {
        val a = ((heard.startUs - 250_000) / HOP_US).toInt()
        val b = ((heard.endUs + 250_000) / HOP_US).toInt()
        val near = islands.filter { it.to > a && it.from < b }
        fun overlap(i: Island) = (min(i.to, b) - max(i.from, a)).toFloat() / i.frames
        near.filter { relaxed(stats(frames, it.from, it.to), it.frames) }.maxByOrNull(::overlap)?.let { return islandSpan(it) }
        // Suono tenuto all'inizio o alla fine di un tratto di parlato.
        for (i in near.sortedByDescending(::overlap)) {
            fun isEn(k: Int) = frames[k].voiced && (frames[k].kind == 'E' || frames[k].kind == 'N')
            fun held(from: Int, to: Int) = stats(frames, from, to).let { it.steady >= 0.6f && it.eAfterN <= 0.2f && it.rangeSt <= 4f }
            // Gli ultimi (o primi) fotogrammi, dove la voce si spegne, possono avere un timbro qualsiasi.
            var end = i.to
            while (end > i.from && i.to - end < 4 && !isEn(end - 1)) end--
            var t = end
            while (t > i.from && isEn(t - 1)) t--
            if (end - t >= 15 && t > i.from && t < b && i.to > a && held(t, end)) return Span(t * HOP_US + 20_000, (i.to + min(3, i.gapAfter / 2)) * HOP_US)
            var start = i.from
            while (start < i.to && start - i.from < 4 && !isEn(start)) start++
            var s = start
            while (s < i.to && isEn(s)) s++
            if (s - start >= 15 && s < i.to && i.from < b && s > a && held(start, s)) return Span((i.from - min(3, i.gapBefore / 2)).coerceAtLeast(0) * HOP_US, s * HOP_US - 20_000)
        }
        return null
    }

    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { var t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang).toFloat(); val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f; var ci = 0f
                for (k in 0 until len / 2) {
                    val p = i + k; val q = p + len / 2
                    val tr = re[q] * cr - im[q] * ci; val ti = re[q] * ci + im[q] * cr
                    re[q] = re[p] - tr; im[q] = im[p] - ti; re[p] += tr; im[p] += ti
                    val ncr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
