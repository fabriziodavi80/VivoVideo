package it.fabriziodavi.vivovideo.engine

import it.fabriziodavi.vivovideo.model.AutoEdit
import it.fabriziodavi.vivovideo.model.AutoOptions
import it.fabriziodavi.vivovideo.model.AutoStyle
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.ClipFx
import it.fabriziodavi.vivovideo.model.FxCategory
import it.fabriziodavi.vivovideo.model.FxType
import it.fabriziodavi.vivovideo.model.MusicTrack
import it.fabriziodavi.vivovideo.model.TextItem
import it.fabriziodavi.vivovideo.model.TextPreset
import it.fabriziodavi.vivovideo.model.TransitionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Tendenze 2026: temi d'azione senza elementi datati, nuovi effetti, transizioni e testi. */
class TrendTest {
    private val action = listOf(
        AutoStyle.ENERGICO, AutoStyle.VIRALE, AutoStyle.AZIONE, AutoStyle.ESTREMO, AutoStyle.NEON, AutoStyle.ESTATE,
        AutoStyle.BLOCKBUSTER, AutoStyle.VIAGGIO, AutoStyle.MARE, AutoStyle.MONTAGNA, AutoStyle.DIARIO,
    )

    /** Ciò che oggi fa "video di una volta": forme 3D, vortici, prismi, linee da fumetto, lampi bianchi pieni a ogni colpo. */
    private val datedTransitions = setOf(
        TransitionType.CUBE, TransitionType.SWIRL, TransitionType.SPIN, TransitionType.PRISM, TransitionType.STRIPES,
        TransitionType.PUNCH, TransitionType.BURST, TransitionType.TEAR, TransitionType.SPEEDZOOM, TransitionType.SMASH,
        TransitionType.LENS, TransitionType.IRIS, TransitionType.HEART, TransitionType.FLASH,
    )
    private val datedFx = setOf(
        FxType.SPEEDLINES, FxType.DUOPOP, FxType.SPLIT3, FxType.HOLO, FxType.NEON, FxType.SWEEP, FxType.RAYS,
        FxType.HIT, FxType.ZOOMBEAT, FxType.IMPACT, FxType.SPARKLE,
    )

    @Test fun actionThemesHaveNoDatedIngredients() {
        action.forEach { st ->
            assertTrue("$st transizioni datate", (st.transitions + st.impact).none { it in datedTransitions })
            assertTrue("$st intro datata", st.introCut !in datedTransitions)
            val fx = st.fx + st.accents + st.beatFx + st.dropFx + st.introFx
            assertTrue("$st effetti datati: ${fx.filter { it.type in datedFx }}", fx.none { it.type in datedFx })
            assertTrue("$st titolo non di tendenza", st.title.trend)
            assertTrue("$st senza stacchi netti", st.cutShare > 0f)
        }
    }

    @Test fun trendTextStylesAreClean() {
        val trend = TextPreset.entries.filter { it.trend }
        assertEquals(5, trend.size)
        trend.forEach { pr ->
            val t = pr.applyTo(TextItem(text = "Prova"))
            assertEquals("$pr sfumatura", null, t.gradientTo)
            assertEquals("$pr storto", 0f, t.rotation, 0f)
            assertEquals(pr, TextPreset.matching(t))
        }
    }

