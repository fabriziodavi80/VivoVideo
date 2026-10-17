package it.fabriziodavi.vivovideo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Correzione del colore per clip: il calcolo (identico a quello dello shader) e il salvataggio. */
class GradeTest {
    private fun f(r: Float, g: Float, b: Float) = floatArrayOf(r, g, b)
    private fun near(a: FloatArray, b: FloatArray, tol: Float = 1e-4f) = a.indices.all { abs(a[it] - b[it]) < tol }

    @Test fun noCorrectionChangesNothing() {
        assertTrue(Grade().isIdentity)
        listOf(f(0f, 0f, 0f), f(1f, 1f, 1f), f(0.2f, 0.5f, 0.8f), f(0.9f, 0.1f, 0.3f)).forEach { c ->
            assertTrue(c.joinToString(), near(GradeMath.apply(c, Grade()), c))
        }
        assertFalse(Grade(exposure = 0.1f).isIdentity)
        assertFalse(Grade(midWheel = Wheel(120f, 0.3f)).isIdentity)
        assertTrue(Grade(midWheel = Wheel(120f, 0f)).isIdentity) // forza zero: niente
        assertFalse(Grade().withBand(HslBand.BLUE, Hsl(sat = 0.5f)).isIdentity)
    }

    @Test fun basicControlsGoTheRightWay() {
        val grey = f(0.2f, 0.2f, 0.2f)
        assertEquals(0.4f, GradeMath.apply(grey, Grade(exposure = 1f))[0], 1e-3f)          // +1 stop = doppio
        val warm = GradeMath.apply(grey, Grade(temperature = 1f))
        assertTrue(warm[0] > grey[0] && warm[2] < grey[2])
        val bw = GradeMath.apply(f(0.8f, 0.2f, 0.1f), Grade(saturation = -1f))
        assertTrue(abs(bw[0] - bw[1]) < 1e-3f && abs(bw[1] - bw[2]) < 1e-3f)              // bianco e nero
        val dark = GradeMath.apply(f(0.1f, 0.1f, 0.1f), Grade(shadows = 1f))
        assertTrue(dark[0] > 0.15f)                                                          // ombre aperte
        val bright = GradeMath.apply(f(0.9f, 0.9f, 0.9f), Grade(highlights = -1f))
        assertTrue(bright[0] < 0.75f)                                                        // luci recuperate
        val lowC = GradeMath.apply(f(0.9f, 0.9f, 0.9f), Grade(contrast = -0.5f))
        assertTrue(lowC[0] < 0.9f)
    }

    @Test fun wheelsTintTheirZoneOnly() {
        val shadowsTeal = Grade(shadowWheel = Wheel(190f, 1f))
        val d = GradeMath.apply(f(0.1f, 0.1f, 0.1f), shadowsTeal)
        assertTrue("ombre verso il ciano", d[2] > d[0] && d[1] > d[0])
        val w = GradeMath.apply(f(0.95f, 0.95f, 0.95f), shadowsTeal)
        assertTrue("le luci quasi non cambiano", abs(w[0] - w[2]) < 0.02f)
    }

    @Test fun hslTouchesOnlyItsColor() {
        val g = Grade().withBand(HslBand.BLUE, Hsl(sat = -1f))
        val sky = GradeMath.apply(f(0.2f, 0.35f, 0.85f), g)
        assertTrue("blu spento", abs(sky[2] - sky[0]) < 0.25f)
        val red = f(0.85f, 0.15f, 0.12f)
        assertTrue("rosso intatto", near(GradeMath.apply(red, g), red, 2e-3f))
        assertEquals(1f, GradeMath.bandWeight(230f, 230f), 1e-4f)
        assertEquals(0f, GradeMath.bandWeight(0f, 230f), 1e-4f)
        assertEquals(GradeMath.bandWeight(350f, 0f), GradeMath.bandWeight(10f, 0f), 1e-4f)  // attraverso lo 0
    }

    @Test fun histogramCountsEveryPixel() {
        val px = IntArray(1000) { i -> if (i < 500) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val h = GradeMath.histogram(px, Grade())
        assertEquals(1000, h[0].sum())
        assertEquals(500, h[0].first()); assertEquals(500, h[0].last())
        val darker = GradeMath.histogram(px, Grade(exposure = -2f))
        assertEquals(0, darker[0].last()) // il bianco non è più al massimo
    }

    @Test fun savedWithTheClipAndOldProjectsStillOpen() {
        val g = Grade(exposure = 0.5f, shadowWheel = Wheel(200f, 0.4f)).withBand(HslBand.GREEN, Hsl(-0.2f, 0.3f, 0.1f))
        val p = Project(name = "c", clips = listOf(Clip(uri = "u", sourceDurationUs = 1_000_000, grade = g)))
        val back = Project.JSON.decodeFromString(Project.serializer(), p.toJson())
        assertEquals(g, back.clips[0].grade)
        val old = Project.JSON.decodeFromString(Project.serializer(), """{"name":"x","clips":[{"uri":"u","sourceDurationUs":1000}]}""")
        assertTrue(old.clips[0].grade.isIdentity)
        assertNull(null)
    }
}
