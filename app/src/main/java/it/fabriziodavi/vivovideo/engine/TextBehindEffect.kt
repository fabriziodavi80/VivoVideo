package it.fabriziodavi.vivovideo.engine

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import it.fabriziodavi.vivovideo.model.TextItem
import kotlin.math.cos
import kotlin.math.sin

/**
 * Testo dietro la persona (01/11/2026): i testi [items] si disegnano solo sullo sfondo, la persona resta davanti.
 * La maschera della persona arriva nel canale alfa (PersonEffect con stash); senza maschera ([masked] = false)
 * il testo si disegna normalmente. Posizione, misura, rotazione e animazioni sono quelle dei testi normali
 * (si usano gli stessi TimedTextOverlay). All'uscita l'alfa torna pieno.
 */
class TextBehindEffect(
    private val items: List<TextItem>,
    private val frameW: Int,
    private val frameH: Int,
    private val masked: Boolean,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = Program(context, useHdr)

    private inner class Program(context: Context, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
        private val program: GlProgram = try { GlProgram(VERTEX, HdrGl.fragment(FRAGMENT, useHdr)) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
        private val overlays = items.map { TimedTextOverlay(context, it, frameW, frameH) }
        private val tex = IntArray(MAX)
        private val last = arrayOfNulls<Bitmap>(MAX)
        private var w = 1f; private var h = 1f

        init {
            program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
            GLES20.glGenTextures(MAX, tex, 0)
            for (t in tex) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888), 0)
            }
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            w = inputWidth.toFloat(); h = inputHeight.toFloat()
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                program.use()
                program.setSamplerTexIdUniform("uTex", inputTexId, 0)
                program.setFloatsUniform("uRes", floatArrayOf(w, h))
                program.setFloatsUniform("uMasked", floatArrayOf(if (masked) 1f else 0f))
                var show = 0f
                val active = items.indices.filter { presentationTimeUs >= items[it].startUs && presentationTimeUs < items[it].endUs }.take(MAX)
                for (slot in 0 until MAX) {
                    val i = active.getOrNull(slot)
                    var alpha = 0f
                    val place = FloatArray(4)
                    val rot = floatArrayOf(1f, 0f)
                    if (i != null) {
                        val ov = overlays[i]
                        val b = ov.getBitmap(presentationTimeUs)
                        if (b !== last[slot]) {
                            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[slot])
                            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, b, 0)
                            last[slot] = b
                        }
                        val st = ov.getOverlaySettings(presentationTimeUs)
                        val k = place(st, b.width, b.height, w, h)
                        k.copyInto(place, 0, 0, 4)
                        rot[0] = k[4]; rot[1] = k[5]
                        alpha = st.alphaScale
                        show = maxOf(show, items[i].behindShow.coerceIn(0f, 0.9f))
                    }
                    program.setSamplerTexIdUniform("uText$slot", tex[slot], slot + 1)
                    program.setFloatsUniform("uPlace$slot", place)
                    program.setFloatsUniform("uRot$slot", rot)
                    program.setFloatsUniform("uAlpha$slot", floatArrayOf(alpha))
                }
                program.setFloatsUniform("uShow", floatArrayOf(show))
                program.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            runCatching { GLES20.glDeleteTextures(MAX, tex, 0) }
            try { program.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
        }
    }

    companion object {
        /** Testi dietro nello stesso istante, al massimo. */
        const val MAX = 3

        /**
         * Dove sta il testo, come lo mette Media3 per i testi normali: centro (in pixel dall'alto a sinistra), mezza
         * larghezza e mezza altezza già scalate, coseno e seno della rotazione (in senso orario sullo schermo).
         */
        fun place(st: androidx.media3.common.OverlaySettings, bw: Int, bh: Int, frameW: Float, frameH: Float): FloatArray {
            val anchor = st.backgroundFrameAnchor
            val scale = st.scale
            val cx = (anchor.first + 1f) / 2f * frameW
            val cy = (1f - anchor.second) / 2f * frameH
            val a = Math.toRadians(-st.rotationDegrees.toDouble())
            return floatArrayOf(cx, cy, bw * scale.first / 2f, bh * scale.second / 2f, cos(a).toFloat(), sin(a).toFloat())
        }

        private const val VERTEX = """
attribute vec4 aFramePosition;
varying vec2 vUv;
void main() {
  gl_Position = aFramePosition;
  vUv = aFramePosition.xy * 0.5 + 0.5;
}
"""

        // I testi si leggono con "texture2D (" (con lo spazio): in HDR sono già nei colori giusti, non vanno convertiti.
        internal const val FRAGMENT = """
precision highp float;
uniform sampler2D uTex;
uniform sampler2D uText0;
uniform sampler2D uText1;
uniform sampler2D uText2;
uniform vec4 uPlace0;
uniform vec4 uPlace1;
uniform vec4 uPlace2;
uniform vec2 uRot0;
uniform vec2 uRot1;
uniform vec2 uRot2;
uniform float uAlpha0;
uniform float uAlpha1;
uniform float uAlpha2;
uniform vec2 uRes;
uniform float uMasked;
uniform float uShow;
varying vec2 vUv;

vec4 textAt(sampler2D t, vec4 place, vec2 rot, float alpha, vec2 px) {
  if (alpha <= 0.0 || place.z <= 0.0) return vec4(0.0);
  vec2 d = px - place.xy;
  // Rotazione inversa (il testo è ruotato di rot in senso orario).
  vec2 q = vec2(d.x * rot.x + d.y * rot.y, -d.x * rot.y + d.y * rot.x);
  vec2 uv = q / (2.0 * place.zw) + 0.5;
  if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) return vec4(0.0);
  return texture2D (t, uv) * alpha;
}

void main() {
  vec4 src = texture2D(uTex, vUv);
  vec2 px = vec2(vUv.x * uRes.x, (1.0 - vUv.y) * uRes.y);
  vec3 col = src.rgb;
  vec4 t0 = textAt(uText0, uPlace0, uRot0, uAlpha0, px);
  col = t0.rgb + col * (1.0 - t0.a);
  vec4 t1 = textAt(uText1, uPlace1, uRot1, uAlpha1, px);
  col = t1.rgb + col * (1.0 - t1.a);
  vec4 t2 = textAt(uText2, uPlace2, uRot2, uAlpha2, px);
  col = t2.rgb + col * (1.0 - t2.a);
  // Dove c'è la persona (alfa alto) resta il video: il testo le passa dietro.
  // Con uShow il testo si legge un po' anche attraverso la persona.
  float person = uMasked > 0.5 ? smoothstep(0.35, 0.65, src.a) * (1.0 - uShow) : 0.0;
  gl_FragColor = vec4(mix(col, src.rgb, person), 1.0);
}
"""
    }
}
