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
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.PI

/**
 * Transizioni a due clip (tendine, orologio, scalini, sovrapposizione, passaggio con maschera): nel compositore la clip
 * di sopra (prima del taglio la successiva, dopo il taglio la precedente) riceve qui la trasparenza che decide dove si
 * vede. La maschera dipende solo dal punto e dall'avanzamento, uguale per le due metà: al taglio non c'è nessun salto.
 * [role]: [MAIN] = traccia principale (solo la luce della sovrapposizione), [NEXT] = sopra c'è la clip successiva,
 * [PREV] = sopra c'è la precedente. [startUs] e [totalUs]: la finestra della transizione nel tempo del montaggio.
 */
class TransitionMaskEffect(
    private val type: TransitionType,
    private val role: Int,
    private val startUs: Long,
    private val totalUs: Long,
    private val aspect: Float,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = MaskProgram(useHdr)

    /** Avanzamento 0…1 della transizione all'istante [presentationTimeUs] del montaggio. */
    internal fun progress(presentationTimeUs: Long): Float =
        ((presentationTimeUs - startUs).toFloat() / totalUs.coerceAtLeast(1)).coerceIn(0f, 1f)

    private inner class MaskProgram(useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
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
        const val MAIN = 0
        const val NEXT = 1
        const val PREV = 2

        /** Codice dello shader per ogni transizione (0 = nessuna maschera). */
        fun modeOf(t: TransitionType): Int = when (t) {
            TransitionType.WIPE -> 1
            TransitionType.WIPE_DIAG -> 2
            TransitionType.CLOCK -> 3
            TransitionType.STEPS -> 4
            TransitionType.OVERLAY -> 5
            TransitionType.MASK_PASS -> 6
            else -> 0
        }

        private fun smoothstep(a: Float, b: Float, x: Float): Float { val u = ((x - a) / (b - a)).coerceIn(0f, 1f); return u * u * (3 - 2 * u) }

        /**
         * Quanto si vede la clip successiva nel punto ([x], [y]: 0…1, y verso l'alto come in OpenGL) con avanzamento
         * [r] 0…1. Stessi conti dello shader (qui per le prove).
         */
        fun reveal(mode: Int, x: Float, y: Float, r: Float, aspect: Float = 9f / 16f): Float = when (mode) {
            1 -> { val s = 0.08f; val e = r * (1 + 2 * s) - s; 1f - smoothstep(e - s, e + s, x) }
            2 -> { val s = 0.08f; val d = (x + (1f - y)) * 0.5f; val e = r * (1 + 2 * s) - s; 1f - smoothstep(e - s, e + s, d) }
            3 -> {
                val a = ((atan2((x - 0.5f) * aspect, y - 0.5f) / (2 * PI).toFloat()) + 1f).let { it - floor(it) }
                val s = 0.015f; val e = r * (1 + 2 * s) - s
                1f - smoothstep(e - s, e + s, a)
            }
            4 -> {
                val i = floor(x * STEPS).coerceIn(0f, STEPS - 1f)
                val lp = ((r - i / STEPS * 0.45f) / 0.55f).coerceIn(0f, 1f)
                val aa = 0.004f; val e = lp * (1 + 2 * aa) - aa
                1f - smoothstep(e - aa, e + aa, 1f - y)
            }
            else -> r
        }

        /** Filo di luce sul bordo della tendina (come nello shader): massimo dove la maschera è a metà. */
        fun edgeGlow(mode: Int, v: Float): Float {
            val e = 4 * v * (1 - v)
            val k = if (mode <= 2) 0.55f else if (mode == 3) 0.4f else 0.25f
            return k * e * e * e * e
        }

        /** Luce della sovrapposizione: cresce fino a metà transizione e poi si spegne. */
        fun glow(p: Float): Float { val k = 1f - abs(2 * p - 1); return 0.8f * k * k }

        private const val STEPS = 6f

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

float reveal(vec2 uv, float r, int m) {
  if (m == 1) { float s = 0.08; float e = r * (1.0 + 2.0 * s) - s; return 1.0 - smoothstep(e - s, e + s, uv.x); }
  if (m == 2) { float s = 0.08; float d = (uv.x + (1.0 - uv.y)) * 0.5; float e = r * (1.0 + 2.0 * s) - s; return 1.0 - smoothstep(e - s, e + s, d); }
  if (m == 3) {
    float a = fract(atan((uv.x - 0.5) * uAspect, uv.y - 0.5) / 6.2831853 + 1.0);
    float s = 0.015; float e = r * (1.0 + 2.0 * s) - s;
    return 1.0 - smoothstep(e - s, e + s, a);
  }
  if (m == 4) {
    float i = clamp(floor(uv.x * 6.0), 0.0, 5.0);
    float lp = clamp((r - i / 6.0 * 0.45) / 0.55, 0.0, 1.0);
    float aa = 0.004; float e = lp * (1.0 + 2.0 * aa) - aa;
    return 1.0 - smoothstep(e - aa, e + aa, 1.0 - uv.y);
  }
  return r;
}

void main() {
  vec4 src = texture2D(uTex, vUv);
  int m = int(uMode + 0.5);
  int role = int(uRole + 0.5);
  float r = uP * uP * (3.0 - 2.0 * uP);
  vec3 c = src.rgb;
  // Sovrapposizione: le luci si accendono a metà passaggio (su tutte e due le clip, sopra e sotto).
  if (m == 5) { float k = 1.0 - abs(2.0 * uP - 1.0); float g = 0.8 * k * k; c = c + g * c * c; }
  float a = 1.0;
  if (role >= 1 && m >= 1 && m <= 4) {
    float v = reveal(vUv, r, m);
    a = role == 1 ? v : 1.0 - v;
    // Un filo di luce sul bordo che avanza (look moderno), più deciso sulle tendine, appena accennato sugli scalini.
    float edge = 4.0 * v * (1.0 - v);
    edge = edge * edge * edge * edge;
    float k = m <= 2 ? 0.55 : (m == 3 ? 0.4 : 0.25);
    c = c + vec3(k * edge);
  } else if (role == 1) a = r;
  else if (role == 2) {
    // Passaggio con maschera: la persona (già scontornata, trasparenza nell'immagine) resta e poi svanisce.
    a = m == 6 ? 1.0 - smoothstep(0.5, 1.0, uP) : 1.0 - r;
  }
  gl_FragColor = vec4(c, src.a * a);
}
"""
    }
}
