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

/**
 * "Sfondo sfocato": porta il fotogramma alla misura [outW]×[outH] mostrandolo intero (adattato)
 * e riempie il resto con una copia ingrandita, sfocata e un po' scurita della stessa immagine,
 * invece delle bande nere. Prende il posto di Presentation per quella clip.
 */
class BlurFillEffect(private val outW: Int, private val outH: Int) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = BlurFillProgram(outW, outH, useHdr)
}

/** Rettangolo (in 0..1 del quadro finale) dove sta l'immagine adattata: x0, y0, larghezza, altezza. */
internal fun fitRect(inW: Int, inH: Int, outW: Int, outH: Int): FloatArray {
    val inA = inW.toFloat() / inH
    val outA = outW.toFloat() / outH
    return if (inA > outA) {
        val h = outA / inA
        floatArrayOf(0f, (1f - h) / 2f, 1f, h)
    } else {
        val w = inA / outA
        floatArrayOf((1f - w) / 2f, 0f, w, 1f)
    }
}

private class BlurFillProgram(private val outW: Int, private val outH: Int, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    private val program: GlProgram = try { GlProgram(VERTEX, FRAGMENT) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    private var rect = floatArrayOf(0f, 0f, 1f, 1f)
    private var fillScale = floatArrayOf(1f, 1f)

    init {
        program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        rect = fitRect(inputWidth, inputHeight, outW, outH)
        // Sfondo: immagine che riempie tutto il quadro (parte centrale), quindi coordinate ristrette.
        val inA = inputWidth.toFloat() / inputHeight
        val outA = outW.toFloat() / outH
        fillScale = if (inA > outA) floatArrayOf(outA / inA, 1f) else floatArrayOf(1f, inA / outA)
        return Size(outW, outH)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTex", inputTexId, 0)
            program.setFloatsUniform("uRect", rect)
            program.setFloatsUniform("uFill", fillScale)
            program.setFloatsUniform("uAspect", floatArrayOf(outW.toFloat() / outH))
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
  vUv = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
}
"""
        private const val FRAGMENT = """
precision highp float;
uniform sampler2D uTex;
uniform vec4 uRect;
uniform vec2 uFill;
uniform float uAspect;
varying vec2 vUv;
void main() {
  vec2 local = (vUv - uRect.xy) / uRect.zw;
  if (local.x >= 0.0 && local.x <= 1.0 && local.y >= 0.0 && local.y <= 1.0) {
    gl_FragColor = vec4(texture2D(uTex, local).rgb, 1.0);
    return;
  }
  // Sfondo: zoom del 10% in più, sfocatura larga a disco, un po' più scuro.
  vec2 base = (vUv - 0.5) * uFill / 1.1 + 0.5;
  vec3 c = vec3(0.0);
  float r = 0.045;
  for (int i = 0; i < 24; i++) {
    float a = float(i) * 2.3999;
    float d = sqrt((float(i) + 0.5) / 24.0);
    vec2 o = vec2(cos(a), sin(a) * uAspect) * r * d;
    c += texture2D(uTex, clamp(base + o * uFill, 0.0, 1.0)).rgb;
  }
  c /= 24.0;
  gl_FragColor = vec4(c * 0.7, 1.0);
}
"""
    }
}