    @Test fun trendPanelCollectsTheNewEffects() {
        val trendy = FxType.entries.filter { it.trendy }
        assertTrue(listOf(FxType.HALATION, FxType.DIGICAM, FxType.GRAIN, FxType.MUTED, FxType.FLASHBEAT, FxType.BWBEAT, FxType.BOUNCE).all { it in trendy })
        assertTrue(FxType.FLASHBEAT.onBeats && FxType.BWBEAT.onBeats && FxType.BOUNCE.onBeats && !FxType.HALATION.onBeats)
        assertTrue(listOf(FxType.HALATION, FxType.DIGICAM, FxType.GRAIN, FxType.MUTED).all { it.category == FxCategory.TREND && it.wow2 })
        assertEquals(FxCategory.TREND, FxCategory.entries.first())
        assertTrue(TransitionType.entries.filter { it.trend }.containsAll(listOf(TransitionType.BLACKOUT, TransitionType.EXPOSE, TransitionType.CAMSHAKE, TransitionType.SNAPZOOM)))
        // Gli stacchi di tendenza sono d'impatto; zoom morbido e scivolata sono morbidi (per i temi calmi).
        assertTrue(TransitionType.entries.filter { it.trend && !it.gentleTrend }.all { it.punchy && !it.soft })
        assertTrue(TransitionType.entries.filter { it.gentleTrend }.all { it.trend && it.soft && !it.punchy })
    }

    @Test fun beatShapesOfTheNewEffects() {
        val w = WowFxEffect(listOf(ClipFx(FxType.BWBEAT), ClipFx(FxType.BOUNCE)), listOf(1_000_000, 2_000_000))
        // B/N a ritmo: scatto netto per un istante dopo il colpo, poi colore.
        assertEquals(1f, w.heldAfterBeat(1_050_000, 0.18f), 0f)
        assertEquals(0f, w.heldAfterBeat(1_300_000, 0.18f), 0f)
        assertEquals(0f, w.heldAfterBeat(900_000, 0.18f), 0f)
        // Rimbalzo: massimo sul colpo, poi scende un poco sotto zero e si assesta.
        assertEquals(1f, w.bounceAt(1_000_000), 1e-3f)
        val after = (1..40).map { w.bounceAt(1_000_000L + it * 10_000L) }
        assertTrue("non rimbalza: $after", after.any { it < -0.05f })
        assertEquals(0f, w.bounceAt(1_700_000), 0f)
    }

    @Test fun gentleTransitionsAreSubtle() {
        // Zoom morbido e scivolata: movimento piccolo e sfocatura leggera, niente onde, prismi o lampi.
        listOf(TransitionType.SOFTZOOM, TransitionType.DRIFT).forEach { t ->
            val fx = TransitionEffect(0, 2_000_000, null, 0, t, 550_000)
            val at = fx.params(1_999_000)
            assertTrue("$t zoom", at.zoom <= 1.13f)
            assertTrue("$t spostamento", abs(at.shift) <= 0.11f)
            assertEquals("$t onde", 0f, at.warp, 0f)
            assertEquals("$t prisma", 0f, at.prism, 0f)
            assertEquals("$t lampo", 0f, at.white, 0f)
            assertTrue("$t non nasconde il taglio", at.blur > 0f)
        }
    }

    @Test fun trendTransitionsAreBriefAndClean() {
        // Blackout: nero pieno solo vicino al taglio, niente a metà della transizione.
        val b = TransitionEffect(0, 2_000_000, null, 0, TransitionType.BLACKOUT, 500_000)
        assertEquals(1f, b.params(1_999_000).black, 0f)
        assertEquals(0f, b.params(1_600_000).black, 0f)
        // Flash esposizione: mai un bianco pieno.
        val e = TransitionEffect(0, 2_000_000, null, 0, TransitionType.EXPOSE, 500_000)
        assertTrue(e.params(1_999_000).white in 0.5f..0.8f)
        // Scossa camera: piccola (niente rotazioni da capogiro).
        val s = TransitionEffect(0, 2_000_000, null, 0, TransitionType.CAMSHAKE, 500_000)
        (0 until 20).forEach { k -> val p = s.params(1_999_000L - k * 40_000L); assertTrue(abs(p.rotation) <= 0.02f && abs(p.shift) <= 0.03f) }
    }

    private fun song(): MusicTrack {
        val beats = (1 until 200).map { it * 500_000L } // 120 bpm, drop a 30 s
        val energy = beats.map { if (it < 30_000_000) 0.2f else 0.95f }
        return MusicTrack(id = "m", uri = "content://m", name = "M", sourceDurationUs = 99_000_000, lengthUs = 99_000_000, beatsUs = beats, bpm = 120f, energy = energy)
    }

