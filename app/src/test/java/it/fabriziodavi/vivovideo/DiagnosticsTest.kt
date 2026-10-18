package it.fabriziodavi.vivovideo

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.Project
import it.fabriziodavi.vivovideo.model.TestPlan
import it.fabriziodavi.vivovideo.online.LibraryKeys
import it.fabriziodavi.vivovideo.ui.EditorViewModel
import it.fabriziodavi.vivovideo.ui.TestPlanScreen
import it.fabriziodavi.vivovideo.ui.VivoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class DiagnosticsTest {
    @get:Rule val compose = createComposeRule()
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test fun planItemsAreUniqueAndComplete() {
        assertEquals(TestPlan.items.size, TestPlan.items.map { it.id }.toSet().size)
        assertTrue(TestPlan.items.size >= 25)
        TestPlan.items.forEach { assertTrue(it.id, it.how.isNotBlank() && it.expect.isNotBlank()) }
    }

    @Test fun resultsAreRemembered() {
        val s = TestPlan.Store(app); s.reset()
        s.set("exp_4k", true)
        s.set("ai_person", false, "bordo dei capelli sfrangiato")
        val again = TestPlan.Store(app)
        assertEquals(true, again.get("exp_4k").state)
        assertEquals(false, again.get("ai_person").state); assertEquals("bordo dei capelli sfrangiato", again.get("ai_person").note)
        assertNull(again.get("base_edit").state)
        again.reset(); assertNull(TestPlan.Store(app).get("exp_4k").state)
    }

    @Test fun reportHasEverythingButTheKeys() {
        LibraryKeys.save(app, LibraryKeys("jamendo1234567", "freesoundABCDEFGH"))
        Diary.init(app)
        Diary.log("Messaggio: chiamata https://api.jamendo.com/v3.0/tracks/?client_id=jamendo1234567&limit=5")
        TestPlan.Store(app).apply { reset(); set("ai_person", false, "bordo sfrangiato") }
        val p = Project(name = "Prova", clips = listOf(Clip(uri = "content://v/1", sourceDurationUs = 5_000_000, width = 3840, height = 2160, fps = 120f, hdr = 1)))
        val r = Diagnostics.report(app, p, "si blocca in esportazione")
        listOf("Versione app", "si blocca in esportazione", "===== Collaudo", "KO  Intelligenti", "bordo sfrangiato", "===== Progetto", "3840×2160", "HDR HLG", "===== Diario")
            .forEach { assertTrue("manca \"$it\"", r.contains(it)) }
        assertFalse(r.contains("jamendo1234567")); assertFalse(r.contains("freesoundABCDEFGH"))
        assertTrue(r.contains("client_id=•••"))
        val f = Diagnostics.write(app, r)
        assertTrue(f.exists() && f.length() > 100 && f.parentFile!!.name == "segnalazioni")
        LibraryKeys.save(app, LibraryKeys(null, null))
    }

    @Test fun scrubCatchesLooseKeys() {
        val t = Diagnostics.scrub("token=abc123xyz&x=1 Authorization: Token 9f8e7d6c5b key=zzzzzz", listOf("segreto123"))
        assertFalse(t.contains("abc123xyz")); assertFalse(t.contains("9f8e7d6c5b")); assertFalse(t.contains("zzzzzz"))
        assertEquals("•••", Diagnostics.scrub("segreto123", listOf("segreto123")))
    }

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
