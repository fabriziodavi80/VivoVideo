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
        assertTrue(b in 445_000_000L..460_000_000L)
        assertEquals("circa 451 MB", ExportEstimate.label(b))
        // 1080p come le riprese del telefono; in HDR a 10 bit un quarto in più.
        assertEquals(15_000_000, ExportEstimate.bitrate(1080, 1920, 30f, OutputCodec.H265))
        assertEquals(18_750_000, ExportEstimate.bitrate(1080, 1920, 30f, OutputCodec.H265, hdr = true))
        // Mai meno dell'originale (un 1080p Dolby Vision del telefono a circa 17 Mbps): almeno il 10% in più.
        assertEquals(22_000_000, ExportEstimate.bitrate(1080, 1920, 30f, OutputCodec.H265, hdr = true, source = 20_000_000))
        assertEquals("circa 1,2 GB", ExportEstimate.label(1_200_000_000))
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class PhotoProxyTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test fun bigPhotosGetALightCopyForThePreview() {
        val src = java.io.File(app.filesDir, "photos/grande.jpg").apply { parentFile?.mkdirs() }
        val bmp = android.graphics.Bitmap.createBitmap(4000, 3000, android.graphics.Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.rgb(200, 120, 60))
        src.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        val uri = android.net.Uri.fromFile(src).toString()
        val photo = Clip(uri = uri, sourceDurationUs = 3_000_000, trimEndUs = 3_000_000, width = 4000, height = 3000, isPhoto = true)
        val small = photo.copy(uri = "file:///piccola.jpg", width = 1600, height = 1200)
        assertTrue(Proxies.photoNeeds(photo)); assertFalse(Proxies.photoNeeds(small))
        val p = Project(name = "f", clips = listOf(photo, small))
        assertEquals(listOf(uri), Proxies.missingPhotos(app, p))
        // Prima della copia l'anteprima usa l'originale.
        assertEquals(uri, Proxies.forPreview(app, p).clips[0].uri)
        assertTrue(Proxies.makePhoto(app, uri))
        assertTrue(Proxies.missingPhotos(app, p).isEmpty())
        val shown = Proxies.forPreview(app, p)
        assertTrue(shown.clips[0].uri.endsWith(".jpg") && shown.clips[0].uri != uri)
        assertEquals(small.uri, shown.clips[1].uri)
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(Proxies.photoFile(app, uri).path, o)
        assertTrue("lato lungo ${o.outWidth}", o.outWidth in 1000..Proxies.PHOTO_SIDE)
        Proxies.clear(app)
    }
}
