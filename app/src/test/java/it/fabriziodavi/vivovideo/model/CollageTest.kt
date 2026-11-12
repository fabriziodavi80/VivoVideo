package it.fabriziodavi.vivovideo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Collage: modelli per ogni numero di foto e ogni formato, celle dentro il quadro, griglie senza buchi, movimento. */
class CollageTest {
    private fun overlap(a: CollageCell, b: CollageCell): Float {
        val w = min(a.x + a.w, b.x + b.w) - max(a.x, b.x)
        val h = min(a.y + a.h, b.y + b.h) - max(a.y, b.y)
        return if (w > 0 && h > 0) w * h else 0f
    }

    @Test fun manyTemplatesForEveryCountAndFormat() {
        for (asp in CollageAspect.entries) {
            val all = CollageTemplates.all(asp)
            assertTrue("$asp: ${all.size} modelli", all.size >= 120)
            assertEquals("id unici in $asp", all.size, all.map { it.id }.toSet().size)
            for (n in 1..9) {
                val t = CollageTemplates.forCount(n, asp)
                assertTrue("$asp, $n foto: ${t.size}", t.size >= if (n == 1) 6 else 8)
                if (n in 2..6) assertTrue("$asp, $n foto: famiglie ${t.map { it.family }.toSet()}", t.map { it.family }.toSet().size >= 4)
            }
            // Ogni famiglia c'è.
            assertEquals(CollageFamily.entries.toSet(), all.map { it.family }.toSet())
            all.forEach { assertEquals(it, CollageTemplates.byId(it.id, asp)) }
        }
    }

    @Test fun cellsStayInsideTheFrame() {
        for (asp in CollageAspect.entries) for (t in CollageTemplates.all(asp)) for (c in t.cells) {
            // Le polaroid hanno la cornice attorno (larga 1,15 e alta 1,3 volte la foto) e sono un po' storte.
            val fw = if (c.frame == CellFrame.NONE) c.w else c.w * 1.15f
            val fh = if (c.frame == CellFrame.NONE) c.h else c.h * 1.3f
            val tol = if (c.frame == CellFrame.NONE && c.rotation == 0f) 1e-3f else 0.05f
            assertTrue("$asp ${t.id}: $c", c.cx - fw / 2 >= -tol && c.cy - fh / 2 >= -tol && c.cx + fw / 2 <= 1 + tol && c.cy + fh / 2 <= 1 + tol)
            assertTrue("$asp ${t.id}: cella troppo piccola $c", c.w > 0.05f && c.h > 0.03f)
        }
    }

    @Test fun gridsCoverTheFrameWithoutHoles() {
        for (asp in CollageAspect.entries) for (t in CollageTemplates.all(asp).filter { it.family == CollageFamily.GRID || it.family == CollageFamily.HERO }) {
            assertEquals("${t.id}: area", 1f, t.cells.sumOf { (it.w * it.h).toDouble() }.toFloat(), 1e-3f)
            for (i in t.cells.indices) for (j in i + 1 until t.cells.size) assertTrue("${t.id}: $i e $j si sovrappongono", overlap(t.cells[i], t.cells[j]) < 1e-5f)
        }
        // Tagli diagonali: i poligoni coprono il quadro.
        for (t in CollageTemplates.all(CollageAspect.STORY).filter { it.family == CollageFamily.DIAGONAL }) {
            assertEquals(t.id, 1f, t.cells.sumOf { abs(CollageGeometry.area(it.poly)).toDouble() }.toFloat(), 1e-3f)
            t.cells.forEach { c -> assertTrue("${t.id} convesso", insideConvex(c.poly, c.cx, c.cy)) }
        }
    }

    @Test fun roundShapesStayRoundInEveryFormat() {
        for (asp in CollageAspect.entries) {
            val (w, h) = asp.size(1080)
            for (t in CollageTemplates.all(asp)) for (c in t.cells.filter { it.shape == CellShape.CIRCLE || it.shape == CellShape.HEART || it.shape == CellShape.BLOB }) {
                assertEquals("$asp ${t.id}: cerchio schiacciato", c.w * w, c.h * h, 3f)
            }
            for (t in CollageTemplates.all(asp)) for (c in t.cells.filter { it.shape == CellShape.ARCH }) assertTrue("$asp ${t.id}: arco basso", c.h * h > c.w * w * 1.2f)
        }
    }

    @Test fun insetShrinksPolygonsEvenly() {
        val sq = listOf(Pt(0f, 0f), Pt(1f, 0f), Pt(1f, 1f), Pt(0f, 1f))
        val inn = CollageGeometry.inset(sq, 0.1f)
        assertEquals(listOf(0.1f, 0.1f, 0.9f, 0.9f), listOf(inn.minOf { it.x }, inn.minOf { it.y }, inn.maxOf { it.x }, inn.maxOf { it.y }).map { (it * 1000).toInt() / 1000f })
        // Anche girato al contrario e per un poligono obliquo: resta dentro e più piccolo.
        val q = listOf(Pt(0.2f, 0f), Pt(0.6f, 0f), Pt(0.4f, 1f), Pt(0f, 1f))
        for (p in listOf(q, q.reversed())) {
            val r = CollageGeometry.inset(p, 0.02f)
            assertTrue(abs(CollageGeometry.area(r)) < abs(CollageGeometry.area(p)))
            r.forEach { assertTrue("$it", insideConvex(p, it.x, it.y)) }
        }
    }

