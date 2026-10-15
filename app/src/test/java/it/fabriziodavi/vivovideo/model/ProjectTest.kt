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

    @Test fun musicLanesAndGain() {
        val clip = Clip(uri = "a", sourceDurationUs = 10_000_000)
        val a = MusicTrack(uri = "m", name = "A", sourceDurationUs = 60_000_000, startUs = 0, lengthUs = 6_000_000, fadeInUs = 1_000_000, fadeOutUs = 2_000_000)
        val b = a.copy(id = "b", startUs = 4_000_000, lengthUs = 20_000_000, fadeInUs = 0, fadeOutUs = 0)
        val c = a.copy(id = "c", startUs = 6_000_000, lengthUs = 1_000_000)
        val p = Project(name = "t", clips = listOf(clip), music = listOf(a, b, c))
        val lanes = p.musicLanes()
        assertEquals(2, lanes.size)
        assertEquals(listOf("A", "A"), lanes[0].map { it.name })
        // b si ferma alla fine del video.
        assertEquals(6_000_000, b.playUs(p.durationUs))
        assertEquals(0.5f, a.gainAt(500_000, p.durationUs), 0.001f)
        assertEquals(1f, a.gainAt(3_000_000, p.durationUs), 0.001f)
        assertEquals(0.5f, a.gainAt(5_000_000, p.durationUs), 0.001f)
    }

    @Test fun alignCutsLandOnBeats() {
        val a = Clip(uri = "a", sourceDurationUs = 10_000_000, trimEndUs = 2_300_000)
        val b = Clip(uri = "b", sourceDurationUs = 10_000_000, trimEndUs = 3_100_000, speed = ClipSpeed.SLOW)
        val beats = (0 until 40).map { it * 500_000L }
        val m = MusicTrack(uri = "m", name = "M", sourceDurationUs = 30_000_000, lengthUs = 30_000_000, beatsUs = beats, bpm = 120f)
        val p = Project(name = "t", clips = listOf(a, b), music = listOf(m))
        val r = p.alignCutsToBeats()
        assertNotNull(r)
        val starts = r!!.clipStartsUs()
        val ends = r.clips.mapIndexed { i, c -> starts[i] + c.outputUs }
        for (e in ends) assertTrue("taglio a $e", e % 500_000 < 2_000 || e % 500_000 > 498_000)
        // Il primo taglio era a 2,3 s: il battito più vicino è 2,5 s.
        assertEquals(2_500_000.0, ends[0].toDouble(), 2_000.0)
    }

    @Test fun manualBeats() {
        val m = MusicTrack(uri = "m", name = "M", sourceDurationUs = 10_000_000, beatsUs = listOf(0, 500_000, 1_000_000, 1_500_000))
        val total = 10_000_000L
        // Togli un battito automatico vicino all'indicatore.
        val a = m.toggleBeatAt(520_000)
        assertEquals(listOf(0L, 1_000_000L, 1_500_000L), a.timelineAutoBeats(total))
        // Aggiungi un battito a mano e poi toglilo.
        val b = a.toggleBeatAt(700_000)
        assertEquals(listOf(700_000L), b.markersUs)
        assertEquals(emptyList<Long>(), b.toggleBeatAt(690_000).markersUs)
        // Spostamento fine dei battiti automatici.
        assertEquals(1_030_000L, m.copy(beatShiftUs = 30_000).timelineAutoBeats(total)[2])
        // Tap doppio ravvicinato ignorato.
        assertEquals(1, m.addMarker(2_000_000).addMarker(2_030_000).markersUs.size)
    }

    @Test fun slideshowOnBeats() {
        val photo = Clip(uri = "p", sourceDurationUs = Clip.PHOTO_MAX_US, trimEndUs = Clip.PHOTO_DEFAULT_US, isPhoto = true, width = 4, height = 3)
        val clips = List(4) { photo.copy(id = "p$it") }
        val m = MusicTrack(uri = "m", name = "M", sourceDurationUs = 60_000_000, lengthUs = 60_000_000, beatsUs = (0 until 200).map { it * 500_000L })
        val p = Project(name = "s", clips = clips, music = listOf(m)).slideshow(SlideshowOptions(beatsPerPhoto = 4))
        // Ogni foto dura 4 battiti (2 s) e ogni taglio cade su un battito.
        val starts = p.clipStartsUs()
        p.clips.forEachIndexed { i, c ->
            assertEquals(2_000_000L, c.outputUs)
            assertEquals(0L, (starts[i] + c.outputUs) % 500_000)
            assertTrue(c.keyframes.size == 2)
        }
        assertNotNull(p.clips[0].transitionOut)
        assertNull(p.clips.last().transitionOut)
    }
}
