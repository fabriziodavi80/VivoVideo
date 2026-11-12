package it.fabriziodavi.vivovideo.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.TimelapsePlan
import it.fabriziodavi.vivovideo.model.TimelapseTrails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Time-lapse: la clip accelerata di 5–100 volte, a 30 fotogrammi al secondo. Ogni fotogramma del risultato può
 * mescolare più fotogrammi dell'originale presi nel suo intervallo (come un otturatore lento): ciò che sta fermo resta
 * nitido, ciò che si muove (passanti, auto, nuvole) diventa una scia morbida. Il risultato è un video normale (H.264,
 * lato lungo fino a 1920) nella cartella dell'app, con l'audio d'ambiente basso o muto.
 */
object Timelapse {
    const val FPS = 30
    const val MAX_SIDE = 1920

    fun dir(ctx: Context) = File(ctx.filesDir, "timelapse").apply { mkdirs() }

    suspend fun render(
        ctx: Context, clip: Clip, speed: Int, trails: TimelapseTrails, ambient: Boolean, onProgress: (Float) -> Unit,
    ): Reverser.Result = withContext(Dispatchers.Default) {
        require(!clip.isPhoto) { "Il time-lapse è per i video." }
        val plan = TimelapsePlan(clip.trimmedUs, speed, clip.fps, trails)
        check(plan.outFrames >= 2) { "Clip troppo corta per un time-lapse a $speed×: allungala o scegli meno velocità." }
        val out = File(dir(ctx), "timelapse_${System.currentTimeMillis()}.mp4")
        val side = minOf(MAX_SIDE, maxOf(clip.width, clip.height).takeIf { it > 0 } ?: MAX_SIDE)
        var acc: Bitmap? = null
        var canvas: Canvas? = null
        var group = -1
        var inGroup = 0
        val blend = Paint(Paint.FILTER_BITMAP_FLAG)
        var enc: MediaCodec? = null
        var surface: android.view.Surface? = null
        var muxer: MediaMuxer? = null
        var vTrack = -1
        var started = false
        var written = 0
        var done = false
        var emitted = 0
        val info = MediaCodec.BufferInfo()
        val frameUs = 1_000_000L / FPS
        val audio = runCatching { audioTrack(ctx, clip, plan.outputUs, ambient) }.getOrNull()

        fun drain(timeoutUs: Long) {
            val e = enc ?: return
            while (true) {
                val i = e.dequeueOutputBuffer(info, timeoutUs)
                when {
                    i == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                    i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val m = muxer!!
                        vTrack = m.addTrack(e.outputFormat)
                        val aTrack = audio?.let { m.addTrack(it.first) }
                        m.start(); started = true
                        if (audio != null && aTrack != null) audio.second.forEach { (bytes, bi) -> m.writeSampleData(aTrack, ByteBuffer.wrap(bytes), bi) }
                    }
                    i >= 0 -> {
                        val b = e.getOutputBuffer(i)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && started) {
                            info.presentationTimeUs = written * frameUs
                            muxer!!.writeSampleData(vTrack, b, info)
                            written++
                        }
                        e.releaseOutputBuffer(i, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) { done = true; return }
                    }
                }
            }
        }

        /** Il fotogramma pronto (la media dei campioni del gruppo) va all'encoder. */
        fun emit() {
            val a = acc ?: return
            emitted++
            if (enc == null) {
                val w = a.width / 2 * 2; val h = a.height / 2 * 2
                val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, (w.toLong() * h * FPS / 6).toInt().coerceIn(6_000_000, 40_000_000))
                    setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                    setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
                }
                enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                    configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    surface = createInputSurface()
                    start()
                }
                muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            }
            val s = surface!!
            val c = runCatching { s.lockHardwareCanvas() }.getOrElse { s.lockCanvas(null) }
            try { c.drawBitmap(a, null, RectF(0f, 0f, c.width.toFloat(), c.height.toFloat()), blend) } finally { s.unlockCanvasAndPost(c) }
            drain(0)
        }

        try {
            FrameReader.readStep(ctx, clip.uri, clip.trimStartUs, clip.trimEndUs - 1, plan.stepUs, side) { bmp, t ->
                val g = plan.groupOf(t - clip.trimStartUs)
                if (g >= plan.outFrames) return@readStep
                if (g != group) {
                    if (group >= 0) {
                        emit()
                        // Gruppi saltati (video con pochi fotogrammi): si ripete l'ultimo.
                        repeat((g - group - 1).coerceAtMost(plan.outFrames - emitted)) { emit() }
                    }
                    group = g; inGroup = 0
                }
                if (acc == null) { acc = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888); canvas = Canvas(acc!!) }
                // Media che si aggiorna: il campione n-esimo pesa 1/n (il primo copre tutto).
                blend.alpha = (255f / (inGroup + 1)).toInt().coerceIn(1, 255)
                canvas!!.drawBitmap(bmp, null, RectF(0f, 0f, acc!!.width.toFloat(), acc!!.height.toFloat()), blend)
                blend.alpha = 255
                inGroup++
                onProgress((g + 1).toFloat() / plan.outFrames * 0.97f)
            }
            if (group >= 0) emit()
            // Se il video finisce prima, l'ultimo fotogramma si tiene fino alla durata prevista.
            while (acc != null && emitted < plan.outFrames) emit()
            val e = enc ?: error("Nessun fotogramma letto dal video.")
            e.signalEndOfInputStream()
            var guard = 0
            while (!done && guard++ < 600) drain(10_000)
            check(started && written > 0) { "L'encoder non ha prodotto fotogrammi." }
        } catch (t: Throwable) {
            out.delete(); throw t
        } finally {
            runCatching { enc?.stop() }; enc?.release()
            surface?.release()
            runCatching { if (started) muxer?.stop() }; muxer?.release()
            acc?.recycle()
        }
        onProgress(1f)
        val a = acc
        val w = (a?.width ?: 0) / 2 * 2; val h = (a?.height ?: 0) / 2 * 2
        Reverser.Result(out, written * frameUs, w, h)
    }

    /**
     * Audio del time-lapse: con [ambient] il suono vero del posto (i primi secondi dell'originale, a velocità normale,
     * basso e con dissolvenze), altrimenti silenzio (la clip resta una clip normale con l'audio).
     */
    private fun audioTrack(ctx: Context, clip: Clip, outUs: Long, ambient: Boolean): Pair<MediaFormat, List<Pair<ByteArray, MediaCodec.BufferInfo>>> {
        val rate = 48_000; val ch = 2
        val n = (outUs * rate / 1_000_000).toInt()
        if (!ambient) return Reverser.encodeAac(ShortArray(n * ch), rate, ch)
        val ex = MediaExtractor()
        try {
            ex.setDataSource(ctx, Uri.parse(clip.uri), null)
            val at = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: return Reverser.encodeAac(ShortArray(n * ch), rate, ch)
            val f = ex.getTrackFormat(at)
            ex.selectTrack(at)
            ex.seekTo(clip.trimStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val srcRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val srcCh = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val pcm = Reverser.decodePcm(ex, f)
            val frames = pcm.size / srcCh
            val want = (outUs * srcRate / 1_000_000).toInt().coerceAtMost(frames)
            val fade = (srcRate * 0.4f).toInt().coerceAtLeast(1)
            val outPcm = ShortArray(want * srcCh)
            for (i in 0 until want) {
                val env = minOf(1f, i.toFloat() / fade, (want - i).toFloat() / fade) * 0.35f
                for (c in 0 until srcCh) outPcm[i * srcCh + c] = (pcm[i * srcCh + c] * env).toInt().toShort()
            }
            return Reverser.encodeAac(outPcm, srcRate, srcCh)
        } finally { ex.release() }
    }
}
