package it.fabriziodavi.vivovideo.engine

import it.fabriziodavi.vivovideo.model.Bus
import it.fabriziodavi.vivovideo.model.BusMix
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.Comp
import it.fabriziodavi.vivovideo.model.Eq
import it.fabriziodavi.vivovideo.model.EqPreset
import it.fabriziodavi.vivovideo.model.Layer
import it.fabriziodavi.vivovideo.model.LoudTarget
import it.fabriziodavi.vivovideo.model.Mix
import it.fabriziodavi.vivovideo.model.MusicTrack
import it.fabriziodavi.vivovideo.model.Project
import it.fabriziodavi.vivovideo.model.bus
import it.fabriziodavi.vivovideo.model.buses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/** Mixer: canali, muto e solo, bilanciamento, equalizzatore, compressore, limitatore. */
class MixerTest {
    private val r = VoiceChain.RATE

    private fun tone(hz: Double, amp: Float, seconds: Double = 1.0) =
        FloatArray((r * seconds).toInt() * 2) { i -> (amp * sin(2 * PI * hz * (i / 2) / r)).toFloat() }

    private fun rmsDb(s: FloatArray, ch: Int, from: Int = s.size / 4): Double {
        var e = 0.0; var n = 0
        var i = from - from % 2 + ch
        while (i < s.size) { e += s[i] * s[i]; n++; i += 2 }
        return 20 * log10(sqrt(e / n) + 1e-12)
    }

    @Test fun muteSoloAndPanDecideWhatIsHeard() {
        var m = Mix()
        assertTrue(m.neutral && m.audible(Bus.MUSIC))
        m = m.with(Bus.VOICE, BusMix(solo = true))
        assertTrue(m.audible(Bus.VOICE)); assertFalse(m.audible(Bus.MUSIC))
        m = m.with(Bus.VOICE, BusMix()).with(Bus.MUSIC, BusMix(mute = true))
        assertFalse(m.audible(Bus.MUSIC)); assertTrue(m.audible(Bus.CLIPS))
        assertEquals(0f to 0f, m.gains(Bus.MUSIC))
        // Un canale riportato a zero sparisce dalla lista (progetto pulito).
        assertFalse(Bus.VOICE in m.buses)
        val (l, rr) = Mix().with(Bus.CLIPS, BusMix(gainDb = -6f, pan = 0.5f)).gains(Bus.CLIPS)
        assertEquals(0.25f, l, 0.01f); assertEquals(0.5f, rr, 0.01f)
    }

    @Test fun tracksGoToTheRightGroup() {
        val song = MusicTrack(uri = "content://m/1", name = "Brano", sourceDurationUs = 5_000_000)
        val voice = MusicTrack(uri = "file:///data/files/voice/voce_1.wav", name = "Voce 1", sourceDurationUs = 1_000_000, dialog = true)
        val detached = MusicTrack(uri = "content://v/2", name = "Audio clip 2", sourceDurationUs = 1_000_000, dialog = true)
        assertEquals(Bus.MUSIC, song.bus()); assertEquals(Bus.VOICE, voice.bus()); assertEquals(Bus.DETACHED, detached.bus())
        val p = Project(
            name = "m", clips = listOf(Clip(uri = "content://v/1", sourceDurationUs = 5_000_000, trimEndUs = 5_000_000)),
            music = listOf(song, voice),
            layers = listOf(Layer(uri = "content://v/3", name = "Sopra", width = 10, height = 10, isVideo = true, sourceDurationUs = 2_000_000)),
        )
        assertEquals(listOf(Bus.CLIPS, Bus.VOICE, Bus.MUSIC, Bus.OVERLAY), p.buses())
    }

    @Test fun mixIsSavedWithTheProject() {
        val p = Project(name = "s", mix = Mix(target = LoudTarget.PODCAST, limiter = false).with(Bus.VOICE, BusMix(gainDb = 3f, eq = EqPreset.VOICE_CLEAR.eq, comp = Comp.MEDIUM)))
        val back = Project.JSON.decodeFromString(Project.serializer(), Project.JSON.encodeToString(Project.serializer(), p))
        assertEquals(p.mix, back.mix)
        // Progetti vecchi senza mixer: si aprono con il mixer neutro.
        val old = Project.JSON.decodeFromString(Project.serializer(), """{"name":"vecchio"}""")
        assertTrue(old.mix.neutral)
    }

