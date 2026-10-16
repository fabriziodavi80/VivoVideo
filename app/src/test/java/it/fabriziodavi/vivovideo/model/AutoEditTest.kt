package it.fabriziodavi.vivovideo.model

import it.fabriziodavi.vivovideo.ui.dateRangeLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import kotlin.math.abs

/** Il "regista" del video automatico. */
class AutoEditTest {
    private fun video(i: Int, dur: Long = 12_000_000) = Clip(id = "v$i", uri = "content://v/$i", sourceDurationUs = dur, trimEndUs = dur, width = 1920, height = 1080)
    private fun photo(i: Int) = Clip(id = "p$i", uri = "file:///p$i.jpg", sourceDurationUs = Clip.PHOTO_MAX_US, trimEndUs = Clip.PHOTO_DEFAULT_US, isPhoto = true, width = 4000, height = 3000)
    private fun sources() = (0 until 12).map { if (it % 3 == 0) video(it) else photo(it) }

    @Test fun pickSpreadsOverTheWholePeriodInDateOrder() {
        val items = (0 until 100).map { AutoItem(isVideo = it % 4 == 0, durationUs = 10_000_000, dateMs = 1_000_000L * (100 - it)) } // al contrario
        val idx = AutoEdit.pick(items, AutoStyle.CINEMA, 60_000_000)
        assertTrue("${idx.size} elementi", idx.size in 12..20)
        val dates = idx.map { items[it].dateMs }
        assertEquals(dates.sorted(), dates)                 // in ordine di data
        assertEquals(items.minOf { it.dateMs }, dates.first()) // dal primo…
        assertEquals(items.maxOf { it.dateMs }, dates.last())  // …all'ultimo giorno
        assertTrue(AutoEdit.pick(items, AutoStyle.ENERGICO, 60_000_000).size > idx.size) // più veloce = più scene
        assertEquals(100, AutoEdit.pick(items, AutoStyle.CINEMA, 0).size)
        assertTrue(AutoEdit.pick(emptyList(), AutoStyle.CINEMA, 60_000_000).isEmpty())
        val huge = (0 until 1000).map { AutoItem(false, 0, it.toLong()) }
        assertEquals(AutoEdit.MAX_ITEMS, AutoEdit.pick(huge, AutoStyle.CINEMA, 0).size)
    }

    @Test fun buildsAFinishedVideoWithoutMusic() {
        AutoStyle.entries.forEach { s ->
            val p = AutoEdit.build("Prova", sources(), null, AutoOptions(style = s, title = "Estate", subtitle = "Agosto 2026"))
            assertEquals(12, p.clips.size)
            p.clips.forEachIndexed { i, c ->
                if (c.isPhoto) {
                    assertEquals("$s foto $i", s.photoUs, c.outputUs)
                    assertTrue("$s foto $i senza movimento", c.keyframes.isNotEmpty())
                } else {
                    assertEquals("$s video $i", s.videoUs, c.outputUs)
                    assertTrue("$s video $i: preso dal mezzo", c.trimStartUs > 0 && c.trimEndUs < c.sourceDurationUs)
                    assertTrue(!c.muted) // senza musica si sentono i video
                }
                assertTrue(c.fill)
                s.fx.forEach { f -> assertTrue("$s effetto ${f.type}", c.fx.any { it.type == f.type }) }
                if (i < p.clips.lastIndex) assertNotNull("$s transizione $i", c.transitionOut) else assertNull(c.transitionOut)
                c.transitionOut?.let { assertTrue(it.type in s.transitions && it.durationUs <= s.transitionUs) }
            }
            if (s.accents.isNotEmpty()) assertTrue("$s accenti", p.clips[1].fx.size > s.fx.size)
            assertTrue(p.clips.first().fadeInUs > 0 && p.clips.last().fadeOutUs > 0)
            assertEquals(2, p.texts.size)
            assertEquals("Estate", p.texts[0].text)
            assertEquals(s.title, TextPreset.matching(p.texts[0]))
            assertTrue(p.music.isEmpty())
            // Le dissolvenze incrociate hanno sempre fotogrammi oltre il taglio (foto o video presi dal mezzo).
            p.crossfades().forEach { x -> assertTrue("$s incrociata corta", x.beforeUs + x.afterUs > 0) }
        }
    }

    @Test fun withMusicCutsLandOnTheBeats() {
        val beats = (1 until 400).map { it * 500_000L } // 120 BPM
        val m = MusicTrack(id = "m", uri = "content://m", name = "M", sourceDurationUs = 200_000_000, lengthUs = 200_000_000, beatsUs = beats, bpm = 120f)
        AutoStyle.entries.forEach { s ->
            val p = AutoEdit.build("Ritmo", sources(), m, AutoOptions(style = s))
            val starts = p.clipStartsUs() + p.durationUs
            starts.drop(1).forEach { t -> assertTrue("$s taglio a $t fuori battito", t % 500_000L == 0L) }
            p.clips.forEach { c -> assertEquals("$s battiti per scena", s.beatsPerSlot * 500_000L, c.outputUs); assertTrue(c.muted) }
            val track = p.music.single()
            assertEquals(0L, track.startUs)
            assertEquals(p.durationUs, track.lengthUs)
            assertTrue(track.fadeOutUs > 0)
            assertTrue(p.texts.isEmpty()) // nessun titolo chiesto
        }
    }

    @Test fun shortVideosAreUsedWholeAndNeverStretched() {
        val p = AutoEdit.build("Corti", listOf(video(0, 1_000_000), photo(1), video(2, 700_000)), null, AutoOptions(style = AutoStyle.CINEMA))
        assertEquals(1_000_000L, p.clips[0].outputUs)
        assertEquals(700_000L, p.clips[2].outputUs)
        assertTrue(p.clips.all { it.trimEndUs <= it.sourceDurationUs })
        assertTrue(AutoEdit.build("Vuoto", emptyList(), null, AutoOptions()).clips.isEmpty())
    }

    @Test fun dateRanges() {
        fun d(y: Int, m: Int) = Calendar.getInstance().apply { set(y, m, 15, 12, 0) }.timeInMillis
        assertEquals("Agosto 2026", dateRangeLabel(d(2026, Calendar.AUGUST), d(2026, Calendar.AUGUST)))
        assertEquals("Luglio – Agosto 2026", dateRangeLabel(d(2026, Calendar.JULY), d(2026, Calendar.AUGUST)))
        assertEquals("2024 – 2026", dateRangeLabel(d(2024, Calendar.MAY), d(2026, Calendar.AUGUST)))
    }
}
