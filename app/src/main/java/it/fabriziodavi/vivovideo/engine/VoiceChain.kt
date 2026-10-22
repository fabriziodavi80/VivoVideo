package it.fabriziodavi.vivovideo.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Catena "da podcast" per la voce registrata col microfono del telefono (Kotlin puro, mono a 48 kHz):
 * 1. passa-alto a 85 Hz (rimbombo, colpi sul telefono, vento leggero);
 * 2. equalizzazione: un po' meno "scatola" a 300 Hz, più presenza a 4 kHz e aria a 10 kHz (voce nitida);
 * 3. riduzione del fruscio fra le parole (espansore morbido sotto il rumore di fondo);
 * 4. livellatore lento (la voce non cambia volume se ci si allontana o si abbassa il tono);
 * 5. compressore veloce (sillabe forti e deboli più vicine);
 * 6. volume finale a -16 LUFS e limitatore a -1 dBFS (niente distorsione).
 */
object VoiceChain {
    const val RATE = 48_000
    private const val BLOCK = RATE / 100 // 10 ms

    /** Filtro biquad (formule RBJ), forma diretta I. */
    class Biquad private constructor(private val b0: Double, private val b1: Double, private val b2: Double, private val a1: Double, private val a2: Double) {
        private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0
        fun run(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            return y
        }

        companion object {
            private fun make(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
                Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)

            fun highPass(f: Double, q: Double = 0.707, rate: Int = RATE): Biquad {
                val w = 2 * PI * f / rate; val al = sin(w) / (2 * q); val c = cos(w)
                return make((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + al, -2 * c, 1 - al)
            }

            fun peak(f: Double, gainDb: Double, q: Double, rate: Int = RATE): Biquad {
                val a = 10.0.pow(gainDb / 40); val w = 2 * PI * f / rate; val al = sin(w) / (2 * q); val c = cos(w)
                return make(1 + al * a, -2 * c, 1 - al * a, 1 + al / a, -2 * c, 1 - al / a)
            }

            fun highShelf(f: Double, gainDb: Double, rate: Int = RATE): Biquad {
                val a = 10.0.pow(gainDb / 40); val w = 2 * PI * f / rate; val c = cos(w)
                val al = sin(w) / 2 * sqrt(2.0); val sa = 2 * sqrt(a) * al
                return make(
                    a * ((a + 1) + (a - 1) * c + sa), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - sa),
                    (a + 1) - (a - 1) * c + sa, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - sa,
                )
            }
        }
    }

    private fun db(x: Double) = if (x <= 1e-9) -120.0 else 20 * log10(x)
    private fun lin(d: Double) = 10.0.pow(d / 20)

    /** Livelli (dB RMS) a blocchi da 10 ms. */
    fun blockDb(s: FloatArray): DoubleArray = DoubleArray(s.size / BLOCK) { b ->
        var e = 0.0
        for (i in b * BLOCK until (b + 1) * BLOCK) e += s[i] * s[i]
        db(sqrt(e / BLOCK))
    }

    private fun percentile(v: List<Double>, p: Double): Double =
        if (v.isEmpty()) -120.0 else v.sorted()[(v.size * p).toInt().coerceIn(0, v.size - 1)]

