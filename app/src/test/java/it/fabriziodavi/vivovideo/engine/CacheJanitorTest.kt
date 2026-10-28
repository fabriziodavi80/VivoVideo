package it.fabriziodavi.vivovideo.engine

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.fabriziodavi.vivovideo.AudioRenderCancelled
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.Project
import it.fabriziodavi.vivovideo.renderTimelineAudio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** Versione 1.0: la cache non cresce senza limiti e i lavori annullati non lasciano file. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CacheJanitorTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val now = 1_800_000_000_000L
    private val gb = 1024L * 1024 * 1024

    private fun e(name: String, ageMs: Long, size: Long = 1000, proxy: Boolean = false) =
        CacheJanitor.Entry("/c/$name", name, size, now - ageMs, proxy)

    @Test fun oldTempFilesGoFreshOnesStay() {
        val entries = listOf(
            e("timeline_1.wav", 2 * CacheJanitor.HOUR_MS),
            e("mix_1.f32", 3 * CacheJanitor.HOUR_MS),
            e("export_1.mp4", 5 * CacheJanitor.HOUR_MS, size = 2 * gb),
            e("export_2.mp4", 10_000), // esportazione in corso: il file si aggiorna di continuo
            e("abc.mp4.tmp.mp4", 2 * CacheJanitor.HOUR_MS, proxy = true),
            e("wave_1.bin", 300 * CacheJanitor.DAY_MS), // forme d'onda: piccole, restano
        )
        val gone = CacheJanitor.plan(entries, emptySet(), now).toSet()
        assertEquals(setOf("/c/timeline_1.wav", "/c/mix_1.f32", "/c/export_1.mp4", "/c/abc.mp4.tmp.mp4"), gone)
    }

    @Test fun proxiesOfProjectsAreNeverDeleted() {
        val entries = listOf(
            e("usata.mp4", 400 * CacheJanitor.DAY_MS, size = 9 * gb, proxy = true),
            e("vecchia.mp4", 20 * CacheJanitor.DAY_MS, proxy = true),
            e("recente.mp4", 2 * CacheJanitor.DAY_MS, proxy = true),
        )
        val gone = CacheJanitor.plan(entries, setOf("usata.mp4"), now)
        assertEquals(listOf("/c/vecchia.mp4"), gone)
    }

    @Test fun unusedProxiesStayUnderBudgetOldestFirst() {
        val entries = (1..6).map { i -> e("p$i.mp4", i * CacheJanitor.HOUR_MS, size = gb, proxy = true) }
        val gone = CacheJanitor.plan(entries, emptySet(), now)
        // 4 GB di spazio: restano le 4 più recenti.
        assertEquals(listOf("/c/p5.mp4", "/c/p6.mp4"), gone)
    }

    @Test fun runKeepsProjectProxiesAndRemovesLeftovers() {
        val proxies = Proxies.dir(app)
        val used = Proxies.file(app, "content://v/a")
        val unused = File(proxies, "nonusata.mp4")
        val half = File(app.cacheDir, "export_123.mp4")
        listOf(used, unused, half).forEach { it.writeBytes(ByteArray(2000)) }
        val old = System.currentTimeMillis() - 30 * CacheJanitor.DAY_MS
        listOf(used, unused, half).forEach { it.setLastModified(old) }
        val p = Project(name = "x", clips = listOf(Clip(uri = "content://v/a", sourceDurationUs = 1_000_000, width = 3840, height = 2160, fps = 30f)))
        val freed = CacheJanitor.run(app, listOf(p))
        assertTrue(used.exists())
        assertFalse(unused.exists())
        assertFalse(half.exists())
        assertEquals(4000L, freed)
    }

    @Test fun cancelledSoundtrackLeavesNoFiles() {
        fun leftovers() = app.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("timeline_") || it.name.startsWith("mix_") }
        leftovers().forEach { it.delete() }
        val p = Project(name = "x", clips = listOf(Clip(uri = "file:///nessuna.jpg", sourceDurationUs = 3_000_000, isPhoto = true)))
        try {
            renderTimelineAudio(app, p, isCancelled = { true })
            fail("doveva fermarsi")
        } catch (_: AudioRenderCancelled) {
        }
        assertEquals(emptyList<File>(), leftovers())
        // Senza annullamento esce il WAV, e il mix intermedio non resta nella cache.
        val wav = renderTimelineAudio(app, p)
        assertTrue(wav.length() > 44)
        assertEquals(listOf(wav.name), leftovers().map { it.name })
        wav.delete()
    }
}
