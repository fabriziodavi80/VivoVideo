package it.fabriziodavi.vivovideo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Rimescola clip: ordine nuovo, tagli a ritmo che restano sul battito, niente video uguali vicini. */
class ShuffleTest {
    private fun video(id: String, uri: String = "content://v/$id", src: Long = 10_000_000, s: Long = 0, e: Long = src) =
        Clip(id = id, uri = uri, sourceDurationUs = src, trimStartUs = s, trimEndUs = e, width = 1920, height = 1080)

    private fun photo(id: String, len: Long = 2_000_000) =
        Clip(id = id, uri = "file:///p/$id.jpg", sourceDurationUs = Clip.PHOTO_MAX_US, trimEndUs = len, isPhoto = true)

    /** Brano a 120 BPM: un battito ogni 0,5 s per un minuto. */
    private val song = MusicTrack(uri = "content://m/1", name = "brano", sourceDurationUs = 60_000_000,
        beatsUs = (0 until 120).map { it * 500_000L }, bpm = 120f)

    @Test fun freeClipsGetANewOrderAndKeepTheirLength() {
        val clips = listOf(video("a", e = 3_000_000), video("b", e = 2_000_000), photo("c"), video("d", e = 4_000_000), photo("e", 1_500_000))
        val p = Project(name = "x", clips = clips)
        val r = Shuffle.apply(p, null, seed = 1)!!
        assertFalse(r.onBeat)
        assertNotEquals(clips.map { it.id }, r.project.clips.map { it.id })
        assertEquals(clips.map { it.id }.sorted(), r.project.clips.map { it.id }.sorted())
        // Senza ritmo ogni clip resta com'era (stesso taglio): cambia solo il posto. La durata totale non cambia.
        r.project.clips.forEach { c -> assertEquals(clips.first { it.id == c.id }.trimEndUs, c.trimEndUs) }
        assertEquals(p.durationUs, r.project.durationUs)
        // Un altro seme, un altro ordine.
        val orders = (1L..8L).map { Shuffle.apply(p, null, it)!!.project.clips.map { c -> c.id } }.toSet()
        assertTrue(orders.size >= 3)
    }

    @Test fun beatCutClipsShuffleTheContentAndKeepEveryCutOnTheBeat() {
        // Tre video tagliati a ritmo in pezzi da 1 s, 0,5 s e 1,5 s.
        val lens = listOf(1_000_000L, 500_000L, 1_500_000L, 1_000_000L, 500_000L, 1_000_000L, 1_500_000L, 1_000_000L, 500_000L)
        val uris = listOf("A", "B", "C")
        val clips = lens.mapIndexed { i, l -> video("c$i", uri = "content://v/${uris[i % 3]}", s = 1_000_000L * i, e = 1_000_000L * i + l) }
        val p = Project(name = "x", clips = clips, music = listOf(song))
        assertTrue(Shuffle.onBeat(p, clips.indices.toList()))
        val r = Shuffle.apply(p, null, seed = 7)!!
        assertTrue(r.onBeat)
        // Le durate restano al loro posto: i tagli sono gli stessi, tutti su un battito.
        assertEquals(lens, r.project.clips.map { it.outputUs })
        val beats = p.beatTimes()
        r.project.clipStartsUs().drop(1).forEach { c -> assertTrue("taglio a $c", beats.any { abs(it - c) <= 1_000 }) }
        assertEquals(p.durationUs, r.project.durationUs)
        // Il contenuto si è mosso e due pezzi dello stesso video non sono vicini.
        assertTrue(r.moved > 0)
        assertEquals(0, r.sameNeighbours)
        (1 until r.project.clips.size).forEach { j -> assertNotEquals(r.project.clips[j - 1].uri, r.project.clips[j].uri) }
    }

    @Test fun onlyTheChosenClipsMoveAndTransitionsStayOnTheCuts() {
        val flash = TransitionSpec(TransitionType.FLASH, 300_000)
        val clips = listOf(video("a", e = 2_000_000), video("b", e = 2_000_000).copy(transitionOut = flash), video("c", e = 2_000_000), video("d", e = 2_000_000), video("e", e = 2_000_000))
        val p = Project(name = "x", clips = clips)
        val r = Shuffle.apply(p, setOf("b", "c", "d"), seed = 3)!!
        val q = r.project.clips
        assertEquals("a", q[0].id); assertEquals("e", q[4].id)
        assertEquals(setOf("b", "c", "d"), q.subList(1, 4).map { it.id }.toSet())
        assertNotEquals(listOf("b", "c", "d"), q.subList(1, 4).map { it.id })
        // Il lampo resta fra il secondo e il terzo posto, qualunque clip ci sia ora.
        assertEquals(flash, q[1].transitionOut)
        assertTrue(q.filterIndexed { i, _ -> i != 1 }.all { it.transitionOut == null })
    }

    @Test fun contentStretchedToALongerSlotStaysInsideItsFile() {
        // Pezzo di 0,5 s alla fine del suo file (9,5–10 s) messo in un posto da 1,5 s: l'inizio si anticipa.
        val c = video("x", s = 9_500_000, e = 10_000_000)
        val f = Shuffle.fit(c, 1_500_000)
        assertEquals(1_500_000L, f.outputUs)
        assertEquals(10_000_000L, f.trimEndUs)
        assertEquals(8_500_000L, f.trimStartUs)
        // Curva di velocità: la durata nel video è comunque quella del posto.
        val ramp = video("r", s = 1_000_000, e = 3_000_000).copy(speed = ClipSpeed.RAMP, speedPoints = SpeedPreset.VALLEY.points)
        assertEquals(1_000_000.0, Shuffle.fit(ramp, 1_000_000).outputUs.toDouble(), 5_000.0)
        // Una foto si allunga e basta.
        assertEquals(3_000_000L, Shuffle.fit(photo("p"), 3_000_000).outputUs)
    }

    @Test fun tooFewClipsDoNothing() {
        val p = Project(name = "x", clips = listOf(video("a"), video("b")))
        assertNull(Shuffle.apply(p, setOf("a"), seed = 1))
        assertNull(Shuffle.apply(Project(name = "x", clips = listOf(video("a"))), null, seed = 1))
    }

    @Test fun manyBeatPiecesAreFast() {
        // Come il collaudo "Progetto lungo": 368 pezzi a ritmo di 6 video.
        val clips = (0 until 368).map { i -> video("c$i", uri = "content://v/${i % 6}", src = 200_000_000, s = 500_000L * i, e = 500_000L * i + 500_000) }
        val song2 = song.copy(sourceDurationUs = 400_000_000, lengthUs = 400_000_000, beatsUs = (0 until 800).map { it * 500_000L })
        val p = Project(name = "x", clips = clips, music = listOf(song2))
        val t0 = System.nanoTime()
        val r = Shuffle.apply(p, null, seed = 5)!!
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("$ms ms", ms < 5_000)
        assertTrue(r.onBeat)
        assertEquals(0, r.sameNeighbours)
        assertEquals(p.durationUs, r.project.durationUs)
    }
}
