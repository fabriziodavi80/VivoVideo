package it.fabriziodavi.vivovideo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Temi calmi con brani veloci, verso di foto e video, raffiche e momenti migliori. */
class CalmAutoTest {
    private fun fastSong(): MusicTrack {
        val beats = (1 until 250).map { it * 400_000L } // 150 bpm
        return MusicTrack(id = "m", uri = "content://m", name = "M", sourceDurationUs = 100_000_000, lengthUs = 100_000_000,
            beatsUs = beats, bpm = 150f, energy = beats.map { 0.9f })
    }

    @Test fun calmThemesKeepLongScenesOnFastSongs() {
        val vids = (0 until 40).map { Clip(id = "v$it", uri = "content://v/$it", sourceDurationUs = 20_000_000, trimEndUs = 20_000_000, width = 1920, height = 1080, fps = 30f) }
        listOf(AutoStyle.NATURA, AutoStyle.ROMANTICO, AutoStyle.MATRIMONIO).forEach { st ->
            val p = AutoEdit.build("Calmo", vids, fastSong(), AutoOptions(style = st, targetUs = 60_000_000, intro = false))
            val short = p.clips.dropLast(1).filter { it.outputUs < st.minSceneUs - 50_000 }
            assertTrue("$st scene corte: ${short.map { it.outputUs }}", short.isEmpty())
            // Sempre sul battito.
            p.clipStartsUs().drop(1).forEach { t -> assertTrue("$st taglio a $t fuori battito", t % 400_000L < 20_000 || t % 400_000L > 380_000) }
            assertTrue(abs(p.durationUs - 60_000_000L) <= 20_000)
        }
        assertTrue(AutoEdit.beatSlotUs(AutoStyle.NATURA, fastSong())!! >= AutoStyle.NATURA.minSceneUs)
    }

    @Test fun ricordiHasNothingThatFlickers() {
        val r = AutoStyle.RICORDI
        val flicker = setOf(FxType.STROBE, FxType.PULSE, FxType.FLASHBEAT, FxType.BWBEAT, FxType.PROJECTOR, FxType.SUPER8, FxType.SHAKE)
        assertFalse((r.fx + r.accents + r.beatFx).any { it.type in flicker })
        assertTrue(r.fx.first { it.type == FxType.FILM35 }.amount <= 0.5f)
    }

    @Test fun ricordiAndCinemaHaveNoFlameOrFlashTransitions() {
        // "Pellicola bruciata" sembra una fiammata; luce e flash bianchi stonano nei temi calmi.
        val loud = setOf(TransitionType.BURN, TransitionType.FLASH, TransitionType.LEAK, TransitionType.LUMA)
        listOf(AutoStyle.RICORDI, AutoStyle.CINEMA).forEach { st ->
            assertFalse(st.name, st.transitions.any { it in loud })
        }
        assertFalse(AutoStyle.RICORDI.impact.any { it in loud })
    }

    @Test fun naturaHasNoFallingLeaves() {
        val all = AutoStyle.NATURA.fx + AutoStyle.NATURA.accents + AutoStyle.NATURA.beatFx
        assertFalse(all.any { it.type == FxType.LEAVES })
    }

    @Test fun naturaHasNoFlashesOrPulsingGlow() {
        val st = AutoStyle.NATURA
        val flashy = setOf(TransitionType.LUMA, TransitionType.LEAK, TransitionType.BOKEHFADE, TransitionType.FLASH,
            TransitionType.BURST, TransitionType.PUNCH, TransitionType.SMASH, TransitionType.TEAR, TransitionType.SPEEDZOOM)
        assertTrue((st.transitions + st.impact).none { it in flashy })
        val glow = setOf(FxType.DREAMY, FxType.RAYS, FxType.GLOW, FxType.GLOWBEAT, FxType.FLARE, FxType.SPARKLE, FxType.ZOOMBEAT, FxType.HIT)
        assertTrue((st.fx + st.accents + st.beatFx).none { it.type in glow })
        assertTrue(st.beatFx.isEmpty())
        assertFalse(st.gentlePops)
        assertTrue(st.impact.isNotEmpty()) // serve per i drop
        // Niente onde (Liquido) né passaggi vistosi: dissolvenze e passaggi morbidi di tendenza.
        val all = st.transitions + st.impact
        assertTrue(all.none { it == TransitionType.LIQUID || it == TransitionType.SWIRL || it == TransitionType.CUBE })
        assertTrue(all.any { it.gentleTrend })
        assertTrue(listOf(AutoStyle.SOGNO, AutoStyle.MARE).none { s -> (s.transitions + s.impact).contains(TransitionType.LIQUID) })
    }

    /** Brano veloce con un ritornello che attacca (drop) a metà. */
    private fun songWithDrop(): MusicTrack {
        val beats = (1 until 250).map { it * 400_000L }
        return MusicTrack(id = "d", uri = "content://d", name = "D", sourceDurationUs = 100_000_000, lengthUs = 100_000_000,
            beatsUs = beats, bpm = 150f, energy = beats.mapIndexed { i, _ -> if (i in 60..120) 0.95f else 0.2f })
    }

