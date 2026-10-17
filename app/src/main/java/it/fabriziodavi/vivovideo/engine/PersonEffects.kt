package it.fabriziodavi.vivovideo.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.MatrixTransformation
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.Moments
import it.fabriziodavi.vivovideo.model.PersonFx
import it.fabriziodavi.vivovideo.model.PersonMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** Segui il soggetto: sposta la sorgente (prima del riempimento) per tenere il soggetto al centro del quadro. */
class ReframeTransformation(private val clip: Clip, private val startUs: Long, private val outAspect: Float) : MatrixTransformation {
    private val srcAspect = if (clip.height > 0) clip.width.toFloat() / clip.height else 16f / 9f

    override fun getMatrix(presentationTimeUs: Long): Matrix {
        val path = clip.reframe ?: return Matrix()
        val src = if (clip.isPhoto) 0L else clip.trimStartUs + clip.speedCurve().sourceTimeAt(localTimeUs(presentationTimeUs, startUs))
        val (cx, cy) = path.at(src)
        val (tx, ty) = Moments.panFor(cx, cy, srcAspect, outAspect)
        return Matrix().apply { postTranslate(tx, ty) }
    }
}

/**
 * Maschere della persona (scontorno con ML Kit, sul telefono): [RATE] al secondo, [SIDE]×[SIDE] in 8 bit,
 * salvate in un file per non rifarle a ogni anteprima.
 */
object PersonMasks {
    const val RATE = 15
    const val SIDE = 128
    private const val IN = 256

    fun dir(ctx: Context) = File(ctx.filesDir, "maschere").apply { mkdirs() }

    class Masks(val count: Int, val side: Int, val data: ByteArray) {
        fun slice(i: Int): ByteBuffer = ByteBuffer.wrap(data, i.coerceIn(0, count - 1) * side * side, side * side)
    }

    fun load(path: String): Masks? = runCatching {
        DataInputStream(File(path).inputStream().buffered()).use { s ->
            val n = s.readInt(); val side = s.readInt()
            val d = ByteArray(n * side * side); s.readFully(d)
            Masks(n, side, d)
        }
    }.getOrNull()

    /** Analizza [uri] da [fromUs] a [toUs] (tempo del file); [onProgress] 0…1. */
    suspend fun analyze(ctx: Context, uri: String, fromUs: Long, toUs: Long, mode: PersonMode, onProgress: (Float) -> Unit): PersonFx =
        withContext(Dispatchers.Default) {
            val seg = Segmentation.getClient(
                SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.STREAM_MODE).enableRawSizeMask().build()
            )
            val masks = java.io.ByteArrayOutputStream()
            var count = 0
            try {
                FrameReader.read(ctx, uri, fromUs, toUs, RATE, IN) { bmp, t ->
                    coroutineContext.ensureActive()
                    val m = Tasks.await(seg.process(InputImage.fromBitmap(bmp, 0)), 10, TimeUnit.SECONDS)
                    val buf = m.buffer.asFloatBuffer()
                    val mw = m.width; val mh = m.height
                    val out = ByteArray(SIDE * SIDE)
                    for (y in 0 until SIDE) for (x in 0 until SIDE) {
                        val v = buf.get((y * mh / SIDE) * mw + (x * mw / SIDE))
                        out[y * SIDE + x] = (v.coerceIn(0f, 1f) * 255).toInt().toByte()
                    }
                    masks.write(out); count++
                    onProgress(((t - fromUs).toFloat() / (toUs - fromUs).coerceAtLeast(1)).coerceIn(0f, 1f))
                }
            } finally { seg.close() }
            check(count > 0) { "Non sono riuscito a leggere i fotogrammi del video." }
            val f = File(dir(ctx), "persona_${System.currentTimeMillis()}.bin")
            DataOutputStream(f.outputStream().buffered()).use { o -> o.writeInt(count); o.writeInt(SIDE); o.write(masks.toByteArray()) }
            PersonFx(mode = mode, maskFile = f.absolutePath, fromUs = fromUs, rateHz = RATE, count = count)
        }
}

/**
 * Decodifica un video e passa, [rate] volte al secondo, un'immagine piccola (lato lungo [size]) già dritta.
 * Funziona anche con i video a 10 bit (si prende il byte alto).
 */
