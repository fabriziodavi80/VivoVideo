package it.fabriziodavi.vivovideo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TutorialTest {
    @Test fun zoomBringsThePointToTheCentreWithoutEdges() {
        val z = Tutorial.zoomOn(0.3f, -0.3f, 2f)
        assertEquals(2f, z.scale, 0f)
        // Con 2× si può spostare al massimo di mezzo fotogramma (0,5): il bordo non si vede mai.
        assertTrue(abs(z.x) <= 0.5f && abs(z.y) <= 0.5f)
        assertEquals(-0.5f, z.x, 1e-4f); assertEquals(0.5f, z.y, 1e-4f)
        assertEquals(Transform(scale = 1.5f), Tutorial.zoomOn(0f, 0f, 1.5f))
        // Regola del movimento: lo zoom deve coprire lo spostamento.
        Tutorial.Spot.entries.forEach { s -> listOf(1.5f, 2f, 2.5f).forEach { k ->
            val t = Tutorial.zoomOn(s.x, s.y, k)
            assertTrue("$s $k", t.scale >= 1f + 2 * maxOf(abs(t.x), abs(t.y)) - 1e-4f)
        } }
    }

    @Test fun zoomKeyframesGoInHoldAndComeBack() {
        val k = Tutorial.zoomKeyframes(emptyList(), 2_000_000, 10_000_000, 0.3f, 0.3f, 2f, 2_500_000)
        assertEquals(listOf(2_000_000L, 2_450_000L, 4_950_000L, 5_400_000L), k.map { it.timeUs })
        assertEquals(Transform(), k.first().value); assertEquals(Transform(), k.last().value)
        assertEquals(2f, k[1].value.scale, 0f); assertEquals(k[1].value, k[2].value)
        // Vicino alla fine della clip: si accorcia la pausa, non si esce dalla clip.
        val end = Tutorial.zoomKeyframes(emptyList(), 9_500_000, 10_000_000, 0f, 0f, 2f, 4_000_000)
        assertTrue(end.all { it.timeUs in 0..10_000_000 })
        // I keyframe prima e dopo restano.
        val old = listOf(Keyframe(0, Transform(scale = 1.1f)), Keyframe(9_000_000, Transform(scale = 1.2f)))
        val mixed = Tutorial.zoomKeyframes(old, 3_000_000, 10_000_000, 0f, 0f, 2f, 1_000_000)
        assertTrue(mixed.first().timeUs == 0L && mixed.last().timeUs == 9_000_000L)
        assertEquals(6, mixed.size)
    }

    @Test fun stepsAreNumberedInOrder() {
        val clip = Clip(id = "a", uri = "content://v/a", sourceDurationUs = 10_000_000, trimEndUs = 10_000_000)
        var p = Project(name = "t", clips = listOf(clip))
        assertEquals(1, Tutorial.nextStep(p))
        val s1 = Tutorial.stepText(1, 0, 3_000_000)
        assertEquals(TextPreset.PASSO, TextPreset.matching(s1))
        assertTrue(s1.text.startsWith("1. "))
        p = p.copy(texts = listOf(s1, s1.copy(text = "2. Apri il menu")))
        p = p.copy(layers = listOf(Layer(uri = "file:///n.png", name = "Numero 3", width = 512, height = 512)))
        assertEquals(4, Tutorial.nextStep(p))
    }
}

class SnapToPlayheadTest {
    private val p = Project(name = "x", clips = listOf(Clip(id = "a", uri = "content://v/a", sourceDurationUs = 20_000_000, trimEndUs = 10_000_000)))

    @org.junit.Test fun startMovesKeepingLengthInsideTheVideo() {
        org.junit.Assert.assertEquals(4_000_000L to 3_000_000L, p.snapToPlayhead(1_000_000, 3_000_000, 4_000_000, true, 300_000))
        // Vicino alla fine: si accorcia per restare dentro.
        org.junit.Assert.assertEquals(9_000_000L to 1_000_000L, p.snapToPlayhead(1_000_000, 3_000_000, 9_000_000, true, 300_000))
    }

    @org.junit.Test fun endCutsAtThePlayheadButNotBeforeTheMinimum() {
        org.junit.Assert.assertEquals(1_000_000L to 5_000_000L, p.snapToPlayhead(1_000_000, 3_000_000, 6_000_000, false, 300_000))
        org.junit.Assert.assertEquals(1_000_000L to 300_000L, p.snapToPlayhead(1_000_000, 3_000_000, 500_000, false, 300_000))
    }
}
