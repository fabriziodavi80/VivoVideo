package it.fabriziodavi.vivovideo

import android.content.Context
import it.fabriziodavi.vivovideo.engine.HdrGl
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/**
 * Riduzione rumore che preserva i bordi (filtro bilaterale, 25 campioni):
 * leviga zone uniformi come cielo e nuvole, dove il LOG mostra la grana di
 * compressione, e lascia nitidi contorni e dettagli. amount da 0 a 1.
 */
class DenoiseEffect(private val amount: Float) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        DenoiseShaderProgram(amount, useHdr)
}

private class DenoiseShaderProgram(amount: Float, useHdr: Boolean) :
    BaseGlShaderProgram(useHdr, 1) {

    private val program: GlProgram = try {
        GlProgram(VERTEX, HdrGl.fragment(FRAGMENT, useHdr))
    } catch (e: GlUtil.GlException) {
        throw VideoFrameProcessingException(e)
    }

    init {
        // Tolleranza sui toni: più alta = leviga anche differenze più marcate.
        program.setFloatsUniform("uSigmaR", floatArrayOf(0.006f + 0.022f * amount))
        program.setBufferAttribute(
            "aFramePosition",
            GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
        )
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        // Passo dei campioni proporzionato alla risoluzione (3 px in 4K, 1,5 px in 1080p).
        val step = 3f * minOf(inputWidth, inputHeight) / 2160f
        program.setFloatsUniform("uStep", floatArrayOf(step / inputWidth, step / inputHeight))
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            program.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    companion object {
        private const val VERTEX = """
attribute vec4 aFramePosition;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = aFramePosition;
  vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
}
"""
        private const val FRAGMENT = """
precision highp float;
uniform sampler2D uTexSampler;
uniform vec2 uStep;
uniform float uSigmaR;
varying vec2 vTexSamplingCoord;

void main() {
  vec4 center = texture2D(uTexSampler, vTexSamplingCoord);
  const vec3 L = vec3(0.2126, 0.7152, 0.0722);
  float yc = dot(center.rgb, L);
  float inv2sr2 = 1.0 / (2.0 * uSigmaR * uSigmaR);
  vec3 acc = vec3(0.0);
  float wsum = 0.0;
  for (int dy = -2; dy <= 2; dy++) {
    for (int dx = -2; dx <= 2; dx++) {
      vec2 off = vec2(float(dx), float(dy));
      vec3 s = texture2D(uTexSampler, vTexSamplingCoord + off * uStep).rgb;
      float dyl = dot(s, L) - yc;
      float w = exp(-dot(off, off) / 4.8 - dyl * dyl * inv2sr2);
      acc += s * w;
      wsum += w;
    }
  }
  gl_FragColor = vec4(acc / wsum, center.a);
}
"""
    }
}
