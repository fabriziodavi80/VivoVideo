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
import it.fabriziodavi.vivovideo.model.RBox
import it.fabriziodavi.vivovideo.model.Redaction

/**
 * Oscura i riquadri trovati da [Redactor] (prima di inquadratura e keyframe: le misure sono quelle del fotogramma dritto).
 * Per i video si disegnano i riquadri del campione attuale e del successivo, così un volto che si muove resta coperto.
 * [srcTimeUs] porta il tempo del montaggio al tempo del file.
 */
class RedactEffect(private val r: Redaction, private val isPhoto: Boolean, private val srcTimeUs: (Long) -> Long) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = RedactProgram(r, isPhoto, srcTimeUs, useHdr)

    companion object {
        /** Quanti riquadri lo shader disegna insieme (due campioni da 6). */
        const val SLOTS = 12

        /** Riquadri da disegnare al tempo del file [srcUs]: campione attuale + successivo (al massimo [SLOTS]). */
        fun boxesAt(frames: List<List<RBox>>, r: Redaction, srcUs: Long): List<RBox> {
            if (frames.isEmpty()) return emptyList()
            val f = ((srcUs - r.fromUs) * r.rateHz / 1e6).toInt().coerceIn(0, frames.lastIndex)
            return (frames[f] + frames[(f + 1).coerceAtMost(frames.lastIndex)]).take(SLOTS)
        }
    }
}

private class RedactProgram(private val r: Redaction, private val isPhoto: Boolean, private val srcTimeUs: (Long) -> Long, useHdr: Boolean) :
    BaseGlShaderProgram(useHdr, 1) {
    private val program: GlProgram = try { GlProgram(VERTEX, HdrGl.fragment(FRAGMENT, useHdr)) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    private val frames: List<List<RBox>> = if (isPhoto) listOf(r.boxes) else Redactor.load(r.file).orEmpty()
    private var w = 1f; private var h = 1f

    init {
        program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        w = inputWidth.toFloat(); h = inputHeight.toFloat()
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            val boxes = if (isPhoto) r.boxes.take(RedactEffect.SLOTS) else RedactEffect.boxesAt(frames, r, srcTimeUs(presentationTimeUs))
            program.use()
            program.setSamplerTexIdUniform("uTex", inputTexId, 0)
            for (i in 0 until RedactEffect.SLOTS) {
                val b = boxes.getOrNull(i)
                // Larghezza negativa = ovale (volti); zero = riquadro vuoto.
                val v = if (b == null) floatArrayOf(0f, 0f, 0f, 0f) else floatArrayOf(b.x, b.y, if (b.round) -b.w else b.w, b.h)
                program.setFloatsUniform("uB$i", v)
            }
            program.setFloatsUniform("uMode", floatArrayOf(r.style.ordinal.toFloat()))
            program.setFloatsUniform("uAspect", floatArrayOf(w / h))
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

    companion object {
        private const val VERTEX = """
attribute vec4 aFramePosition;
varying vec2 vUv;
void main() {
  gl_Position = aFramePosition;
  vUv = aFramePosition.xy * 0.5 + 0.5;
}
"""
        private const val FRAGMENT = """
precision highp float;
uniform sampler2D uTex;
uniform vec4 uB0, uB1, uB2, uB3, uB4, uB5, uB6, uB7, uB8, uB9, uB10, uB11;
uniform float uMode;
uniform float uAspect;
varying vec2 vUv;

// Quanto il punto p (in alto a sinistra = 0,0) sta dentro al riquadro b, con bordo morbido.
float inside(vec4 b, vec2 p) {
  float bw = abs(b.z);
  if (bw <= 0.0) return 0.0;
  vec2 c = vec2(b.x + bw * 0.5, b.y + b.w * 0.5);
  vec2 hs = vec2(bw, b.w) * 0.5;
  if (b.z < 0.0) {
    vec2 q = (p - c) / hs;
    return 1.0 - smoothstep(0.92, 1.0, dot(q, q));
  }
  vec2 d = abs(p - c) - hs;
  float e = max(d.x, d.y);
  return 1.0 - smoothstep(-0.004, 0.0, e);
}

void main() {
  vec4 src = texture2D(uTex, vUv);
  vec2 p = vec2(vUv.x, 1.0 - vUv.y);
  float m = inside(uB0, p);
  m = max(m, inside(uB1, p)); m = max(m, inside(uB2, p)); m = max(m, inside(uB3, p));
  m = max(m, inside(uB4, p)); m = max(m, inside(uB5, p)); m = max(m, inside(uB6, p));
  m = max(m, inside(uB7, p)); m = max(m, inside(uB8, p)); m = max(m, inside(uB9, p));
  m = max(m, inside(uB10, p)); m = max(m, inside(uB11, p));
  if (m <= 0.0) { gl_FragColor = src; return; }
  vec3 hid;
  if (uMode < 0.5) {
    // Pixel: quadretti grandi (circa 1/40 del lato corto), il centro del quadretto dà il colore.
    vec2 cell = uAspect >= 1.0 ? vec2(1.0 / (40.0 * uAspect), 1.0 / 40.0) : vec2(1.0 / 40.0, uAspect / 40.0);
    vec2 uv = (floor(vUv / cell) + 0.5) * cell;
    hid = texture2D(uTex, clamp(uv, 0.0, 1.0)).rgb;
  } else if (uMode < 1.5) {
    // Sfocato forte: tre anelli di campioni.
    vec3 acc = src.rgb;
    vec2 asp = vec2(1.0 / uAspect, 1.0);
    for (int i = 0; i < 10; i++) {
      float a = float(i) * 0.6283;
      vec2 d = vec2(cos(a), sin(a)) * asp;
      acc += texture2D(uTex, clamp(vUv + d * 0.02, 0.0, 1.0)).rgb;
      acc += texture2D(uTex, clamp(vUv + d * 0.045, 0.0, 1.0)).rgb;
      acc += texture2D(uTex, clamp(vUv + d * 0.075, 0.0, 1.0)).rgb;
    }
    hid = acc / 31.0;
  } else {
    hid = vec3(0.04);
  }
  gl_FragColor = vec4(mix(src.rgb, hid, m), src.a);
}
"""
    }
}
