package it.fabriziodavi.vivovideo.engine

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import it.fabriziodavi.vivovideo.model.TransitionType
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/**
 * Transizioni d'azione 2026 (frame d'impatto, swipe, tagli a fette, tessere, lama di luce, strobo): a due clip, nel
 * compositore. Tutte e due le clip si muovono (non solo una trasparenza): per questo l'effetto va su quattro pezzi, con
 * gli stessi conti e lo stesso avanzamento, così al taglio non c'è nessun salto:
 * [A_BELOW] la clip prima sulla traccia principale (prima del taglio), [B_ABOVE] la testa della successiva sopra,
 * [A_ABOVE] la coda della precedente sopra (dopo il taglio), [B_BELOW] la successiva sulla traccia principale.
 * Chi sta sopra ha la trasparenza giusta; chi sta sotto è pieno.
 */
class ActionTransitionEffect(
    private val type: TransitionType,
    private val role: Int,
    private val startUs: Long,
    private val totalUs: Long,
    private val aspect: Float,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = Program(useHdr)

    internal fun progress(presentationTimeUs: Long): Float =
        ((presentationTimeUs - startUs).toFloat() / totalUs.coerceAtLeast(1)).coerceIn(0f, 1f)

    private inner class Program(useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
        private val program: GlProgram = try { GlProgram(VERTEX, HdrGl.fragment(FRAGMENT, useHdr)) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }

        init {
            program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                program.use()
                program.setSamplerTexIdUniform("uTex", inputTexId, 0)
                program.setFloatsUniform("uMode", floatArrayOf(modeOf(type).toFloat()))
                program.setFloatsUniform("uRole", floatArrayOf(role.toFloat()))
                program.setFloatsUniform("uP", floatArrayOf(progress(presentationTimeUs)))
                program.setFloatsUniform("uAspect", floatArrayOf(aspect))
                program.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            try { program.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
        }
    }

    companion object {
        const val A_BELOW = 0
        const val B_ABOVE = 1
        const val A_ABOVE = 2
        const val B_BELOW = 3

        fun modeOf(t: TransitionType): Int = when (t) {
            TransitionType.IMPACT -> 1
            TransitionType.SLICES -> 2
            TransitionType.SWIPE -> 3
            TransitionType.TILES -> 4
            TransitionType.BLADE -> 5
            TransitionType.STROBE -> 6
            else -> 0
        }

        private fun hash(x: Float, y: Float): Float { val v = sin(x * 12.9898f + y * 78.233f) * 43758.5453f; return v - floor(v) }
        private fun backOut(q: Float, c1: Float): Float { val t = q - 1f; return 1f + (c1 + 1f) * t * t * t + c1 * t * t }
        private fun inside(x: Float, y: Float) = if (x in 0f..1f && y in 0f..1f) 1f else 0f

        /**
         * Quanto copre la clip successiva il punto ([x], [y]: 0…1, y verso l'alto) con avanzamento [p]: stessi conti dello
         * shader (qui per le prove). 0 = si vede la precedente, 1 = la successiva.
         */
        fun coverage(mode: Int, x: Float, y: Float, p: Float, aspect: Float = 9f / 16f): Float = when (mode) {
            1 -> if (p >= 0.5f) 1f else 0f
            2 -> {
                val k = floor(y * 7f).coerceIn(0f, 6f)
                val dir = if (k % 2f < 0.5f) 1f else -1f
                val q = ((p - k / 7f * 0.35f) / 0.65f).coerceIn(0f, 1f)
                val e = 1f - (1f - q).pow(4)
                val bx = x - (1f - e) * dir * 1.02f
                if (bx in 0f..1f) 1f else 0f
            }
            3 -> {
                // Agli estremi tutta vecchia o tutta nuova (niente riga di pixel della nuova sul bordo all'inizio).
                if (p <= 0f) 0f else if (p >= 1f) 1f else {
                    val e = backOut(p, 1.2f)
                    if (aspect < 1f) inside(x, y + (1f - e)) else inside(x - (1f - e), y)
                }
            }
            4 -> {
                val cols = if (aspect < 1f) 5f else 8f
                val rows = floor(cols / aspect + 0.5f)
                val ix = floor(x * cols).coerceIn(0f, cols - 1); val iy = floor(y * rows).coerceIn(0f, rows - 1)
                val q = ((p - 0.45f * hash(ix, iy)) / 0.55f).coerceIn(0f, 1f)
                val s = if (q <= 0f) 0f else if (q >= 1f) 1f else backOut(q, 1.7f)
                val gap = 0.88f + 0.12f * (((q - 0.7f) / 0.3f).coerceIn(0f, 1f)).let { it * it * (3 - 2 * it) }
                val hs = 0.5f * s * gap
                val lx = (x - (ix + 0.5f) / cols) * cols; val ly = (y - (iy + 0.5f) / rows) * rows
                if (q > 0f && abs(lx) <= hs + 1e-4f && abs(ly) <= hs + 1e-4f) 1f else 0f
            }
            5 -> {
                val e = if (p < 0.5f) 4f * p * p * p else 1f - (-2f * p + 2f).pow(3) / 2f
                val edge = e * 1.2f - 0.1f
                val d = x * 0.7f + (1f - y) * 0.3f
                if (d < edge) 1f else 0f
            }
            6 -> {
                if (p <= 0f) 0f else if (p >= 1f) 1f else {
                    val seg = floor(p * 7f).coerceAtMost(6f)
                    if (seg >= 6f || seg % 2f >= 0.5f) 1f else 0f
                }
            }
            else -> p
        }

        private const val VERTEX = """
attribute vec4 aFramePosition;
varying vec2 vUv;
void main() {
  gl_Position = aFramePosition;
  vUv = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
}
"""
        private const val FRAGMENT = """
precision highp float;
uniform sampler2D uTex;
uniform float uMode;
uniform float uRole;
uniform float uP;
uniform float uAspect;
varying vec2 vUv;

float hash(vec2 q) { return fract(sin(dot(q, vec2(12.9898, 78.233))) * 43758.5453); }
float backOut(float q, float c1) { float t = q - 1.0; return 1.0 + (c1 + 1.0) * t * t * t + c1 * t * t; }
float inside(vec2 u) { return step(0.0, u.x) * step(u.x, 1.0) * step(0.0, u.y) * step(u.y, 1.0); }
vec3 tex(vec2 u) { return texture2D(uTex, clamp(u, 0.0, 1.0)).rgb; }
vec3 blur5(vec2 u, vec2 d) { return (tex(u) + tex(u + d) + tex(u - d) + tex(u + 2.0 * d) + tex(u - 2.0 * d)) * 0.2; }

void main() {
  int m = int(uMode + 0.5);
  int role = int(uRole + 0.5);
  bool isB = role == 1 || role == 3;
  bool above = role == 1 || role == 2;
  float p = uP;
  vec2 uv = vUv;
  vec3 c = tex(uv);
  float cov = p;
  vec3 light = vec3(0.0);

  if (m == 1) {
    // Frame d'impatto: taglio secco al centro; attorno, fotogrammi a inchiostro (bianco e nero, alternati in negativo)
    // con linee di velocità dal centro e uno scatto di zoom.
    cov = step(0.5, p);
    float k = 1.0 - abs(2.0 * p - 1.0);
    vec2 z = (uv - 0.5) / (1.0 + 0.14 * k * k) + 0.5;
    c = tex(z);
    if (p > 0.3 && p < 0.7) {
      float L = dot(c, vec3(0.299, 0.587, 0.114));
      float ink = smoothstep(0.40, 0.52, L);
      vec2 d = (uv - 0.5) * vec2(uAspect, 1.0);
      float ang = atan(d.y, d.x);
      float ray = step(0.86, hash(vec2(floor((ang + 3.1416) * 38.0), floor(p * 14.0)))) * smoothstep(0.18, 0.55, length(d));
      ink = max(ink, ray);
      float flip = mod(floor(p * 14.0), 2.0);
      ink = mix(ink, 1.0 - ink, flip);
      c = vec3(ink);
    }
  } else if (m == 2) {
    // Tagli a fette: la successiva arriva a strisce, da lati alternati e sfalsate, con scia e filo di luce sul bordo.
    float k = clamp(floor(uv.y * 7.0), 0.0, 6.0);
    float dir = mod(k, 2.0) < 0.5 ? 1.0 : -1.0;
    float q = clamp((p - k / 7.0 * 0.35) / 0.65, 0.0, 1.0);
    float e = 1.0 - pow(1.0 - q, 4.0);
    float off = (1.0 - e) * dir * 1.02;
    float bx = uv.x - off;
    cov = step(0.0, bx) * step(bx, 1.0);
    float vel = 4.0 * pow(1.0 - q, 3.0) * step(0.001, q) * step(q, 0.999);
    if (isB) c = blur5(vec2(bx, uv.y), vec2(0.012 * vel * dir, 0.0));
    float lead = dir > 0.0 ? bx : 1.0 - bx;
    light = vec3(0.9, 0.95, 1.0) * exp(-abs(lead) * 140.0) * (1.0 - e) * 1.2;
  } else if (m == 3) {
    // Swipe: la successiva spinge via la precedente con un piccolo rimbalzo (dal basso nei video verticali, come un feed).
    float e = p <= 0.0 ? 0.0 : (p >= 1.0 ? 1.0 : backOut(p, 1.2));
    float e2 = p <= 0.0 ? 0.0 : (p >= 1.0 ? 1.0 : backOut(min(p + 0.02, 1.0), 1.2));
    float vel = abs(e2 - e) / 0.02;
    vec2 dv = uAspect < 1.0 ? vec2(0.0, 1.0) : vec2(-1.0, 0.0);
    vec2 sB = uv + (1.0 - e) * dv;
    vec2 sA = uv - e * dv;
    cov = p <= 0.0 ? 0.0 : (p >= 1.0 ? 1.0 : inside(sB));
    vec2 bl = dv * 0.01 * vel;
    c = isB ? blur5(sB, bl) : blur5(sA, bl);
  } else if (m == 4) {
    // Tessere: la successiva si compone a tessere che scoppiano in ordine casuale, con un bordo luminoso.
    float cols = uAspect < 1.0 ? 5.0 : 8.0;
    float rows = floor(cols / uAspect + 0.5);
    vec2 g = vec2(cols, rows);
    vec2 id = clamp(floor(uv * g), vec2(0.0), g - 1.0);
    float q = clamp((p - 0.45 * hash(id)) / 0.55, 0.0, 1.0);
    float s = q <= 0.0 ? 0.0 : (q >= 1.0 ? 1.0 : backOut(q, 1.7));
    float gap = 0.88 + 0.12 * smoothstep(0.7, 1.0, q);
    float hs = 0.5 * s * gap;
    vec2 ctr = (id + 0.5) / g;
    vec2 l = (uv - ctr) * g;
    cov = step(0.001, q) * step(abs(l.x), hs + 0.0001) * step(abs(l.y), hs + 0.0001);
    if (isB) c = tex(ctr + (uv - ctr) / max(s, 0.001));
    float rim = cov * (1.0 - step(abs(l.x), hs - 0.03) * step(abs(l.y), hs - 0.03));
    light = vec3(1.0) * rim * 0.5 * (1.0 - q);
  } else if (m == 5) {
    // Lama di luce: un taglio diagonale velocissimo con una scia calda sul filo; la successiva entra con un filo di parallasse.
    float e = p < 0.5 ? 4.0 * p * p * p : 1.0 - pow(-2.0 * p + 2.0, 3.0) / 2.0;
    float edge = e * 1.2 - 0.1;
    float d = uv.x * 0.7 + (1.0 - uv.y) * 0.3;
    cov = 1.0 - step(edge, d);
    if (isB) c = tex(uv + vec2((1.0 - e) * 0.06, 0.0));
    float s = sin(3.14159 * p);
    light = vec3(1.0, 0.86, 0.62) * (exp(-abs(d - edge) * 90.0) * 1.6 + exp(-abs(d - edge) * 14.0) * 0.25) * s;
  } else if (m == 6) {
    // Strobo: le due clip si alternano tre volte a ritmo, con un colpo di luce e di zoom a ogni scambio.
    float seg = min(floor(p * 7.0), 6.0);
    cov = p <= 0.0 ? 0.0 : (p >= 1.0 ? 1.0 : ((seg >= 6.0 || mod(seg, 2.0) >= 0.5) ? 1.0 : 0.0));
    float f = fract(p * 7.0);
    float kick = (p > 0.0 && p < 0.999) ? (1.0 - f) : 0.0;
    c = tex((uv - 0.5) / (1.0 + 0.07 * kick) + 0.5);
    light = vec3(0.45 * kick * kick);
  }
  c = c + light;
  float a = above ? (isB ? cov : 1.0 - cov) : 1.0;
  gl_FragColor = vec4(c, a);
}
"""
    }
}
