package it.fabriziodavi.vivovideo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class SnapToVoiceTest {
    /** 3 s a 16 kHz: silenzio (fruscio leggero) e voce da 1,00 a 1,60 s e da 2,00 a 2,50 s. */
    private fun audio(): FloatArray = FloatArray(48_000) { i ->
        val t = i / 16_000f
        val voice = (t in 1.0f..1.6f) || (t in 2.0f..2.5f)
        (if (voice) 0.4f * sin(2 * Math.PI * 220 * t).toFloat() else 0f) + 0.002f * sin(i * 1.7f)
    }

    @Test fun earlyStartMovesOntoTheVoice() {
        val w = listOf(SpokenWord(700_000, 1_500_000, "ciao"), SpokenWord(2_000_000, 2_400_000, "amici"))
        val s = Subtitles.snapToVoice(w, audio())
        assertTrue("prima ${s[0].startUs}", s[0].startUs in 960_000..1_010_000)
        assertEquals(2_000_000, s[1].startUs)
        assertEquals("ciao", s[0].text)
    }

    @Test fun startDeepInSilenceBeyondHalfTheWordStays() {
        // Voce oltre metà parola: probabilmente non è questa parola, non si tocca.
        val w = listOf(SpokenWord(100_000, 1_100_000, "boh"))
        assertEquals(100_000, Subtitles.snapToVoice(w, audio())[0].startUs)
    }

    @Test fun emptyInputs() {
        assertTrue(Subtitles.snapToVoice(emptyList(), audio()).isEmpty())
        val w = listOf(SpokenWord(0, 10, "x"))
        assertEquals(w, Subtitles.snapToVoice(w, FloatArray(0)))
    }

    @Test fun startsStayInOrder() {
        val w = listOf(SpokenWord(1_000_000, 1_300_000, "a"), SpokenWord(950_000, 1_500_000, "b"))
        val s = Subtitles.ordered(w)
        assertEquals(1_000_000, s[1].startUs)
        assertTrue(s[1].endUs >= s[1].startUs)
    }

    @Test fun lateStartAfterAPauseMovesBackToTheVoice() {
        val w = listOf(SpokenWord(1_200_000, 1_600_000, "ciao"), SpokenWord(2_150_000, 2_500_000, "amici"))
        val s = Subtitles.snapToVoice(w, audio())
        assertTrue("prima ${s[0].startUs}", s[0].startUs in 970_000..1_010_000)
        assertTrue("seconda ${s[1].startUs}", s[1].startUs in 1_970_000..2_010_000)
    }

    @Test fun wordInsideTheSamePhraseDoesNotStealThePreviousOnset() {
        val w = listOf(SpokenWord(1_050_000, 1_200_000, "ciao"), SpokenWord(1_250_000, 1_600_000, "bella"))
        val s = Subtitles.snapToVoice(w, audio())
        assertTrue(s[0].startUs < 1_010_000)
        assertEquals(1_250_000, s[1].startUs)
    }

    @Test fun shortStopInsideSpeechIsNotAPause() {
        // Voce con un buco di 60 ms (una "tt"): la parola dopo il buco resta dov'è.
        val a = FloatArray(32_000) { i ->
            val t = i / 16_000f
            val voice = (t in 0.5f..1.3f) || (t in 1.36f..1.9f)
            (if (voice) 0.4f * sin(2 * Math.PI * 220 * t).toFloat() else 0f) + 0.002f * sin(i * 1.7f)
        }
        val w = listOf(SpokenWord(500_000, 1_300_000, "molto"), SpokenWord(1_450_000, 1_900_000, "fatto"))
        assertEquals(1_450_000, Subtitles.snapToVoice(w, a)[1].startUs)
    }

    @Test fun tooLateStaysPut() {
        val w = listOf(SpokenWord(1_450_000, 1_600_000, "boh"))
        assertEquals(1_450_000, Subtitles.snapToVoice(w, audio())[0].startUs)
    }
}
