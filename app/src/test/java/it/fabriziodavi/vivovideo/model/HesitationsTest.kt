package it.fabriziodavi.vivovideo.model

import it.fabriziodavi.vivovideo.model.VoiceSynth.Companion.pause
import it.fabriziodavi.vivovideo.model.VoiceSynth.Companion.word
import it.fabriziodavi.vivovideo.model.VoiceSynth.Ph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Ehm riconosciuti dal suono: sì gli ehm, eh, mmm, uhm, eee; no le parole, anche corte come "no", "sì", "ma". */
class HesitationsTest {
    private val ehm = listOf(Ph('e', 300), Ph('m', 250))

    private fun found(ph: List<Ph>, seed: Int = 3): Boolean {
        val pcm = VoiceSynth(seed).say(pause(500) + ph + pause(500) + word("ecco la casa") + pause(400))
        val fr = Hesitations.analyze(pcm)
        return Hesitations.detect(fr).any { it.startUs < 1_000_000 }
    }

    @Test fun hesitationSoundsAreFound() {
        assertTrue("ehm", found(ehm))
        assertTrue("mmm", found(listOf(Ph('m', 500))))
        assertTrue("uhm", found(listOf(Ph('u', 200), Ph('m', 250))))
        assertTrue("eee", found(listOf(Ph('e', 450))))
        assertTrue("ehm con un'altra voce", found(ehm, seed = 9))
    }

    @Test fun realWordsAreNotCut() {
        listOf("allora", "andiamo al mare", "nemmeno", "mamma", "uno", "bene", "sono").forEach { assertFalse(it, found(word(it))) }
        assertFalse("no", found(listOf(Ph('n', 70), Ph('o', 300))))
        assertFalse("sì", found(listOf(Ph('s', 110), Ph('i', 350))))
        assertFalse("ma", found(listOf(Ph('m', 70), Ph('a', 300))))
        // "e" congiunzione, breve: resta.
        assertFalse("e", found(listOf(Ph('e', 150))))
    }

    private fun starts(ph: List<Ph>): IntArray { var t = 0; return ph.map { val s = t; t += it.ms; s }.toIntArray() }

    @Test fun whisperTimesAreCorrectedFromTheSound() {
        // "allora" · pausa · ehm · pausa · "andiamo": il riconoscimento vocale mette l'ehm 350 ms prima (o 300 ms dopo).
        val ph = pause(500) + word("allora") + pause(300) + ehm + pause(300) + word("andiamo") + pause(500)
        val st = starts(ph)
        val from = st[8] * 1000L; val to = st[10] * 1000L
        val fr = Hesitations.analyze(VoiceSynth(5).say(ph))
        val isl = Hesitations.islands(fr)
        for (heard in listOf(Span(from - 350_000, from + 50_000), Span(to + 100_000, to + 400_000))) {
            val at = Hesitations.locate(fr, isl, heard)
            assertNotNull("$heard", at)
            assertTrue("inizio ${at!!.startUs} invece di $from", abs(at.startUs - from) < 80_000)
            assertTrue("fine ${at.endUs} invece di $to", abs(at.endUs - to) < 80_000)
        }
    }

    @Test fun ehmGluedToAWordTakesOnlyTheHeldSound() {
        // "allora-ehm" senza pausa: si toglie solo l'ehm, "allora" resta intera.
        val ph = pause(500) + word("allora") + ehm + pause(400) + word("andiamo") + pause(500)
        val ehStart = (500 + word("allora").sumOf { it.ms }) * 1000L
        val fr = Hesitations.analyze(VoiceSynth(5).say(ph))
        val isl = Hesitations.islands(fr)
        val at = Hesitations.locate(fr, isl, Span(ehStart - 100_000, ehStart + 300_000))
        assertNotNull(at)
        assertTrue("inizio ${at!!.startUs}", at.startUs >= ehStart - 20_000 && at.startUs < ehStart + 80_000)
        // "ehm" sentito su una parola vera: nel suono non c'è, non si sposta niente.
        assertNull(Hesitations.locate(fr, isl, Span(ehStart + 900_000, ehStart + 1_200_000)))
    }

    private fun video(lenUs: Long) = Clip(id = "v", uri = "content://v/v", sourceDurationUs = 60_000_000, trimEndUs = lenUs, width = 1920, height = 1080, fps = 30f)

    @Test fun planListsFillersToReviewAndToggle() {
        // Due ehm: uno sentito dal riconoscimento vocale (con i tempi sbagliati), uno no. Più un "ehm" sentito su una parola.
        val ph = pause(500) + word("allora") + pause(300) + ehm + pause(300) + word("andiamo") + pause(400) + listOf(Ph('m', 450)) + pause(400) + word("bene") + pause(500)
        val st = starts(ph)
        val pcm = VoiceSynth(7).say(ph)
        val p = Project(name = "parlato", clips = listOf(video(pcm.size * 1_000_000L / 16_000)))
        val firstEhm = st[8] * 1000L
        val wordAndiamo = st[11] * 1000L
        val words = listOf(SpokenWord(firstEhm - 300_000, firstEhm + 100_000, "ehm,"), SpokenWord(wordAndiamo + 50_000, wordAndiamo + 300_000, "eh"))
        val plan = SpeechCuts.plan(p, pcm, words, minPauseUs = 0, fillers = true)
        assertEquals("${plan.fillerCuts}", 3, plan.fillerCuts.size)
        val (a, b, c) = plan.fillerCuts
        assertEquals("ehm", a.word); assertTrue(a.on && !a.doubtful)
        assertTrue("primo ehm a ${a.span.startUs}", abs(a.span.startUs - firstEhm) < 80_000)
        assertTrue("«eh» su «andiamo»: da controllare", b.doubtful && !b.on)
        assertNull("il «mmm» l'ha trovato solo il suono", c.word); assertTrue(c.on)
        assertEquals(2, plan.fillers)
        val off = plan.toggle(0)
        assertEquals(1, off.fillers)
        assertTrue(off.totalUs < plan.totalUs)
        // Senza "togli gli ehm" la lista è vuota.
        assertTrue(SpeechCuts.plan(p, pcm, emptyList(), minPauseUs = 700_000).fillerCuts.isEmpty())
    }
}
