package it.fabriziodavi.vivovideo.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.fabriziodavi.vivovideo.model.TestPlan
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class TestPlanUiTest {
    @get:Rule val compose = createComposeRule()
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test fun testPlanScreenMarksResults() {
        TestPlan.Store(app).reset()
        val vm = EditorViewModel(app)
        vm.showTestPlan = true
        compose.setContent { VivoTheme { TestPlanScreen(vm) } }
        compose.onNodeWithText("Provati 0 di ${TestPlan.items.size}").assertExists()
        compose.onAllNodesWithText("Funziona").onFirst().performClick()
        compose.onNodeWithText("Provati 1 di ${TestPlan.items.size}").assertExists()
        assertEquals(true, TestPlan.Store(app).get(TestPlan.items.first().id).state)
        compose.onAllNodesWithText("Problema").onFirst().performClick()
        compose.onNodeWithText("Cosa non va? (anche poche parole)").assertExists()
    }
}
