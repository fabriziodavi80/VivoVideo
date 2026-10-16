package it.fabriziodavi.vivovideo.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.Project
import it.fabriziodavi.vivovideo.model.TransitionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** La nuova barra degli strumenti usata come la usa l'utente: tocchi sui pulsanti. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class EditorUiTest {
    @get:Rule val compose = createComposeRule()

    private fun vm(): EditorViewModel {
        val vm = EditorViewModel(ApplicationProvider.getApplicationContext<Application>())
        val clips = listOf(
            Clip(id = "a", uri = "content://demo/1", sourceDurationUs = 4_000_000, width = 1920, height = 1080),
            Clip(id = "b", uri = "content://demo/2", sourceDurationUs = 4_000_000, width = 1920, height = 1080),
            Clip(id = "c", uri = "content://demo/3", sourceDurationUs = 4_000_000, width = 1920, height = 1080),
        )
        vm.openProject(Project(name = "prova", clips = clips), startPreview = false)
        vm.selectedClipId = null
        return vm
    }

    private fun show(vm: EditorViewModel) = compose.setContent { VivoTheme { EditorScreen(vm) } }

    /** Voce della barra principale: è l'ultima scritta con quel nome (il pannello aperto può ripeterla). */
    private fun bar(label: String) = compose.onAllNodesWithText(label).onLast()

    @Test fun mainBarHasSixEntries() {
        val vm = vm(); show(vm)
        ToolGroup.entries.forEach { bar(it.label).assertIsDisplayed() }
    }

    @Test fun editOpensSecondRowAndClipToolsUseClipAtPlayhead() {
        val vm = vm(); show(vm)
        bar("Modifica").performClick()
        listOf("Dividi", "Velocità", "Inquadra", "Keyframe", "Transizione", "Slideshow", "Altro")
            .forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        assertNull(vm.selectedClipId)
        compose.onNodeWithText("Velocità").performClick()
        // Nessuna clip selezionata: lavora su quella sotto l'indicatore.
        assertEquals("a", vm.selectedClipId)
    }

    @Test fun moreOpensClipMenu() {
        val vm = vm(); show(vm)
        bar("Modifica").performClick()
        compose.onNodeWithText("Altro").performClick()
        compose.onNodeWithText("Duplica").performClick()
        assertEquals(4, vm.project!!.clips.size)
    }

    @Test fun audioPanelHasTabs() {
        val vm = vm(); show(vm)
        bar("Audio").performClick()
        listOf("Brano", "Ritmo", "Battiti", "Clip").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithText("Aggiungi musica").assertIsDisplayed()
        compose.onNodeWithText("Clip").performClick()
        compose.onNodeWithText("Disattiva l'audio della clip").assertIsDisplayed()
        assertNotNull(vm.selectedClipId)
    }

    @Test fun effectsPanelHasTabs() {
        val vm = vm(); show(vm)
        bar("Effetti").performClick()
        assertEquals("a", vm.selectedClipId)
        compose.onNodeWithText("Dissolvenze").performClick()
        compose.onNodeWithText("Apri e chiudi il video").assertIsDisplayed()
    }

    @Test fun textPanelHasTabs() {
        val vm = vm(); show(vm)
        bar("Testo").performClick()
        assertNotNull(vm.selectedTextId)
        compose.onNodeWithText("Animazione").performClick()
        compose.onNodeWithText("In entrata").assertIsDisplayed()
        compose.onNodeWithText("Movimento").performClick()
        compose.onNodeWithText("Centro").assertIsDisplayed()
    }

    @Test fun cutButtonOpensTransition() {
        val vm = vm(); show(vm)
        compose.onAllNodesWithContentDescription("Transizione")[0].performClick()
        assertEquals("a", vm.selectedClipId)
        compose.onNodeWithText("Verso la clip 2").assertIsDisplayed()
        compose.onNodeWithText("Incrociata").performClick()
        assertEquals(TransitionType.CROSSFADE, vm.project!!.clips[0].transitionOut?.type)
    }

    @Test fun tappingSameEntryClosesPanel() {
        val vm = vm(); show(vm)
        bar("Colore").performClick()
        compose.onNodeWithText("Tutto il progetto").assertIsDisplayed()
        bar("Colore").performClick()
        compose.onNodeWithText("Tutto il progetto").assertDoesNotExist()
    }
}
