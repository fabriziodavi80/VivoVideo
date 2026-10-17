package it.fabriziodavi.vivovideo.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import it.fabriziodavi.vivovideo.model.Clip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Clip al contrario. 1) Copia del tratto tagliato con soli fotogrammi chiave (ognuno si decodifica da solo);
 * 2) fotogrammi dall'ultimo al primo, dal decoder direttamente all'encoder; 3) audio rovesciato.
 * Il risultato è un normale video (H.264, fino a 1080p) nella cartella dell'app.
 */
object Reverser {
    const val MAX_US = 60_000_000L
    const val MAX_SHORT_SIDE = 1080

    class Result(val file: File, val durationUs: Long, val width: Int, val height: Int)

    fun dir(ctx: Context) = File(ctx.filesDir, "contrario").apply { mkdirs() }

    /** [onProgress] 0…1 (prima metà: copia; seconda: rovescio). */
    suspend fun reverse(ctx: Context, clip: Clip, onProgress: (Float) -> Unit): Result {
        require(!clip.isPhoto) { "Le foto non hanno un verso." }
        require(clip.trimmedUs <= MAX_US) { "Al contrario si possono mettere clip fino a 60 secondi: accorciala prima." }
        val tmp = File(ctx.cacheDir, "contrario_${System.currentTimeMillis()}.mp4")
        val out = File(dir(ctx), "contrario_${System.currentTimeMillis()}.mp4")
        try {
            intraCopy(ctx, clip, tmp) { onProgress(it * 0.5f) }
            val r = withContext(Dispatchers.Default) { reverseFile(tmp, out) { onProgress(0.5f + it * 0.5f) } }
            return r
        } catch (e: Throwable) {
            out.delete(); throw e
        } finally { tmp.delete() }
    }