    @Test fun velocityEditCutsHardOnBeatsAndBlacksOutOnTheDrop() {
        val vids = (0 until 60).map { Clip(id = "v$it", uri = "content://v/$it", sourceDurationUs = 20_000_000, trimEndUs = 20_000_000, width = 1080, height = 1920, fps = 120f) }
        val p = AutoEdit.build("Velocity", vids, song(), AutoOptions(style = AutoStyle.ENERGICO, targetUs = 60_000_000, title = "Estate", subtitle = "Agosto 2026"))
        val cuts = p.clips.dropLast(1)
        val hard = cuts.count { it.transitionOut == null }
        assertTrue("stacchi netti $hard su ${cuts.size}", hard >= cuts.size / 4)
        // Sul drop: blackout, e la scena che parte lì ha flash e rimbalzo a ritmo (non più lo zoom e il lampo bianco).
        val starts = p.clipStartsUs()
        val d = starts.indexOfFirst { abs(it - 30_000_000) < 40_000 }
        assertTrue("nessun taglio sul drop: $starts", d > 0)
        assertEquals(TransitionType.BLACKOUT, p.clips[d - 1].transitionOut?.type)
        assertTrue(p.clips[d].fx.any { it.type == FxType.FLASHBEAT } && p.clips[d].fx.any { it.type == FxType.BOUNCE })
        assertTrue(p.clips.none { c -> c.fx.any { it.type == FxType.HIT || it.type == FxType.ZOOMBEAT } })
        // Dopo l'intro a raffica (8 lampi su mezzi battiti) tutto sul battito, e della durata giusta.
        starts.drop(9).forEach { t -> assertTrue("taglio a $t fuori battito", t % 500_000L < 20_000 || t % 500_000L > 480_000) }
        assertTrue(abs(p.durationUs - 60_000_000L) <= 20_000)
        // Titolo grande e pulito, sottotitolo minimal.
        val title = p.texts.first { it.text == "Estate" }
        assertEquals(TextPreset.TITOLONE, TextPreset.matching(title))
        val sub = p.texts.first { it.text == "Agosto 2026" }
        assertTrue(sub.upper && sub.size < 0.04f && sub.gradientTo == null)
    }

    @Test fun grungeIsRawButNotCheesy() {
        val st = AutoStyle.GRUNGE
        assertTrue((st.transitions + st.impact).none { it in datedTransitions || it == TransitionType.GLITCH || it == TransitionType.BURN })
        assertTrue((st.fx + st.accents + st.beatFx + st.dropFx).none { it.type in datedFx || it.type == FxType.VHS })
        assertTrue(st.cutShare >= 0.5f && st.grade.saturation < 0f)
        val vids = (0 until 30).map { Clip(id = "v$it", uri = "content://v/$it", sourceDurationUs = 20_000_000, trimEndUs = 20_000_000, width = 1080, height = 1920, fps = 60f) }
        val p = AutoEdit.build("Grunge", vids, song(), AutoOptions(style = st, targetUs = 40_000_000, title = "Notte"))
        assertTrue(p.clips.all { c -> c.fx.any { it.type == FxType.GRAIN } })
        assertEquals(TextPreset.STAMPA, TextPreset.matching(p.texts.first { it.text == "Notte" }))
    }

    @Test fun retroThemeKeepsItsVhsHits() {
        val vids = (0 until 30).map { Clip(id = "v$it", uri = "content://v/$it", sourceDurationUs = 20_000_000, trimEndUs = 20_000_000, width = 1920, height = 1080, fps = 30f) }
        val p = AutoEdit.build("Anni 90", vids, song(), AutoOptions(style = AutoStyle.ANNI90, targetUs = 45_000_000))
        assertTrue(p.clips.take(7).all { it.transitionOut?.type == TransitionType.GLITCH })
        assertTrue(p.clips.dropLast(1).all { it.transitionOut != null })
    }
}
