package it.fabriziodavi.vivovideo.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Rilevamento dei battiti di un brano (stesso metodo dei programmi musicali):
 * 1. inviluppo degli attacchi (flusso spettrale, 100 valori al secondo);
 * 2. tempo (BPM) dall'autocorrelazione dell'inviluppo, con preferenza attorno a 120;
 * 3. posizione dei battiti con programmazione dinamica (Ellis 2007): battiti regolari che cadono sugli attacchi.
 */
object BeatDetector {
    data class Result(val bpm: Float, val beatsUs: List<Long>)

    private const val RATE = 12_000      // dopo il sottocampionamento
    private const val HOP = 120          // 10 ms → 100 frame al secondo
    private const val FPS = RATE / HOP
    private const val NFFT = 512

    /** @param mono campioni mono; @param sampleRate loro frequenza (es. 48000). */
    fun detect(mono: FloatArray, sampleRate: Int): Result {
        val x = downsample(mono, sampleRate)
        val env = onsetEnvelope(x)
        if (env.size < FPS * 2) return Result(0f, emptyList())
        val period = estimatePeriod(env)
        val beats = track(env, period)
        val bpm = 60f * FPS / period
        return Result(bpm, beats.map { it * 1_000_000L / FPS })
    }

    /**
     * Energia del brano a ogni battito (0 = parte più calma, 1 = la più carica): volume medio fra un battito e
     * il successivo, riportato fra il 10° e il 90° percentile e levigato su tre battiti. Serve a capire intro e "drop".
     */
    fun energy(mono: FloatArray, sampleRate: Int, beatsUs: List<Long>): List<Float> {
        if (beatsUs.size < 2) return emptyList()
        val db = beatsUs.indices.map { i ->
            val a = (beatsUs[i] * sampleRate / 1_000_000).toInt().coerceIn(0, mono.size)
            val b = ((beatsUs.getOrNull(i + 1) ?: (beatsUs[i] + (beatsUs[i] - beatsUs[i - 1]))) * sampleRate / 1_000_000).toInt().coerceIn(a, mono.size)
            var e = 0.0
            var k = a
            while (k < b) { e += mono[k] * mono[k]; k += 4 }
            val n = ((b - a) / 4).coerceAtLeast(1)
            (10 * kotlin.math.log10(e / n + 1e-10)).toFloat()
        }
        val sorted = db.sorted()
        val lo = sorted[(sorted.size * 0.1).toInt()]
        val hi = sorted[(sorted.size * 0.9).toInt().coerceAtMost(sorted.size - 1)]
        val norm = db.map { ((it - lo) / (hi - lo).coerceAtLeast(1f)).coerceIn(0f, 1f) }
        return norm.indices.map { i -> (norm[(i - 1).coerceAtLeast(0)] + 2 * norm[i] + norm[(i + 1).coerceAtMost(norm.lastIndex)]) / 4f }
    }

    /** Stereo interleaved → mono. */
    fun toMono(stereo: FloatArray): FloatArray = FloatArray(stereo.size / 2) { (stereo[2 * it] + stereo[2 * it + 1]) * 0.5f }

    private fun downsample(x: FloatArray, rate: Int): FloatArray {
        val f = max(1, rate / RATE)
        val n = x.size / f
        return FloatArray(n) { i ->
            var s = 0f
            for (k in 0 until f) s += x[i * f + k]
            s / f
        }
    }

    private fun onsetEnvelope(input: FloatArray): FloatArray {
        // Finestre centrate: il frame f descrive l'istante f * HOP.
        val x = FloatArray(input.size + NFFT).also { input.copyInto(it, NFFT / 2) }
        val frames = (x.size - NFFT) / HOP + 1
        if (frames <= 1) return FloatArray(0)
        val window = FloatArray(NFFT) { (0.5 - 0.5 * cos(2 * PI * it / NFFT)).toFloat() }
        val re = FloatArray(NFFT)
        val im = FloatArray(NFFT)
        val bins = NFFT / 2
        var prev = FloatArray(bins)
        val env = FloatArray(frames)
        for (f in 0 until frames) {
            val o = f * HOP
            for (i in 0 until NFFT) { re[i] = x[o + i] * window[i]; im[i] = 0f }
            fft(re, im)
            val cur = FloatArray(bins)
            var flux = 0f
            // Da circa 45 Hz a 6 kHz: grancassa, rullante, piatti.
            for (b in 2 until bins) {
                val m = ln(1f + 100f * sqrt(re[b] * re[b] + im[b] * im[b]))
                cur[b] = m
                val d = m - prev[b]
                if (d > 0) flux += d
            }
            // I primi frame vedono l'inizio del file (dal nulla al suono): non è un attacco musicale.
            env[f] = if (f <= NFFT / HOP) 0f else flux
            prev = cur
        }
        // Toglie l'andamento lento (media mobile di ~0,5 s) e normalizza.
        val w = FPS / 2
        val out = FloatArray(frames)
        var sum = 0.0
        val pre = DoubleArray(frames + 1)
        for (i in 0 until frames) { sum += env[i]; pre[i + 1] = sum }
        for (i in 0 until frames) {
            val a = max(0, i - w); val b = minOf(frames, i + w + 1)
            val mean = (pre[b] - pre[a]) / (b - a)
            out[i] = max(0f, (env[i] - mean).toFloat())
        }
        val sd = sqrt(out.fold(0.0) { s, v -> s + v * v } / frames).toFloat().coerceAtLeast(1e-6f)
        for (i in out.indices) out[i] /= sd
        return out
    }

