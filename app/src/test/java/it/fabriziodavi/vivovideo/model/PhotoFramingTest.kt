package it.fabriziodavi.vivovideo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le foto nel video automatico: si vedono (quasi) intere, senza ingrandimenti forti. */
class PhotoFramingTest {
    private fun song(): MusicTrack {
        val beats = (1 until 200).map { it * 500_000L }
        return MusicTrack(id = "m", uri = "content://m", name = "M", sourceDurationUs = 99_000_000, lengthUs = 99_000_000,
            beatsUs = beats, bpm = 120f, energy = beats.map { if (it < 20_000_000) 0.3f else 0.95f })
    }

    private fun photo(i: Int, w: Int, h: Int) =
        Clip(id = "p$i", uri = "file:///p$i.jpg", sourceDurationUs = Clip.PHOTO_MAX_US, trimEndUs = 3_000_000, isPhoto = true, width = w, height = h)

    @Test fun photosAreNeverZoomedMuchInAnyTheme() {
        // Foto verticali 3:4 (come quelle del telefono) in un video verticale, scene lunghe (tanti battiti dentro).
        val photos = (0 until 24).map { photo(it, 3000, 4000) }
        AutoStyle.entries.forEach { st ->
            val p = AutoEdit.build("T", photos, song(), AutoOptions(style = st, targetUs = 40_000_000, format = VideoFormat.VERTICAL))
            p.clips.filter { it.isPhoto }.forEach { c ->
                val maxScale = c.keyframes.maxOfOrNull { it.value.scale } ?: 1f
                assertTrue("$st: foto ingrandita a $maxScale", maxScale <= 1.101f)
                // 3:4 in 9:16 si vede per il 75%: riempie il quadro.
                assertEquals("$st", FrameMode.FILL, c.frameMode)
            }
        }
    }

    @Test fun photosInTheOtherOrientationStayWhole() {
        val photos = (0 until 6).map { photo(it, 4000, 3000) } + (6 until 12).map { photo(it, 3000, 4000) }
        val p = AutoEdit.build("T", photos, song(), AutoOptions(style = AutoStyle.ENERGICO, targetUs = 30_000_000, format = VideoFormat.HORIZONTAL))
        val vertical = p.clips.filter { it.isPhoto && it.height > it.width }
        assertTrue(vertical.isNotEmpty())
        vertical.forEach { c ->
            assertEquals(FrameMode.BLUR, c.frameMode)
            assertNull(c.reframe)
            assertTrue((c.keyframes.maxOfOrNull { it.value.scale } ?: 1f) <= 1.05f)
        }
        assertTrue(p.clips.filter { it.isPhoto && it.width > it.height }.all { it.frameMode == FrameMode.FILL })
        // Quanta parte resta riempiendo: 3:4 in 16:9 meno della metà, 4:3 in 16:9 tre quarti.
        assertTrue(AutoEdit.fillKeeps(0.75f, 16f / 9f) < AutoEdit.MIN_FILL_KEEP)
        assertEquals(0.75f, AutoEdit.fillKeeps(4f / 3f, 16f / 9f), 1e-3f)
    }

    @Test fun motionStylesAreGentle() {
        MotionStyle.entries.forEach { m -> m.keyframes(3_000_000).forEach { assertTrue("$m", it.value.scale <= 1.1f && kotlin.math.abs(it.value.rotation) <= 1f) } }
    }
}
