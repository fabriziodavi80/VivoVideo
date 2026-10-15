package it.fabriziodavi.vivovideo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Test della logica di tutte le funzioni principali (senza Android). */
class FeaturesTest {
    private fun video(id: String, src: Long = 10_000_000, s: Long = 0, e: Long = src) =
        Clip(id = id, uri = "content://v/$id", sourceDurationUs = src, trimStartUs = s, trimEndUs = e, width = 2160, height = 3840)

    private fun photo(id: String, dur: Long = 3_000_000) =
        Clip(id = id, uri = "file:///p/$id.jpg", sourceDurationUs = Clip.PHOTO_MAX_US, trimEndUs = dur, isPhoto = true, width = 3000, height = 4000)

    private fun near(expected: Long, actual: Long, tol: Long = 5_000) =
        assertTrue("atteso $expected, ottenuto $actual", abs(expected - actual) <= tol)

    // ---------------- Progetto completo: salvataggio ----------------

    @Test fun everyFieldSurvivesSaveAndLoad() {
        val p = Project(
            name = "Tutto",
            clips = listOf(
                video("a").copy(
                    speed = ClipSpeed.RAMP, speedPoints = SpeedPreset.HERO.points, log = true, volume = 1.5f,
                    keyframes = listOf(Keyframe(0, Transform()), Keyframe(2_000_000, Transform(scale = 1.4f), Easing.EASE_IN)),
                    transitionOut = TransitionSpec(TransitionType.CROSSFADE, 800_000), fill = true,
                    frame = Transform(x = 0.1f, rotation = 90f), fx = listOf(ClipFx(FxType.VHS, 0.3f), ClipFx(FxType.MIRROR)),
                ),
                photo("b"),
            ),
            color = ColorSettings(look = "WARM", highlights = 40, denoise = 20),
            music = listOf(MusicTrack(uri = "m", name = "M", sourceDurationUs = 60_000_000, beatsUs = listOf(0, 500_000), bpm = 120f,
                markersUs = listOf(250_000), hiddenUs = listOf(500_000), beatShiftUs = 20_000, beatEvery = 2)),
            texts = listOf(TextItem(text = "Ciao", font = TextFont.IMPATTO, look = TextLook.NEON,
                gradientTo = 0xFFFF0000.toInt(), spacing = 0.1f, upper = true, animIn = TextAnim.WORDS, endPose = Transform(scale = 1.2f))),
            layers = listOf(Layer(uri = "u", width = 10, height = 10, isVideo = true, sourceDurationUs = 5_000_000, sourceStartUs = 1_000_000,
                mask = LayerMask.HEART, key = KeyColor.GREEN, keyTolerance = 0.5f, border = LayerBorder.WHITE, muted = true,
                endPose = Transform(x = 0.2f))),
        )
        val back = Project.fromJson(p.toJson())
        assertEquals(p, back)
    }

    @Test fun oldProjectsWithoutNewFieldsStillLoad() {
        val old = """{"id":"x","name":"Vecchio","createdAt":1,"updatedAt":1,"clips":[{"id":"c","uri":"u","sourceDurationUs":4000000}]}"""
        val p = Project.fromJson(old)
        assertEquals(1, p.clips.size)
        assertEquals(4_000_000L, p.durationUs)
        assertTrue(p.music.isEmpty() && p.texts.isEmpty() && p.layers.isEmpty())
    }

    // ---------------- Modifiche di base ----------------

    @Test fun addInsertMoveDelete() {
        var p = Project(name = "x", clips = listOf(video("a"), video("b")))
        p = p.addClips(listOf(video("c")), atIndex = 1)
        assertEquals(listOf("a", "c", "b"), p.clips.map { it.id })
        p = p.moveClip("a", +2)
        assertEquals(listOf("c", "b", "a"), p.clips.map { it.id })
        assertEquals(p, p.moveClip("a", +1)) // oltre il bordo: niente
        p = p.removeClip("b")
        assertEquals(listOf("c", "a"), p.clips.map { it.id })
    }

    @Test fun splitPhotoAndRefuseNearEdges() {
        val p = Project(name = "x", clips = listOf(photo("f", 4_000_000)))
        val s = p.splitAt(1_500_000)!!
        assertEquals(2, s.clips.size)
        assertTrue(s.clips.all { it.isPhoto })
        near(1_500_000, s.clips[0].outputUs)
        near(2_500_000, s.clips[1].outputUs)
        assertNull(p.splitAt(50_000))
        assertNull(p.splitAt(3_960_000))
    }

    @Test fun splitMovesTransitionToSecondPiece() {
        val spec = TransitionSpec(TransitionType.ZOOM)
        val p = Project(name = "x", clips = listOf(video("a").copy(transitionOut = spec), video("b")))
        val s = p.splitAt(4_000_000)!!
        assertNull(s.clips[0].transitionOut)
        assertEquals(spec, s.clips[1].transitionOut)
    }

