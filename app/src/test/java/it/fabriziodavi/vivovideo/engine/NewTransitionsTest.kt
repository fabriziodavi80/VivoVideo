package it.fabriziodavi.vivovideo.engine

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.PersonFx
import it.fabriziodavi.vivovideo.model.Project
import it.fabriziodavi.vivovideo.model.TransitionSpec
import it.fabriziodavi.vivovideo.model.TransitionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Le transizioni dal video dell'utente: tendine, orologio, scalini, sovrapposizione, taglio invisibile, maschera. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class NewTransitionsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val factory = CompositionFactory(app)

    /** Due video tagliati con margine (1 s di materiale prima e dopo il taglio). */
    private fun pair(t: TransitionType, person: Boolean = false): Project {
        val a = Clip(id = "a", uri = "content://v/a", sourceDurationUs = 10_000_000, trimStartUs = 1_000_000, trimEndUs = 5_000_000, width = 1920, height = 1080)
            .let { if (person) it.copy(person = PersonFx(maskFile = "/nessuna", count = 1)) else it }
            .copy(transitionOut = TransitionSpec(t, 800_000))
        val b = Clip(id = "b", uri = "content://v/b", sourceDurationUs = 10_000_000, trimStartUs = 1_000_000, trimEndUs = 5_000_000, width = 1920, height = 1080)
        return Project(name = "x", clips = listOf(a, b))
    }

    private val twoClip = listOf(TransitionType.WIPE, TransitionType.WIPE_DIAG, TransitionType.CLOCK, TransitionType.STEPS, TransitionType.OVERLAY)

    @Test fun twoClipTransitionsUseTheFramesBeyondTheCut() {
        twoClip.forEach { t ->
            assertTrue("$t", t.composite)
            val cf = pair(t).crossfades().single()
            assertEquals(t, cf.type)
            assertEquals(400_000L, cf.beforeUs)
            assertEquals(400_000L, cf.afterUs)
            assertNotEquals(null, t.hint)
        }
        assertFalse(TransitionType.INVISIBLE.composite)
        assertTrue(pair(TransitionType.INVISIBLE).crossfades().isEmpty())
    }

    @Test fun maskPassNeedsThePersonCutoutAndHappensAfterTheCut() {
        assertTrue(pair(TransitionType.MASK_PASS).crossfades().isEmpty())
        val cf = pair(TransitionType.MASK_PASS, person = true).crossfades().single()
        assertEquals(0L, cf.beforeUs)
        assertEquals(800_000L, cf.afterUs)
    }

    @Test fun masksGoFromNothingToEverythingWithoutJumps() {
        val pts = (0..10).flatMap { i -> (0..10).map { j -> i / 10f to j / 10f } }
        for (m in 1..4) {
            // All'inizio non si vede nulla della clip successiva, alla fine tutto.
            assertTrue("modo $m inizio", pts.all { (x, y) -> TransitionMaskEffect.reveal(m, x, y, 0f) < 0.02f })
            assertTrue("modo $m fine", pts.all { (x, y) -> TransitionMaskEffect.reveal(m, x, y, 1f) > 0.98f })
            // Avanzando, ogni punto si scopre sempre di più (mai avanti e indietro).
            pts.forEach { (x, y) ->
                var last = -1f
                for (k in 0..20) {
                    val v = TransitionMaskEffect.reveal(m, x, y, k / 20f)
                    assertTrue("modo $m ($x,$y)", v >= last - 1e-4f)
                    last = v
                }
            }
        }
        // Tendina: a metà, sinistra scoperta, destra no.
        assertTrue(TransitionMaskEffect.reveal(1, 0.2f, 0.5f, 0.5f) > 0.95f)
        assertTrue(TransitionMaskEffect.reveal(1, 0.8f, 0.5f, 0.5f) < 0.05f)
        // Orologio a un quarto: in alto a destra sì, a sinistra no.
        assertTrue(TransitionMaskEffect.reveal(3, 0.75f, 0.75f, 0.25f) > 0.95f)
        assertTrue(TransitionMaskEffect.reveal(3, 0.25f, 0.5f, 0.25f) < 0.05f)
        // Scalini a metà: la prima colonna è scesa quasi tutta, l'ultima appena.
        assertTrue(TransitionMaskEffect.reveal(4, 0.05f, 0.5f, 0.5f) > 0.95f)
        assertTrue(TransitionMaskEffect.reveal(4, 0.95f, 0.5f, 0.5f) < 0.05f)
        // La luce della sovrapposizione: spenta agli estremi, massima a metà.
        assertEquals(0f, TransitionMaskEffect.glow(0f), 1e-6f)
        assertEquals(0f, TransitionMaskEffect.glow(1f), 1e-6f)
        assertTrue(TransitionMaskEffect.glow(0.5f) > TransitionMaskEffect.glow(0.3f))
    }

    @Test fun progressCoversTheWholeTransitionWindow() {
        val fx = TransitionMaskEffect(TransitionType.WIPE, TransitionMaskEffect.NEXT, 3_600_000, 800_000, 1f)
        assertEquals(0f, fx.progress(3_000_000), 1e-6f)
        assertEquals(0.5f, fx.progress(4_000_000), 1e-6f)
        assertEquals(1f, fx.progress(5_000_000), 1e-6f)
    }

    private fun laneEffects(p: Project) = factory.build(p, ExportOptions(), forPreview = false, nativeAudio = null)
        .sequences.drop(1).dropLast(1).flatMap { it.editedMediaItems }.flatMap { it.effects.videoEffects }

    @Test fun exportPutsTheMaskOnTheClipAboveAndTheLightBelow() {
        twoClip.forEach { t ->
            val lane = laneEffects(pair(t))
            assertEquals("$t", 2, lane.count { it is TransitionMaskEffect })
        }
        // Sovrapposizione: le luci anche sulla traccia principale (fine della prima clip, inizio della seconda).
        val main = factory.build(pair(TransitionType.OVERLAY), ExportOptions(), forPreview = false, nativeAudio = null)
            .sequences.last().editedMediaItems.flatMap { it.effects.videoEffects }
        assertEquals(2, main.count { it is TransitionMaskEffect })
        // Incrociata: nessuna maschera, come prima.
        assertEquals(0, laneEffects(pair(TransitionType.CROSSFADE)).count { it is TransitionMaskEffect })
        // Passaggio con maschera: sopra c'è solo la coda della prima clip, scontornata.
        val mp = laneEffects(pair(TransitionType.MASK_PASS, person = true))
        assertEquals(1, mp.count { it is TransitionMaskEffect })
        assertTrue(mp.any { it is PersonEffect })
    }

    @Test fun withoutTheCompositorSomethingSimilarOnOneClip() {
        // Anteprima prima che il tratto sia pronto: tendina → passaggio al nero, sovrapposizione → luce, maschera → taglio netto.
        fun mainFx(t: TransitionType) = factory.build(pair(t, person = true), ExportOptions(), forPreview = true, nativeAudio = null, allowMulti = false)
            .sequences.first().editedMediaItems.flatMap { it.effects.videoEffects }
        assertTrue(mainFx(TransitionType.WIPE).any { it is TransitionEffect })
        assertTrue(mainFx(TransitionType.OVERLAY).any { it is TransitionEffect })
        assertTrue(mainFx(TransitionType.MASK_PASS).none { it is TransitionEffect })
        assertTrue(mainFx(TransitionType.WIPE).none { it is TransitionMaskEffect })
    }

    @Test fun invisibleCutHidesTheCutInMotion() {
        val fx = TransitionEffect(0, 2_000_000, null, 0, TransitionType.INVISIBLE, 300_000)
        val at = fx.params(1_999_000)
        assertTrue(at.blur > 0.1f && at.blurMode == 1)
        // Quasi tutta scia: lo spostamento è piccolo rispetto allo Scorrimento.
        val whip = TransitionEffect(0, 2_000_000, null, 0, TransitionType.WHIP, 300_000).params(1_999_000)
        assertTrue(kotlin.math.abs(at.shift) < kotlin.math.abs(whip.shift) / 2)
        assertEquals(TransitionEffect.Params(seed = fx.params(500_000).seed), fx.params(500_000))
        // In entrata la scia continua nello stesso verso.
        val inFx = TransitionEffect(0, 2_000_000, TransitionType.INVISIBLE, 300_000, null, 0).params(1_000)
        assertTrue(inFx.shift < 0f && at.shift > 0f)
    }

    @Test fun wholeClipsGetRoomForTheTransition() {
        // Due video interi (come nel collaudo): nessun fotogramma oltre il taglio, quindi la tendina non si vedeva.
        val a = Clip(id = "a", uri = "content://v/a", sourceDurationUs = 6_000_000, width = 1920, height = 1080)
            .copy(transitionOut = TransitionSpec(TransitionType.WIPE, 900_000))
        val b = Clip(id = "b", uri = "content://v/b", sourceDurationUs = 6_000_000, width = 1920, height = 1080)
        val text = it.fabriziodavi.vivovideo.model.TextItem(text = "dopo", startUs = 8_000_000, durationUs = 1_000_000)
        val p = Project(name = "x", clips = listOf(a, b), texts = listOf(text))
        assertTrue(p.crossfades().isEmpty())
        val (q, removed) = p.makeRoomForTransition(0)!!
        assertEquals(900_000L, removed)
        assertEquals(5_550_000L, q.clips[0].trimEndUs)
        assertEquals(450_000L, q.clips[1].trimStartUs)
        // Ora la tendina c'è, piena; il testo dopo il taglio si sposta con il video.
        val cf = q.crossfades().single()
        assertEquals(450_000L, cf.beforeUs); assertEquals(450_000L, cf.afterUs)
        assertEquals(7_100_000L, q.texts.single().startUs)
        // Già abbastanza video oltre il taglio: nulla da fare. Transizione su una clip sola: nulla da fare.
        assertNull(q.makeRoomForTransition(0))
        assertNull(p.copy(clips = listOf(a.copy(transitionOut = TransitionSpec(TransitionType.FLASH)), b)).makeRoomForTransition(0))
        // Passaggio con maschera: tutto dopo il taglio, si accorcia solo la fine della clip prima.
        val mp = p.copy(clips = listOf(a.copy(transitionOut = TransitionSpec(TransitionType.MASK_PASS, 1_200_000), person = PersonFx(maskFile = "/x", count = 1)), b))
        val (m, mr) = mp.makeRoomForTransition(0)!!
        assertEquals(1_200_000L, mr)
        assertEquals(0L, m.clips[1].trimStartUs)
        assertEquals(1_200_000L, m.crossfades().single().afterUs)
        // Clip cortissime: mai sotto 1 s.
        val tiny = Project(name = "x", clips = listOf(a.copy(sourceDurationUs = 1_100_000, trimEndUs = 1_100_000), b))
        assertEquals(1_000_000L, tiny.makeRoomForTransition(0)!!.first.clips[0].trimmedUs)
    }

    @Test fun newTransitionsStartWithAVisibleDuration() {
        listOf(TransitionType.WIPE, TransitionType.WIPE_DIAG, TransitionType.CLOCK, TransitionType.STEPS).forEach { assertEquals(900_000L, it.defaultUs) }
        assertEquals(1_200_000L, TransitionType.OVERLAY.defaultUs)
        assertEquals(350_000L, TransitionType.INVISIBLE.defaultUs)
        assertNull(TransitionType.CROSSFADE.defaultUs)
        // Il filo di luce: solo sul bordo (maschera a metà), niente dove la clip è tutta scoperta o coperta.
        assertEquals(0f, TransitionMaskEffect.edgeGlow(1, 0f), 1e-6f)
        assertEquals(0f, TransitionMaskEffect.edgeGlow(1, 1f), 1e-6f)
        assertEquals(0.55f, TransitionMaskEffect.edgeGlow(1, 0.5f), 1e-6f)
    }

    @Test fun newestComeFirstInThePanel() {
        val list = it.fabriziodavi.vivovideo.ui.TRANSITIONS_NEW_FIRST
        assertEquals(TransitionType.CROSSFADE, list.first())
        assertEquals(listOf(TransitionType.WIPE, TransitionType.WIPE_DIAG, TransitionType.CLOCK, TransitionType.STEPS,
            TransitionType.OVERLAY, TransitionType.INVISIBLE, TransitionType.MASK_PASS), list.subList(1, 8))
        assertEquals(TransitionType.entries.size, list.toSet().size)
        assertNull(TransitionType.FLASH.hint)
    }
}
