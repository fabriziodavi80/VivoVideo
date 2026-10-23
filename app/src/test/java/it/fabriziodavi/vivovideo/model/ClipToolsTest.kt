package it.fabriziodavi.vivovideo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class ClipToolsTest {
    /** Immagine con macchie casuali sfocate; [ox], [oy] = spostamento del contenuto. */
    private fun img(w: Int, h: Int, ox: Int, oy: Int, seed: Int = 1): ByteArray {
        val r = Random(seed)
        val spots = List(40) { Triple(r.nextInt(-20, w + 20), r.nextInt(-20, h + 20), r.nextInt(4, 14)) }
        return ByteArray(w * h) { i ->
            val x = i % w - ox; val y = i / w - oy
            var v = 40.0
            spots.forEach { (sx, sy, rad) -> val d = ((x - sx) * (x - sx) + (y - sy) * (y - sy)).toDouble(); v += 200 * kotlin.math.exp(-d / (2.0 * rad * rad)) }
            v.coerceIn(0.0, 255.0).toInt().toByte()
        }
    }

    @Test fun shiftFindsTheMovement() {
        val a = img(192, 108, 0, 0)
        for ((dx, dy) in listOf(3 to -2, -7 to 5, 0 to 0, 6 to 6)) {
            assertEquals(dx to dy, StabMath.shift(a, img(192, 108, dx, dy), 192, 108))
        }
    }

    @Test fun planSmoothsAndZoomsJustEnough() {
        // Camera ferma che trema di ±1% a ogni fotogramma.
        val n = 90
        val raw = Stabilization(strength = 0.8f, dx = List(n) { if (it % 2 == 0) 0.01f else -0.01f }, dy = List(n) { 0f })
        val p = StabMath.plan(raw)
        // Dopo la correzione il movimento resta quasi nullo.
        val fixed = List(n) { raw.dx[it] + p.cx[it] }
        val jitter = (1 until n).maxOf { abs(fixed[it] - fixed[it - 1]) }
        assertTrue("tremolio residuo $jitter", jitter < 0.002f)
        assertTrue(p.zoom in 1.015f..1.05f)
        // Un movimento enorme non fa zoomare oltre quanto concede la forza (al massimo 12%).
        val big = StabMath.plan(Stabilization(dx = List(n) { if (it % 2 == 0) 0.3f else -0.3f }, dy = List(n) { 0f }))
        assertTrue(big.zoom <= StabMath.budget(0.6f) + 1e-6f && StabMath.budget(1f) == StabMath.MAX_ZOOM)
        assertTrue(big.cx.all { abs(it) <= (big.zoom - 1) / 2 + 1e-6f })
        // Camminata: panoramica lenta + tremolio. Il tremolio sparisce, la panoramica resta, lo zoom è moderato,
        // e le correzioni cambiano senza scatti anche quando toccano il margine.
        val walk = Stabilization(strength = 1f, dx = List(150) { it * 0.004f + (if (it % 2 == 0) 0.012f else -0.012f) }, dy = List(150) { 0f })
        val w = StabMath.plan(walk)
        assertTrue("zoom ${w.zoom}", w.zoom <= StabMath.MAX_ZOOM)
        val path = List(150) { walk.dx[it] + w.cx[it] }
        val shake = (2 until 148).maxOf { abs(path[it + 1] - 2 * path[it] + path[it - 1]) }
        assertTrue("scossa residua $shake", shake < 0.006f)
        assertTrue(path.last() - path.first() > 0.4f)
        // Interpolazione nel tempo del file.
        val s = Stabilization(fromUs = 1_000_000, dx = listOf(0f, 0f, 0f), dy = listOf(0f, 0f, 0f))
        assertEquals(0f to 0f, StabMath.plan(s).at(0))
    }

    @Test fun rotationMapsToTheViewer() {
        assertEquals(-2f to 1f, StabMath.rotate(1f, 2f, 90))
        assertEquals(-1f to -2f, StabMath.rotate(1f, 2f, 180))
        assertEquals(2f to -1f, StabMath.rotate(1f, 2f, 270))
        assertEquals(1f to 2f, StabMath.rotate(1f, 2f, 0))
    }

    @Test fun reverseAndBack() {
        val c = Clip(
            uri = "content://v", sourceDurationUs = 10_000_000, trimStartUs = 2_000_000, trimEndUs = 6_000_000, width = 3840, height = 2160, hdr = 1,
            keyframes = listOf(Keyframe(0, Transform()), Keyframe(1_000_000, Transform(scale = 2f))),
            speedPoints = listOf(SpeedPoint(0f, 1f), SpeedPoint(0.25f, 0.5f), SpeedPoint(1f, 1f)),
            stab = Stabilization(),
        )
        val r = ClipTools.reversed(c, "file:///r.mp4", 4_000_000, 1920, 1080)
        assertEquals("file:///r.mp4", r.uri); assertEquals(0L, r.trimStartUs); assertEquals(4_000_000L, r.trimEndUs)
        assertEquals(listOf(3_000_000L, 4_000_000L), r.keyframes.map { it.timeUs })
        assertEquals(2f, r.keyframes.first().value.scale)
        assertEquals(listOf(0f, 0.75f, 1f), r.speedPoints.map { it.t })
        assertNull(r.stab); assertEquals(0, r.hdr)
        val back = ClipTools.unreversed(r)
        assertEquals(c.copy(stab = null), back)
    }

    @Test fun insertSplitsAndShiftsWhatComesAfter() {
        val a = Clip(id = "a", uri = "a", sourceDurationUs = 10_000_000, trimEndUs = 4_000_000)
        val b = Clip(id = "b", uri = "b", sourceDurationUs = 10_000_000, trimEndUs = 4_000_000)
        val p = Project(name = "t", clips = listOf(a, b), texts = listOf(TextItem(text = "prima", startUs = 500_000), TextItem(text = "dopo", startUs = 5_000_000)))
        val f = ClipTools.freezeClip(a, "file:///f.jpg", 1920, 1080)
        val q = p.insertAt(2_000_000, f)
        assertEquals(4, q.clips.size)
        assertEquals(f.id, q.clips[1].id)
        assertEquals(p.durationUs + 2_000_000, q.durationUs)
        assertEquals(500_000L, q.texts[0].startUs); assertEquals(7_000_000L, q.texts[1].startUs)
        // Vicino al bordo non divide: va prima della clip.
        val e = p.insertAt(4_050_000, f)
        assertEquals(listOf("a", f.id, "b"), e.clips.map { it.id })
        assertTrue(q.clips[1].freeze && q.clips[1].isPhoto)
    }
}