object FrameReader {
    suspend fun read(ctx: Context, uri: String, fromUs: Long, toUs: Long, rate: Int, size: Int, onFrame: suspend (Bitmap, Long) -> Unit) {
        val ex = MediaExtractor()
        ex.setDataSource(ctx, Uri.parse(uri), null)
        val vt = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
            ?: run { ex.release(); error("Nel video non c'è la traccia video.") }
        val fmt = ex.getTrackFormat(vt)
        ex.selectTrack(vt)
        val rotation = if (fmt.containsKey(MediaFormat.KEY_ROTATION)) fmt.getInteger(MediaFormat.KEY_ROTATION) else runCatching {
            val r = MediaMetadataRetriever(); r.setDataSource(ctx, Uri.parse(uri))
            try { r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0 } finally { r.release() }
        }.getOrDefault(0)
        val codec = runCatching { MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!) }.getOrElse {
            fmt.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_VIDEO_HEVC); MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC)
        }
        fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        codec.configure(fmt, null, null, 0)
        codec.start()
        ex.seekTo(fromUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val info = MediaCodec.BufferInfo()
        var inDone = false
        var next = fromUs
        val step = 1_000_000L / rate
        try {
            while (true) {
                coroutineContext.ensureActive()
                if (!inDone) {
                    val ii = codec.dequeueInputBuffer(5_000)
                    if (ii >= 0) {
                        val t = ex.sampleTime
                        if (t < 0 || t > toUs + 200_000) { codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { val n = ex.readSampleData(codec.getInputBuffer(ii)!!, 0); codec.queueInputBuffer(ii, 0, n.coerceAtLeast(0), t, 0); ex.advance() }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 5_000)
                if (oi < 0) continue
                val t = info.presentationTimeUs
                val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                // Un fotogramma per ogni passo: quello che arriva al tempo giusto (o subito dopo).
                if (info.size > 0 && t >= next - step / 2 && t <= toUs + step) {
                    val bmp = codec.getOutputImage(oi)?.let { img -> try { toBitmap(img, size, rotation) } finally { img.close() } }
                    codec.releaseOutputBuffer(oi, false)
                    if (bmp != null) {
                        // Tanti campioni quanti i passi superati (se il video ha meno fotogrammi del passo, si ripete).
                        while (next <= t + step / 2 && next <= toUs) { onFrame(bmp, next); next += step }
                        bmp.recycle()
                    }
                } else codec.releaseOutputBuffer(oi, false)
                if (eos || next > toUs) break
            }
        } finally {
            runCatching { codec.stop() }; codec.release(); ex.release()
        }
    }

    /** YUV (8 o 10 bit) → immagine RGB piccola, dritta. */
    fun toBitmap(img: Image, size: Int, rotation: Int): Bitmap {
        val w = img.width; val h = img.height
        val k = size.toFloat() / maxOf(w, h)
        val ow = (w * k).toInt().coerceAtLeast(2); val oh = (h * k).toInt().coerceAtLeast(2)
        val yP = img.planes[0]; val uP = img.planes[1]; val vP = img.planes[2]
        val yb = yP.buffer; val ub = uP.buffer; val vb = vP.buffer
        val hi = if (yP.pixelStride >= 2) 1 else 0
        val chi = if (uP.pixelStride >= 4 || (hi == 1 && uP.pixelStride >= 2)) 1 else 0
        val px = IntArray(ow * oh)
        for (y in 0 until oh) {
            val sy = y * h / oh
            for (x in 0 until ow) {
                val sx = x * w / ow
                val yy = (yb.get(sy * yP.rowStride + sx * yP.pixelStride + hi).toInt() and 255)
                val ci = (sy / 2) * uP.rowStride + (sx / 2) * uP.pixelStride + chi
                val cv = (sy / 2) * vP.rowStride + (sx / 2) * vP.pixelStride + chi
                val u = (ub.get(ci).toInt() and 255) - 128
                val v = (vb.get(cv).toInt() and 255) - 128
                val r = (yy + 1.402f * v).toInt().coerceIn(0, 255)
                val g = (yy - 0.344f * u - 0.714f * v).toInt().coerceIn(0, 255)
                val b = (yy + 1.772f * u).toInt().coerceIn(0, 255)
                px[y * ow + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        val b = Bitmap.createBitmap(px, ow, oh, Bitmap.Config.ARGB_8888)
        if (rotation % 360 == 0) return b
        val m = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(b, 0, 0, ow, oh, m, true).also { if (it !== b) b.recycle() }
    }
}

/**
 * Lo sfondo dietro alla persona: sfocato, a colore, in bianco e nero, o trasparente (solo la persona).
 * [srcTimeUs] porta il tempo del montaggio al tempo del file, dove vivono le maschere.
 */
class PersonEffect(private val fx: PersonFx, private val srcTimeUs: (Long) -> Long) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = PersonProgram(fx, srcTimeUs, useHdr)
}

private class PersonProgram(private val fx: PersonFx, private val srcTimeUs: (Long) -> Long, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    private val program: GlProgram = try { GlProgram(VERTEX, HdrGl.fragment(FRAGMENT, useHdr)) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    private val masks = PersonMasks.load(fx.maskFile)
    private val tex = IntArray(2)
    private val loaded = intArrayOf(-1, -1)
    private var w = 1f; private var h = 1f

    init {
        program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
        GLES20.glGenTextures(2, tex, 0)
        val side = masks?.side ?: 1
        for (t in tex) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
            // Senza maschere: tutto "persona" (nessun cambiamento).
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, side, side, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE,
                ByteBuffer.allocateDirect(side * side).apply { repeat(side * side) { put(255.toByte()) }; rewind() })
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        w = inputWidth.toFloat(); h = inputHeight.toFloat()
        return Size(inputWidth, inputHeight)
    }

    private val direct by lazy { ByteBuffer.allocateDirect((masks?.side ?: 1).let { it * it }) }

    private fun upload(slot: Int, index: Int) {
        val m = masks ?: return
        if (loaded[slot] == index) return
        direct.clear(); direct.put(m.slice(index)); direct.rewind()
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[slot])
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, m.side, m.side, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, direct)
        loaded[slot] = index
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            var mix = 0f
            masks?.let { m ->
                val f = ((srcTimeUs(presentationTimeUs) - fx.fromUs) * fx.rateHz / 1e6f).coerceIn(0f, (m.count - 1).toFloat())
                val i = f.toInt()
                mix = f - i
                upload(0, i); upload(1, (i + 1).coerceAtMost(m.count - 1))
            }
            program.use()
            program.setSamplerTexIdUniform("uTex", inputTexId, 0)
            program.setSamplerTexIdUniform("uMaskA", tex[0], 1)
            program.setSamplerTexIdUniform("uMaskB", tex[1], 2)
            program.setFloatsUniform("uMix", floatArrayOf(mix))
            program.setFloatsUniform("uMode", floatArrayOf(fx.mode.ordinal.toFloat()))
            val c = fx.color
            program.setFloatsUniform("uColor", floatArrayOf(((c shr 16) and 255) / 255f, ((c shr 8) and 255) / 255f, (c and 255) / 255f, 1f))
            program.setFloatsUniform("uTexel", floatArrayOf(1f / w, 1f / h))
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        runCatching { GLES20.glDeleteTextures(2, tex, 0) }
        try { program.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }

    companion object {
        private const val VERTEX = """
attribute vec4 aFramePosition;
varying vec2 vUv;
void main() {
  gl_Position = aFramePosition;
  vUv = aFramePosition.xy * 0.5 + 0.5;
}
"""
        // Le maschere si leggono con "texture2D (" (con lo spazio): in HDR non vanno convertite come i colori.
        private const val FRAGMENT = """
precision highp float;
uniform sampler2D uTex;
uniform sampler2D uMaskA;
uniform sampler2D uMaskB;
uniform float uMix;
uniform float uMode;
uniform vec4 uColor;
uniform vec2 uTexel;
varying vec2 vUv;
void main() {
  vec4 src = texture2D(uTex, vUv);
  // Le maschere sono dritte (in alto la prima riga), le texture OpenGL partono dal basso.
  vec2 mu = vec2(vUv.x, 1.0 - vUv.y);
  float m = mix(texture2D (uMaskA, mu).r, texture2D (uMaskB, mu).r, uMix);
  m = smoothstep(0.35, 0.7, m);
  vec3 bg;
  if (uMode < 0.5) {
    // Sfocatura forte: due anelli di campioni.
    vec3 acc = src.rgb;
    float r1 = 0.025; float r2 = 0.055;
    vec2 asp = vec2(uTexel.y / uTexel.x, 1.0);
    for (int i = 0; i < 8; i++) {
      float a = float(i) * 0.7854;
      vec2 d = vec2(cos(a), sin(a)) / asp;
      acc += texture2D(uTex, clamp(vUv + d * r1, 0.0, 1.0)).rgb;
      acc += texture2D(uTex, clamp(vUv + d * r2, 0.0, 1.0)).rgb;
    }
    bg = acc / 17.0 * 0.92;
  } else if (uMode < 1.5) {
    bg = uColor.rgb;
  } else if (uMode < 2.5) {
    bg = vec3(dot(src.rgb, vec3(0.2126, 0.7152, 0.0722)));
  } else {
    bg = src.rgb;
  }
  float alpha = uMode > 2.5 ? m * src.a : src.a;
  gl_FragColor = vec4(mix(bg, src.rgb, m), alpha);
}
"""
    }
}
