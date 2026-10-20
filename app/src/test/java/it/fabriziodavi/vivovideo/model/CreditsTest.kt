package it.fabriziodavi.vivovideo.model

import it.fabriziodavi.vivovideo.engine.rollU
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditsTest {
    private fun video(id: String) = Clip(id = id, uri = "content://v/$id", sourceDurationUs = 10_000_000, trimEndUs = 5_000_000, width = 1920, height = 1080)
    private val black = Clip(uri = "file:///nero.jpg", sourceDurationUs = Clip.PHOTO_MAX_US, trimEndUs = 5_000_000, isPhoto = true, width = 1920, height = 1080, fill = true)

    @Test fun buildsTheRollText() {
        val t = Credits.text("Grazie", listOf(Credits.Row("Riprese", "Fabrizio"), Credits.Row("", ""), Credits.Row("Musica originale", "")), listOf("\"Summer\" di Rossi (CC BY 4.0)"))
        assertEquals("Grazie\n\nRIPRESE\nFabrizio\n\nMUSICA ORIGINALE\n\nMUSICA\n\"Summer\" di Rossi (CC BY 4.0)", t)
        assertEquals("", Credits.text("", emptyList(), emptyList()))
        assertEquals(6_000_000L, Credits.suggestedUs("a\nb"))
        assertEquals(30_000_000L, Credits.suggestedUs((1..60).joinToString("\n")))
    }

    @Test fun onBlackAddsAnEndingAndRollsOverIt() {
        val p = Project(name = "x", clips = listOf(video("a"), video("b")))
        val q = p.withCredits("Grazie\n\nRIPRESE\nFabrizio", 8_000_000, onBlack = true, black = black)
        assertEquals(18_000_000L, q.durationUs) // 10 s di video + 8 s di nero
        assertEquals(Credits.BLACK_ID, q.clips.last().id)
        assertEquals(TransitionType.FADE_BLACK, q.clips[1].transitionOut?.type)
        val roll = q.creditsRoll!!
        assertEquals(10_000_000L, roll.startUs); assertEquals(8_000_000L, roll.durationUs)
        assertTrue(roll.roll && roll.animIn == TextAnim.NONE)
        // Rifatti: niente doppioni; tolti: si torna al video di prima.
        val again = q.withCredits("Fine", 6_000_000, onBlack = true, black = black)
        assertEquals(1, again.texts.count { it.roll }); assertEquals(1, again.clips.count { it.id == Credits.BLACK_ID })
        val none = again.withoutCredits()
        assertNull(none.creditsRoll); assertEquals(10_000_000L, none.durationUs)
    }

    @Test fun overTheLastScenesKeepsTheLength() {
        val p = Project(name = "x", clips = listOf(video("a"), video("b")))
        val q = p.withCredits("Fine", 8_000_000, onBlack = false, black = null)
        assertEquals(10_000_000L, q.durationUs)
        assertEquals(2_000_000L, q.creditsRoll!!.startUs)
        // Più lunghi del video: durano quanto il video.
        assertEquals(10_000_000L, p.withCredits("Fine", 30_000_000, onBlack = false, black = null).creditsRoll!!.durationUs)
    }

    @Test fun rollMovesAtConstantSpeed() {
        assertEquals(0f, rollU(-5, 1_000), 0f)
        assertEquals(0.25f, rollU(250, 1_000), 1e-4f)
        assertEquals(0.5f, rollU(500, 1_000), 1e-4f)
        assertEquals(1f, rollU(2_000, 1_000), 0f)
    }
}
