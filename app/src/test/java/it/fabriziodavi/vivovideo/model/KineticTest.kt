package it.fabriziodavi.vivovideo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sottotitoli e testi cinetici: parole importanti, stili, tempi delle parole scritte a mano. */
class KineticTest {
    @Test fun keywords() {
        listOf("2026", "45km/h", "fantastico!", "Incredibile", "WOW", "montagna,").forEach { assertTrue(it, Keywords.isKey(it)) }
        listOf("che", "bella", "perché", "comunque", "e", "...", "questo").forEach { assertFalse(it, Keywords.isKey(it)) }
    }

    @Test fun kineticSubtitleStylesComeFirst() {
        val kinetic = SubStyle.entries.filter { it.kinetic != null }
        assertEquals(KineticStyle.entries.size, kinetic.size)
        assertEquals(kinetic, SubStyle.entries.take(kinetic.size))
        val ws = listOf(SpokenWord(1_000_000, 1_300_000, "Che"), SpokenWord(1_300_000, 1_600_000, "giornata"), SpokenWord(1_600_000, 2_000_000, "fantastica!"))
        kinetic.forEach { st ->
            val t = Subtitles.build(ws, st, SubPosition.BOTTOM, 10_000_000).single()
            assertEquals(st.kinetic, t.kinetic)
            assertEquals(Subtitles.KINETIC_HIGHLIGHT, t.highlight)
            assertEquals(listOf(0L, 300_000L, 600_000L), t.wordsUs)
            assertTrue(t.size >= 0.06f)
        }
        // Gli altri stili restano com'erano.
        assertEquals(null, Subtitles.build(ws, SubStyle.SOCIAL, SubPosition.BOTTOM, 10_000_000).single().kinetic)
    }

    @Test fun handwrittenTextGetsEvenWordTimes() {
        assertEquals(listOf(0L, 350_000L, 700_000L), Subtitles.evenWords("Si parte domani", 3_000_000))
        // Testo lungo in poco tempo: le parole si stringono per stare nei 3/4 della durata.
        val t = Subtitles.evenWords((1..10).joinToString(" ") { "p$it" }, 1_000_000)
        assertEquals(10, t.size)
        assertTrue(t.last() <= 750_000)
        assertEquals(1, Subtitles.evenWords("", 1_000_000).size)
    }

    @Test fun textItemKeepsNewFieldsInJson() {
        val t = TextItem(text = "Ciao", behindPerson = true, kinetic = KineticStyle.CHROME)
        val back = Project.fromJson(Project(name = "x", texts = listOf(t)).toJson()).texts.single()
        assertEquals(t, back)
    }
}
