package it.fabriziodavi.vivovideo.engine

import it.fabriziodavi.vivovideo.model.AutoEdit
import it.fabriziodavi.vivovideo.model.AutoOptions
import it.fabriziodavi.vivovideo.model.AutoStyle
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.ClipFx
import it.fabriziodavi.vivovideo.model.ClipSpeed
import it.fabriziodavi.vivovideo.model.FxType
import it.fabriziodavi.vivovideo.model.MusicTrack
import it.fabriziodavi.vivovideo.model.TextAnim
import it.fabriziodavi.vivovideo.model.TextPreset
import it.fabriziodavi.vivovideo.model.TextItem
import it.fabriziodavi.vivovideo.model.TransitionType
import it.fabriziodavi.vivovideo.model.VideoFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/** Pacchetto premium: transizioni, effetti, testi e regia automatica che segue l'energia del brano. */
class WowTest {
    @Test fun premiumTransitionsDoSomethingAtTheCut() {
        TransitionType.entries.filter { it.premium }.forEach { t ->
            val fx = TransitionEffect(0, 2_000_000, null, 0, t, 500_000)
            val at = fx.params(1_999_000)
            assertTrue("$t non fa nulla sul taglio", at != TransitionEffect.Params(seed = at.seed))
            val calm = fx.params(500_000)
            assertEquals("$t lontano dal taglio", TransitionEffect.Params(seed = calm.seed), calm)
        }
        assertTrue(TransitionType.PUNCH.punchy && !TransitionType.PUNCH.soft && TransitionType.LUMA.soft)
    }

    @Test fun beatEffectsPeakOnTheBeat() {
        val w = WowFxEffect(listOf(ClipFx(FxType.ZOOMBEAT)), listOf(1_000_000, 1_500_000))
        assertEquals(1f, w.beatAt(1_000_000, 0.14f), 1e-3f)
        assertTrue(w.beatAt(1_300_000, 0.14f) < 0.15f)
        assertEquals(0f, w.beatAt(500_000, 0.14f), 0f)
        assertTrue(WowFxEffect.needed(listOf(ClipFx(FxType.DREAMY))))
        assertTrue(!WowFxEffect.needed(listOf(ClipFx(FxType.BW))))
        assertTrue(FxType.ZOOMBEAT.onBeats && FxType.RGBBEAT.onBeats && !FxType.DREAMY.onBeats)
    }

    @Test fun newLetterAnimationsStartHiddenAndEndInPlace() {
        listOf(TextAnim.BOUNCE, TextAnim.FLIP, TextAnim.SPIN, TextAnim.SCATTER).forEach { a ->
            val start = letterFx(a, true, 0f, 0, 5)
            val end = letterFx(a, true, 1f, 4, 5)
            assertTrue("$a parte invisibile o piccola", start.alpha < 0.05f || start.scale * start.sx < 0.05f)
            assertEquals("$a alla fine è al suo posto", 1f, end.alpha, 0.02f)
            assertEquals(1f, end.scale * end.sx, 0.03f)
            assertEquals(0f, end.dx, 0.01f); assertEquals(0f, end.dy, 0.01f)
            assertTrue("$a in uscita sparisce", letterFx(a, false, 1f, 0, 5).alpha < 0.05f)
        }
        // Esplosione: le lettere vanno in direzioni diverse.
        val a = letterFx(TextAnim.SCATTER, false, 0.8f, 0, 5); val b = letterFx(TextAnim.SCATTER, false, 0.8f, 1, 5)
        assertTrue(abs(a.dx - b.dx) > 0.05f || abs(a.dy - b.dy) > 0.05f)
    }

    @Test fun neonFlickersThenStaysOnAndBlurFocuses() {
        val early = (0..890 step 10).map { flicker(it * 1000L) }
        assertTrue(early.any { it < 0.2f } && early.any { it > 0.9f })
        assertEquals(1f, flicker(2_000_000), 0f)
        val t = TextItem(text = "Ciao", durationUs = 3_000_000, animIn = TextAnim.BLUR, animOut = TextAnim.BLUR)
        assertEquals(1f, blurAmount(t, 0), 1e-3f)
        assertEquals(0f, blurAmount(t, 1_500_000), 0f)
        assertTrue(blurAmount(t, 2_990_000) > 0.9f)
        TextPreset.entries.forEach { p -> assertEquals(p, TextPreset.matching(p.applyTo(TextItem()))) }
    }