    @Test fun everythingIsInPlaceAtTheEnd() {
        val dur = 4_000_000L
        for (m in CollageMotion.entries) for (t in CollageTemplates.forCount(6, CollageAspect.STORY)) {
            t.cells.forEachIndexed { i, c ->
                val p = CollageAnim.pose(m, i, t.count, c, (dur * 0.7).toLong(), dur, kenBurns = false)
                assertTrue("$m ${t.id} $i: $p", abs(p.alpha - 1) < 1e-3 && abs(p.scale - 1) < 1e-2 && abs(p.dx) < 1e-3 && abs(p.dy) < 1e-3 && abs(p.rot) < 1e-2 && p.reveal > 0.999f)
                assertEquals(CellPose(), CollageAnim.pose(m, i, t.count, c, 0, dur, kenBurns = true, still = true))
            }
        }
        // All'inizio le foto non ci sono ancora (tranne con i movimenti senza entrata).
        val c = CollageTemplates.forCount(4, CollageAspect.STORY).first().cells[3]
        assertEquals(0f, CollageAnim.pose(CollageMotion.CASCADE, 3, 4, c, 0, dur, true).alpha, 1e-6f)
        assertEquals(0f, CollageAnim.pose(CollageMotion.WIPE, 3, 4, c, 0, dur, true).reveal, 1e-6f)
        assertTrue(CollageAnim.pose(CollageMotion.DROP, 0, 4, c, 0, dur, true).dy < -1f)
        // Una dopo l'altra: la seconda arriva dopo la prima.
        val t1 = 200_000L
        assertTrue(CollageAnim.pose(CollageMotion.CASCADE, 0, 4, c, t1, dur, false).alpha > CollageAnim.pose(CollageMotion.CASCADE, 1, 4, c, t1, dur, false).alpha)
        // Zoom lento dentro le foto: a turno avanti e indietro.
        assertTrue(CollageAnim.pose(CollageMotion.STILL, 0, 4, c, dur, dur, true).inner > 1.05f)
        assertTrue(CollageAnim.pose(CollageMotion.STILL, 1, 4, c, dur, dur, true).inner < 1.01f)
    }

    @Test fun zoomStartsOnTheFirstPhotoAndOpensOnTheCollage() {
        val dur = 4_000_000L
        val t = CollageTemplates.byId("h-top-4", CollageAspect.STORY)
        assertNotNull(t)
        val first = t!!.cells[0]
        val c0 = CollageAnim.camera(CollageMotion.ZOOM, first, 9f / 16, 0, dur)
        assertTrue("$c0", c0.scale > 1.5f && abs(c0.cx - first.cx) < 1e-3 && abs(c0.cy - first.cy) < 1e-3)
        val c1 = CollageAnim.camera(CollageMotion.ZOOM, first, 9f / 16, dur, dur)
        assertEquals(CollageCamera(1f, 0.5f, 0.5f), c1)
        // Lo zoom va sempre avanti, senza scatti.
        var prev = Float.MAX_VALUE
        for (k in 0..40) { val s = CollageAnim.camera(CollageMotion.ZOOM, first, 9f / 16, dur * k / 40, dur).scale; assertTrue(s <= prev + 1e-4f); prev = s }
        assertEquals(CollageCamera(), CollageAnim.camera(CollageMotion.CASCADE, first, 9f / 16, 0, dur))
        assertTrue(CollageAnim.scroll(CollageMotion.SCROLL, dur, dur) < -0.3f)
        assertEquals(0f, CollageAnim.scroll(CollageMotion.SCROLL, dur, dur, still = true), 0f)
    }

    @Test fun formatSizesAndPicking() {
        assertEquals(2160 to 3840, CollageAspect.STORY.size(2160))
        assertEquals(2160 to 2700, CollageAspect.PORTRAIT.size(2160))
        assertEquals(3840 to 2160, CollageAspect.LANDSCAPE.size(2160))
        assertEquals(CollageAspect.STORY, CollageAspect.closest(1080, 1920))
        assertEquals(CollageAspect.LANDSCAPE, CollageAspect.closest(3840, 2160))
        assertEquals(CollageAspect.SQUARE, CollageAspect.closest(1000, 1000))
        // Meno foto che celle: si ripetono in ordine.
        assertEquals(listOf(0, 1, 0, 1), (0 until 4).map { CollagePlan.itemFor(it, 2) })
        // Video più corto del collage: riparte da capo.
        val v = CollageItem("v", video = true, startUs = 1_000_000, sourceDurationUs = 3_000_000)
        assertEquals(1_500_000L, CollagePlan.videoTimeUs(v, 500_000))
        assertEquals(1_500_000L, CollagePlan.videoTimeUs(v, 2_500_000))
    }
}
