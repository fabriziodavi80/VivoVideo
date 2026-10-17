package it.fabriziodavi.vivovideo.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin

/** Volume percepito (LUFS) e ducking della musica. */
class AudioDspTest {
    private fun sine(amp: Float, seconds: Double, hz: Double = 1000.0): FloatArray {
        val n = (AudioDsp.RATE * seconds).toInt()
        return FloatArray(n * 2) { i -> (amp * sin(2 * PI * hz * (i / 2) / AudioDsp.RATE)).toFloat() }
    }

    @Test fun loudnessOfAKnownTone() {
        // Seno a 1 kHz, ampiezza 0,5 su due canali: circa -6 LUFS (la pesatura K a 1 kHz vale ~+0,7 dB).
        val m = AudioDsp.LoudnessMeter().apply { addInterleaved(sine(0.5f, 3.0)) }
        assertEquals(-6.0, m.lufs(), 0.8)
        assertEquals(0.5f, m.peak, 1e-3f)
        // 20 dB più basso → 20 LU in meno.
        val q = AudioDsp.LoudnessMeter().apply { addInterleaved(sine(0.05f, 3.0)) }
        assertEquals(m.lufs() - 20, q.lufs(), 0.2)
        assertEquals(-70.0, AudioDsp.LoudnessMeter().apply { addInterleaved(FloatArray(96_000)) }.lufs(), 0.0)
    }

    @Test fun normalizationHitsTheTargetWithoutClipping() {
        assertEquals(10.0, 20 * log10(AudioDsp.normalizeGain(-24.0, 0.1f).toDouble()), 0.01)  // +10 dB
        val g = AudioDsp.normalizeGain(-30.0, 0.8f)                                          // vorrebbe +16 dB…
        assertTrue("picco sotto -1 dBFS", 0.8f * g <= 0.892f)                                // …ma il picco lo limita
        assertEquals(1f, AudioDsp.normalizeGain(-70.0, 0f), 0f)                               // silenzio: niente
        assertTrue(AudioDsp.normalizeGain(-8.0, 0.9f) < 1f)                                   // troppo forte: abbassa
    }

    @Test fun musicDucksWhereSomeoneSpeaks() {
        // 1 s di silenzio, 1 s di voce, 2 s di silenzio (blocchi da 10 ms).
        val levels = FloatArray(400) { i -> if (i in 100 until 200) -20f else -90f }
        val g = AudioDsp.duckGains(levels, 12f)
        val low = Math.pow(10.0, -12.0 / 20).toFloat()
        assertEquals(1f, g[50], 1e-4f)                     // prima: piena
        assertEquals(low, g[150], 1e-3f)                    // durante: abbassata di 12 dB
        assertTrue("scende un po' prima della voce", g[99] < 1f)
        assertTrue("tiene dopo la voce", g[220] < 0.5f)
        assertEquals(1f, g[399], 1e-3f)                     // poi risale
        val lv = AudioDsp.blockLevels(sine(0.5f, 0.1))
        assertEquals(10, lv.size)
        assertEquals(-9.0, lv[3].toDouble(), 0.3)           // RMS di un seno da 0,5 = -9 dBFS
    }
}
