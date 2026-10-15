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
import it.fabriziodavi.vivovideo.model.Transform
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

    @Test fun home() {
        val vm = EditorViewModel(ApplicationProvider.getApplicationContext<Application>())
        compose.setContent { VivoTheme { HomeScreen(vm) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/5_progetti.png")
    }
}
