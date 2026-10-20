package it.fabriziodavi.vivovideo.model

import it.fabriziodavi.vivovideo.ui.STYLE_GROUPS
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
            val p = AutoEdit.build("Prova", sources(), null, AutoOptions(targetUs = 0, style = s, title = "Estate", subtitle = "Agosto 2026"))
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
                assertEquals(FrameMode.FILL, c.frameMode) // tutte orizzontali come il video
                s.fx.forEach { f -> assertTrue("$s effetto ${f.type}", c.fx.any { it.type == f.type }) }
                if (i < p.clips.lastIndex) assertNotNull("$s transizione $i", c.transitionOut) else assertNull(c.transitionOut)
                c.transitionOut?.let { assertTrue(it.type in s.transitions && it.durationUs <= s.transitionUs) }
            }
            if (s.accents.isNotEmpty()) assertTrue("$s accenti", p.clips[1].fx.size > s.fx.size)
            assertTrue(p.clips.first().fadeInUs > 0 && p.clips.last().fadeOutUs > 0)
            assertEquals(3, p.texts.size) // titolo, sottotitolo e chiusura
            assertEquals("Estate", p.texts[0].text)
            assertEquals("Agosto 2026", p.texts.last().text)
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
            val p = AutoEdit.build("Ritmo", sources(), m, AutoOptions(targetUs = 0, style = s, intro = false))
            val starts = p.clipStartsUs() + p.durationUs
            starts.drop(1).forEach { t -> assertTrue("$s taglio a $t fuori battito", t % 500_000L == 0L) }
            // Battiti per scena dello stile, di più se la scena sarebbe sotto la durata minima del tema.
            var bps = s.beatsPerSlot
            while (500_000L * bps < s.minSceneUs) bps++
            p.clips.forEach { c -> assertEquals("$s battiti per scena", bps * 500_000L, c.outputUs); assertTrue(c.muted) }
            val track = p.music.single()
            assertEquals(0L, track.startUs)
            assertEquals(p.durationUs, track.lengthUs)
            assertTrue(track.fadeOutUs > 0)
            assertTrue(p.texts.isEmpty()) // nessun titolo chiesto
        }
    }

    @Test fun shortVideosAreUsedWholeAndNeverStretched() {
        val p = AutoEdit.build("Corti", listOf(video(0, 1_000_000), photo(1), video(2, 700_000)), null, AutoOptions(targetUs = 0, style = AutoStyle.CINEMA))
        assertEquals(1_000_000L, p.clips[0].outputUs)
        assertEquals(700_000L, p.clips[2].outputUs)
        assertTrue(p.clips.all { it.trimEndUs <= it.sourceDurationUs })
        assertTrue(AutoEdit.build("Vuoto", emptyList(), null, AutoOptions(targetUs = 0)).clips.isEmpty())
    }

    @Test fun dateRanges() {
        fun d(y: Int, m: Int) = Calendar.getInstance().apply { set(y, m, 15, 12, 0) }.timeInMillis
        assertEquals("Agosto 2026", dateRangeLabel(d(2026, Calendar.AUGUST), d(2026, Calendar.AUGUST)))
        assertEquals("Luglio – Agosto 2026", dateRangeLabel(d(2026, Calendar.JULY), d(2026, Calendar.AUGUST)))
        assertEquals("2024 – 2026", dateRangeLabel(d(2024, Calendar.MAY), d(2026, Calendar.AUGUST)))
    }

    @Test fun otherProposalsChangeScenesButKeepThePeriod() {
        val items = (0 until 200).map { AutoItem(isVideo = it % 5 == 0, durationUs = 8_000_000, dateMs = it * 1000L) }
        val first = AutoEdit.pick(items, AutoStyle.CINEMA, 60_000_000)
        val second = AutoEdit.pick(items, AutoStyle.CINEMA, 60_000_000, variant = 2)
        val third = AutoEdit.pick(items, AutoStyle.CINEMA, 60_000_000, variant = 3)
        assertTrue(first != second && second != third)
        listOf(second, third).forEach { v ->
            assertEquals(0, v.first()); assertEquals(199, v.last())     // sempre dal primo all'ultimo giorno
            assertEquals(v.sorted(), v)                                  // in ordine
            assertTrue(abs(v.size - first.size) <= 2)                    // durata simile
        }
        // Anche montaggio diverso: transizioni o punto dei video.
        val a = AutoEdit.build("x", sources(), null, AutoOptions(targetUs = 0, seed = 7))
        val b = AutoEdit.build("x", sources(), null, AutoOptions(targetUs = 0, seed = 7 + 7919 * 2, variant = 2))
        assertTrue(a.clips.map { it.transitionOut to it.trimStartUs } != b.clips.map { it.transitionOut to it.trimStartUs })
    }

    @Test fun recipeIsSavedWithTheProject() {
        val r = AutoRecipe(
            sources = listOf(AutoSource("content://media/1", true, 5_000_000, 1L)),
            options = AutoOptions(style = AutoStyle.ELEGANTE, targetUs = 30_000_000, title = "T", variant = 2),
            music = MusicTrack(uri = "content://m", name = "M", sourceDurationUs = 9_000_000, beatsUs = listOf(1, 2)),
            round = 3, imported = mapOf("content://media/1" to video(1)),
        )
        val p = Project(name = "auto", auto = r)
        val back = Project.JSON.decodeFromString(Project.serializer(), Project.JSON.encodeToString(Project.serializer(), p))
        assertEquals(r, back.auto)
        // I progetti vecchi (senza ricetta) si leggono ancora.
        assertNull(Project.JSON.decodeFromString(Project.serializer(), """{"name":"vecchio"}""").auto)
    }

    @Test fun formatFollowsTheMajorityAndOppositeOnesGetABlurredBackground() {
        fun v(i: Int, w: Int, h: Int) = video(i).copy(width = w, height = h)
        val srcs = listOf(v(0, 1080, 1920), v(1, 1080, 1920), v(2, 1920, 1080), photo(3).copy(width = 3000, height = 4000))
        val p = AutoEdit.build("Verticale", srcs, null, AutoOptions(targetUs = 0, smart = false))
        assertEquals(VideoFormat.VERTICAL, p.format)
        assertEquals(listOf(FrameMode.FILL, FrameMode.FILL, FrameMode.BLUR, FrameMode.FILL), p.clips.map { it.frameMode })
        // Formato scelto a mano: orizzontale.
        val h = AutoEdit.build("Orizzontale", srcs, null, AutoOptions(targetUs = 0, format = VideoFormat.HORIZONTAL, smart = false))
        assertEquals(listOf(FrameMode.BLUR, FrameMode.BLUR, FrameMode.FILL, FrameMode.BLUR), h.clips.map { it.frameMode })
        assertTrue(h.followsPresets())
        // Preimpostazioni diverse: orientamento opposto con bande nere.
        val q = h.copy(otherMode = FrameMode.FIT).applyFramePresets()
        assertEquals(FrameMode.FIT, q.clips[0].frameMode)
        assertEquals(VideoFormat.HORIZONTAL, VideoFormat.majority(listOf(v(0, 1920, 1080), v(1, 1080, 1920))))
        assertEquals(Orientation.SQUARE, orientationOf(1000, 1050))
    }

    /** "Come il brano": il video dura esattamente quanto la musica, con i tagli sempre a tempo. */
    @Test fun fitsTheWholeSong() {
        val beats = (1 until 400).map { it * 500_000L } // 120 BPM
        val song = 95_250_000L
        val m = MusicTrack(id = "m", uri = "content://m", name = "M", sourceDurationUs = song, lengthUs = song, beatsUs = beats.filter { it < song }, bpm = 120f)
        AutoStyle.entries.forEach { s ->
            // Poche scene rispetto al brano: si allungano (a battiti interi).
            val p = AutoEdit.build("Brano", sources(), m, AutoOptions(style = s, targetUs = song, intro = false))
            assertEquals("$s durata", song, p.durationUs)
            val track = p.music.single()
            assertEquals("$s musica intera", song, track.lengthUs)
            assertTrue("$s finale del brano", track.fadeOutUs <= 600_000)
            p.clipStartsUs().drop(1).forEach { t -> assertTrue("$s taglio a $t fuori battito", t % 500_000L == 0L) }
        }
        // Troppe scene: quelle in più si tolgono, e si finisce comunque col brano.
        val many = (0 until 80).map { if (it % 3 == 0) video(it) else photo(it) }
        val p = AutoEdit.build("Brano", many, m, AutoOptions(targetUs = song))
        assertEquals(song, p.durationUs)
        assertTrue(p.clips.size < 80)
        // Il numero di scene si stima con il ritmo: 95 s a 2 battiti (1 s) = circa 95 scene.
        val items = (0 until 500).map { AutoItem(isVideo = false, durationUs = 0, dateMs = it.toLong()) }
        val n = AutoEdit.pick(items, AutoStyle.ENERGICO, song, slotUs = AutoEdit.beatSlotUs(AutoStyle.ENERGICO, m)).size
        assertTrue("$n scene", n in 40..200)
    }

    @Test fun everyThemeIsInExactlyOneGroup() {
        val all = STYLE_GROUPS.flatMap { it.second }
        assertEquals(AutoStyle.entries.size, all.size)
        assertEquals(AutoStyle.entries.toSet(), all.toSet())
    }
}
