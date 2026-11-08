package it.fabriziodavi.vivovideo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BehindPlacementTest {
    private val side = 64

    /** Persona finta: testa (cerchio) con il bordo alto alla riga [top], spalle larghe sotto. */
    private fun person(top: Int, cx: Int = side / 2): ByteArray = ByteArray(side * side) { i ->
        val r = i / side; val c = i % side
        val headR = 8
        val inHead = (c - cx) * (c - cx) + (r - (top + headR)) * (r - (top + headR)) <= headR * headR
        val inBody = r >= top + 2 * headR - 2 && abs(c - cx) <= 20
        if (inHead || inBody) 255.toByte() else 0
    }

    @Test fun findsHeadTopAndCentre() {
        val (top, cx) = BehindPlacement.head(listOf(person(20, 24)), side)!!
        assertEquals(20f / side, top, 1.5f / side)
        assertEquals(24.5f / side, cx, 1.5f / side)
        assertNull(BehindPlacement.head(listOf(ByteArray(side * side)), side))
    }

    @Test fun textSitsAboveShouldersWithHeadCoveringOnlyItsLowerPart() {
        val w = 1080; val h = 1920
        val spot = BehindPlacement.place(listOf(person(24)), side, w, h, "MONTAGNA", upper = true)!!
        assertFalse(spot.tight)
        assertEquals(0f, spot.show, 0f)
        val headPx = 24f / side * h
        val centre = (spot.y + 0.5f) * h
        val capH = 0.75f * spot.size * h
        val topPx = centre - capH / 2; val bottomPx = centre + capH / 2
        // La testa entra nelle lettere, ma ne copre al massimo metà; il testo resta dentro al quadro.
        assertTrue("testa sotto la cima delle lettere", headPx > topPx)
        assertTrue("al massimo metà coperta", (bottomPx - headPx) / capH in 0.3f..0.5f)
        assertTrue("dentro al quadro", topPx >= BehindPlacement.TOP * h - 1)
        // Largo: almeno metà del quadro.
        assertTrue(0.66f * spot.size * h * 8 > w * 0.5f)
    }

    @Test fun noRoomAboveHeadGoesTopAndShowsThrough() {
        val spot = BehindPlacement.place(listOf(person(1)), side, 1080, 1920, "CIAO", upper = true)
        assertNotNull(spot)
        assertTrue(spot!!.tight)
        assertTrue(spot.show > 0.2f)
        assertTrue(spot.y < -0.3f)
    }

    @Test fun movingPersonUsesTheHighestPoint() {
        val (top, _) = BehindPlacement.head(listOf(person(30), person(18), person(26)), side)!!
        assertEquals(18f / side, top, 1.5f / side)
    }
}
