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

    @Test fun noWordIsLostInShortLines() {
        // «Sì.» detto subito prima della frase dopo: la riga è troppo breve per stare da sola, ma la parola resta.
        val w = listOf(
            SpokenWord(1_000_000, 1_150_000, "Sì."),
            SpokenWord(1_200_000, 1_400_000, "andiamo"),
            SpokenWord(1_420_000, 1_600_000, "subito"),
            SpokenWord(1_620_000, 1_700_000, "Ok."),
        )
        for (style in SubStyle.entries) {
            val subs = Subtitles.build(w, style, SubPosition.BOTTOM, 1_750_000)
            val text = subs.joinToString(" ") { it.text }.lowercase()
            for (x in w) assertTrue("$style: manca ${x.text} in «$text»", text.contains(x.text.lowercase()))
            assertTrue(subs.all { it.durationUs >= TextItem.MIN_US })
            assertTrue(subs.all { it.wordsUs.size == it.text.split(' ').size })
        }
    }

    @Test fun unclosedBracketDoesNotEatTheSpeech() {
        val w = listOf("(ride", "allora", "partiamo", "insieme", "oggi", "verso", "il", "mare").mapIndexed { i, t -> SpokenWord(i * 300_000L, i * 300_000L + 250_000, t) }
        assertEquals(listOf("ride", "allora", "partiamo", "insieme", "oggi", "verso", "il", "mare"), Subtitles.clean(w).map { it.text })
        val m = listOf("[Musica]", "ciao").mapIndexed { i, t -> SpokenWord(i * 300_000L, i * 300_000L + 250_000, t) }
        assertEquals(listOf("ciao"), Subtitles.clean(m).map { it.text })
    }

    private fun ok(ts: List<Long>, n: Int, dur: Long) {
        assertEquals(n, ts.size)
        ts.zipWithNext().forEach { (a, b) -> assertTrue("parola troppo breve: $ts", b - a >= Subtitles.WORD_MIN_US) }
        assertTrue("fuori dal testo: $ts", ts.first() >= 0 && ts.last() < dur)
    }

    @Test fun wordsStartingTogetherAllGetShown() {
        val ts = Subtitles.wordTimes(listOf(0, 400_000, 400_000, 420_000, 900_000), 5, 1_500_000)
        ok(ts, 5, 1_500_000)
        assertEquals(0L, ts[0]); assertEquals(400_000L, ts[1])
        // Gruppo stretto alla fine: le parole tornano indietro, non escono dal testo.
        ok(Subtitles.wordTimes(listOf(0, 950_000, 960_000, 970_000), 4, 1_000_000), 4, 1_000_000)
        // Testo cortissimo con tante parole: passo regolare.
        val short = Subtitles.wordTimes(listOf(0, 0, 0, 0), 4, 300_000)
        assertEquals(4, short.size)
        short.zipWithNext().forEach { (a, b) -> assertTrue("$short", b > a) }
        assertTrue(short.last() < 300_000)
    }

    @Test fun moreWordsThanTimesStillAppear() {
        val ts = Subtitles.wordTimes(listOf(0, 300_000), 4, 2_000_000)
        ok(ts, 4, 2_000_000)
        assertEquals(300_000L, ts[1])
        assertTrue(ts[3] <= 1_800_000)
    }

    @Test fun wordAddedByHandGetsItsOwnTime() {
        val times = listOf(0L, 500_000, 1_000_000)
        // Parola aggiunta in mezzo: le altre tengono il tempo, la nuova sta tra le due vicine.
        val a = Subtitles.retime("ciao a tutti", "ciao a voi tutti", times, 1_600_000)
        ok(a, 4, 1_600_000)
        assertEquals(listOf(0L, 500_000L, 1_000_000L), listOf(a[0], a[1], a[3]))
        assertTrue(a[2] in 600_000..900_000)
        // Parola corretta: prende il tempo di quella che sostituisce.
        assertEquals(times, Subtitles.retime("ciao a tutti", "Ciao a tutto!", times, 1_600_000))
        // Parola aggiunta in fondo e all'inizio.
        val c = Subtitles.retime("ciao a tutti", "ciao a tutti amici", times, 1_600_000)
        ok(c, 4, 1_600_000); assertTrue(c[3] > 1_000_000)
        val d = Subtitles.retime("ciao a tutti", "ehi ciao a tutti", times, 1_600_000)
        ok(d, 4, 1_600_000); assertEquals(1_000_000L, d[3])
        // Parola tolta: le altre tengono il tempo.
        assertEquals(listOf(0L, 1_000_000L), Subtitles.retime("ciao a tutti", "ciao tutti", times, 1_600_000))
    }
}
