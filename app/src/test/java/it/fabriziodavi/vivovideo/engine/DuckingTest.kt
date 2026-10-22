package it.fabriziodavi.vivovideo.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.MusicTrack
import it.fabriziodavi.vivovideo.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/** Voce fuori campo: volume chiaro, ducking che scatta sulla voce e non sul rumore, anche in anteprima. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DuckingTest {
    private fun sine(amp: Float, seconds: Double): FloatArray {
        val n = (AudioDsp.RATE * seconds).toInt()
        return FloatArray(n * 2) { i -> (amp * sin(2 * PI * 300.0 * (i / 2) / AudioDsp.RATE)).toFloat() }
    }

    @Test fun quietVoiceIsBroughtToAClearLevel() {
        val quiet = sine(0.05f, 3.0)
        val g = AudioDsp.levelVoice(quiet)
        assertTrue("alzata: $g", g > 3f)
        assertEquals(-16.0, AudioDsp.LoudnessMeter().apply { addInterleaved(quiet) }.lufs(), 1.5)
        // Una voce già forte non si alza oltre i picchi.
        val loud = sine(0.95f, 1.0)
        assertTrue(AudioDsp.levelVoice(loud) <= 1f)
        assertTrue(loud.maxOf { kotlin.math.abs(it) } <= 0.9f)
    }

    @Test fun steadyBackgroundNoiseDoesNotDuck() {
        // Clip con vento/traffico costante a -30 dB (sopra la vecchia soglia fissa di -38) e due frasi a -12 dB.
        val levels = FloatArray(600) { i -> if (i in 200 until 260 || i in 400 until 450) -12f else -30f }
        val m = AudioDsp.clipSpeechMask(levels)
        assertFalse(m[100]); assertFalse(m[350]); assertFalse(m[590])
        assertTrue(m[220]); assertTrue(m[420])
        val g = AudioDsp.duckGainsFrom(m, 12f)
        assertEquals(1f, g[100], 1e-3f)
        assertTrue(g[230] < 0.3f)
        // Voce fuori campo piana (dopo la registrazione) con fruscio a -60: scatta sulla voce.
        val voice = FloatArray(300) { i -> if (i in 100 until 200) -24f else if (i in 50 until 250) -60f else -120f }
        val v = AudioDsp.voiceMask(voice)
        assertTrue(v[150]); assertFalse(v[60]); assertFalse(v[10])
    }

    @Test fun energyLandsInTheRightBlocks() {
        val e = DoubleArray(400)
        AudioDsp.addEnergy(e, sine(0.5f, 1.0), startFrame = AudioDsp.RATE.toLong(), frames = AudioDsp.RATE.toLong()) { 1f }
        val lv = AudioDsp.levelsFromEnergy(e)
        assertEquals(-120f, lv[50], 0f)
        assertEquals(-9.0, lv[150].toDouble(), 0.5)
        assertEquals(-120f, lv[250], 0f)
        assertEquals(0.5f, AudioDsp.gainAt(floatArrayOf(1f, 0f), 5_000), 1e-4f)
        assertEquals(1f, AudioDsp.gainAt(floatArrayOf(0.2f), 900_000), 0f)
    }

    private fun process(p: DuckAudioProcessor, offsetUs: Long, value: Short, frames: Int): ShortArray {
        p.flush(AudioProcessor.StreamMetadata.Builder().setPositionOffsetUs(offsetUs).build())
        val inBuf = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
        repeat(frames * 2) { inBuf.putShort(value) }
        inBuf.flip()
        p.queueInput(inBuf)
        val out = p.output
        return ShortArray(out.remaining() / 2) { out.getShort() }
    }

    @Test fun previewProcessorFollowsThePositionAfterASeek() {
        // Musica abbassata a 0,25 fra 1 s e 3 s del montaggio; il brano comincia a 0,5 s.
        val gains = FloatArray(400) { i -> if (i in 100 until 300) 0.25f else 1f }
        val p = DuckAudioProcessor(gains, itemStartUs = 500_000)
        p.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
        // Salto a 1 s dentro il brano = 1,5 s del montaggio: musica bassa.
        assertEquals(2500, process(p, 1_000_000, 10000, 480).first().toInt(), 2)
        // Salto all'inizio del brano (0,5 s del montaggio): piena.
        assertEquals(10000, process(p, 0, 10000, 480).first().toInt())
        // Scorre: 3 s nel brano = 3,5 s del montaggio, di nuovo piena.
        assertEquals(10000, process(p, 3_000_000, 10000, 480).first().toInt())
    }

    @Test fun previewGainsNeedVoiceMusicAndDucking() {
        val clip = Clip(id = "a", uri = "content://v/a", sourceDurationUs = 4_000_000, trimEndUs = 4_000_000)
        val song = MusicTrack(uri = "content://m/s", name = "Brano", sourceDurationUs = 10_000_000)
        val voice = MusicTrack(uri = "file:///data/voice/voce_1.wav", name = "Voce 1", sourceDurationUs = 1_000_000, startUs = 1_000_000, lengthUs = 1_000_000, dialog = true)
        val p = Project(name = "d", clips = listOf(clip), music = listOf(song, voice), duck = true)
        assertNull(DuckPreview.key(p.copy(duck = false)))
        assertNull(DuckPreview.key(p.copy(music = listOf(song))))
        assertTrue(DuckPreview.needs(p))
        DuckPreview.put(p, FloatArray(400) { i -> if (i in 100 until 200) -20f else -120f })
        assertFalse(DuckPreview.needs(p))
        val g = DuckPreview.gainsFor(p)!!
        assertEquals(1f, g[50], 1e-3f)
        assertTrue(g[150] < 0.3f)
        // Spostando la voce va rimisurata.
        assertTrue(DuckPreview.needs(p.copy(music = listOf(song, voice.copy(startUs = 2_000_000)))))
    }
}
