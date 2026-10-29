package it.fabriziodavi.vivovideo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Torna ai video originali: le clip come importate, senza nessuna modifica. */
class OriginalsTest {
    private val a = Clip(id = "a", uri = "content://v/a", sourceDurationUs = 10_000_000, width = 1920, height = 1080, fileName = "a.mp4")
    private val b = Clip(id = "b", uri = "content://v/b", sourceDurationUs = 8_000_000, width = 1080, height = 1920)
    private val f = Clip(id = "f", uri = "file:///f.jpg", sourceDurationUs = Clip.PHOTO_MAX_US, trimEndUs = Clip.PHOTO_DEFAULT_US, isPhoto = true, width = 4000, height = 3000)

    /** Progetto molto modificato: tagli, velocità, effetti, transizioni, testi, musica, livelli, colore, ordine cambiato. */
    private fun edited(originals: List<Clip>) = Project(
        name = "gita",
        clips = listOf(
            b.copy(id = "b1", trimStartUs = 1_000_000, trimEndUs = 3_000_000, speed = ClipSpeed.SLOW, fx = listOf(ClipFx(FxType.VHS))),
            a.copy(id = "a1", trimEndUs = 2_000_000, transitionOut = TransitionSpec(TransitionType.WIPE), frame = Transform(scale = 1.3f)),
            a.copy(id = "a2", trimStartUs = 5_000_000, grade = Grade(exposure = 0.5f)),
            f.copy(trimEndUs = 9_000_000),
        ),
        texts = listOf(TextItem(text = "Ciao")),
        music = listOf(MusicTrack(uri = "content://m/1", name = "brano", sourceDurationUs = 60_000_000)),
        layers = listOf(Layer(uri = "file:///logo.png", width = 10, height = 10)),
        color = ColorSettings(look = "WARM"),
        duck = true,
        format = VideoFormat.VERTICAL,
        originals = originals,
    )

    @Test fun backToTheImportedMediaWithoutChanges() {
        val p = edited(listOf(a, b, f))
        val r = p.restoreOriginals()!!
        assertEquals(listOf(a.uri, b.uri, f.uri), r.clips.map { it.uri })
        // Interi, senza velocità, effetti, transizioni, colore, inquadratura.
        assertEquals(listOf(0L to 10_000_000L, 0L to 8_000_000L, 0L to Clip.PHOTO_DEFAULT_US), r.clips.map { it.trimStartUs to it.trimEndUs })
        assertTrue(r.clips.all { it.speed == ClipSpeed.NORMAL && it.fx.isEmpty() && it.transitionOut == null && it.grade == Grade() && it.frame == Transform() })
        assertTrue(r.texts.isEmpty() && r.music.isEmpty() && r.layers.isEmpty())
        assertEquals(ColorSettings(), r.color)
        assertTrue(!r.duck)
        // Stesso progetto (nome, formato), clip nuove (identificativi nuovi), originali ricordati.
        assertEquals(p.id, r.id); assertEquals("gita", r.name); assertEquals(VideoFormat.VERTICAL, r.format)
        assertTrue(r.clips.none { it.id in setOf("a", "b", "f") })
        assertEquals(listOf(a, b, f), r.originals)
        // Le preimpostazioni del formato valgono anche qui (riempi / sfondo sfocato secondo l'orientamento).
        assertEquals(p.copy(clips = r.clips).applyFramePresets().clips.map { it.fill to it.blurBg }, r.clips.map { it.fill to it.blurBg })
    }

    @Test fun olderProjectsRebuildTheListFromTheirClips() {
        // Senza originali ricordati: ogni file una volta, nell'ordine in cui compare; il contrario torna al video vero;
        // i fermi immagine (fatti dall'app) e i pezzi preparati non contano.
        val rev = a.copy(id = "r", uri = "file:///contrario.mp4", sourceDurationUs = 2_000_000,
            reversed = ReversedSource(uri = "content://v/c", sourceDurationUs = 7_000_000, trimStartUs = 0, trimEndUs = 2_000_000, width = 1920, height = 1080))
        val freeze = f.copy(id = "z", uri = "file:///fermo.jpg", freeze = true)
        val p = edited(emptyList()).let { it.copy(clips = it.clips + rev + freeze) }
        val media = p.originalMedia()
        assertEquals(listOf(b.uri, a.uri, f.uri, "content://v/c"), media.map { it.uri })
        assertEquals(listOf(8_000_000L, 10_000_000L, Clip.PHOTO_DEFAULT_US, 7_000_000L), media.map { it.trimmedUs })
        assertEquals("a.mp4", media[1].fileName)
        assertEquals(4, p.restoreOriginals()!!.clips.size)
        assertNull(Project(name = "vuoto").restoreOriginals())
    }

    @Test fun templatesDoNotCarryTheOldOriginals() {
        val t = Templates.fromProject(edited(listOf(a, b, f)), "modello")
        assertTrue(t.project.originals.isEmpty())
        val c = Clip(uri = "content://v/nuovo", sourceDurationUs = 20_000_000, width = 1920, height = 1080)
        val made = Templates.apply(t, listOf(c))
        assertEquals(listOf(c), made.originals)
    }
}