    @Test fun energyFollowsTheLoudness() {
        val rate = 8000
        // 8 s piano, 8 s forte.
        val mono = FloatArray(rate * 16) { i -> (if (i < rate * 8) 0.05f else 0.6f) * sin(i * 0.3f) }
        val beats = (0 until 32).map { it * 500_000L }
        val e = BeatDetector.energy(mono, rate, beats)
        assertEquals(32, e.size)
        assertTrue(e.take(14).all { it < 0.2f }); assertTrue(e.drop(18).all { it > 0.8f })
    }

    private fun music(): MusicTrack {
        val beats = (1 until 200).map { it * 500_000L }
        // Strofa calma per 30 s, poi il drop.
        val energy = beats.map { if (it < 30_000_000) 0.2f else 0.95f }
        return MusicTrack(id = "m", uri = "content://m", name = "M", sourceDurationUs = 99_000_000, lengthUs = 99_000_000, beatsUs = beats, bpm = 120f, energy = energy)
    }

    @Test fun autoEditFollowsTheSongEnergy() {
        val vids = (0 until 60).map { Clip(id = "v$it", uri = "content://v/$it", sourceDurationUs = 20_000_000, trimEndUs = 20_000_000, width = 1080, height = 1920, fps = 120f) }
        val p = AutoEdit.build("Drop", vids, music(), AutoOptions(style = AutoStyle.ENERGICO, targetUs = 60_000_000, intro = false))
        val starts = p.clipStartsUs()
        val calm = p.clips.filterIndexed { i, _ -> starts[i] + p.clips[i].outputUs <= 30_000_000 }
        val hot = p.clips.filterIndexed { i, _ -> starts[i] >= 31_000_000 && i < p.clips.lastIndex }
        // Dove il brano spinge le scene sono più corte.
        assertTrue("calme ${calm.map { it.outputUs }} cariche ${hot.map { it.outputUs }}", calm.map { it.outputUs }.average() > 1.5 * hot.map { it.outputUs }.average())
        // Un taglio cade esattamente sul drop (30 s), con una transizione d'impatto.
        val i = starts.indexOfFirst { abs(it - 30_000_000) < 40_000 }
        assertTrue("nessun taglio sul drop: $starts", i > 0)
        assertEquals(AutoStyle.ENERGICO.impact.first(), p.clips[i - 1].transitionOut?.type)
        // Effetti a ritmo nelle parti cariche, e accelerazioni a tempo nei video.
        assertTrue(hot.all { c -> c.fx.any { it.type == FxType.ZOOMBEAT } })
        assertTrue(hot.any { it.speed == ClipSpeed.RAMP })
        // Tutto a tempo e della durata giusta.
        assertTrue("durata ${p.durationUs}", abs(p.durationUs - 60_000_000L) <= 20_000)
        starts.drop(1).forEach { t -> assertTrue("taglio a $t fuori battito", t % 500_000L < 20_000 || t % 500_000L > 480_000) }
    }

    @Test fun calmStylesUseSlowMotionAndOppositePhotosPan() {
        val vids = (0 until 20).map { Clip(id = "v$it", uri = "content://v/$it", sourceDurationUs = 20_000_000, trimEndUs = 20_000_000, width = 1080, height = 1920, fps = 120f) }
        val p = AutoEdit.build("Lento", vids, null, AutoOptions(style = AutoStyle.CINEMA, targetUs = 0))
        assertTrue(p.clips.any { it.speed == ClipSpeed.SLOW })
        p.clips.filter { it.speed == ClipSpeed.SLOW }.forEach { assertTrue(abs(it.trimmedUs * 2 - it.outputUs) <= 10_000) }
        val photo = Clip(id = "p", uri = "file:///p.jpg", sourceDurationUs = Clip.PHOTO_MAX_US, trimEndUs = 3_000_000, isPhoto = true, width = 4000, height = 3000)
        val q = AutoEdit.build("Foto", vids.take(4) + photo, null, AutoOptions(style = AutoStyle.SOGNO, targetUs = 0, format = VideoFormat.VERTICAL))
        val ph = q.clips.first { it.isPhoto }
        assertNotNull(ph.reframe); assertTrue(ph.fill)
        assertTrue(abs(ph.reframe!!.xs.first() - ph.reframe!!.xs.last()) > 0.4f)
    }

