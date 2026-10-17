package it.fabriziodavi.vivovideo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TemplatesTest {
    private val slots = listOf(
        Clip(id = "s1", uri = "old1", sourceDurationUs = 10_000_000, trimStartUs = 1_000_000, trimEndUs = 3_000_000, width = 1080, height = 1920,
            fx = listOf(ClipFx(FxType.entries.first())), transitionOut = TransitionSpec(TransitionType.GLITCH, 400_000), grade = Grade(contrast = 0.3f)),
        Clip(id = "s2", uri = "old2", sourceDurationUs = 10_000_000, trimEndUs = 4_000_000, width = 1080, height = 1920, speed = ClipSpeed.entries.last(),
            transitionOut = TransitionSpec(TransitionType.CROSSFADE, 400_000)),
        Clip(id = "s3", uri = "old3", sourceDurationUs = Clip.PHOTO_MAX_US, trimEndUs = 1_500_000, isPhoto = true, width = 1080, height = 1920),
    )
    private val tpl = Templates.fromProject(Project(name = "Reel", clips = slots, texts = listOf(TextItem(text = "Titolo")), format = VideoFormat.VERTICAL), "Il mio reel")

    private fun v(i: Int, dur: Long = 20_000_000, w: Int = 1080, h: Int = 1920) = Clip(id = "n$i", uri = "new$i", sourceDurationUs = dur, trimEndUs = dur, width = w, height = h)
    private val photo = Clip(id = "np", uri = "file:///np.jpg", sourceDurationUs = Clip.PHOTO_MAX_US, trimEndUs = 3_000_000, isPhoto = true, width = 1080, height = 1920)

    @Test fun mediaFillTheSlotsKeepingTheEdit() {
        val p = Templates.apply(tpl, listOf(v(1), v(2), photo))
        assertEquals(listOf("new1", "new2", "file:///np.jpg"), p.clips.map { it.uri })
        // Stesse durate, effetti, colori e transizioni del modello; testi e formato restano.
        p.clips.zip(slots).forEach { (c, s) -> assertTrue("${c.outputUs} vs ${s.outputUs}", abs(c.outputUs - s.outputUs) < 5_000) }
        assertEquals(slots[0].fx, p.clips[0].fx); assertEquals(slots[0].grade, p.clips[0].grade)
        assertEquals(slots[0].transitionOut, p.clips[0].transitionOut)
        assertEquals(slots[1].speed, p.clips[1].speed)
        assertNull(p.clips.last().transitionOut)
        assertEquals(tpl.project.texts, p.texts); assertEquals(VideoFormat.VERTICAL, p.format)
        // Video al centro (senza analisi) e nuove clip con nuovi id.
        assertEquals(9_000_000L, p.clips[0].trimStartUs)
        assertTrue(p.clips.none { it.id in setOf("s1", "s2", "s3", "n1", "n2") })
    }

    @Test fun moreMediaRepeatTheSlotsAndBestStartIsUsed() {
        val p = Templates.apply(tpl, (1..5).map { v(it) }) { _, _ -> 15_000_000 }
        assertEquals(5, p.clips.size)
        assertEquals(slots[0].outputUs, p.clips[3].outputUs)
        assertTrue(p.clips.filter { it.speed == ClipSpeed.NORMAL }.all { it.trimStartUs == 15_000_000L })
        // Video troppo corto per lo spazio: si usa tutto quello che c'è.
        val short = Templates.apply(tpl, listOf(v(1, dur = 1_000_000)))
        assertEquals(1_000_000L, short.clips[0].trimmedUs)
        // Video orizzontale in uno spazio verticale: inquadratura dalle preimpostazioni.
        val wide = Templates.apply(tpl, listOf(v(1, w = 1920, h = 1080)))
        assertEquals(tpl.project.otherMode, wide.clips[0].frameMode)
    }
}
