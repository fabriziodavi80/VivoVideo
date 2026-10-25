package it.fabriziodavi.vivovideo.engine

import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.LoudTarget
import it.fabriziodavi.vivovideo.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Anteprima forte come il video esportato: stesso guadagno, ma senza far distorcere i picchi. */
class PreviewLoudnessTest {
    private fun video(id: String, vol: Float = 1f) = Clip(id = id, uri = "content://v/$id", sourceDurationUs = 10_000_000, trimEndUs = 5_000_000, width = 1920, height = 1080, fps = 30f, volume = vol)

    @Test fun quietPhoneSpeechIsRaisedButPeaksStayClean() {
        // Parlato a –28 LUFS con picchi a –12 dBFS: per arrivare a –14 servirebbero +14 dB, ma i picchi
        // permettono solo +11 dB (fino a –1 dBFS).
        val g = PreviewLoudness.gainOf(-28.0, PreviewLoudness.lin(-12.0), -14.0)
        assertEquals(11.0, PreviewLoudness.db(g), 0.3)
        // Musica già forte: si abbassa come nel video esportato.
        assertEquals(-4.0, PreviewLoudness.db(PreviewLoudness.gainOf(-10.0, 0.9f, -14.0)), 0.3)
        // Mai oltre ±12 dB; silenzio = com'è.
        assertTrue(PreviewLoudness.gainOf(-60.0, 0.001f, -14.0) <= 4f)
        assertEquals(1f, PreviewLoudness.gainOf(-70.0, 0f, -14.0))
    }

    @Test fun measuredOncePerAudioVersion() {
        val p = Project(name = "x", clips = listOf(video("a"), video("b")))
        val k = PreviewLoudness.key(p)
        // Cambiare il colore non conta; cambiare un taglio o un volume sì.
        assertEquals(k, PreviewLoudness.key(p.copy(clips = p.clips.map { it.copy(grade = it.grade.copy(exposure = 0.5f)) })))
        assertNotEquals(k, PreviewLoudness.key(p.copy(clips = listOf(video("a"), video("b", vol = 0.5f)))))
        assertNotEquals(k, PreviewLoudness.key(p.copy(clips = listOf(video("a"), video("b").copy(trimEndUs = 4_000_000)))))
        // "Come viene" o solo clip mute: niente da misurare.
        assertNull(PreviewLoudness.key(p.copy(mix = p.mix.copy(target = LoudTarget.OFF))))
        assertNull(PreviewLoudness.key(p.copy(clips = p.clips.map { it.copy(muted = true) })))
        assertEquals(1f, PreviewLoudness.gainFor(p))
        assertTrue(PreviewLoudness.needs(p))
        PreviewLoudness.put(p, 2f)
        assertEquals(2f, PreviewLoudness.gainFor(p))
        assertTrue(!PreviewLoudness.needs(p))
    }
}
