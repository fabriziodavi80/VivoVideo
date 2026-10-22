package it.fabriziodavi.vivovideo.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.fabriziodavi.vivovideo.model.Bus
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.Comp
import it.fabriziodavi.vivovideo.model.MusicTrack
import it.fabriziodavi.vivovideo.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Schermata del mixer usata col dito. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class MixerUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun groupsMuteSoloAndCompressor() {
        val vm = EditorViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.openProject(Project(
            name = "mix",
            clips = listOf(Clip(id = "a", uri = "content://demo/1", sourceDurationUs = 4_000_000, width = 1920, height = 1080)),
            music = listOf(MusicTrack(uri = "content://m/1", name = "Brano", sourceDurationUs = 4_000_000)),
        ), startPreview = false)
        compose.setContent { VivoTheme { MixerScreen(vm) } }
        compose.onNodeWithText("Audio delle clip").assertIsDisplayed()
        compose.onNodeWithText("Musica").assertIsDisplayed()
        compose.onNodeWithContentDescription("Muto: Musica").performClick()
        assertTrue(vm.project!!.mix.bus(Bus.MUSIC).mute)
        compose.onNodeWithContentDescription("Solo: Audio delle clip").performClick()
        assertTrue(vm.project!!.mix.bus(Bus.CLIPS).solo)
        compose.onNodeWithText("Uscita").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Azzera il mixer").performScrollTo().performClick()
        assertTrue(vm.project!!.mix.neutral)
        // Compressore del primo canale.
        compose.onAllNodesWithText("Medio")[0].performScrollTo().performClick()
        assertEquals(Comp.MEDIUM, vm.project!!.mix.bus(Bus.CLIPS).comp)
    }
}