    @Test fun speedsChangeOutputDuration() {
        assertEquals(20_000_000L, video("a").copy(speed = ClipSpeed.SLOW).outputUs)
        assertEquals(5_000_000L, video("a").copy(speed = ClipSpeed.FAST).outputUs)
        SpeedPreset.entries.forEach { pr ->
            val c = video("a").copy(speed = ClipSpeed.RAMP, speedPoints = pr.points)
            assertTrue("${pr.label} durata ${c.outputUs}", c.outputUs in 2_000_000..50_000_000)
            // Andata e ritorno fra tempo finale e tempo originale.
            val curve = c.speedCurve()
            listOf(0L, 1_234_567L, 5_000_000L, 9_999_000L).forEach { t ->
                near(t, curve.sourceTimeAt(curve.outputTimeAt(t)), 20_000)
            }
        }
    }

    // ---------------- Keyframe e movimenti pronti ----------------

    @Test fun easingsStayInRange() {
        Easing.entries.forEach { e ->
            assertEquals(0f, e.apply(0f), 1e-3f)
            if (e != Easing.HOLD) assertEquals(1f, e.apply(1f), 1e-3f)
            (0..10).forEach { i -> assertTrue(e.apply(i / 10f) in -0.01f..1.01f) }
        }
    }

    @Test fun motionStylesStartAndEnd() {
        MotionStyle.entries.forEach { m ->
            val k = m.keyframes(3_000_000)
            if (m == MotionStyle.NONE) assertTrue(k.isEmpty()) else {
                assertEquals(2, k.size)
                assertEquals(0L, k[0].timeUs)
                assertEquals(3_000_000L, k[1].timeUs)
                // Con lo spostamento lo zoom deve coprire i bordi (niente nero).
                k.forEach { kf -> assertTrue(kf.value.scale >= 1f + 2 * maxOf(abs(kf.value.x), abs(kf.value.y)) - 0.01f) }
            }
        }
    }

    @Test fun layerMotionInterpolates() {
        val l = Layer(uri = "u", width = 1, height = 1, durationUs = 4_000_000, pose = Transform(x = 0f, scale = 0.5f), endPose = Transform(x = 0.4f, scale = 1f))
        assertEquals(0f, l.poseAt(0).x, 1e-4f)
        assertEquals(0.2f, l.poseAt(2_000_000).x, 1e-3f)
        assertEquals(0.4f, l.poseAt(4_000_000).x, 1e-4f)
        assertEquals(1f, l.poseAt(9_000_000).scale, 1e-4f)
        assertEquals(l.pose, l.copy(endPose = null).poseAt(2_000_000))
    }

    @Test fun layerShapeKeepsOldRoundedFlag() {
        assertEquals(LayerMask.ROUNDED, Layer(uri = "u", width = 1, height = 1, rounded = true).shape)
        assertEquals(LayerMask.STAR, Layer(uri = "u", width = 1, height = 1, rounded = true, mask = LayerMask.STAR).shape)
    }

    // ---------------- Transizioni ----------------

    @Test fun crossfadeSkippedWithoutMaterialAndLastClip() {
        val x = TransitionSpec(TransitionType.CROSSFADE, 1_000_000)
        // Nessun margine: clip intere.
        val p = Project(name = "x", clips = listOf(video("a").copy(transitionOut = x), video("b").copy(transitionOut = x)))
        assertTrue(p.crossfades().isEmpty())
        // Solo coda della prima: tutta la dissolvenza dopo il taglio.
        val q = Project(name = "x", clips = listOf(video("a", e = 6_000_000).copy(transitionOut = x), video("b")))
        val cf = q.crossfades().single()
        assertEquals(0L, cf.beforeUs)
        assertEquals(1_000_000L, cf.afterUs)
    }

    @Test fun crossfadeNeverLongerThanHalfClip() {
        val x = TransitionSpec(TransitionType.CROSSFADE, 4_000_000)
        val p = Project(name = "x", clips = listOf(video("a", s = 3_000_000, e = 4_000_000).copy(transitionOut = x), video("b", s = 3_000_000, e = 4_000_000)))
        val cf = p.crossfades().single()
        assertTrue(cf.beforeUs <= 500_000 && cf.afterUs <= 500_000)
    }

    @Test fun otherTransitionsUseHalves() {
        TransitionType.entries.filter { it != TransitionType.CROSSFADE }.forEach { t ->
            val p = Project(name = "x", clips = listOf(video("a").copy(transitionOut = TransitionSpec(t, 1_000_000)), video("b")))
            assertEquals(500_000L, p.transitionHalfUs(0))
            assertTrue(p.crossfades().isEmpty())
        }
    }

    // ---------------- Musica e battiti ----------------

