package it.fabriziodavi.vivovideo.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.ClipSpeed
import it.fabriziodavi.vivovideo.model.FadeColor
import it.fabriziodavi.vivovideo.model.FxType
import it.fabriziodavi.vivovideo.model.Layer
import it.fabriziodavi.vivovideo.model.MotionStyle
import it.fabriziodavi.vivovideo.model.MusicTrack
import it.fabriziodavi.vivovideo.model.Project
import it.fabriziodavi.vivovideo.model.SlideshowOptions
import it.fabriziodavi.vivovideo.model.TextPreset
import it.fabriziodavi.vivovideo.model.Transform
import it.fabriziodavi.vivovideo.model.TransitionSpec
import it.fabriziodavi.vivovideo.model.TransitionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.math.abs

/** Operazioni dell'editor come le fa l'utente (pulsanti e pannelli), senza schermo. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class EditorOpsTest {
    private fun vm(clips: List<Clip> = listOf(
        Clip(id = "a", uri = "content://v/a", sourceDurationUs = 10_000_000, trimEndUs = 4_000_000, width = 1920, height = 1080),
        Clip(id = "b", uri = "content://v/b", sourceDurationUs = 10_000_000, trimStartUs = 1_000_000, trimEndUs = 5_000_000, width = 1920, height = 1080),
        Clip(id = "p", uri = "file:///p.jpg", sourceDurationUs = Clip.PHOTO_MAX_US, trimEndUs = 3_000_000, isPhoto = true, width = 4000, height = 3000),
    )): EditorViewModel {
        val vm = EditorViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.openProject(Project(name = "prova", clips = clips), startPreview = false)
        return vm
    }

    @Test fun selectionsAreExclusive() {
        val vm = vm()
        vm.selectedClipId = "a"
        vm.addText()
        assertNull(vm.selectedClipId)
        assertNotNull(vm.selectedTextId)
        vm.setLayersForTest(listOf(Layer(id = "l", uri = "u", width = 1, height = 1)))
        vm.selectLayer("l")
        assertNull(vm.selectedTextId)
        vm.selectedClipId = "b"
        assertNull(vm.selectedLayerId)
        assertNull(vm.selectedMusicId)
    }

    @Test fun undoRedoRestoreEveryChange() {
        val vm = vm()
        val start = vm.project!!
        vm.selectedClipId = "a"
        vm.updateSelected { it.copy(speed = ClipSpeed.SLOW) }
        vm.toggleFx(FxType.GLITCH)
        vm.setTransition(TransitionSpec(TransitionType.CROSSFADE))
        assertTrue(vm.canUndo)
        vm.undo(); vm.undo(); vm.undo()
        assertEquals(start.clips, vm.project!!.clips)
        vm.redo()
        assertEquals(ClipSpeed.SLOW, vm.project!!.clips[0].speed)
    }

    @Test fun splitDuplicateMoveDelete() {
        val vm = vm()
        vm.seekTo(2_000_000)
        vm.splitAtPlayhead()
        assertEquals(4, vm.project!!.clips.size)
        val dur = vm.project!!.durationUs
        vm.duplicateSelected()
        assertEquals(5, vm.project!!.clips.size)
        vm.moveSelected(-1)
        vm.deleteSelected()
        assertEquals(4, vm.project!!.clips.size)
        assertEquals(dur, vm.project!!.durationUs)
    }

    @Test fun photoDurationAndMotion() {
        val vm = vm()
        vm.selectedClipId = "p"
        vm.applyMotion(MotionStyle.PAN_LEFT, allPhotos = false)
        vm.setPhotoDuration(6_000_000)
        val p = vm.project!!.clips.first { it.isPhoto }
        assertEquals(6_000_000L, p.outputUs)
        assertEquals(6_000_000L, p.keyframes.last().timeUs) // il movimento segue la nuova durata
        // Il movimento scelto si riconosce (e si illumina) anche dopo il cambio di durata.
        assertEquals(MotionStyle.PAN_LEFT, MotionStyle.matching(p.keyframes, p.trimmedUs))
    }

    @Test fun transitionsToAllCutsAndEffectsToAllClips() {
        val vm = vm()
        vm.selectedClipId = "a"
        vm.setTransitionAll(TransitionSpec(TransitionType.SPIN))
        val clips = vm.project!!.clips
        assertTrue(clips.dropLast(1).all { it.transitionOut?.type == TransitionType.SPIN })
        assertNull(clips.last().transitionOut)
        vm.toggleFx(FxType.VIGNETTE)
        vm.setFxAmount(FxType.VIGNETTE, 0.9f)
        vm.applyFxToAll()
        assertTrue(vm.project!!.clips.all { c -> c.fx.single().let { it.type == FxType.VIGNETTE && abs(it.amount - 0.9f) < 1e-4 } })
    }

    @Test fun framingAppliesToAll() {
        val vm = vm()
        vm.selectedClipId = "b"
        vm.setFrame(Transform(scale = 1.3f, rotation = 90f))
        vm.updateSelected { it.copy(fill = true) }
        vm.applyFrameToAll()
        assertTrue(vm.project!!.clips.all { it.frame.scale == 1.3f && it.fill })
    }

    @Test fun textEditingPresetsAndMotion() {
        val vm = vm()
        vm.seekTo(1_000_000)
        vm.addText()
        val t0 = vm.selectedText!!
        assertEquals(1_000_000L, t0.startUs)
        vm.updateText(typing = true) { it.copy(text = "Titolo") }
        vm.applyTextPreset(TextPreset.CINEMA)
        val t = vm.selectedText!!
        assertEquals("Titolo", t.text)
        assertNotNull(t.endPose) // lo stile Cinema ha lo zoom lento
        // Lo stile scelto si riconosce (e si illumina) finché non lo si ritocca.
        assertEquals(TextPreset.CINEMA, TextPreset.matching(t))
        vm.updateText { it.copy(size = 0.2f) }
        assertNull(TextPreset.matching(vm.selectedText!!))
        vm.applyTextPreset(TextPreset.CINEMA)
        vm.toggleTextMotion()
        assertNull(vm.selectedText!!.endPose)
        vm.toggleTextMotion()
        assertNotNull(vm.selectedText!!.endPose)
        vm.duplicateText()
        assertEquals(2, vm.project!!.texts.size)
        assertEquals(vm.project!!.texts[0].endUs, vm.selectedText!!.startUs)
        vm.applyTextStyleToAll()
        vm.deleteText()
        assertEquals(1, vm.project!!.texts.size)
    }

    @Test fun layerPosesStartAndEnd() {
        val vm = vm()
        vm.setLayersForTest(listOf(Layer(id = "l", uri = "u", width = 100, height = 100, durationUs = 4_000_000)))
        vm.selectLayer("l")
        vm.toggleLayerMotion()
        vm.showPoseEnd(true, 0, 4_000_000)
        vm.setLayerPose(Transform(x = 0.3f, scale = 0.8f))
        vm.showPoseEnd(false, 0, 4_000_000)
        vm.setLayerPose(Transform(x = -0.3f, scale = 0.4f))
        val l = vm.selectedLayer!!
        assertEquals(-0.3f, l.pose.x, 1e-4f)
        assertEquals(0.3f, l.endPose!!.x, 1e-4f)
        vm.moveLayerZ(+1) // unico livello: nessun cambiamento
        vm.duplicateLayer()
        assertEquals(2, vm.project!!.layers.size)
    }

    @Test fun musicSplitBeatsAndSnap() {
        val vm = vm()
        val m = MusicTrack(id = "m", uri = "content://m", name = "M", sourceDurationUs = 60_000_000, lengthUs = 11_000_000,
            beatsUs = (0 until 100).map { it * 500_000L })
        vm.setMusicForTest(listOf(m))
        vm.selectedMusicId = "m"
        // Battito a mano e tolto di nuovo.
        vm.seekTo(1_230_000)
        vm.toggleBeatAtPlayhead()
        assertEquals(listOf(1_230_000L), vm.selectedMusic!!.markersUs)
        vm.toggleBeatAtPlayhead()
        assertTrue(vm.selectedMusic!!.markersUs.isEmpty())
        // Accorciando una clip la fine si aggancia al battito vicino (entro 0,15 s).
        val a = vm.project!!.clips[0]
        vm.trimClip("a", a.trimStartUs, 2_420_000)
        assertTrue("fine a ${vm.project!!.clips[0].outputUs}", abs(vm.project!!.clips[0].outputUs - 2_500_000L) < 2_000)
        // Dividere il brano.
        vm.selectedMusicId = "m"
        vm.seekTo(3_000_000)
        vm.splitMusicAtPlayhead()
        assertEquals(2, vm.project!!.music.size)
        assertEquals(vm.project!!.music[0].endUs, vm.project!!.music[1].startUs)
    }

    @Test fun alignCutsAndSlideshow() {
        val vm = vm()
        vm.setMusicForTest(listOf(MusicTrack(id = "m", uri = "content://m", name = "M", sourceDurationUs = 60_000_000, beatsUs = (0 until 100).map { it * 400_000L })))
        vm.createSlideshow(SlideshowOptions(beatsPerPhoto = 2))
        vm.alignCutsToBeats()
        val p = vm.project!!
        val starts = p.clipStartsUs()
        p.clips.forEachIndexed { i, c ->
            val end = starts[i] + c.outputUs
            val off = end % 400_000
            assertTrue("taglio a $end", off < 3_000 || off > 397_000)
        }
    }

    @Test fun fadesOnVideoEndsAndToAll() {
        val vm = vm()
        vm.fadeVideoEnds()
        val c = vm.project!!.clips
        assertEquals(1_000_000L, c.first().fadeInUs)
        assertEquals(1_500_000L, c.last().fadeOutUs)
        assertEquals(0L, c[1].fadeInUs + c[1].fadeOutUs)
        vm.selectedClipId = "a"
        vm.updateSelected { it.copy(fadeOutUs = 3_000_000, fadeColor = FadeColor.WHITE) }
        vm.applyFadesToAll()
        // Mai più di metà clip.
        assertTrue(vm.project!!.clips.all { it.fadeOutUs <= it.outputUs / 2 && it.fadeOutUs > 0 && it.fadeColor == FadeColor.WHITE })
    }
}