    /** Copia del tratto [trimStart, trimEnd] tutta di fotogrammi chiave, in SDR, lato corto al massimo 1080. */
    private suspend fun intraCopy(ctx: Context, clip: Clip, out: File, onProgress: (Float) -> Unit) {
        val cw = clip.width.takeIf { it > 0 } ?: 1920
        val ch = clip.height.takeIf { it > 0 } ?: 1080
        val k = minOf(1f, MAX_SHORT_SIDE.toFloat() / minOf(cw, ch))
        val ow = (cw * k).toInt() / 2 * 2
        val oh = (ch * k).toInt() / 2 * 2
        val item = EditedMediaItem.Builder(
            MediaItem.Builder().setUri(Uri.parse(clip.uri)).setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setStartPositionUs(clip.trimStartUs).setEndPositionUs(clip.trimEndUs).build()
            ).build()
        ).setEffects(Effects(emptyList(), listOf(Presentation.createForWidthAndHeight(ow, oh, Presentation.LAYOUT_SCALE_TO_FIT)))).build()
        val hasAudio = runCatching {
            val e = MediaExtractor(); e.setDataSource(ctx, Uri.parse(clip.uri), null)
            try { trackOf(e, "audio/") >= 0 } finally { e.release() }
        }.getOrDefault(true)
        val types = if (hasAudio) setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO) else setOf(C.TRACK_TYPE_VIDEO)
        val composition = Composition.Builder(listOf(EditedMediaItemSequence.Builder(types).addItem(item).build()))
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()
        val main = Handler(Looper.getMainLooper())
        suspendCancellableCoroutine { cont ->
            val settings = VideoEncoderSettings.Builder().setiFrameIntervalSeconds(0f).setBitrate(40_000_000).build()
            var t: Transformer? = null
            val holder = ProgressHolder()
            val poll = object : Runnable {
                override fun run() {
                    val tr = t ?: return
                    if (tr.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                    main.postDelayed(this, 300)
                }
            }
            main.post {
                t = Transformer.Builder(ctx)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setMaxDelayBetweenMuxerSamplesMs(60_000)
                    .setEncoderFactory(DefaultEncoderFactory.Builder(ctx).setRequestedVideoEncoderSettings(settings).build())
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            main.removeCallbacks(poll); t = null; if (cont.isActive) cont.resume(Unit)
                        }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            main.removeCallbacks(poll); t = null; if (cont.isActive) cont.resumeWithException(exportException)
                        }
                    }).build()
                t?.start(composition, out.absolutePath)
                main.post(poll)
            }
            cont.invokeOnCancellation { main.post { main.removeCallbacks(poll); t?.cancel(); t = null } }
        }
    }

    private fun trackOf(ex: MediaExtractor, prefix: String): Int =
        (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith(prefix) == true } ?: -1

    /** Il rovescio vero e proprio: [src] → [out]. */
    fun reverseFile(src: File, out: File, onProgress: (Float) -> Unit): Result {
        // Tempi di tutti i fotogrammi (e quali si decodificano da soli).
        val ex = MediaExtractor().apply { setDataSource(src.absolutePath) }
        val vt = trackOf(ex, "video/")
        check(vt >= 0) { "Nel video non c'è la traccia video." }
        val vFormat = ex.getTrackFormat(vt)
        ex.selectTrack(vt)
        val times = ArrayList<Long>()
        while (true) {
            val t = ex.sampleTime
            if (t < 0) break
            times += t
            ex.advance()
        }
        check(times.size >= 2) { "Video troppo corto." }
        times.sort()
        val w = vFormat.getInteger(MediaFormat.KEY_WIDTH)
        val h = vFormat.getInteger(MediaFormat.KEY_HEIGHT)
        val frameUs = ((times.last() - times.first()) / (times.size - 1)).coerceAtLeast(1)
        val fps = (1_000_000f / frameUs).coerceIn(1f, 240f)
        val lastUs = times.last()
        val durUs = lastUs - times.first() + frameUs

        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        // Audio prima, in memoria (al massimo un minuto): entra nel file appena c'è la traccia video.
        val audio = runCatching { reversedAudio(src, durUs) }.getOrNull()

        val encFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, (24_000_000L * w * h / (1920 * 1080) * fps / 30).toInt().coerceIn(4_000_000, 60_000_000))
            setFloat(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        enc.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = enc.createInputSurface()
        enc.start()
        val dec = MediaCodec.createDecoderByType(vFormat.getString(MediaFormat.KEY_MIME)!!)
        dec.configure(vFormat, surface, null, 0)
        dec.start()

        var videoTrack = -1
        var audioTrack = -1
        var started = false
        val info = MediaCodec.BufferInfo()
        var encodedFrames = 0
        var encDone = false

        fun drainEncoder(timeoutUs: Long) {
            while (true) {
                val i = enc.dequeueOutputBuffer(info, timeoutUs)
                when {
                    i == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                    i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        videoTrack = muxer.addTrack(enc.outputFormat)
                        if (audio != null) audioTrack = muxer.addTrack(audio.first)
                        muxer.start(); started = true
                        audio?.second?.forEach { (bytes, bi) -> muxer.writeSampleData(audioTrack, ByteBuffer.wrap(bytes), bi) }
                    }
                    i >= 0 -> {
                        val buf = enc.getOutputBuffer(i)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && started) { muxer.writeSampleData(videoTrack, buf, info); encodedFrames++ }
                        enc.releaseOutputBuffer(i, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) { encDone = true; return }
                    }
                }
            }
        }

        try {
            // Dall'ultimo al primo: si torna al fotogramma chiave precedente e si decodifica fino a quello voluto.
            for ((n, target) in times.asReversed().withIndex()) {
                ex.seekTo(target, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                dec.flush()
                var fed = false
                var shown = false
                var guard = 0
                while (!shown && guard++ < 2000) {
                    if (!fed) {
                        val ii = dec.dequeueInputBuffer(5_000)
                        if (ii >= 0) {
                            val t = ex.sampleTime
                            if (t < 0 || t > target) {
                                dec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); fed = true
                            } else {
                                val size = ex.readSampleData(dec.getInputBuffer(ii)!!, 0)
                                dec.queueInputBuffer(ii, 0, size.coerceAtLeast(0), t, 0)
                                ex.advance()
                            }
                        }
                    }
                    val oi = dec.dequeueOutputBuffer(info, 5_000)
                    if (oi >= 0) {
                        val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        val match = info.presentationTimeUs == target
                        // Tempo nuovo: il fotogramma n-esimo dalla fine va in posizione n.
                        if (match) dec.releaseOutputBuffer(oi, (n * frameUs) * 1000L) else dec.releaseOutputBuffer(oi, false)
                        if (match) shown = true
                        if (eos && !match) break
                    }
                    drainEncoder(0)
                }
                drainEncoder(2_000)
                if (n % 5 == 0) onProgress(n.toFloat() / times.size)
            }
            enc.signalEndOfInputStream()
            var wait = 0
            while (!encDone && wait++ < 500) drainEncoder(10_000)
            check(started && encodedFrames > 0) { "L'encoder non ha prodotto fotogrammi." }
        } finally {
            runCatching { dec.stop() }; dec.release()
            runCatching { enc.stop() }; enc.release()
            surface.release()
            ex.release()
            runCatching { if (started) muxer.stop() }; muxer.release()
        }
        onProgress(1f)
        return Result(out, times.size * frameUs, w, h)
    }

    /** Audio del file decodificato, rovesciato e ricodificato in AAC: formato e campioni pronti per il muxer. */
    private fun reversedAudio(src: File, durUs: Long): Pair<MediaFormat, List<Pair<ByteArray, MediaCodec.BufferInfo>>>? {
        val ex = MediaExtractor().apply { setDataSource(src.absolutePath) }
        try {
            val at = trackOf(ex, "audio/")
            if (at < 0) return null
            val f = ex.getTrackFormat(at)
            ex.selectTrack(at)
            val rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val pcm = decodePcm(ex, f)
            val frames = pcm.size / ch
            val wanted = (durUs * rate / 1_000_000).toInt().coerceAtMost(frames)
            // Rovescio per fotogrammi audio (i canali restano al loro posto).
            val rev = ShortArray(wanted * ch)
            for (i in 0 until wanted) for (c in 0 until ch) rev[i * ch + c] = pcm[(wanted - 1 - i) * ch + c]
            return encodeAac(rev, rate, ch)
        } finally { ex.release() }
    }

    private fun decodePcm(ex: MediaExtractor, f: MediaFormat): ShortArray {
        val dec = MediaCodec.createDecoderByType(f.getString(MediaFormat.KEY_MIME)!!)
        dec.configure(f, null, null, 0); dec.start()
        val out = java.io.ByteArrayOutputStream()
        val info = MediaCodec.BufferInfo()
        var inDone = false
        try {
            while (true) {
                if (!inDone) {
                    val ii = dec.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val n = ex.readSampleData(dec.getInputBuffer(ii)!!, 0)
                        if (n < 0) { dec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { dec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val oi = dec.dequeueOutputBuffer(info, 10_000)
                if (oi >= 0) {
                    val b = dec.getOutputBuffer(oi)!!
                    val bytes = ByteArray(info.size); b.position(info.offset); b.get(bytes)
                    out.write(bytes)
                    dec.releaseOutputBuffer(oi, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } finally { runCatching { dec.stop() }; dec.release() }
        val bb = ByteBuffer.wrap(out.toByteArray()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return ShortArray(bb.remaining()).also { bb.get(it) }
    }

    private fun encodeAac(pcm: ShortArray, rate: Int, ch: Int): Pair<MediaFormat, List<Pair<ByteArray, MediaCodec.BufferInfo>>> {
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, ch).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 192_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); enc.start()
        val samples = ArrayList<Pair<ByteArray, MediaCodec.BufferInfo>>()
        var outFormat: MediaFormat? = null
        val info = MediaCodec.BufferInfo()
        var pos = 0
        var inDone = false
        try {
            while (true) {
                if (!inDone) {
                    val ii = enc.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val buf = enc.getInputBuffer(ii)!!.order(ByteOrder.LITTLE_ENDIAN)
                        val n = minOf(buf.capacity() / 2 / ch * ch, pcm.size - pos)
                        val pts = (pos / ch).toLong() * 1_000_000 / rate
                        if (n <= 0) { enc.queueInputBuffer(ii, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else {
                            buf.asShortBuffer().put(pcm, pos, n)
                            enc.queueInputBuffer(ii, 0, n * 2, pts, 0)
                            pos += n
                        }
                    }
                }
                val oi = enc.dequeueOutputBuffer(info, 10_000)
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) outFormat = enc.outputFormat
                if (oi >= 0) {
                    val b = enc.getOutputBuffer(oi)!!
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        val bytes = ByteArray(info.size); b.position(info.offset); b.get(bytes)
                        samples += bytes to MediaCodec.BufferInfo().apply { set(0, info.size, info.presentationTimeUs, info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM.inv()) }
                    }
                    enc.releaseOutputBuffer(oi, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } finally { runCatching { enc.stop() }; enc.release() }
        return (outFormat ?: error("AAC senza formato")) to samples
    }
}
