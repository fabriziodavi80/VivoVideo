package it.fabriziodavi.vivovideo.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.DataOutputStream
import java.io.File

/** Maschere dello scontorno lette dal file senza copiarle in memoria (clip lunghe con lo scontorno "Preciso"). */
class MaskFileTest {
    @Test fun slicesComeStraightFromTheFile() {
        val side = 384; val n = 300 // come lo scontorno preciso di 20 s: 44 MB
        val f = File.createTempFile("maschere", ".bin")
        DataOutputStream(f.outputStream().buffered()).use { o ->
            o.writeInt(n); o.writeInt(side)
            for (k in 0 until n) { val v = (k % 256).toByte(); o.write(ByteArray(side * side) { v }) }
        }
        val m = PersonMasks.load(f.absolutePath)!!
        assertEquals(n, m.count); assertEquals(side, m.side)
        listOf(0, 1, 137, n - 1).forEach { k ->
            val s = m.slice(k)
            assertEquals(side * side, s.remaining())
            assertEquals((k % 256).toByte(), s.get(0))
            assertEquals((k % 256).toByte(), s.get(side * side - 1))
        }
        // Oltre la fine: l'ultima (come prima).
        assertEquals(((n - 1) % 256).toByte(), m.slice(n + 50).get(0))
        f.delete()
    }

    @Test fun damagedFileIsReportedNotFatal() {
        val f = File.createTempFile("rotto", ".bin")
        DataOutputStream(f.outputStream()).use { o -> o.writeInt(100); o.writeInt(384); o.write(ByteArray(1000)) }
        assertNull(PersonMasks.load(f.absolutePath))
        assertNull(PersonMasks.load("/non/esiste.bin"))
        f.delete()
    }
}