    @Test fun trailerStructureIntroHeroEnding() {
        val vids = (0 until 40).map { Clip(id = "v$it", uri = "content://v/$it", sourceDurationUs = 20_000_000, trimEndUs = 20_000_000, width = 1920, height = 1080, fps = 120f) }
        val p = AutoEdit.build("Trailer", vids, music(), AutoOptions(style = AutoStyle.BLOCKBUSTER, targetUs = 60_000_000, title = "Estate", subtitle = "Agosto 2026"))
        // Intro: 8 lampi su mezzi battiti (0,25 s), poi il titolo sul colpo.
        val intro = p.clips.take(8)
        assertTrue(intro.drop(1).all { abs(it.outputUs - 250_000) < 5_000 })
        assertTrue(intro.dropLast(1).all { it.transitionOut?.type == TransitionType.FLASH })
        val introEnd = intro.sumOf { it.outputUs }
        val title = p.texts.first { it.text == "Estate" }
        assertEquals(introEnd, title.startUs)
        // Look colore del tema su tutte le scene, e bande cinema.
        assertTrue(p.clips.all { it.grade == AutoStyle.BLOCKBUSTER.grade })
        assertTrue(p.clips.drop(8).all { c -> c.fx.any { it.type == FxType.LETTERBOX } })
        // Momento eroe sul drop (30 s): rallenty al centro.
        val starts = p.clipStartsUs()
        val d = starts.indexOfFirst { abs(it - 30_000_000) < 40_000 }
        assertTrue("nessuna scena sul drop", d > 0)
        assertEquals(ClipSpeed.RAMP, p.clips[d].speed)
        // Finale: l'ultima rallenta, e c'è la scritta di chiusura.
        assertEquals(ClipSpeed.RAMP, p.clips.last().speed)
        assertTrue(p.texts.any { it.text == "Agosto 2026" && it.startUs > p.durationUs - 3_500_000 })
        assertTrue("durata ${p.durationUs}", abs(p.durationUs - 60_000_000) <= 25_000)
    }

    @Test fun everyThemeBuilds() {
        val srcs = (0 until 30).map { if (it % 3 == 0) Clip(id = "p$it", uri = "file:///p$it.jpg", sourceDurationUs = Clip.PHOTO_MAX_US, trimEndUs = 3_000_000, isPhoto = true, width = 4000, height = 3000)
            else Clip(id = "v$it", uri = "content://v/$it", sourceDurationUs = 15_000_000, trimEndUs = 15_000_000, width = 1920, height = 1080, fps = if (it % 2 == 0) 120f else 30f) }
        AutoStyle.entries.forEach { st ->
            val p = AutoEdit.build("T", srcs, music(), AutoOptions(style = st, targetUs = 45_000_000, title = "Titolo"))
            assertTrue("$st durata ${p.durationUs}", abs(p.durationUs - 45_000_000) <= 30_000)
            assertTrue("$st", p.clips.isNotEmpty() && p.texts.isNotEmpty())
            p.clips.dropLast(1).forEach { c -> assertNotNull("$st transizione", c.transitionOut) }
        }
    }

    @Test fun vintageThemesStampTheRealDateAndRomanticPhotosBreathe() {
        val photos = (0 until 12).map { Clip(id = "p$it", uri = "file:///p$it.jpg", sourceDurationUs = Clip.PHOTO_MAX_US, trimEndUs = 3_000_000, isPhoto = true, width = 4000, height = 3000) }
        val day = 1_754_000_000_000L // agosto 2025
        val dates = photos.associate { it.uri to day }
        val p = AutoEdit.build("Vacanze", photos, music(), AutoOptions(style = AutoStyle.SUPER8, targetUs = 30_000_000), dates = dates)
        val stamps = p.texts.filter { it.text.endsWith("2025") }
        assertEquals(p.clips.size, stamps.size)
        assertTrue(stamps.all { it.font == it.font && it.color == 0xFFFF9F1C.toInt() && it.text.contains("AGO") })
        assertTrue(p.clips.all { c -> c.fx.any { it.type == FxType.SUPER8 } && c.fx.any { it.type == FxType.FILMFRAME } })
        // Romantico: anche nella parte calma le foto fanno un piccolo respiro sulle battute.
        val r = AutoEdit.build("Noi", photos, music(), AutoOptions(style = AutoStyle.ROMANTICO, targetUs = 25_000_000))
        val calm = r.clips.first()
        assertTrue(calm.keyframes.size >= 3)
        assertTrue(calm.keyframes.maxOf { it.value.scale } < 1.2f)
        assertTrue(r.clips.any { c -> c.fx.any { it.type == FxType.PROMIST } })
    }
}
