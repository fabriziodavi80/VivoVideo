package it.fabriziodavi.vivovideo.model

import it.fabriziodavi.vivovideo.engine.RedactEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactTest {
    @Test fun recognisesPlates() {
        listOf("AB123CD", "AB 123 CD", "ab-123-cd", "1ABC234", "M AB 1234", "ZH 12345").forEach { assertTrue(it, Redact.looksLikePlate(it)) }
        listOf("CIAO", "2026", "Via Roma", "AB12", "STOP", "ABCDEFGH", "123456789").forEach { assertFalse(it, Redact.looksLikePlate(it)) }
        // Forma: larga e bassa sì (16:9), quadrata no.
        assertTrue(Redact.plateShape(RBox(0.4f, 0.6f, 0.12f, 0.04f), 16f / 9f))
        assertFalse(Redact.plateShape(RBox(0.4f, 0.6f, 0.1f, 0.18f), 16f / 9f))
    }

    @Test fun boxesGrowInsideTheFrame() {
        val g = RBox(0.9f, 0.9f, 0.1f, 0.1f, round = true).grow(0.5f)
        assertTrue(g.x >= 0f && g.y >= 0f && g.x + g.w <= 1.0001f && g.y + g.h <= 1.0001f)
        assertTrue(g.round)
        assertEquals(1f, RBox(0f, 0f, 0.5f, 0.5f).iou(RBox(0f, 0f, 0.5f, 0.5f)), 1e-4f)
        assertEquals(0f, RBox(0f, 0f, 0.2f, 0.2f).iou(RBox(0.5f, 0.5f, 0.2f, 0.2f)), 0f)
    }

    @Test fun followsTheChosenObjectAndBridgesShortGaps() {
        val car = { x: Float -> RBox(x, 0.5f, 0.2f, 0.2f) }
        val person = RBox(0.1f, 0.1f, 0.1f, 0.3f)
        // L'auto (id 7) va verso destra; per 2 fotogrammi non viene trovata; poi sparisce del tutto.
        val frames = listOf(
            mapOf(7 to car(0.1f), 3 to person), mapOf(7 to car(0.2f), 3 to person), mapOf(3 to person), mapOf(3 to person),
            mapOf(7 to car(0.5f), 3 to person),
        ) + List(8) { mapOf(3 to person) }
        val path = Redact.followObject(frames, 1, RBox(0.22f, 0.52f, 0.1f, 0.1f))
        assertEquals(car(0.1f), path[0]); assertEquals(car(0.2f), path[1])
        assertEquals(car(0.2f), path[2]); assertEquals(car(0.2f), path[3]) // buco breve: resta l'ultimo
        assertEquals(car(0.5f), path[4])
        assertNull(path.last()) // sparito per più di mezzo secondo: niente riquadro
        // Nessun oggetto dove si è toccato: il riquadro scelto resta fermo.
        val still = Redact.followObject(frames, 0, RBox(0.7f, 0.1f, 0.1f, 0.1f))
        assertTrue(still.all { it == RBox(0.7f, 0.1f, 0.1f, 0.1f) })
    }

    @Test fun shaderGetsTheCurrentAndNextSample() {
        val a = RBox(0f, 0f, 0.1f, 0.1f); val b = RBox(0.5f, 0.5f, 0.1f, 0.1f)
        val frames = listOf(listOf(a), listOf(b), emptyList())
        val r = Redaction(fromUs = 1_000_000, rateHz = 10, count = 3)
        assertEquals(listOf(a, b), RedactEffect.boxesAt(frames, r, 1_000_000))
        assertEquals(listOf(b), RedactEffect.boxesAt(frames, r, 1_150_000))
        assertTrue(RedactEffect.boxesAt(frames, r, 9_000_000).isEmpty())
        assertTrue(RedactEffect.boxesAt(emptyList(), r, 0).isEmpty())
    }
}
