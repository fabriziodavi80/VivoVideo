package it.fabriziodavi.vivovideo.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

/** Progetti con tante dissolvenze: i tratti del progetto aperto non si cancellano per fare spazio (prima non finiva mai). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SegmentCacheTest {
    @Test fun neededSegmentsSurviveTheCleanup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val dir = CrossfadePreview.dir(ctx)
        dir.listFiles()?.forEach { it.delete() }
        // 100 tratti del progetto aperto e 10 di vecchi progetti.
        val needed = (0 until 100).map { "p$it" }.toSet()
        needed.forEachIndexed { i, k -> CrossfadePreview.file(ctx, k).apply { writeText("x") }.setLastModified(1_000_000L + i) }
        (0 until 10).forEach { i -> CrossfadePreview.file(ctx, "old$i").apply { writeText("x") }.setLastModified(2_000_000L + i) }
        CrossfadePreview.trim(ctx, needed, keep = 3)
        assertTrue(needed.all { CrossfadePreview.file(ctx, it).exists() })
        // Dei vecchi restano solo i 3 più recenti.
        assertEquals(listOf("old7", "old8", "old9"), (0 until 10).map { "old$it" }.filter { CrossfadePreview.file(ctx, it).exists() })
    }
}
