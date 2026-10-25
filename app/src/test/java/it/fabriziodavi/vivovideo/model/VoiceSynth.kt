package it.fabriziodavi.vivovideo.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.random.Random

/**
 * Voce sintetica per le prove (sintesi a formanti in parallelo, 16 kHz): vocali, nasali, fricative, occlusive,
 * con passaggi morbidi fra un suono e l'altro, tono che cala lungo la frase e piccole irregolarità come una voce vera.
 */
class VoiceSynth(seed: Int = 1, private val noise: Float = 0.0006f) {
    val rate = 16_000
    private val rnd = Random(seed)

    /** Formanti (frequenza, banda, guadagno). */
    private val table = mapOf(
        'a' to listOf(Triple(750.0, 90.0, 1.0), Triple(1250.0, 110.0, 0.6), Triple(2600.0, 160.0, 0.2)),
        'e' to listOf(Triple(450.0, 70.0, 1.0), Triple(1900.0, 100.0, 0.35), Triple(2600.0, 150.0, 0.2)),
        'i' to listOf(Triple(280.0, 60.0, 1.0), Triple(2300.0, 100.0, 0.3), Triple(3000.0, 180.0, 0.2)),
        'o' to listOf(Triple(500.0, 80.0, 1.0), Triple(900.0, 90.0, 0.6), Triple(2500.0, 160.0, 0.1)),
        'u' to listOf(Triple(320.0, 70.0, 1.0), Triple(800.0, 90.0, 0.3), Triple(2400.0, 160.0, 0.05)),
        'm' to listOf(Triple(250.0, 60.0, 1.0), Triple(1100.0, 200.0, 0.03), Triple(2300.0, 250.0, 0.02)),
        'n' to listOf(Triple(260.0, 60.0, 1.0), Triple(1600.0, 200.0, 0.04), Triple(2500.0, 250.0, 0.03)),
        'l' to listOf(Triple(360.0, 80.0, 0.8), Triple(1300.0, 150.0, 0.3), Triple(2700.0, 200.0, 0.1)),
        'r' to listOf(Triple(450.0, 120.0, 0.6), Triple(1400.0, 200.0, 0.3), Triple(2500.0, 200.0, 0.1)),
    )

    /** Un suono: [p] = vocale/nasale/liquida dalla tabella, 's'/'f' fricativa, 't'/'k'/'p' occlusiva, '_' silenzio. */
    data class Ph(val p: Char, val ms: Int)

