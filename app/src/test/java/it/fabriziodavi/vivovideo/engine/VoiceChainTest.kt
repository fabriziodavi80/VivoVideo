package it.fabriziodavi.vivovideo.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Voce registrata: nitida, volume uniforme, senza rimbombo né fruscio fra le parole, senza distorsione. */
class VoiceChainTest {
    private val r = VoiceChain.RATE

    /** "Sillabe": toni con armoniche accesi e spenti 4 volte al secondo. */
    private fun syllables(amp: Float, seconds: Double, seed: Int): FloatArray {
        val rnd = Random(seed)
        val n = (r * seconds).toInt()
        return FloatArray(n) { i ->
            val t = i.toDouble() / r
            val env = (sin(PI * (t * 4 % 1.0))).let { it * it }
            val v = sin(2 * PI * 180 * t) + 0.5 * sin(2 * PI * 360 * t) + 0.3 * sin(2 * PI * 2500 * t)
            (amp * env * v / 1.8 + (rnd.nextFloat() - 0.5f) * 0.004f).toFloat()
        }
    }

    private fun noise(seconds: Double, seed: Int): FloatArray {
        val rnd = Random(seed)
        return FloatArray((r * seconds).toInt()) { (rnd.nextFloat() - 0.5f) * 0.004f }
    }

    private fun rmsDb(s: FloatArray, from: Double, to: Double): Double {
        val a = (from * r).toInt(); val b = (to * r).toInt()
        var e = 0.0
        for (i in a until b) e += s[i] * s[i]
        return 20 * log10(sqrt(e / (b - a)) + 1e-12)
    }

    @Test fun loudAndQuietPhrasesBecomeEvenAndNoiseDrops() {
        // 1 s di fruscio, 3 s di voce vicina, 1 s di fruscio, 3 s di voce lontana (16 dB più bassa), 1 s di fruscio.
        val s = noise(1.0, 1) + syllables(0.4f, 3.0, 2) + noise(1.0, 3) + syllables(0.063f, 3.0, 4) + noise(1.0, 5)
        val before = rmsDb(s, 1.2, 3.8) - rmsDb(s, 5.2, 7.8)
        val out = VoiceChain.process(s)
        assertEquals(s.size, out.size)
        val after = rmsDb(out, 1.2, 3.8) - rmsDb(out, 5.2, 7.8)
        assertTrue("differenza fra le frasi: prima $before dB, dopo $after dB", before > 14 && abs(after) < 7)
        // Il fruscio fra le frasi scende rispetto alla voce.
        val gapBefore = rmsDb(s, 5.2, 7.8) - rmsDb(s, 4.2, 4.8)
        val gapAfter = rmsDb(out, 5.2, 7.8) - rmsDb(out, 4.2, 4.8)
        assertTrue("fruscio: prima $gapBefore dB sotto la voce, dopo $gapAfter", gapAfter > gapBefore + 6)
        // Volume finale chiaro e niente distorsione.
        val m = AudioDsp.LoudnessMeter().apply { out.forEach { add(it, it) } }
        assertEquals(-16.0, m.lufs(), 2.0)
        assertTrue("picco ${m.peak}", m.peak <= 0.9f)
    }

    private fun goertzel(s: FloatArray, f: Double): Double {
        val k = 2 * cos(2 * PI * f / r)
        var a = 0.0; var b = 0.0
        for (v in s) { val c = v + k * a - b; b = a; a = c }
        return sqrt(a * a + b * b - k * a * b) / s.size
    }

    @Test fun rumbleIsRemovedAndVoiceKept() {
        val n = r * 3
        val s = FloatArray(n) { i -> val t = i.toDouble() / r; (0.2 * sin(2 * PI * 40 * t) + 0.2 * sin(2 * PI * 1000 * t)).toFloat() }
        val out = VoiceChain.process(s)
        val before = goertzel(s, 40.0) / goertzel(s, 1000.0)
        val after = goertzel(out, 40.0) / goertzel(out, 1000.0)
        assertTrue("rimbombo: rapporto $before → $after", after < before / 8)
    }

    @Test fun limiterKeepsPeaksUnderTheCeiling() {
        val s = FloatArray(r) { i -> if (i in 10_000 until 10_200) 1.5f else 0.2f * sin(2 * PI * 300 * i / r).toFloat() }
        VoiceChain.limit(s, 0.89f)
        assertTrue(s.all { abs(it) <= 0.89f })
        // Lontano dal picco il suono resta com'era.
        assertEquals(0.2 * sin(2 * PI * 300 * 30_000 / r), s[30_000].toDouble(), 0.01)
        assertEquals(4, VoiceChain.toStereo(floatArrayOf(0.1f, 0.2f)).size)
    }
}
