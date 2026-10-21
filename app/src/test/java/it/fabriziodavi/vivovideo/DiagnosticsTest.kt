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
        // Versione da incollare nella chat: stesse informazioni essenziali, senza i dati completi del progetto.
        val c = Diagnostics.report(app, p, "si blocca in esportazione", compact = true)
        listOf("si blocca in esportazione", "KO  Intelligenti", "bordo sfrangiato", "3840×2160").forEach { assertTrue("compatto: manca \"$it\"", c.contains(it)) }
        assertFalse(c.contains("===== Progetto (dati completi)")); assertFalse(c.contains("jamendo1234567"))
        assertTrue(c.length <= r.length)
        val f = Diagnostics.write(app, r)
        assertTrue(f.exists() && f.length() > 100 && f.parentFile!!.name == "segnalazioni")
        LibraryKeys.save(app, LibraryKeys(null, null))
    }

    @Test fun compactLogKeepsOnlyErrors() {
        val log = "10-21 15:00:01.000  123  456 I Foo: tutto bene\n10-21 15:00:02.000  123  456 E Exporter: errore 4002\n" +
            "java.lang.IllegalStateException: boom\nCaused by: x\n10-21 15:00:03.000  123  456 D Bar: dettaglio"
        assertEquals(listOf("10-21 15:00:02.000  123  456 E Exporter: errore 4002", "java.lang.IllegalStateException: boom", "Caused by: x"),
            Diagnostics.errorLines(log).lines())
    }

    @Test fun scrubCatchesLooseKeys() {
        val t = Diagnostics.scrub("token=abc123xyz&x=1 Authorization: Token 9f8e7d6c5b key=zzzzzz", listOf("segreto123"))
        assertFalse(t.contains("abc123xyz")); assertFalse(t.contains("9f8e7d6c5b")); assertFalse(t.contains("zzzzzz"))
        assertEquals("•••", Diagnostics.scrub("segreto123", listOf("segreto123")))
    }

}
