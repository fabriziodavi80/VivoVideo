package it.fabriziodavi.vivovideo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.fabriziodavi.vivovideo.model.CollageAspect
import it.fabriziodavi.vivovideo.model.CollageSpec
import it.fabriziodavi.vivovideo.model.PrintFormat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** La finestra «Pronto da stampare» si apre (prima si chiudeva l'app: due righe scorrevoli una dentro l'altra) e restituisce la scelta. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class PrintDialogUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun opensForEveryAspectAndPrints() {
        var aspect by mutableStateOf(CollageAspect.STORY)
        var got: Pair<PrintFormat, Boolean>? = null
        compose.setContent { VivoTheme { key(aspect) { PrintDialog(CollageSpec("x-cover", aspect), {}) { f, s -> got = f to s } } } }
        for (a in CollageAspect.entries) {
            aspect = a; got = null
            compose.waitForIdle()
            compose.onNodeWithText("Pronto da stampare").assertIsDisplayed()
            compose.onNodeWithText("Stampante di casa").performClick()
            compose.onNodeWithText("Salva per la stampa").performClick()
            assertEquals("aspetto $a", false, got?.second)
        }
    }
}
