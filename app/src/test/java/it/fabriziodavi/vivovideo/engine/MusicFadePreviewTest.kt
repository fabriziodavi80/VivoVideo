package it.fabriziodavi.vivovideo.engine

import it.fabriziodavi.vivovideo.model.MusicTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Dissolvenze del brano anche in anteprima: stessa curva del video esportato, volume non applicato due volte. */
class MusicFadePreviewTest {
    @Test fun fadesFollowTheTrack() {
        val t = MusicTrack(uri = "m", name = "M", sourceDurationUs = 60_000_000, startUs = 2_000_000, lengthUs = 10_000_000,
            volume = 0.5f, fadeInUs = 1_000_000, fadeOutUs = 2_000_000)
        val g = musicGainCurve(t, total = 20_000_000, duck = null)!!
        fun at(us: Long) = AudioDsp.gainAt(g, us)
        assertEquals(0f, at(2_000_000), 0.02f)        // inizio del brano: silenzio
        assertEquals(0.5f, at(2_500_000), 0.02f)      // metà dissolvenza in entrata
        assertEquals(1f, at(6_000_000), 0.001f)       // pieno (il volume 0,5 lo fa già un altro passaggio)
        assertEquals(0.5f, at(11_000_000), 0.02f)     // metà dissolvenza in uscita
        assertEquals(0f, at(12_000_000), 0.02f)       // fine del brano
        // Il brano si ferma dove finisce il video: la dissolvenza in uscita arriva lì.
        val short = musicGainCurve(t, total = 8_000_000, duck = null)!!
        assertEquals(0.5f, AudioDsp.gainAt(short, 7_000_000), 0.02f)
        // Con il ducking le due curve si moltiplicano; senza dissolvenze né ducking non serve nulla.
        val duck = FloatArray(2_001) { 0.5f }
        assertEquals(0.25f, AudioDsp.gainAt(musicGainCurve(t, 20_000_000, duck)!!, 11_000_000), 0.02f)
        assertNull(musicGainCurve(t.copy(fadeInUs = 0, fadeOutUs = 0), 20_000_000, null))
    }
}
