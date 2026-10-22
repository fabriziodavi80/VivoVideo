package it.fabriziodavi.vivovideo.ui

import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.Layer
import it.fabriziodavi.vivovideo.model.Project
import it.fabriziodavi.vivovideo.model.TextItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Spostare una clip col dito: dove cade, come si aprono le altre, cosa la segue. */
class ReorderTest {
    @Test fun fingerPicksTheClipAndTheTarget() {
        // Margine di 100 px, clip larghe 50, 120, 30.
        val w = listOf(50f, 120f, 30f)
        assertNull(Reorder.indexAt(90f, 100f, w))
        assertEquals(0, Reorder.indexAt(120f, 100f, w))
        assertEquals(1, Reorder.indexAt(260f, 100f, w))
        assertEquals(2, Reorder.indexAt(285f, 100f, w))
        assertNull(Reorder.indexAt(301f, 100f, w))
        // Tessere da 60 px: il posto segue il dito, senza uscire dai limiti.
        assertEquals(0, Reorder.target(50f, 100f, 60f, 5))
        assertEquals(2, Reorder.target(100f + 150f, 100f, 60f, 5))
        assertEquals(4, Reorder.target(5000f, 100f, 60f, 5))
    }

    @Test fun othersOpenAGapWhereTheClipWillGo() {
        // Clip 1 portata al posto 3 su 5: le altre occupano 0, 1, 2, 4.
        assertEquals(listOf(0, 1, 2, 4), listOf(0, 2, 3, 4).map { Reorder.slot(it, 1, 3) })
        // Clip 3 portata al posto 0: le altre scalano a destra.
        assertEquals(listOf(1, 2, 3, 4), listOf(0, 1, 2, 4).map { Reorder.slot(it, 3, 0) })
    }

    private fun clip(id: String, s: Long) = Clip(id = id, uri = "content://v/$id", sourceDurationUs = s, trimEndUs = s)

    @Test fun textsAndLayersInsideTheClipFollowIt() {
        // a 0–2 s, b 2–5 s, c 5–6 s.
        val p = Project(
            name = "r", clips = listOf(clip("a", 2_000_000), clip("b", 3_000_000), clip("c", 1_000_000)),
            texts = listOf(TextItem(id = "tb", text = "su b", startUs = 2_500_000, durationUs = 1_000_000), TextItem(id = "tc", text = "su c", startUs = 5_200_000, durationUs = 500_000)),
            layers = listOf(Layer(id = "lb", uri = "file:///f.png", name = "Freccia", startUs = 3_000_000, durationUs = 1_000_000, width = 10, height = 10)),
        )
        val m = p.moveClipTo("b", 0)
        assertEquals(listOf("b", "a", "c"), m.clips.map { it.id })
        // b ora parte da 0: il suo testo e la sua freccia si spostano di -2 s; il testo su c resta.
        assertEquals(500_000L, m.texts.first { it.id == "tb" }.startUs)
        assertEquals(1_000_000L, m.layers.first { it.id == "lb" }.startUs)
        assertEquals(5_200_000L, m.texts.first { it.id == "tc" }.startUs)
        // Indietro e menu Sposta dopo: stessa regola.
        assertEquals(listOf("a", "b", "c"), m.moveClipTo("b", 1).clips.map { it.id })
        assertEquals(listOf("b", "a", "c"), p.moveClip("a", 1).clips.map { it.id })
        assertEquals(p, p.moveClipTo("b", 1))
        assertEquals(p, p.moveClipTo("x", 0))
    }
}