    @Test fun gainPanAndMuteAreApplied() {
        val s = tone(1000.0, 0.5f)
        val ref = rmsDb(s, 0)
        BusDsp.of(Mix().with(Bus.MUSIC, BusMix(gainDb = -6f, pan = 1f)), Bus.MUSIC).process(s)
        assertTrue(rmsDb(s, 0) < -100)                      // tutto a destra: sinistra muta
        assertEquals(ref - 6.0, rmsDb(s, 1), 0.3)           // destra: -6 dB
        val m = tone(1000.0, 0.5f)
        BusDsp.of(Mix().with(Bus.MUSIC, BusMix(mute = true)), Bus.MUSIC).process(m)
        assertTrue(m.all { it == 0f })
    }

    @Test fun eqShapesLowsMidsAndHighs() {
        fun after(hz: Double, eq: Eq): Double {
            val s = tone(hz, 0.3f)
            val before = rmsDb(s, 0)
            BusDsp.of(Mix().with(Bus.VOICE, BusMix(eq = eq)), Bus.VOICE).process(s)
            return rmsDb(s, 0) - before
        }
        assertTrue(after(60.0, Eq(lowDb = -8f)) < -6)
        assertTrue(abs(after(3000.0, Eq(lowDb = -8f))) < 1)
        assertTrue(after(1500.0, Eq(midDb = 6f)) > 5)
        assertTrue(after(12000.0, Eq(highDb = 6f)) > 5)
        assertTrue(abs(after(200.0, Eq(highDb = 6f))) < 1)
    }

    @Test fun compressorBringsLoudAndQuietCloser() {
        val loud = tone(400.0, 0.8f); val quiet = tone(400.0, 0.08f)
        val diffBefore = rmsDb(loud, 0) - rmsDb(quiet, 0)
        val mix = Mix().with(Bus.VOICE, BusMix(comp = Comp.STRONG))
        BusDsp.of(mix, Bus.VOICE).process(loud); BusDsp.of(mix, Bus.VOICE).process(quiet)
        val diffAfter = rmsDb(loud, 0) - rmsDb(quiet, 0)
        assertTrue("differenza $diffBefore → $diffAfter dB", diffAfter < diffBefore - 6)
    }

    @Test fun limiterStopsPeaks() {
        val s = tone(200.0, 1.6f)
        PeakLimiter(0.891f).process(s)
        assertTrue(s.all { abs(it) <= 0.891f })
        val q = tone(200.0, 0.3f); val copy = q.copyOf()
        PeakLimiter(0.891f).process(q)
        assertTrue(q.contentEquals(copy)) // sotto la soglia non cambia nulla
    }
}

/** Il canale del mixer in anteprima, sui buffer che passa Media3. */
@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [35])
class MixProcessorTest {
    @Test fun previewChannelAppliesGainAndFeedsTheMeter() {
        Meters.reset()
        val p = MixAudioProcessor.bus(Mix().with(Bus.MUSIC, BusMix(gainDb = -6f)), Bus.MUSIC)
        p.configure(androidx.media3.common.audio.AudioProcessor.AudioFormat(48_000, 2, androidx.media3.common.C.ENCODING_PCM_16BIT))
        p.flush(androidx.media3.common.audio.AudioProcessor.StreamMetadata.DEFAULT)
        val inBuf = java.nio.ByteBuffer.allocateDirect(480 * 4).order(java.nio.ByteOrder.nativeOrder())
        repeat(480 * 2) { inBuf.putShort(16000) }
        inBuf.flip()
        p.queueInput(inBuf)
        val out = p.output
        val first = out.getShort().toInt()
        assertEquals(8000.0, first.toDouble(), 60.0)        // -6 dB ≈ metà
        assertTrue(Meters.db(Bus.MUSIC) > -10f)               // l'indicatore si muove
        assertTrue(Meters.db(Bus.VOICE) <= -59f)              // gli altri no
    }
}
