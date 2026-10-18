package it.fabriziodavi.vivovideo.model

import it.fabriziodavi.vivovideo.engine.ImageStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MomentsTest {
    private fun s(t: Long, sharp: Float, faces: Int = 0, smile: Float = 0f, x: Float = 0.5f) =
        MomentSample(t, sharp = sharp, luma = 0.47f, faces = faces, smile = smile, eyes = if (faces > 0) 1f else 0f, faceSize = if (faces > 0) 0.2f else 0f,
            subjectX = x, hasSubject = faces > 0)

    @Test fun sharpAndSmilingWins() {
        val scan = MomentScan("v", 10_000_000, listOf(s(0, 20f), s(1_000_000, 400f), s(2_000_000, 400f, faces = 1, smile = 0.9f), s(3_000_000, 10f)))
        val sc = Moments.scores(scan)
        assertTrue(sc[2] > sc[1]); assertTrue(sc[1] > sc[0]); assertTrue(sc[0] > sc[3] - 0.01f)
    }

    @Test fun bestWindowLandsOnTheGoodPart() {
        // 12 s: mosso fino a 7 s, poi nitido con un sorriso a 9 s.
        val samples = (0..12).map { i -> val t = i * 1_000_000L; if (i < 7) s(t, 15f) else s(t, 500f, faces = if (i == 9) 1 else 0, smile = 1f) }
        val scan = MomentScan("v", 12_000_000, samples)
        val best = Moments.bestStarts(scan, 2_000_000, 3)
        assertTrue("migliore ${best.first()}", best.first() in 7_000_000L..10_000_000L)
        assertEquals(3, best.size)
        // I tratti non si sovrappongono e stanno nel video.
        best.forEach { a -> assertTrue(a in 0..10_000_000L); best.filter { it != a }.forEach { b -> assertTrue(abs(a - b) >= 2_000_000) } }
        // Tratto più lungo del video: dall'inizio.
        assertEquals(listOf(0L), Moments.bestStarts(scan, 20_000_000))
    }

    @Test fun subjectPathIsSmoothAndPanStaysInside() {
        val scan = MomentScan("v", 6_000_000, (0..6).map { i -> s(i * 1_000_000L, 300f, faces = 1, x = if (i % 2 == 0) 0.2f else 0.8f) })
        val path = Moments.subjectPath(scan)!!
        // Il soggetto che "salta" da una parte all'altra diventa un movimento lento verso il centro.
        assertTrue(path.xs.all { it in 0.35f..0.65f })
        assertNull(Moments.subjectPath(MomentScan("v", 1, listOf(s(0, 300f)))))
        // Orizzontale 16:9 in verticale 9:16: si vede il 31,6% della larghezza, si può spostare al massimo di 1-0,316.
        val (tx, ty) = Moments.panFor(0f, 0.5f, 16f / 9f, 9f / 16f)
        assertEquals(1f - (9f / 16f) / (16f / 9f), tx, 1e-4f)
        assertEquals(0f, ty, 1e-6f)
        assertEquals(0f, Moments.panFor(0.5f, 0.5f, 16f / 9f, 9f / 16f).first, 1e-6f)
        // Stesso formato: niente spostamento possibile.
        assertEquals(0f to 0f, Moments.panFor(0.1f, 0.9f, 16f / 9f, 16f / 9f).let { (a, b) -> abs(a) to abs(b) })
        val r = Reframe(listOf(0, 1_000_000), listOf(0.2f, 0.8f), listOf(0.5f, 0.5f))
        assertEquals(0.5f, r.at(500_000).first, 1e-4f)
        assertEquals(0.2f, r.at(-5).first, 1e-6f); assertEquals(0.8f, r.at(9_000_000).first, 1e-6f)
    }

    @Test fun imageStatsSeeSharpnessAndWhere() {
        val w = 120; val h = 80
        // Dettagli fitti solo a destra, il resto liscio.
        val g = FloatArray(w * h) { i -> val x = i % w; val y = i / w; if (x > 90 && (x + y) % 2 == 0) 230f else 120f }
        val flat = FloatArray(w * h) { 120f }
        val a = ImageStats.measure(g, w, h); val b = ImageStats.measure(flat, w, h)
        assertTrue(a.sharp > 100 * (b.sharp + 1))
        assertTrue("centro ${a.cx}", a.cx > 0.75f)
        assertEquals(0.47f, ImageStats.measure(FloatArray(w * h) { 120f }, w, h).luma, 0.01f)
    }

    @Test fun smartAutoUsesBestMomentsAndFollowsFaces() {
        fun v(i: Int, w: Int, h: Int) = Clip(id = "v$i", uri = "content://v/$i", sourceDurationUs = 12_000_000, trimEndUs = 12_000_000, width = w, height = h)
        val clips = listOf(v(0, 1080, 1920), v(1, 1080, 1920), v(2, 1920, 1080))
        val good = MomentScan("", 12_000_000, (0..12).map { i -> val t = i * 1_000_000L; if (i < 8) s(t, 15f) else s(t, 500f, faces = 1, smile = 1f, x = 0.7f) })
        val scans = clips.associate { it.uri to good.copy(uri = it.uri) }
        val p = AutoEdit.build("t", clips, null, AutoOptions(targetUs = 0, format = VideoFormat.VERTICAL), scans)
        p.clips.forEach { c -> assertTrue("inizio ${c.trimStartUs}", c.trimStartUs >= 6_000_000) }
        val wide = p.clips.first { it.width == 1920 }
        assertNotNull(wide.reframe); assertTrue(wide.fill)
        assertTrue(p.clips.filter { it.width == 1080 }.all { it.reframe == null })
        // Senza regia intelligente: come prima.
        val plain = AutoEdit.build("t", clips, null, AutoOptions(targetUs = 0, format = VideoFormat.VERTICAL, smart = false), scans)
        assertTrue(plain.clips.all { it.reframe == null })
    }
}
