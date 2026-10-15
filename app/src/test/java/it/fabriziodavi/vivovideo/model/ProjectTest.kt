package it.fabriziodavi.vivovideo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ProjectTest {
    private fun clip(durS: Double, speed: ClipSpeed = ClipSpeed.NORMAL) =
        Clip(uri = "content://v/${durS}", sourceDurationUs = (durS * 1e6).toLong(), speed = speed)

    @Test fun constantSpeedDurations() {
        assertEquals(4_000_000L, clip(4.0).outputUs)
        assertEquals(8_000_000L, clip(4.0, ClipSpeed.SLOW).outputUs)
        assertEquals(2_000_000L, clip(4.0, ClipSpeed.FAST).outputUs)
    }

    @Test fun rampDurationMatchesIntegral() {
        // ∫ dt / (a + b cos) su un periodo = 1/sqrt(a²-b²), a = 1,175, b = 0,825
        val expected = 4.116667 / Math.sqrt(1.175 * 1.175 - 0.825 * 0.825)
        val out = clip(4.116667, ClipSpeed.RAMP).outputUs / 1e6
        assertTrue("ramp $out vs $expected", abs(out - expected) < 0.05)
    }

    @Test fun inverseMapping() {
        val c = SpeedCurve.ramp(4_000_000)
        for (t in listOf(0L, 500_000L, 1_999_999L, 3_000_000L, 4_000_000L)) {
            val back = c.sourceTimeAt(c.outputTimeAt(t))
            assertTrue("t=$t back=$back", abs(back - t) < 2)
        }
    }

    @Test fun splitConstantClip() {
        val p = Project(name = "t").addClips(listOf(clip(4.0), clip(2.0, ClipSpeed.SLOW)))
        assertEquals(8_000_000L, p.durationUs)
        // 6 s sulla timeline = 2 s dentro la seconda clip a 0,5× = 1 s di sorgente
        val s = p.splitAt(6_000_000)!!
        assertEquals(3, s.clips.size)
        assertEquals(1_000_000L, s.clips[1].trimEndUs)
        assertEquals(1_000_000L, s.clips[2].trimStartUs)
        assertEquals(p.durationUs, s.durationUs)
    }

    @Test fun splitRefusesRampAndEdges() {
        val p = Project(name = "t").addClips(listOf(clip(4.0, ClipSpeed.RAMP)))
        assertNull(p.splitAt(1_000_000))
        val q = Project(name = "t").addClips(listOf(clip(4.0)))
        assertNull(q.splitAt(50_000))
        assertNotNull(q.splitAt(2_000_000))
    }

    @Test fun moveAndRemove() {
        val a = clip(1.0); val b = clip(2.0); val c = clip(3.0)
        val p = Project(name = "t").addClips(listOf(a, b, c))
        assertEquals(listOf(b.id, a.id, c.id), p.moveClip(b.id, -1).clips.map { it.id })
        assertEquals(p, p.moveClip(a.id, -1))
        assertEquals(listOf(a.id, c.id), p.removeClip(b.id).clips.map { it.id })
    }

    @Test fun locate() {
        val p = Project(name = "t").addClips(listOf(clip(1.0), clip(2.0)))
        assertEquals(0 to 500_000L, p.locate(500_000))
        assertEquals(1 to 500_000L, p.locate(1_500_000))
        assertEquals(1 to 2_000_000L, p.locate(9_000_000))
    }

    @Test fun jsonRoundTrip() {
        val p = Project(name = "Gita", color = ColorSettings(look = "CINEMA", highlights = 30))
            .addClips(listOf(clip(3.0, ClipSpeed.RAMP).copy(log = true, trimStartUs = 500_000)))
        assertEquals(p, Project.fromJson(p.toJson()))
    }
}
