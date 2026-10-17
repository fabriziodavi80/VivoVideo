package it.fabriziodavi.vivovideo.engine

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.Layer
import it.fabriziodavi.vivovideo.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ProxiesTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun clip(uri: String, w: Int, h: Int, fps: Float, us: Long = 4_000_000) =
        Clip(uri = uri, sourceDurationUs = us, trimEndUs = us, width = w, height = h, fps = fps)

    @Test fun onlyHeavyVideosNeedACopy() {
        assertTrue(Proxies.needs(clip("a", 3840, 2160, 30f)))
        assertTrue(Proxies.needs(clip("b", 1920, 1080, 120f)))
        assertTrue(Proxies.needs(clip("c", 7680, 4320, 24f)))
        assertFalse(Proxies.needs(clip("d", 1920, 1080, 60f)))
        assertFalse(Proxies.needs(clip("e", 1280, 720, 30f)))
    }

    @Test fun sizeKeepsProportionsAt720p() {
        assertEquals(1280 to 720, Proxies.size(3840, 2160))
        assertEquals(720 to 1280, Proxies.size(2160, 3840))
        assertEquals(720 to 720, Proxies.size(3000, 3000))
        val (w, h) = Proxies.size(4000, 3000)
        assertEquals(0, w % 2); assertEquals(720, h)
    }

    @Test fun previewUsesReadyCopiesOnly() {
        Proxies.clear(app)
        val p = Project(name = "t", clips = listOf(clip("content://a", 3840, 2160, 30f), clip("content://b", 3840, 2160, 120f), clip("content://c", 1920, 1080, 30f)))
        assertEquals(listOf("content://a", "content://b"), Proxies.missing(app, p))
        Proxies.file(app, "content://a").writeBytes(ByteArray(5000))
        val q = Proxies.forPreview(app, p)
        assertTrue(q.clips[0].uri.startsWith("file:"))
        assertEquals("content://b", q.clips[1].uri)
        assertEquals("content://c", q.clips[2].uri)
        assertEquals(p.durationUs, q.durationUs)
        assertEquals(listOf("content://b"), Proxies.missing(app, p))
        Proxies.clear(app)
    }

    @Test fun overlaidVideoBecomesAPreparedStretch() {
        val p = Project(
            name = "t",
            clips = listOf(clip("a", 1920, 1080, 30f), clip("b", 1920, 1080, 30f)),
            layers = listOf(Layer(uri = "v", width = 1920, height = 1080, isVideo = true, startUs = 1_000_000, durationUs = 2_000_000, sourceDurationUs = 10_000_000)),
        )
        val jobs = CrossfadePreview.jobs(p, 1080, 1920)
        assertEquals(1, jobs.size)
        val j = jobs[0]
        assertEquals(null, j.cf)
        assertEquals(1_000_000L, j.startUs); assertEquals(3_000_000L, j.endUs)
        assertEquals(1, j.sub.layers.size)
        assertEquals(0L, j.sub.layers[0].startUs)
        assertTrue(abs(j.sub.clips.sumOf { it.outputUs } - 2_000_000) < 20_000)
        // Pronto il tratto: stessa durata, il video sovrapposto è già dentro.
        CrossfadePreview.file(app, j.key).writeBytes(ByteArray(10))
        val q = CrossfadePreview.previewProject(app, p, 1080, 1920)
        assertTrue(abs(q.durationUs - p.durationUs) <= 30_000)
        assertTrue(q.layers.none { it.isVideo })
        assertTrue(q.clips.any { it.baked })
        CrossfadePreview.file(app, j.key).delete()
    }

    @Test fun presetsAndEstimate() {
        val wa = ExportPreset.WHATSAPP.applyTo(ExportOptions())
        assertTrue(ExportPreset.WHATSAPP.matches(wa))
        assertFalse(ExportPreset.YOUTUBE_4K.matches(wa))
        assertEquals(3_000_000, ExportEstimate.bitrate(1280, 720, 30f, OutputCodec.H264, wa.maxBitrate))
        // 4K30 H.265 un minuto: 45 Mbps → circa 338 MB.
        val b = ExportEstimate.bytes(3840, 2160, 30f, OutputCodec.H265, 60_000_000)
        assertTrue(b in 330_000_000L..345_000_000L)
        assertEquals("circa 338 MB", ExportEstimate.label(b))
        assertEquals("circa 1,2 GB", ExportEstimate.label(1_200_000_000))
    }
}