    /** Sintetizza; [f0] tono di partenza (Hz), [fall] semitoni di calo lungo la frase, [amp] volume. */
    fun say(phones: List<Ph>, f0: Double = 120.0, fall: Double = 3.0, amp: Float = 0.3f): FloatArray {
        val total = phones.sumOf { it.ms } * rate / 1000
        val out = FloatArray(total)
        val voicedPh = phones.map { it.p in table }
        // Intonazione delle parole: ogni sillaba ha il suo tono (±2 semitoni) e le consonanti abbassano il volume;
        // i suoni lunghi (oltre 200 ms, come gli ehm) restano sul tono di base, senza accento.
        val accent = phones.map { if (it.p in "aeiou" && it.ms < 200) (rnd.nextDouble() - 0.5) * 4 else 0.0 }
        val gainOf = phones.mapIndexed { i, ph ->
            val inWord = ph.ms < 200 && phones.getOrNull(i - 1)?.p?.let { it != '_' } == true && phones.getOrNull(i + 1)?.p?.let { it != '_' } == true
            if (ph.p in "lrmn" && inWord) 0.3 else 1.0
        }
        var acc = 0.0; var gs = 1.0
        // Traiettoria dei formanti campione per campione (passaggi di 30 ms fra suoni vicini).
        val starts = IntArray(phones.size + 1).also { for (i in phones.indices) it[i + 1] = it[i] + phones[i].ms * rate / 1000 }
        val res = Array(3) { DoubleArray(2) }
        var phase = 0.0
        var g1 = 0.0; var g2 = 0.0
        var shimmer = 1.0
        for (i in phones.indices) {
            val ph = phones[i]
            for (k in starts[i] until starts[i + 1]) {
                val t = k.toDouble() / total
                var v = 0.0
                if (voicedPh[i]) {
                    val cur = table.getValue(ph.p)
                    // Verso il suono vicino negli ultimi/primi 30 ms.
                    val edge = 30 * rate / 1000
                    val nextT = phones.getOrNull(i + 1)?.p?.let { table[it] }
                    val prevT = phones.getOrNull(i - 1)?.p?.let { table[it] }
                    val fm = cur.mapIndexed { j, c ->
                        var f = c.first
                        if (nextT != null && starts[i + 1] - k < edge) f += (nextT[j].first - f) * (1 - (starts[i + 1] - k).toDouble() / edge) / 2
                        if (prevT != null && k - starts[i] < edge) f += (prevT[j].first - f) * (1 - (k - starts[i]).toDouble() / edge) / 2
                        Triple(f, c.second, c.third)
                    }
                    // Sorgente glottale: impulsi con un filo di jitter, filtrati (–12 dB/ottava).
                    acc += (accent[i] - acc) * 0.004; gs += (gainOf[i] - gs) * 0.01
                    val f0t = f0 * 2.0.pow((-fall * t + acc) / 12)
                    phase += f0t * (1 + (rnd.nextDouble() - 0.5) * 0.02) / rate
                    var pulse = 0.0
                    if (phase >= 1) { phase -= 1; pulse = 1.0; shimmer = 1 + (rnd.nextDouble() - 0.5) * 0.1 }
                    g1 += (pulse * shimmer - g1) * 0.25; g2 += (g1 - g2) * 0.25
                    val src = g2 + (rnd.nextDouble() - 0.5) * 0.002
                    for (j in 0 until 3) {
                        val (f, bw, gain) = fm[j]
                        val c = -exp(-2 * PI * bw / rate)
                        val b = 2 * exp(-PI * bw / rate) * cos(2 * PI * f / rate)
                        val a = 1 - b - c
                        val y = a * src + b * res[j][0] + c * res[j][1]
                        res[j][1] = res[j][0]; res[j][0] = y
                        v += gain * y
                    }
                    v *= 6 * gs
                } else when (ph.p) {
                    's' -> v = hiss(k, 5000.0, 2500.0) * 0.25
                    'f' -> v = hiss(k, 3000.0, 4000.0) * 0.08
                    't', 'k', 'p' -> v = if (starts[i + 1] - k < 15 * rate / 1000) hiss(k, 3500.0, 3000.0) * 0.3 else 0.0
                    else -> v = 0.0
                }
                out[k] = (v * amp).toFloat() + (rnd.nextFloat() - 0.5f) * noise
            }
        }
        return out
    }

    private var h1 = 0.0; private var h2 = 0.0
    private fun hiss(k: Int, f: Double, bw: Double): Double {
        val c = -exp(-2 * PI * bw / rate)
        val b = 2 * exp(-PI * bw / rate) * cos(2 * PI * f / rate)
        val y = (1 - b - c) * (rnd.nextDouble() - 0.5) * 4 + b * h1 + c * h2
        h2 = h1; h1 = y
        return y
    }

    companion object {
        /** "a-l-l-o-r-a" e simili: testo con un suono per lettera (vocali 90 ms, consonanti 60 ms). */
        fun word(text: String): List<Ph> = text.map { ch ->
            when (ch) {
                'a', 'e', 'i', 'o', 'u' -> Ph(ch, 95)
                's' -> Ph('s', 90)
                't', 'k', 'p' -> Ph(ch, 70)
                'f' -> Ph('f', 80)
                ' ' -> Ph('_', 30)
                else -> Ph(ch, 55)
            }
        }

        fun pause(ms: Int) = listOf(Ph('_', ms))
    }
}