    @Test fun musicStopsAtVideoEndAndFades() {
        val p = Project(name = "x", clips = listOf(video("a")), music = listOf(MusicTrack(uri = "m", name = "M", sourceDurationUs = 60_000_000, startUs = 8_000_000, fadeOutUs = 1_000_000)))
        val m = p.music.single()
        assertEquals(2_000_000L, m.playUs(p.durationUs))
        assertEquals(0f, m.gainAt(2_000_000, p.durationUs), 1e-3f)
        assertEquals(0.5f, m.gainAt(1_500_000, p.durationUs), 1e-3f)
        assertTrue(Project(name = "x", clips = listOf(video("a")), music = listOf(m.copy(startUs = 11_000_000))).audibleMusic().isEmpty())
    }

    @Test fun beatEveryAndShiftAndOffset() {
        val m = MusicTrack(uri = "m", name = "M", sourceDurationUs = 60_000_000, startUs = 1_000_000, offsetUs = 2_000_000,
            beatsUs = (0 until 100).map { it * 500_000L })
        // Il brano parte da 2 s nel file e suona dalla posizione 1 s del video: il battito a 2,5 s del file cade a 1,5 s.
        assertEquals(1_000_000L, m.timelineBeats(60_000_000).first())
        assertEquals(1_500_000L, m.timelineBeats(60_000_000)[1])
        val every4 = m.copy(beatEvery = 4).timelineBeats(60_000_000)
        assertEquals(2_000_000L, every4[1] - every4[0])
        assertEquals(1_030_000L, m.copy(beatShiftUs = 30_000).timelineAutoBeats(60_000_000).first())
    }

    @Test fun splitOnBeatsCutsEveryBeat() {
        val p = Project(name = "x", clips = listOf(video("a")), music = listOf(MusicTrack(uri = "m", name = "M", sourceDurationUs = 60_000_000, beatsUs = (0 until 40).map { it * 1_000_000L })))
        val s = p.splitAtBeats()!!
        assertEquals(10, s.clips.size)
        s.clips.forEach { near(1_000_000, it.outputUs) }
    }

    @Test fun fitClipOutputWorksWithRamps() {
        val p = Project(name = "x", clips = listOf(video("a", e = 5_000_000).copy(speed = ClipSpeed.RAMP, speedPoints = SpeedPreset.BULLET.points)))
        val f = p.fitClipOutput(0, 4_000_000)!!
        near(4_000_000, f.clips[0].outputUs, 20_000)
        val g = p.fitClipOutput(0, 3_000_000, moveEnd = false)!!
        near(3_000_000, g.clips[0].outputUs, 20_000)
        assertNull(p.fitClipOutput(0, 1_000_000_000))
    }

    // ---------------- Slideshow e testi ----------------

    @Test fun slideshowFixedDurationAndNoTransitions() {
        val p = Project(name = "s", clips = listOf(photo("a"), video("v"), photo("b")))
        val s = p.slideshow(SlideshowOptions(photoUs = 2_000_000, noTransitions = true, motion = MotionStyle.ZOOM_IN, fill = false))
        assertEquals(2_000_000L, s.clips[0].outputUs)
        assertEquals(10_000_000L, s.clips[1].outputUs) // il video non cambia
        assertTrue(s.clips.all { it.transitionOut == null })
        assertTrue(s.clips.filter { it.isPhoto }.all { it.keyframes.size == 2 && !it.fill })
        assertTrue(s.clips[1].keyframes.isEmpty())
    }

    @Test fun slideshowVariedMotionNeverRepeatsBackToBack() {
        val p = Project(name = "s", clips = List(12) { photo("p$it") })
        val s = p.slideshow(SlideshowOptions(), seed = 3)
        val firstKeys = s.clips.map { it.keyframes.map { k -> k.value } }
        firstKeys.zipWithNext().forEach { (a, b) -> assertTrue(a != b) }
        assertTrue(s.clips.dropLast(1).all { it.transitionOut != null })
    }

    @Test fun textPresetsAreComplete() {
        val base = TextItem(text = "Prova", startUs = 1_000_000, durationUs = 2_000_000)
        TextPreset.entries.forEach { pr ->
            val t = pr.applyTo(base)
            assertEquals("Prova", t.text)
            assertEquals(1_000_000L, t.startUs) // tempi invariati
            assertTrue(t.size in 0.02f..0.2f)
            assertTrue(t.x in -0.5f..0.5f && t.y in -0.5f..0.5f)
        }
    }

    @Test fun lanesNeverOverlap() {
        val items = listOf(0L to 3L, 1L to 2L, 2L to 5L, 3L to 4L, 5L to 6L)
        val lanes = packLanes(items, { it.first }, { it.second })
        lanes.forEach { lane -> lane.zipWithNext().forEach { (a, b) -> assertTrue(a.second <= b.first) } }
        assertEquals(items.size, lanes.sumOf { it.size })
    }

    @Test fun locateAtBoundariesAndEnd() {
        val p = Project(name = "x", clips = listOf(video("a", e = 2_000_000), video("b", e = 3_000_000)))
        assertEquals(0 to 0L, p.locate(0))
        assertEquals(1 to 0L, p.locate(2_000_000))
        assertEquals(1 to 3_000_000L, p.locate(99_000_000))
        assertNotNull(p.locate(4_999_999))
    }
}
