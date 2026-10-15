package it.fabriziodavi.vivovideo.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class BeatDetectorTest {
    /** Ritmo sintetico: cassa (con attacco) ogni battito + charleston più debole in levare + rumore. */
    private fun synth(bpm: Double, seconds: Double, offsetS: Double): FloatArray {
        val sr = 48_000
        val n = (seconds * sr).toInt()
        val x = FloatArray(n)
        val rnd = Random(1)
        val beat = 60.0 / bpm
        var t = offsetS
        while (t < seconds) {
            val s0 = (t * sr).toInt()
            for (i in 0 until sr / 8) {
                val k = s0 + i; if (k >= n) break
                val tt = i.toDouble() / sr
                x[k] += (0.8 * sin(2 * Math.PI * 60 * tt) * exp(-tt * 30)).toFloat()
                // Attacco della cassa (il "click" del battente), come in una cassa vera.
                if (i < sr / 200) x[k] += (rnd.nextFloat() - 0.5f) * 0.8f * (1f - i / (sr / 200f))
            }
            val h = ((t + beat / 2) * sr).toInt()
            for (i in 0 until sr / 40) {
                val k = h + i; if (k >= n) break
                x[k] += ((rnd.nextFloat() - 0.5f) * 0.12f * exp(-i / 300.0)).toFloat()
            }
            t += beat
        }
        for (i in 0 until n) x[i] += (rnd.nextFloat() - 0.5f) * 0.02f
        return x
    }

    @Test fun findsTempoAndBeats() {
        val bpm = 128.0
        val offset = 0.37
        val r = BeatDetector.detect(synth(bpm, 20.0, offset), 48_000)
        assertEquals(bpm, r.bpm.toDouble(), 2.0)
        assertTrue("pochi battiti: ${r.beatsUs.size}", r.beatsUs.size >= 38)
        val periodUs = 60_000_000.0 / bpm
        // Ogni battito trovato cade su un colpo di cassa (entro 40 ms).
        for (b in r.beatsUs) {
            val k = Math.round((b / 1e6 - offset) * 1e6 / periodUs)
            val expected = offset * 1e6 + k * periodUs
            assertTrue("battito a $b, atteso $expected", abs(b - expected) < 40_000)
        }
    }
}
