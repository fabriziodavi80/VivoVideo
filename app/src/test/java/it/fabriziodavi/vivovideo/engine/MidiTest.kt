package it.fabriziodavi.vivovideo.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayOutputStream

class MidiTest {
    private fun vlq(v: Int): ByteArray {
        var x = v; val out = mutableListOf(x and 0x7F); x = x shr 7
        while (x > 0) { out.add(0, (x and 0x7F) or 0x80); x = x shr 7 }
        return out.map { it.toByte() }.toByteArray()
    }

    /** 8 semiminime a 120 BPM, poi cambio di tempo a 60 BPM per altre 4. */
    private fun file(): ByteArray {
        val div = 480
        val trk = ByteArrayOutputStream()
        fun ev(delta: Int, vararg b: Int) { trk.write(vlq(delta)); b.forEach { trk.write(it) } }
        ev(0, 0xFF, 0x58, 4, 4, 2, 24, 8)              // 4/4
        ev(0, 0xFF, 0x51, 3, 0x07, 0xA1, 0x20)        // 500000 µs = 120 BPM
        ev(0, 0x90, 60, 100)
        ev(div * 8, 0x80, 60, 0)                      // running status non usato
        ev(0, 0xFF, 0x51, 3, 0x0F, 0x42, 0x40)        // 1000000 µs = 60 BPM
        ev(div * 4, 0x90, 62, 100)
        ev(0, 62, 0)                                  // running status
        ev(0, 0xFF, 0x2F, 0)
        val t = trk.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("MThd".toByteArray()); out.write(byteArrayOf(0, 0, 0, 6, 0, 0, 0, 1, (div shr 8).toByte(), (div and 0xFF).toByte()))
        out.write("MTrk".toByteArray())
        out.write(byteArrayOf((t.size shr 24).toByte(), (t.size shr 16).toByte(), (t.size shr 8).toByte(), t.size.toByte()))
        out.write(t)
        return out.toByteArray()
    }

    @Test fun readsTempoMap() {
        val info = Midi.parse(file())
        assertNotNull(info)
        info!!
        assertEquals(120f, info.bpm, 0.01f)
        assertEquals(4, info.beatsPerBar)
        assertEquals(13, info.beatsUs.size)
        assertEquals(3_500_000L, info.beatsUs[7])
        assertEquals(4_000_000L, info.beatsUs[8])
        assertEquals(5_000_000L, info.beatsUs[9])
        assertEquals(8_000_000L, info.durationUs)
    }
}
