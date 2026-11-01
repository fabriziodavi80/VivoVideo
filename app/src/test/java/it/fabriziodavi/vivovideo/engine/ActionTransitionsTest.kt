package it.fabriziodavi.vivovideo.engine

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.fabriziodavi.vivovideo.model.AutoEdit
import it.fabriziodavi.vivovideo.model.AutoStyle
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.Project
import it.fabriziodavi.vivovideo.model.TransitionSpec
import it.fabriziodavi.vivovideo.model.TransitionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Transizioni d'azione 2026: frame d'impatto, swipe, tagli a fette, tessere, lama di luce, strobo. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ActionTransitionsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val actions = TransitionType.entries.filter { it.action }
    private val grid = (0..20).flatMap { i -> (0..20).map { j -> i / 20f to j / 20f } }

    @Test fun sixActionTransitionsAllTwoClipAndShort() {
        assertEquals(listOf(TransitionType.IMPACT, TransitionType.SWIPE, TransitionType.SLICES, TransitionType.TILES, TransitionType.BLADE, TransitionType.STROBE), actions)
        actions.forEach { t ->
            assertTrue("$t", t.composite && t.punchy && !t.soft && t.hint != null)
            assertTrue("$t", t.defaultUs!! in 300_000L..700_000L)
            assertTrue("$t", ActionTransitionEffect.modeOf(t) > 0)
        }
    }

    @Test fun startOnTheOldClipEndOnTheNewOne() {
        for (aspect in listOf(9f / 16f, 16f / 9f)) for (t in actions) {
            val m = ActionTransitionEffect.modeOf(t)
            assertTrue("$t inizio", grid.all { (x, y) -> ActionTransitionEffect.coverage(m, x, y, 0f, aspect) == 0f })
            assertTrue("$t fine", grid.all { (x, y) -> ActionTransitionEffect.coverage(m, x, y, 1f, aspect) == 1f })
        }
    }

    @Test fun eachTransitionHasItsShape() {
        val v = 9f / 16f
        // Frame d'impatto: taglio secco a metà.
        assertEquals(0f, ActionTransitionEffect.coverage(1, 0.5f, 0.5f, 0.49f, v), 0f)
        assertEquals(1f, ActionTransitionEffect.coverage(1, 0.5f, 0.5f, 0.5f, v), 0f)
        // Swipe verticale: a metà la nuova occupa la parte bassa (sale dal basso), la vecchia quella alta, senza buchi.
        assertEquals(1f, ActionTransitionEffect.coverage(3, 0.5f, 0.1f, 0.4f, v), 0f)
        assertEquals(0f, ActionTransitionEffect.coverage(3, 0.5f, 0.95f, 0.4f, v), 0f)
        // Swipe orizzontale: la nuova entra da destra.
        assertEquals(1f, ActionTransitionEffect.coverage(3, 0.95f, 0.5f, 0.4f, 16f / 9f), 0f)
        assertEquals(0f, ActionTransitionEffect.coverage(3, 0.05f, 0.5f, 0.4f, 16f / 9f), 0f)
        // Tagli a fette: a metà fette vicine arrivano da lati opposti (una copre la sinistra, l'altra la destra).
        val f0 = (0..20).map { ActionTransitionEffect.coverage(2, it / 20f, 0.07f, 0.35f, v) }
        val f1 = (0..20).map { ActionTransitionEffect.coverage(2, it / 20f, 0.21f, 0.35f, v) }
        assertTrue(f0.last() == 1f && f0.first() == 0f)
        assertTrue(f1.first() == 1f && f1.last() == 0f)
        // Strobo: si alterna (vecchia, nuova, vecchia, nuova…) prima di fermarsi sulla nuova.
        val strobe = (0 until 7).map { ActionTransitionEffect.coverage(6, 0.5f, 0.5f, (it + 0.5f) / 7f, v) }
        assertEquals(listOf(0f, 1f, 0f, 1f, 0f, 1f, 1f), strobe)
        // Tessere: a metà alcune tessere ci sono già e altre no.
        val mid = grid.map { (x, y) -> ActionTransitionEffect.coverage(4, x, y, 0.5f, v) }
        assertTrue(mid.count { it == 1f } in 40..400)
        // Lama di luce: a metà, a sinistra la nuova, a destra la vecchia.
        assertEquals(1f, ActionTransitionEffect.coverage(5, 0.1f, 0.5f, 0.5f, v), 0f)
        assertEquals(0f, ActionTransitionEffect.coverage(5, 0.95f, 0.5f, 0.5f, v), 0f)
    }

    @Test fun exportMovesBothClipsAboveAndBelow() {
        val a = Clip(id = "a", uri = "content://v/a", sourceDurationUs = 10_000_000, trimStartUs = 1_000_000, trimEndUs = 5_000_000, width = 1080, height = 1920)
        val b = Clip(id = "b", uri = "content://v/b", sourceDurationUs = 10_000_000, trimStartUs = 1_000_000, trimEndUs = 5_000_000, width = 1080, height = 1920)
        actions.forEach { t ->
            val p = Project(name = "x", clips = listOf(a.copy(transitionOut = TransitionSpec(t, t.defaultUs!!)), b))
            val c = CompositionFactory(app).build(p, ExportOptions(), forPreview = false, nativeAudio = null)
            val all = c.sequences.flatMap { it.editedMediaItems }.flatMap { it.effects.videoEffects }
            // Sopra la testa della nuova e la coda della vecchia, sotto la fine della vecchia e l'inizio della nuova.
            assertEquals("$t", 4, all.count { it is ActionTransitionEffect })
            assertEquals("$t", 0, all.count { it is TransitionMaskEffect })
        }
    }

    @Test fun actionThemesUseThemWithTheirDuration() {
        listOf(AutoStyle.ESTREMO, AutoStyle.ENERGICO, AutoStyle.AZIONE).forEach { st ->
            assertTrue("$st", st.impact.first() == TransitionType.IMPACT)
            assertTrue("$st", st.transitions.any { it.action })
        }
        assertTrue(AutoStyle.BLOCKBUSTER.impact.contains(TransitionType.IMPACT))
    }
}
