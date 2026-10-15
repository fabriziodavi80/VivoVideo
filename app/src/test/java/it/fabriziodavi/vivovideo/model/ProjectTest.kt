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

    @Test fun curvePassesThroughPointsWithoutOvershoot() {
        val pts = SpeedPreset.BULLET.points
        for (p in pts) assertTrue(abs(SpeedInterp.valueAt(pts, p.t) - p.speed) < 1e-3)
        var t = 0f
        while (t <= 1f) {
            val v = SpeedInterp.valueAt(pts, t)
            assertTrue("t=$t v=$v", v >= 0.3f - 1e-3 && v <= 3f + 1e-3)
            t += 0.01f
        }
    }

    @Test fun valleyDurationIsLonger() {
        val out = clip(4.0, ClipSpeed.RAMP).outputUs / 1e6
        assertTrue("valley $out", out > 4.0 && out < 8.0)
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

    @Test fun splitCurveKeepsTotalDurationClose() {
        val p = Project(name = "t").addClips(listOf(clip(4.0, ClipSpeed.RAMP)))
        val s = p.splitAt(p.durationUs / 2)!!
        assertEquals(2, s.clips.size)
        assertEquals(2, s.clips.count { it.speed == ClipSpeed.RAMP })
        val diff = abs(s.durationUs - p.durationUs) / 1e6
        assertTrue("diff $diff", diff < 0.15)
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
            .addClips(listOf(clip(3.0, ClipSpeed.RAMP).copy(log = true, trimStartUs = 500_000, speedPoints = SpeedPreset.HERO.points)))
        assertEquals(p, Project.fromJson(p.toJson()))
    }

    @Test fun keyframeInterpolation() {
        val keys = listOf(
            Keyframe(0, Transform(scale = 1f), Easing.LINEAR),
            Keyframe(2_000_000, Transform(scale = 4f, x = 0.5f, opacity = 0f)),
        )
        val mid = Keyframes.valueAt(keys, 1_000_000)
        assertTrue("scale ${mid.scale}", abs(mid.scale - 2f) < 1e-3) // a metà tempo, metà zoom "percepito"
        assertTrue(abs(mid.x - 0.25f) < 1e-4)
        assertEquals(Transform(scale = 1f), Keyframes.valueAt(keys, -5))
        assertEquals(4f, Keyframes.valueAt(keys, 9_000_000).scale, 1e-4f)
        val hold = listOf(Keyframe(0, Transform(x = 0f), Easing.HOLD), Keyframe(1_000_000, Transform(x = 1f)))
        assertEquals(0f, Keyframes.valueAt(hold, 999_000).x, 1e-6f)
    }

    @Test fun keyframeSetSnapsAndSorts() {
        var k = Keyframes.set(emptyList(), 1_000_000, Transform(scale = 2f))
        k = Keyframes.set(k, 0, Transform())
        k = Keyframes.set(k, 1_020_000, Transform(scale = 3f)) // entro 40 ms: aggiorna
        assertEquals(listOf(0L, 1_000_000L), k.map { it.timeUs })
        assertEquals(3f, k[1].value.scale, 1e-6f)
    }

    @Test fun splitKeepsKeyframeValues() {
        val c = clip(4.0).copy(keyframes = listOf(Keyframe(0, Transform(scale = 1f), Easing.LINEAR), Keyframe(4_000_000, Transform(scale = 2f), Easing.LINEAR)))
        val p = Project(name = "t").addClips(listOf(c))
        val s = p.splitAt(2_000_000)!!
        val endOfFirst = Keyframes.valueAt(s.clips[0].keyframes, s.clips[0].trimmedUs)
        val startOfSecond = Keyframes.valueAt(s.clips[1].keyframes, 0)
        assertEquals(endOfFirst.scale, startOfSecond.scale, 1e-4f)
        assertEquals(2f, Keyframes.valueAt(s.clips[1].keyframes, s.clips[1].trimmedUs).scale, 1e-4f)
    }

    @Test fun speedPointAtPlayhead() {
        var pts = SpeedPreset.FLAT.points
        pts = SpeedPoints.set(pts, 0.4f, 0.3f)
        pts = SpeedPoints.set(pts, 0.41f, 0.25f) // vicino: aggiorna
        pts = SpeedPoints.set(pts, 0.7f, 2f)
        assertEquals(listOf(0f, 0.4f, 0.7f, 1f), pts.map { it.t })
        assertEquals(0.25f, SpeedInterp.valueAt(pts, 0.4f), 1e-3f)
        assertEquals(2f, SpeedInterp.valueAt(pts, 0.7f), 1e-3f)
    }

    @Test fun transitionHalfIsClamped() {
        val a = clip(4.0).copy(transitionOut = TransitionSpec(TransitionType.ZOOM, 800_000))
        val b = clip(0.4)
        val p = Project(name = "t").addClips(listOf(a, b))
        assertEquals(200_000L, p.transitionHalfUs(0)) // metà della clip corta
        assertEquals(0L, p.transitionHalfUs(1)) // ultima clip: nessuna successiva
        assertEquals(p, Project.fromJson(p.toJson()))
    }
}
