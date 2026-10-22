package it.fabriziodavi.vivovideo.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Scontorno: maschera a piena risoluzione del modello, stabile nel tempo, bordo affinato sull'immagine. */
class PersonMaskTest {
    @Test fun resampleKeepsShapeAndRange() {
        // Metà sinistra persona (1), metà destra sfondo (0), 4×2 → 8×8.
        val src = floatArrayOf(1f, 1f, 0f, 0f, 1f, 1f, 0f, 0f)
        val out = PersonMasks.resample(src, 4, 2, 8)
        assertEquals(64, out.size)
        assertEquals(1f, out[0], 1e-4f)
        assertEquals(0f, out[7], 1e-4f)
        assertTrue(out.all { it in 0f..1f })
        // Al passaggio il valore è intermedio (bordo morbido, non a scalino).
        assertTrue(out[3] in 0.2f..0.9f || out[4] in 0.1f..0.8f)
        // Stessa misura: invariata.
        val same = PersonMasks.resample(floatArrayOf(0.2f, 0.4f, 0.6f, 0.8f), 2, 2, 2)
        assertEquals(listOf(0.2f, 0.4f, 0.6f, 0.8f), same.map { (it * 100).toInt() / 100f })
    }

    @Test fun smoothingDampsFlicker() {
        val prev = FloatArray(4) { 1f }
        val cur = FloatArray(4) { 0f }
        val s = PersonMasks.smooth(cur, prev)
        assertEquals(PersonMasks.SMOOTH, s[0], 1e-5f)
        // Il primo fotogramma resta com'è.
        assertTrue(PersonMasks.smooth(cur, null).contentEquals(cur))
        assertTrue(abs(PersonMasks.SMOOTH - 0.3f) < 0.21f) // abbastanza per lo sfarfallio, poco ritardo
    }

    @Test fun shaderRefinesTheEdgeWithTheImageAndStaysHdrSafe() {
        val f = PERSON_FRAGMENT
        assertTrue(f.contains("Filtro guidato"))
        // Le maschere si leggono con "texture2D (" (con lo spazio), i colori con "texture2D(".
        assertTrue(Regex("texture2D \\(uMask").findAll(f).count() >= 4)
        assertTrue(!f.contains("texture2D(uMask"))
        assertTrue(Regex("\\buTexel\\b").findAll(f).count() >= 2)
        assertEquals(256, PersonMasks.SIDE)
    }
}