    @Test fun calmThemesNeverPunchOnDropsOrSnapPhotos() {
        val photos = (0 until 30).map { Clip(id = "p$it", uri = "file:///p$it.jpg", sourceDurationUs = Clip.PHOTO_MAX_US, trimEndUs = 3_000_000, isPhoto = true, width = 4000, height = 3000) }
        val vids = (0 until 10).map { Clip(id = "v$it", uri = "content://v/$it", sourceDurationUs = 20_000_000, trimEndUs = 20_000_000, width = 1920, height = 1080, fps = 30f) }
        val beatTypes = setOf(FxType.HIT, FxType.ZOOMBEAT, FxType.GLOWBEAT, FxType.SHAKEBEAT, FxType.RGBBEAT, FxType.IMPACT)
        val p = AutoEdit.build("Natura", photos + vids, songWithDrop(), AutoOptions(style = AutoStyle.NATURA, targetUs = 60_000_000, intro = false))
        assertTrue(p.clips.size > 5)
        p.clips.forEachIndexed { i, c ->
            assertTrue("effetto a ritmo in ${c.id}: ${c.fx}", c.fx.none { it.type in beatTypes })
            // Foto: un solo movimento lento dall'inizio alla fine (due keyframe), nessuno scatto sui battiti.
            if (c.isPhoto) assertTrue("scatti in ${c.id}: ${c.keyframes.size}", c.keyframes.size <= 2)
            // Passaggi lenti anche dove il brano spinge (le ultime due scene possono essere accorciate).
            if (i < p.clips.size - 2) c.transitionOut?.let { assertTrue("passaggio corto ${it.durationUs}", it.durationUs >= 1_000_000) }
        }
        // Un tema energico invece i colpi sul drop li tiene.
        val e = AutoEdit.build("Estremo", photos + vids, songWithDrop(), AutoOptions(style = AutoStyle.ESTREMO, targetUs = 60_000_000, intro = false))
        assertTrue(e.clips.any { c -> c.fx.any { it.type in beatTypes } })
    }

    private fun src(i: Int, w: Int, h: Int, date: Long = i * 60_000L, video: Boolean = false) =
        AutoSource("content://s/$i", video, if (video) 10_000_000 else 0, date, w, h)

    @Test fun sameOrientationIsPreferred() {
        val items = (0 until 20).map { src(it, 4000, 3000) } + (20 until 26).map { src(it, 3000, 4000) }
        val auto = AutoEdit.preferOrientation(items, VideoFormat.AUTO, 15)
        assertEquals(20, auto.size); assertTrue(auto.all { it.width > it.height })
        // Verticale scelto ma poche verticali: non basterebbero, si tiene tutto.
        assertEquals(26, AutoEdit.preferOrientation(items, VideoFormat.VERTICAL, 15).size)
        // Misure ignote: restano.
        val unknown = items + AutoSource("content://x", false, 0, 0)
        assertTrue(AutoEdit.preferOrientation(unknown, VideoFormat.HORIZONTAL, 10).any { it.width == 0 })
    }

    @Test fun burstsKeepTheBestPhoto() {
        val burst = (0 until 5).map { src(it, 4000, 3000, date = 1_000_000L + it * 500) }
        val lone = src(9, 4000, 3000, date = 2_000_000L)
        val video = src(10, 1920, 1080, date = 1_000_700L, video = true)
        val out = AutoEdit.dedupeBursts(burst + lone + video, { if (it.uri.endsWith("/3")) 0.9f else 0.2f })
        assertEquals(listOf("content://s/3", "content://s/10", "content://s/9").sorted(), out.map { it.uri }.sorted())
        // Senza punteggi: quella di mezzo.
        assertEquals(listOf("content://s/2"), AutoEdit.dedupeBursts(burst, { null }).map { it.uri })
    }

    private fun s(t: Long, motion: Float = 0.2f) = MomentSample(t, sharp = 300f, luma = 0.47f, motion = motion)

    @Test fun bestMomentAvoidsEdgesAndShakyParts() {
        // Tutto uguale: non si parte dal primissimo istante né si finisce in fondo.
        val flat = MomentScan("v", 10_000_000, (0..20).map { s(it * 500_000L) })
        val st = Moments.bestStarts(flat, 3_000_000, 1).first()
        assertTrue("inizio $st", st >= Moments.EDGE_US && st + 3_000_000 <= 10_000_000 - Moments.EDGE_US)
        // Nei temi calmi si evita la parte che sbanda (primi 5 s molto mossi).
        val shaky = MomentScan("v", 10_000_000, (0..20).map { s(it * 500_000L, if (it < 10) 0.95f else 0.3f) })
        assertTrue(Moments.bestStarts(shaky, 3_000_000, 1, calm = true).first() >= 4_500_000)
    }
}
