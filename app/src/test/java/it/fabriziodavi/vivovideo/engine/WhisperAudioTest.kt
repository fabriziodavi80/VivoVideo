package it.fabriziodavi.vivovideo.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/** Audio per il riconoscimento vocale: portato a un volume chiaro, senza distorcere. */
class WhisperAudioTest {
    private fun tone(amp: Float, n: Int = 16_000) = FloatArray(n) { amp * sin(2 * Math.PI * 200 * it / 16_000).toFloat() }
    private fun rms(x: FloatArray) = sqrt(x.sumOf { (it * it).toDouble() } / x.size).toFloat()

    @Test fun quietPhoneSpeechIsRaised() {
        // Voce bassa (–40 dBFS di valore efficace): arriva a –20 dBFS.
        val x = tone(0.0141f)
        val y = Whisper.normalized(x)
        assertEquals(0.1f, rms(y), 0.005f)
        assertTrue(y.all { abs(it) <= 0.945f })
        // L'originale non cambia (serve ancora per pause ed ehm).
        assertEquals(0.0141f, x.maxOf { abs(it) }, 1e-3f)
    }

    @Test fun peaksAndSilenceAreSafe() {
        // Un colpo forte in mezzo al parlato basso: il guadagno si ferma prima di far distorcere il picco.
        val x = tone(0.01f).also { it[100] = 0.5f }
        assertTrue(Whisper.normalized(x).all { abs(it) <= 0.945f })
        // Al massimo ×20 (il silenzio non diventa rumore assordante), e un audio già giusto resta com'è.
        val silent = tone(0.0001f)
        assertEquals(0.002f, Whisper.normalized(silent).maxOf { abs(it) }, 2e-4f)
        val ok = tone(0.141f)
        assertSame(ok, Whisper.normalized(ok))
        assertEquals(0, Whisper.normalized(FloatArray(0)).size)
    }
}
