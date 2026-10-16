package it.fabriziodavi.vivovideo.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.ClipSpeed
import it.fabriziodavi.vivovideo.model.Keyframe
import it.fabriziodavi.vivovideo.model.Project
import it.fabriziodavi.vivovideo.model.TextFont
import it.fabriziodavi.vivovideo.model.Transform
import it.fabriziodavi.vivovideo.model.TransitionSpec
import it.fabriziodavi.vivovideo.model.TransitionType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Fotografa le schermate principali, così si possono controllare senza il telefono. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun vm(): EditorViewModel {
        val vm = EditorViewModel(ApplicationProvider.getApplicationContext<Application>())
        val clips = listOf(
            Clip(uri = "content://demo/1", sourceDurationUs = 4_000_000, width = 2160, height = 3840, fps = 30f),
            Clip(uri = "content://demo/2", sourceDurationUs = 4_100_000, speed = ClipSpeed.RAMP, speedPoints = it.fabriziodavi.vivovideo.model.SpeedPreset.BULLET.points, log = true, width = 2160, height = 3840, fps = 120f),
            Clip(uri = "content://demo/3", sourceDurationUs = 3_000_000, muted = true, width = 2160, height = 3840),
        )
        vm.openProject(Project(name = "Gita in montagna", clips = clips), startPreview = false)
        return vm
    }

    @Test fun editorPortrait() {
        val vm = vm(); vm.selectedClipId = null
        compose.setContent { VivoTheme { EditorScreen(vm) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/1_editor_verticale.png")
    }

    @Test fun editorSpeedPanel() {
        val vm = vm(); vm.selectedClipId = vm.project!!.clips[1].id
        compose.setContent { VivoTheme { EditorScreen(vm, initialTool = Tool.SPEED) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/2_editor_velocita.png")
    }

    @Test fun editorExportPanel() {
        val vm = vm(); vm.selectedClipId = null
        compose.setContent { VivoTheme { EditorScreen(vm, initialTool = Tool.EXPORT) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/3_editor_esporta.png")
    }

    @Config(qualifiers = "w915dp-h412dp-land-xxhdpi")
    @Test fun editorLandscape() {
        val vm = vm(); vm.selectedClipId = vm.project!!.clips[1].id
        compose.setContent { VivoTheme { EditorScreen(vm, initialTool = Tool.COLOR) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/4_editor_orizzontale.png")
    }

    @Config(qualifiers = "w915dp-h412dp-land-xxhdpi")
    @Test fun editorLandscapeTools() {
        val vm = vm(); vm.selectedClipId = vm.project!!.clips[1].id
        compose.setContent { VivoTheme { EditorScreen(vm) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/6_editor_orizzontale_strumenti.png")
    }

    @Test fun editorKeyframePanel() {
        val vm = vm()
        val c = vm.project!!.clips[0]
        vm.selectedClipId = c.id
        vm.updateSelected {
            it.copy(keyframes = listOf(
                Keyframe(0, Transform()),
                Keyframe(3_000_000, Transform(scale = 1.8f, x = 0.1f)),
            ))
        }
        compose.setContent { VivoTheme { EditorScreen(vm, initialTool = Tool.KEYFRAME) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/7_editor_keyframe.png")
    }

    @Test fun editorTransitionPanel() {
        val vm = vm()
        vm.selectedClipId = vm.project!!.clips[0].id
        vm.setTransition(TransitionSpec(TransitionType.ZOOM, 800_000))
        compose.setContent { VivoTheme { EditorScreen(vm, initialTool = Tool.TRANSITION) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/8_editor_transizioni.png")
    }

    @Test fun editorMusicPanel() {
        val vm = vm()
        val t = it.fabriziodavi.vivovideo.model.MusicTrack(
            uri = "content://demo/music", name = "Sunset Drive", sourceDurationUs = 180_000_000,
            startUs = 1_000_000, lengthUs = 9_000_000, fadeOutUs = 2_000_000,
            beatsUs = (0 until 400).map { it * 468_750L }, bpm = 128f,
        )
        val t2 = t.copy(id = "b", name = "Voce", startUs = 6_000_000, lengthUs = 4_000_000)
        vm.setMusicForTest(listOf(t, t2))
        vm.selectedMusicId = t.id
        compose.setContent { VivoTheme { EditorScreen(vm, initialTool = Tool.MUSIC) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/9_editor_musica.png")
    }

    @Test fun editorPhotoPanel() {
        val vm = vm()
        vm.openProject(
            Project(name = "Foto", clips = listOf(
                Clip(uri = "file:///nofile/photo_1.jpg", sourceDurationUs = Clip.PHOTO_MAX_US, trimEndUs = Clip.PHOTO_DEFAULT_US, isPhoto = true, width = 3072, height = 4096),
                Clip(uri = "content://demo/1", sourceDurationUs = 4_000_000, width = 2160, height = 3840, fps = 30f),
            )),
            startPreview = false,
        )
        compose.setContent { VivoTheme { EditorScreen(vm, initialTool = Tool.FRAME) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/10_editor_foto.png")
    }

    @Test fun editorTextPanel() {
        val vm = vm()
        vm.selectedClipId = null
        vm.addText()
        vm.updateText { it.copy(text = "Gita in montagna", font = TextFont.TITOLO, size = 0.09f, y = -0.3f) }
        compose.setContent { VivoTheme { EditorScreen(vm, initialTool = Tool.TEXT) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/11_editor_testo.png")
    }

    @Test fun editorLayerPanel() {
        val vm = vm()
        val l = it.fabriziodavi.vivovideo.model.Layer(uri = "file:///nofile/logo.png", name = "Logo", width = 800, height = 400, startUs = 0, durationUs = 5_000_000)
        vm.setLayersForTest(listOf(l))
        vm.selectLayer(l.id)
        vm.toggleLayerMotion()
        compose.setContent { VivoTheme { EditorScreen(vm, initialTool = Tool.LAYER) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/12_editor_immagini.png")
    }

    @Test fun editorEffectsPanel() {
        val vm = vm()
        vm.selectedClipId = vm.project!!.clips[0].id
        vm.toggleFx(it.fabriziodavi.vivovideo.model.FxType.VHS)
        vm.toggleFx(it.fabriziodavi.vivovideo.model.FxType.VIGNETTE)
        compose.setContent { VivoTheme { EditorScreen(vm, initialTool = Tool.EFFECTS) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/13_editor_effetti.png")
    }

    @Test fun editorEditRow() {
        val vm = vm(); vm.selectedClipId = vm.project!!.clips[0].id
        vm.setTransition(TransitionSpec(TransitionType.CROSSFADE))
        vm.selectedClipId = vm.project!!.clips[1].id
        compose.setContent { VivoTheme { EditorScreen(vm) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/15_editor_modifica.png")
    }

    @Test fun editorMusicRhythmTab() {
        val vm = vm()
        val t = it.fabriziodavi.vivovideo.model.MusicTrack(
            uri = "content://demo/music", name = "Sunset Drive", sourceDurationUs = 180_000_000,
            beatsUs = (0 until 400).map { it * 468_750L }, bpm = 128f,
        )
        vm.setMusicForTest(listOf(t))
        vm.selectedMusicId = t.id
        compose.setContent { VivoTheme { EditorScreen(vm, initialTool = Tool.MUSIC, initialTab = 1) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/16_editor_musica_ritmo.png")
    }

    @Test fun guide() {
        compose.setContent { VivoTheme { GuideScreen {} } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/14_guida.png")
    }

    @Test fun home() {
        val vm = EditorViewModel(ApplicationProvider.getApplicationContext<Application>())
        compose.setContent { VivoTheme { HomeScreen(vm) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/5_progetti.png")
    }
}
