package it.fabriziodavi.vivovideo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/** Tagli a ritmo: nella timeline il bordo di ogni clip resta sotto il suo battito, anche con centinaia di clip. */
class TimelineGeometryTest {
    @Test fun cutsStayUnderTheBeatsAfterHundredsOfClips() {
        // 500 clip da un battito a 128 BPM; schermo a densità 3,5; zoom dal più lontano al più vicino.
        val beatUs = 468_750L
        val outs = List(500) { beatUs }
        for (dps in listOf(8f, 15f, 30f, 60f, 120f)) {
            val pxPerUs = dps * 3.5f / 1_000_000f
            val e = TimelineGeometry.edgesPx(outs, pxPerUs)
            assertEquals(501, e.size)
            assertEquals(0, e[0])
            for (i in 1..500) {
                val exact = i * beatUs * pxPerUs.toDouble()
                assertTrue("zoom $dps, taglio $i: ${e[i]} invece di $exact", abs(e[i] - exact) <= 0.5)
            }
            // Prima si sommavano le larghezze arrotondate una per una: l'errore cresceva clip dopo clip.
            val old = (beatUs * pxPerUs).roundToInt() * 500
            if (dps == 30f) assertTrue("vecchio metodo: scarto ${abs(old - e[500])} px", abs(old - e[500]) > 20)
        }
    }

    @Test fun mixedLengthsHaveNoGapsOrOverlaps() {
        val outs = listOf(1_000_000L, 33_333L, 250_000L, 2_000_000L, 1L)
        val e = TimelineGeometry.edgesPx(outs, 105f / 1_000_000f)
        for (i in 1 until e.size) assertTrue(e[i] >= e[i - 1])
        assertEquals((outs.sum() * 105.0 / 1_000_000).roundToInt(), e.last())
    }
}
