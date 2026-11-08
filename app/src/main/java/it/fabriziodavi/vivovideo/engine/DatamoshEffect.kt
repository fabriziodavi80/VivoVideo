package it.fabriziodavi.vivovideo.engine

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLContext
import android.opengl.GLES20
import android.opengl.GLES30
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import kotlin.math.max
import kotlin.math.min

/**
 * Datamosh (08/11/2026): sul taglio l'ultima immagine della clip prima resta e si "scioglie", trascinata a blocchi dal
 * movimento della clip nuova (come un video compresso a cui mancano i fotogrammi chiave); poi la clip nuova emerge a
 * tasselli. Effetto su tutto il montaggio (serve il fotogramma prima del taglio), tempi = tempi del montaggio.
 *
 * Ogni fotogramma nella finestra: 1) movimento a blocchi fra il fotogramma attuale e il precedente (ricerca a griglia,
 * su una texture piccola); 2) l'uscita precedente spostata blocco per blocco da quel movimento, con alcuni blocchi che
 * passano alla clip nuova; 3) copia in uscita. Fuori dalle finestre l'immagine passa com'è.
 */
class DatamoshEffect(private val windows: List<LongRange>) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = Program(useHdr)

    /** Avanzamento (0…1) nella finestra che contiene [t], o null. */
    fun progress(t: Long): Float? = windows.firstOrNull { t in it }?.let { w -> (t - w.first).toFloat() / max(1L, w.last - w.first) }

    // Media3 ricrea gli effetti quando cambia clip (nuova serie di effetti): l'ultimo fotogramma della clip prima
    // resta qui (stesso contesto GL) e il programma nuovo lo riprende, altrimenti al taglio non ci sarebbe nulla da trascinare.
    private var keptCtx: EGLContext? = null
    private var keptTex = 0; private var keptFbo = 0
    private var keptW = 0; private var keptH = 0; private var keptHdr = false
    private var keptT = Long.MIN_VALUE

    private inner class Program(private val hdr: Boolean) : BaseGlShaderProgram(hdr, 1) {
        private val flowProg = prog(FLOW)
        private val moshProg = prog(MOSH)
        private val copyProg = prog(COPY)
        private var w = 1; private var h = 1
        private var block = 16
        private var fw = 1; private var fh = 1
        // Texture: fotogramma precedente, due uscite che si alternano, movimento.
        private val tex = IntArray(4)
        private val fbo = IntArray(4)
        private var cur = 1 // uscita "precedente" in tex[cur]; si scrive nell'altra
        private var ready = false
        private var lastT = Long.MIN_VALUE
        private var active = false
        private var frames = 0

        private fun prog(frag: String): GlProgram = try {
            GlProgram(VERTEX, frag).also { it.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE) }
        } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }

        private fun makeTex(i: Int, tw: Int, th: Int, precise: Boolean) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[i])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, if (i == 3) GLES20.GL_NEAREST else GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, if (i == 3) GLES20.GL_NEAREST else GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            if (precise) GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, tw, th, 0, GLES20.GL_RGBA, GLES30.GL_HALF_FLOAT, null)
            else GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, tw, th, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[i])
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, tex[i], 0)
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            if (inputWidth != w || inputHeight != h || !ready) {
                w = inputWidth; h = inputHeight
                block = max(8, min(w, h) / 64)
                fw = (w + block - 1) / block; fh = (h + block - 1) / block
                val prevFbo = IntArray(1)
                GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, prevFbo, 0)
                val ctx = EGL14.eglGetCurrentContext()
                val reuse = !ready && keptTex != 0 && keptCtx == ctx && keptW == w && keptH == h && keptHdr == hdr
                if (!ready) {
                    GLES20.glGenTextures(4, tex, 0); GLES20.glGenFramebuffers(4, fbo, 0)
                    if (keptTex != 0 && keptCtx == ctx) {
                        if (reuse) {
                            // Il fotogramma "precedente" è quello lasciato dal programma di prima.
                            GLES20.glDeleteTextures(1, tex, 0); GLES20.glDeleteFramebuffers(1, fbo, 0)
                            tex[0] = keptTex; fbo[0] = keptFbo; lastT = keptT
                        } else {
                            GLES20.glDeleteTextures(1, intArrayOf(keptTex), 0); GLES20.glDeleteFramebuffers(1, intArrayOf(keptFbo), 0)
                        }
                    }
                    keptTex = 0; keptFbo = 0; keptCtx = null
                }
                for (i in 0..2) if (!(reuse && i == 0)) makeTex(i, w, h, hdr)
                makeTex(3, fw, fh, false)
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, prevFbo[0])
                ready = true
            }
            return Size(inputWidth, inputHeight)
        }

        private fun quad(p: GlProgram) {
            p.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        private fun copy(src: Int, dstFbo: Int, vw: Int, vh: Int) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, dstFbo)
            GLES20.glViewport(0, 0, vw, vh)
            copyProg.use()
            copyProg.setSamplerTexIdUniform("uTex", src, 0)
            quad(copyProg)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                val outFbo = IntArray(1); val vp = IntArray(4)
                GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, outFbo, 0)
                GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, vp, 0)
                // Salto nel tempo (ricerca in anteprima): si riparte da capo.
                val continuous = lastT != Long.MIN_VALUE && presentationTimeUs > lastT && presentationTimeUs - lastT < 250_000
                val p = progress(presentationTimeUs)
                if (p == null) {
                    active = false
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, outFbo[0]); GLES20.glViewport(vp[0], vp[1], vp[2], vp[3])
                    copyProg.use(); copyProg.setSamplerTexIdUniform("uTex", inputTexId, 0); quad(copyProg)
                } else {
                    val next = if (cur == 1) 2 else 1 // l'altra uscita
                    if (!active) {
                        // Primo fotogramma dopo il taglio: si parte dall'ultima immagine della clip prima (o, dopo un salto, da questa).
                        active = true; frames = 0
                        copy(if (continuous) tex[0] else inputTexId, fbo[next], w, h)
                    } else {
                        // 1) Movimento a blocchi: da dove viene ogni blocco del fotogramma attuale nel precedente.
                        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[3]); GLES20.glViewport(0, 0, fw, fh)
                        flowProg.use()
                        flowProg.setSamplerTexIdUniform("uCur", inputTexId, 0)
                        flowProg.setSamplerTexIdUniform("uPrev", tex[0], 1)
                        flowProg.setFloatsUniform("uRes", floatArrayOf(w.toFloat(), h.toFloat()))
                        flowProg.setFloatsUniform("uBlock", floatArrayOf(block.toFloat()))
                        quad(flowProg)
                        // 2) L'uscita precedente spostata dal movimento; qualche blocco diventa la clip nuova.
                        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[next]); GLES20.glViewport(0, 0, w, h)
                        moshProg.use()
                        moshProg.setSamplerTexIdUniform("uCur", inputTexId, 0)
                        moshProg.setSamplerTexIdUniform("uFeed", tex[cur], 1)
                        moshProg.setSamplerTexIdUniform("uFlow", tex[3], 2)
                        moshProg.setFloatsUniform("uRes", floatArrayOf(w.toFloat(), h.toFloat()))
                        moshProg.setFloatsUniform("uBlock", floatArrayOf(block.toFloat()))
                        moshProg.setFloatsUniform("uProgress", floatArrayOf(p))
                        moshProg.setFloatsUniform("uSeed", floatArrayOf((frames % 997).toFloat()))
                        quad(moshProg)
                    }
                    frames++
                    cur = next
                    // 3) In uscita.
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, outFbo[0]); GLES20.glViewport(vp[0], vp[1], vp[2], vp[3])
                    copyProg.use(); copyProg.setSamplerTexIdUniform("uTex", tex[cur], 0); quad(copyProg)
                }
                // Il fotogramma attuale diventa il "precedente" (anche fuori dalle finestre: serve al taglio dopo).
                copy(inputTexId, fbo[0], w, h)
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, outFbo[0]); GLES20.glViewport(vp[0], vp[1], vp[2], vp[3])
                lastT = presentationTimeUs
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            runCatching {
                if (ready && lastT != Long.MIN_VALUE) {
                    // Si tiene il fotogramma precedente per il programma che verrà (vedi keptTex).
                    keptCtx = EGL14.eglGetCurrentContext(); keptTex = tex[0]; keptFbo = fbo[0]
                    keptW = w; keptH = h; keptHdr = hdr; keptT = lastT
                    GLES20.glDeleteTextures(3, tex, 1); GLES20.glDeleteFramebuffers(3, fbo, 1)
                } else {
                    GLES20.glDeleteTextures(4, tex, 0); GLES20.glDeleteFramebuffers(4, fbo, 0)
                }
            }
            try { flowProg.delete(); moshProg.delete(); copyProg.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
        }
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
        internal const val COPY = """
precision highp float;
uniform sampler2D uTex;
varying vec2 vUv;
void main() { gl_FragColor = texture2D(uTex, vUv); }
"""
        /** Spostamento massimo cercato, in blocchi. */
        internal const val RANGE_BLOCKS = 2.5

        // Per ogni blocco: 16 punti, spostamenti su una griglia 9×9 fino a ±2,5 blocchi; si tiene quello con meno differenza.
        internal const val FLOW = """
precision highp float;
uniform sampler2D uCur;
uniform sampler2D uPrev;
uniform vec2 uRes;
uniform float uBlock;
varying vec2 vUv;
float lum(vec3 c) { return dot(min(c, vec3(1.0)), vec3(0.299, 0.587, 0.114)); }
void main() {
  vec2 base = floor(vUv * uRes / uBlock) * uBlock;
  float range = 2.5 * uBlock;
  float stepPx = range / 4.0;
  float best = 1e9;
  vec2 bestO = vec2(0.0);
  for (int oy = -4; oy <= 4; oy++) {
    for (int ox = -4; ox <= 4; ox++) {
      vec2 o = vec2(float(ox), float(oy)) * stepPx;
      float err = 0.0;
      for (int j = 0; j < 4; j++) {
        for (int i = 0; i < 4; i++) {
          vec2 q = base + (vec2(float(i), float(j)) + 0.5) * uBlock / 4.0;
          float a = lum(texture2D(uCur, q / uRes).rgb);
          float b = lum(texture2D(uPrev, clamp((q + o) / uRes, 0.0, 1.0)).rgb);
          err += abs(a - b);
        }
      }
      // A parità, meglio fermo (evita movimenti a caso nelle zone piatte).
      err += length(o) / range * 0.08;
      if (err < best) { best = err; bestO = o; }
    }
  }
  gl_FragColor = vec4(bestO / (2.0 * range) + 0.5, 0.0, 1.0);
}
"""
        internal const val MOSH = """
precision highp float;
uniform sampler2D uCur;
uniform sampler2D uFeed;
uniform sampler2D uFlow;
uniform vec2 uRes;
uniform float uBlock;
uniform float uProgress;
uniform float uSeed;
varying vec2 vUv;
float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
void main() {
  vec2 px = vUv * uRes;
  vec2 blk = floor(px / uBlock);
  vec2 fuv = (blk + 0.5) * uBlock / uRes;
  vec2 o = (texture2D(uFlow, fuv).rg - 0.5) * 2.0 * 2.5 * uBlock;
  // Il movimento si esagera un poco: i pixel "colano" più della realtà.
  vec2 src = clamp((px + o * 1.35) / uRes, 0.0, 1.0);
  vec4 moshed = texture2D(uFeed, src);
  vec4 cur = texture2D(uCur, vUv);
  // Tasselli che passano alla clip nuova: pochi all'inizio, sempre di più verso la fine (a gruppi di 2×2 blocchi).
  float r = hash(floor(blk / 2.0) + vec2(uSeed * 0.37, uSeed * 1.13));
  float refresh = 0.004 + 0.12 * smoothstep(0.45, 1.0, uProgress);
  vec4 col = r < refresh ? cur : moshed;
  // Alla fine la clip nuova prende il sopravvento.
  col = mix(col, cur, smoothstep(0.82, 1.0, uProgress));
  gl_FragColor = col;
}
"""
    }
}
