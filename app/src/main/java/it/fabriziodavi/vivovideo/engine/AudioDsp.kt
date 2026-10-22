package it.fabriziodavi.vivovideo.engine

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Calcoli audio del montaggio (Kotlin puro, provabili senza telefono):
 * misura del volume percepito (EBU R128 / ITU-R BS.1770) e "ducking" della musica quando si parla.
 * Audio stereo intrecciato a 48 kHz.
 */
object AudioDsp {
    const val RATE = 48_000
    /** Blocchi di 10 ms per il ducking. */
    const val BLOCK = RATE / 100

    /** Filtro biquad (forma diretta I) per un canale. */
    private class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        fun run(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            return y
        }
    }

    /**
     * Misuratore di volume percepito (LUFS integrati): filtro "K" (BS.1770, coefficienti per 48 kHz),
     * blocchi di 400 ms sovrapposti al 75%, soglia assoluta -70 LUFS e relativa -10 LU.
     */
    class LoudnessMeter {
        private val pre = Array(2) { Biquad(1.53512485958697, -2.69169618940638, 1.19839281085285, -1.69065929318241, 0.73248077421585) }
        private val rlb = Array(2) { Biquad(1.0, -2.0, 1.0, -1.99004745483398, 0.99007225036621) }
        private val step = RATE / 10           // 100 ms
        private val sub = ArrayList<Double>()   // energia di ogni tratto da 100 ms
        private var acc = 0.0
        private var n = 0
        var peak = 0f
            private set

        fun add(l: Float, r: Float) {
            peak = max(peak, max(kotlin.math.abs(l), kotlin.math.abs(r)))
            val kl = rlb[0].run(pre[0].run(l.toDouble()))
            val kr = rlb[1].run(pre[1].run(r.toDouble()))
            acc += kl * kl + kr * kr
            if (++n == step) { sub += acc / step; acc = 0.0; n = 0 }
        }

        fun addInterleaved(s: FloatArray) { var i = 0; while (i + 1 < s.size) { add(s[i], s[i + 1]); i += 2 } }

        /** Volume integrato in LUFS (-70 se è tutto silenzio). */
        fun lufs(): Double {
            // Blocchi da 400 ms = 4 tratti consecutivi (passo 100 ms).
            val blocks = (0..sub.size - 4).map { i -> (sub[i] + sub[i + 1] + sub[i + 2] + sub[i + 3]) / 4 }
            fun l(e: Double) = -0.691 + 10 * log10(e)
            val abs = blocks.filter { it > 0 && l(it) > -70 }
            if (abs.isEmpty()) return -70.0
            val rel = l(abs.average()) - 10
            val gated = abs.filter { l(it) > rel }
            return if (gated.isEmpty()) -70.0 else l(gated.average())
        }
    }

    /** Guadagno per portare il volume a [targetLufs] senza far superare ai picchi [ceilingDb] dBFS. */
    fun normalizeGain(lufs: Double, peak: Float, targetLufs: Double = -14.0, ceilingDb: Double = -1.0): Float {
        if (lufs <= -69.0 || peak <= 0f) return 1f
        val wanted = 10.0.pow((targetLufs - lufs) / 20)
        val maxByPeak = 10.0.pow(ceilingDb / 20) / peak
        return minOf(wanted, maxByPeak, 8.0).toFloat() // al massimo +18 dB
    }

    /** Livello (dBFS RMS) di ogni blocco da 10 ms di un audio stereo intrecciato. */
    fun blockLevels(s: FloatArray): FloatArray {
        val blocks = s.size / 2 / BLOCK
        return FloatArray(blocks) { b ->
            var e = 0.0
            val from = b * BLOCK * 2
            for (i in from until from + BLOCK * 2) e += s[i] * s[i]
            val rms = sqrt(e / (BLOCK * 2))
            if (rms <= 1e-9) -120f else (20 * log10(rms)).toFloat()
        }
    }

    /**
     * Guadagno della musica per blocco da 10 ms: scende di [depthDb] quando nel parlato c'è voce
     * (livello sopra [thresholdDb]), con discesa rapida (80 ms), tenuta (300 ms) e risalita lenta (500 ms).
     */
    fun duckGains(levels: FloatArray, depthDb: Float, thresholdDb: Float = -38f): FloatArray =
        duckGainsFrom(BooleanArray(levels.size) { levels[it] > thresholdDb }, depthDb)

    /** Come [duckGains], partendo dai blocchi in cui si parla. */
    fun duckGainsFrom(speech: BooleanArray, depthDb: Float, hold: Int = 30, release: Int = 50): FloatArray {
        val low = 10f.pow(-depthDb / 20f)
        val attack = 8
        var since = Int.MAX_VALUE / 2
        var g = 1f
        // Si guarda un po' avanti: la musica inizia a scendere prima della prima sillaba.
        val active = BooleanArray(speech.size) { i -> (i until minOf(speech.size, i + attack)).any { speech[it] } }
        return FloatArray(speech.size) { i ->
            if (active[i]) since = 0 else since++
            val target = if (since <= hold) low else 1f
            g = if (target < g) maxOf(target, g - (1f - low) / attack) else minOf(target, g + (1f - low) / release)
            g
        }
    }

    /**
     * Blocchi in cui si parla: livello sopra [minDb] e almeno [aboveFloorDb] sopra il "fondo"
     * (il 20% più basso dei blocchi non muti). Così un rumore d'ambiente costante non conta come voce.
     */
    fun speechMask(levels: FloatArray, minDb: Float, aboveFloorDb: Float): BooleanArray {
        val audible = levels.filter { it > -90f }.sorted()
        if (audible.isEmpty()) return BooleanArray(levels.size)
        val floor = audible[(audible.size * 0.2f).toInt().coerceAtMost(audible.size - 1)]
        val thr = maxOf(minDb, floor + aboveFloorDb)
        return BooleanArray(levels.size) { levels[it] > thr }
    }

    /**
     * Voce fuori campo e audio staccato: ci sono apposta, quindi conta tutto ciò che non è quasi silenzio
     * (fino a 30 dB sotto le parti forti, mai sotto -50 dB). Così la musica resta giù anche nelle brevi pause fra le frasi.
     */
    fun voiceMask(levels: FloatArray): BooleanArray {
        val audible = levels.filter { it > -90f }.sorted()
        if (audible.isEmpty()) return BooleanArray(levels.size)
        val loud = audible[(audible.size * 0.95f).toInt().coerceAtMost(audible.size - 1)]
        val thr = maxOf(-50f, loud - 30f)
        return BooleanArray(levels.size) { levels[it] > thr }
    }
    /** Audio delle clip: serve uno stacco netto sul rumore di fondo. */
    fun clipSpeechMask(levels: FloatArray) = speechMask(levels, -38f, 14f)

    /**
     * Somma l'energia di un audio stereo (con guadagno per fotogramma) nei blocchi da 10 ms del montaggio,
     * a partire dal fotogramma [startFrame].
     */
    fun addEnergy(energy: DoubleArray, audio: FloatArray, startFrame: Long, frames: Long, gain: (Long) -> Float) {
        for (f in 0 until frames) {
            val b = ((startFrame + f) / BLOCK).toInt()
            if (b < 0) continue
            if (b >= energy.size) break
            val g = gain(f)
            val l = audio[(f * 2).toInt()] * g; val r = audio[(f * 2 + 1).toInt()] * g
            energy[b] += (l * l + r * r).toDouble()
        }
    }

    /** Livelli (dBFS RMS) dall'energia per blocco. */
    fun levelsFromEnergy(energy: DoubleArray) = FloatArray(energy.size) { b ->
        val rms = sqrt(energy[b] / (BLOCK * 2))
        if (rms <= 1e-9) -120f else (20 * log10(rms)).toFloat()
    }

    /** Guadagno a [us] (tempo del montaggio) da una curva a blocchi da 10 ms, interpolata (niente scalini). */
    fun gainAt(gains: FloatArray, us: Long): Float {
        if (gains.isEmpty() || us < 0) return 1f
        val pos = us / 10_000.0
        val i = pos.toInt()
        if (i >= gains.size - 1) return if (i < gains.size) gains[i] else 1f
        val f = (pos - i).toFloat()
        return gains[i] + (gains[i + 1] - gains[i]) * f
    }

    /**
     * Voce registrata col microfono: la porta a un volume chiaro ([targetLufs], picchi sotto -1 dBFS,
     * al massimo +18 dB). Modifica [audio] e restituisce il guadagno usato.
     */
    fun levelVoice(audio: FloatArray, targetLufs: Double = -16.0): Float {
        val m = LoudnessMeter().apply { addInterleaved(audio) }
        val g = normalizeGain(m.lufs(), m.peak, targetLufs)
        if (g != 1f) for (i in audio.indices) audio[i] *= g
        return g
    }
}
