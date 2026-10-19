package it.fabriziodavi.vivovideo.model

import it.fabriziodavi.vivovideo.engine.Whisper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sottotitoli automatici: lettura delle parole, righe da leggere, tempi. */
class SubtitlesTest {
    private fun w(s: Double, e: Double, t: String) = SpokenWord((s * 1e6).toLong(), (e * 1e6).toLong(), t)

    @Test fun readsTheRecognizerOutput() {
        val raw = "0\t420\t Ciao\n420\t900\t a\n900\t1500\t tutti!\nmalformata\n1500\t1600\t \n".toByteArray(Charsets.UTF_8) +
            byteArrayOf(0x31, 0x36, 0x30, 0x30, 0x09, 0x31, 0x39, 0x30, 0x30, 0x09, 0x20, 0x63, 0xC3.toByte(), 0x0A) // "c" + byte spezzato
        val words = Whisper.parseWords(raw)
        assertEquals(listOf("Ciao", "a", "tutti!", "c"), words.map { it.text })
        assertEquals(420_000L, words[1].startUs)
        assertEquals(1_500_000L, words[2].endUs)
    }

    @Test fun dropsNonSpeech() {
        val ws = listOf(w(0.0, 1.0, "[Musica]"), w(1.0, 1.5, "Ecco"), w(1.5, 2.0, "(applausi"), w(2.0, 2.2, "forti)"), w(2.2, 2.6, "qui"), w(2.6, 2.7, "♪"))
        assertEquals(listOf("Ecco", "qui"), Subtitles.clean(ws).map { it.text })
    }

    @Test fun linesBreakOnLengthPausesAndSentences() {
        val ws = "Oggi siamo andati al mare con tutta la famiglia e abbiamo mangiato benissimo".split(" ")
            .mapIndexed { i, t -> w(i * 0.3, i * 0.3 + 0.25, t) }
        val lines = Subtitles.group(ws)
        assertTrue(lines.all { l -> l.joinToString(" ") { it.text }.length <= 32 && l.size <= 7 })
        assertEquals(ws.size, lines.sumOf { it.size })
        // Pausa lunga e fine frase vanno a capo.
        val p = Subtitles.group(listOf(w(0.0, 0.3, "Ciao."), w(0.4, 0.6, "Come"), w(0.7, 0.9, "va"), w(2.0, 2.3, "bene")))
        assertEquals(listOf(listOf("Ciao."), listOf("Come", "va"), listOf("bene")), p.map { l -> l.map { it.text } })
    }

    @Test fun buildsTimedTextsInEveryStyle() {
        val ws = listOf(w(1.0, 1.3, "Che"), w(1.3, 1.6, "bella"), w(1.6, 2.2, "giornata."), w(4.0, 4.4, "Andiamo!"))
        SubStyle.entries.forEach { st ->
            val t = Subtitles.build(ws, st, SubPosition.BOTTOM, 10_000_000)
            assertEquals(2, t.size)
            assertTrue(t.all { it.subtitle })
            assertEquals("Che bella giornata.".let { if (st == SubStyle.MINIMAL) it.lowercase() else it }, t[0].text)
            assertEquals(1_000_000L, t[0].startUs)
            assertEquals(listOf(0L, 300_000L, 600_000L), t[0].wordsUs)
            assertTrue("non si accavalla col successivo", t[0].endUs <= t[1].startUs)
            assertTrue(t[1].endUs <= 10_000_000)
        }
        assertEquals(1, Subtitles.build(ws, SubStyle.KARAOKE, SubPosition.TOP, 10_000_000)[0].wordMode)
        assertEquals(2, Subtitles.build(ws, SubStyle.REVEAL, SubPosition.TOP, 10_000_000)[0].wordMode)
        // Stili 2026: niente tutto maiuscolo; Social a poche parole, Minimal in minuscolo.
        assertTrue(SubStyle.entries.none { st -> Subtitles.build(ws, st, SubPosition.TOP, 10_000_000)[0].upper })
        val long = (0 until 9).map { w(it * 0.3, it * 0.3 + 0.25, "parola$it") }
        assertTrue(Subtitles.build(long, SubStyle.SOCIAL, SubPosition.BOTTOM, 10_000_000).all { it.text.split(" ").size <= 3 })
        assertEquals(2, Subtitles.build(ws, SubStyle.SOCIAL, SubPosition.TOP, 10_000_000)[0].wordMode)
        assertEquals(-1, Subtitles.wordAt(listOf(100L, 400L), 50))
        assertEquals(1, Subtitles.wordAt(listOf(100L, 400L), 450))
    }
}