    /** Applica tutta la catena. Restituisce un nuovo audio mono della stessa lunghezza. */
    fun process(input: FloatArray): FloatArray {
        if (input.size < BLOCK * 10) return input.copyOf()
        // 1–2. Filtri.
        val hp1 = Biquad.highPass(85.0); val hp2 = Biquad.highPass(85.0)
        val mud = Biquad.peak(300.0, -2.5, 1.0)
        val presence = Biquad.peak(4000.0, 3.0, 0.9)
        val air = Biquad.highShelf(10000.0, 1.5)
        val s = FloatArray(input.size) { i ->
            air.run(presence.run(mud.run(hp2.run(hp1.run(input[i].toDouble()))))).toFloat()
        }

        // Rumore di fondo e livello del parlato (blocchi da 10 ms).
        val lv = blockDb(s)
        val audible = lv.filter { it > -100 }
        val floor = percentile(audible, 0.1)
        val speech = percentile(audible, 0.85)
        val hasSpeech = speech - floor > 10

        // 3. Espansore: sotto (fondo + 8 dB) il volume scende di 1,5 dB per ogni dB, al massimo di 15 dB.
        // 4. Livellatore: a parlato attivo porta ogni tratto (finestra ~0,4 s) verso il livello tipico, ±8 dB.
        val gateAt = floor + 8
        val target = speech - 3
        val gainDb = DoubleArray(lv.size)
        if (hasSpeech) {
            // Livello medio su 0,4 s (40 blocchi) per il livellatore.
            val win = 20
            val energy = DoubleArray(lv.size) { lin(lv[it]).let { a -> a * a } }
            val pre = DoubleArray(lv.size + 1).also { for (i in lv.indices) it[i + 1] = it[i] + energy[i] }
            for (b in lv.indices) {
                val lo = maxOf(0, b - win); val hi = minOf(lv.size, b + win + 1)
                val avg = db(sqrt((pre[hi] - pre[lo]) / (hi - lo)))
                val expand = if (lv[b] < gateAt) -minOf(15.0, 1.5 * (gateAt - lv[b])) else 0.0
                val level = if (avg > gateAt + 4) (target - avg).coerceIn(-8.0, 8.0) else 0.0
                gainDb[b] = expand + level
            }
        }
        // Guadagno morbido (attacco 10 ms, rilascio 120 ms) interpolato campione per campione.
        val smooth = DoubleArray(lv.size)
        var g = 0.0
        val att = 1 - exp(-1.0 / 1.0); val rel = 1 - exp(-1.0 / 12.0)
        for (b in lv.indices) { val t = gainDb[b]; g += (t - g) * (if (t < g) att else rel); smooth[b] = g }
        for (i in s.indices) {
            val pos = i.toDouble() / BLOCK - 0.5
            val b0 = pos.toInt().coerceIn(0, smooth.size - 1); val b1 = (b0 + 1).coerceAtMost(smooth.size - 1)
            val f = (pos - b0).coerceIn(0.0, 1.0)
            s[i] = (s[i] * lin(smooth[b0] + (smooth[b1] - smooth[b0]) * f)).toFloat()
        }

        // 5. Compressore (rilevatore RMS ~10 ms): sopra la soglia rapporto 3:1, attacco 5 ms, rilascio 100 ms.
        val lv2 = blockDb(s).filter { it > -100 }
        val thr = percentile(lv2, 0.7) - 6
        var env = 0.0
        var cg = 0.0
        val envA = 1 - exp(-1.0 / (0.01 * RATE))
        val gA = 1 - exp(-1.0 / (0.005 * RATE)); val gR = 1 - exp(-1.0 / (0.1 * RATE))
        for (i in s.indices) {
            env += (s[i] * s[i] - env) * envA
            val d = db(sqrt(env))
            val want = if (d > thr) -(d - thr) * (1 - 1.0 / 3) else 0.0
            cg += (want - cg) * (if (want < cg) gA else gR)
            s[i] = (s[i] * lin(cg)).toFloat()
        }

        // 6. Volume a -16 LUFS (misurato come stereo con due canali uguali) e limitatore.
        val meter = AudioDsp.LoudnessMeter()
        for (v in s) meter.add(v, v)
        val lufs = meter.lufs()
        if (lufs > -69) {
            val k = minOf(lin(-16.0 - lufs), 16.0).toFloat() // al massimo +24 dB
            for (i in s.indices) s[i] *= k
        }
        limit(s, lin(-1.0).toFloat())
        return s
    }

    /** Limitatore con 5 ms di anticipo: i picchi restano sotto [ceiling] senza tagli secchi. */
    fun limit(s: FloatArray, ceiling: Float) {
        val look = RATE / 200
        // Guadagno necessario per ogni campione, poi il minimo sulla finestra in arrivo, poi rilascio morbido.
        val need = FloatArray(s.size) { i -> val a = abs(s[i]); if (a > ceiling) ceiling / a else 1f }
        val ahead = FloatArray(s.size)
        val dq = ArrayDeque<Int>()
        for (i in s.indices.reversed()) {
            while (dq.isNotEmpty() && need[dq.last()] >= need[i]) dq.removeLast()
            dq.addLast(i)
            while (dq.first() > i + look) dq.removeFirst()
            ahead[i] = need[dq.first()]
        }
        var g = 1f
        val rel = (1 - exp(-1.0 / (0.08 * RATE))).toFloat()
        for (i in s.indices) {
            val t = ahead[i]
            g = if (t < g) t else g + (t - g) * rel
            s[i] = (s[i] * g).coerceIn(-ceiling, ceiling)
        }
    }

    /** Mono → stereo intrecciato (per il WAV del progetto). */
    fun toStereo(m: FloatArray) = FloatArray(m.size * 2) { m[it / 2] }
}