    /** Periodo in frame (decimale) fra 60 e 200 BPM, con preferenza logaritmica per 120. */
    private fun estimatePeriod(env: FloatArray): Float {
        val minLag = 60 * FPS / 200
        val maxLag = 60 * FPS / 60
        val n = env.size
        val ac = FloatArray(maxLag + 2)
        for (lag in minLag - 1..maxLag + 1) {
            var s = 0.0
            for (i in lag until n) s += env[i] * env[i - lag]
            ac[lag] = (s / (n - lag)).toFloat()
        }
        var best = minLag
        var bestScore = Float.NEGATIVE_INFINITY
        for (lag in minLag..maxLag) {
            val bpm = 60.0 * FPS / lag
            val prior = exp(-0.5 * (ln(bpm / 120.0) / ln(2.0)).let { it * it } / (0.9 * 0.9)).toFloat()
            val score = ac[lag] * prior
            if (score > bestScore) { bestScore = score; best = lag }
        }
        // Raffinamento parabolico sul picco.
        val y0 = ac[best - 1]; val y1 = ac[best]; val y2 = ac[best + 1]
        val den = y0 - 2 * y1 + y2
        val delta = if (den != 0f) (0.5f * (y0 - y2) / den).coerceIn(-0.5f, 0.5f) else 0f
        return best + delta
    }

    private fun track(env: FloatArray, period: Float): List<Long> {
        val n = env.size
        // Inviluppo levigato con una gaussiana larga 1/32 di battito.
        val sigma = max(1f, period / 32f)
        val half = (sigma * 3).toInt()
        val kernel = FloatArray(2 * half + 1) { exp(-0.5f * ((it - half) / sigma).let { v -> v * v }) }
        val local = FloatArray(n)
        for (i in 0 until n) {
            var s = 0f
            for (k in -half..half) { val j = i + k; if (j in 0 until n) s += env[j] * kernel[k + half] }
            local[i] = s
        }
        val score = FloatArray(n)
        val back = IntArray(n) { -1 }
        val tight = 100f
        val lo = (period / 2).toInt().coerceAtLeast(1)
        val hi = (period * 2).toInt()
        for (t in 0 until n) {
            var bestV = 0f
            var bestP = -1
            for (p in t - hi..t - lo) {
                if (p < 0) continue
                val r = ln((t - p) / period)
                val v = score[p] - tight * r * r
                if (bestP < 0 || v > bestV) { bestV = v; bestP = p }
            }
            score[t] = local[t] + if (bestP >= 0) max(0f, bestV) else 0f
            back[t] = if (bestP >= 0 && bestV > 0) bestP else -1
        }
        // Ultimo battito: il punteggio più alto nell'ultimo periodo.
        var end = n - 1
        for (t in maxOf(0, n - period.toInt() - 1) until n) if (score[t] > score[end]) end = t
        val beats = ArrayList<Long>()
        var t = end
        while (t >= 0) { beats += t.toLong(); t = back[t] }
        beats.reverse()
        // Scarta i battiti iniziali e finali senza musica (silenzio prima/dopo il brano).
        if (beats.isEmpty()) return beats
        val strong = beats.map { local[it.toInt()] }.average().toFloat() * 0.25f
        val first = beats.indexOfFirst { local[it.toInt()] > strong }.coerceAtLeast(0)
        val last = beats.indexOfLast { local[it.toInt()] > strong }.let { if (it < 0) beats.lastIndex else it }
        return if (first <= last) beats.subList(first, last + 1) else beats
    }

    /** FFT radix-2 sul posto. */
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang).toFloat(); val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f; var ci = 0f
                for (k in 0 until len / 2) {
                    val a = i + k; val b = a + len / 2
                    val tr = re[b] * cr - im[b] * ci
                    val ti = re[b] * ci + im[b] * cr
                    re[b] = re[a] - tr; im[b] = im[a] - ti
                    re[a] += tr; im[a] += ti
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr; cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
