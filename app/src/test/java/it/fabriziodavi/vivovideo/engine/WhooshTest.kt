package it.fabriziodavi.vivovideo.engine

import it.fabriziodavi.vivovideo.model.JumpCuts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Il whoosh dei jump cut Social: culmina sul taglio, passa da sinistra a destra, non distorce. */
class WhooshTest {
    private val r = Whoosh.RATE

    @Test fun peaksOnTheCutAndSweepsLeftToRight() {
        val x = Whoosh.samples()
        val n = x.size / 2
        assertEquals((JumpCuts.WHOOSH_US * r / 1_000_000).toInt(), n)
        fun ch(c: Int, from: Int, to: Int) = Whoosh.rms(FloatArray(to - from) { x[(from + it) * 2 + c] }, 0, to - from)
        val peak = (JumpCuts.WHOOSH_PEAK_US * r / 1_000_000).toInt()
        val w = r / 20
        val atPeak = ch(0, peak - w, peak) + ch(1, peak - w, peak)
        val start = ch(0, 0, w) + ch(1, 0, w)
        val end = ch(0, n - w, n) + ch(1, n - w, n)
        // Il suono cresce fino al taglio e poi si spegne.
        assertTrue("culmine $atPeak, inizio $start", atPeak > 6 * start)
        assertTrue("culmine $atPeak, fine $end", atPeak > 6 * end)
        // All'inizio sta a sinistra, alla fine a destra.
        assertTrue(ch(0, n / 10, n / 4) > ch(1, n / 10, n / 4))
        assertTrue(ch(1, peak, peak + w * 2) > ch(0, peak, peak + w * 2))
        // Niente picchi oltre –6 dBFS e niente clic agli estremi.
        assertTrue(x.all { kotlin.math.abs(it) <= 0.5001f })
        assertEquals(0f, x[0], 1e-6f); assertEquals(0f, x[x.size - 1], 1e-6f)
    }

    @Test fun wavHeaderIsStereo48k16bit() {
        val x = Whoosh.samples()
        val b = Whoosh.wav(x)
        assertEquals(44 + x.size * 2, b.size)
        assertEquals("RIFF", String(b, 0, 4)); assertEquals("WAVE", String(b, 8, 4)); assertEquals("data", String(b, 36, 4))
        val bb = java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        assertEquals(2, bb.getShort(22).toInt())
        assertEquals(r, bb.getInt(24))
        assertEquals(16, bb.getShort(34).toInt())
    }
}
