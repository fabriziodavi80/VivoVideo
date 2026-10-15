package it.fabriziodavi.vivovideo

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
 * Alte luci / cielo calcolato pixel per pixel sulla GPU (niente LUT, niente gradini),
 * con un dithering impercettibile che evita le bande sulle sfumature (nuvole, cielo).
 * amount = 0 applica solo il dithering: utile dopo la conversione LOG.
 */
class HighlightEffect(private val amount: Float) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        HighlightShaderProgram(amount, useHdr)
}

private class HighlightShaderProgram(amount: Float, useHdr: Boolean) :
    BaseGlShaderProgram(/* useHighPrecisionColorComponents= */ useHdr, /* texturePoolCapacity= */ 1) {

    private val program: GlProgram = try {
        GlProgram(VERTEX, FRAGMENT)
    } catch (e: GlUtil.GlException) {
        throw VideoFrameProcessingException(e)
    }

    init {
        program.setFloatsUniform("uAmount", floatArrayOf(amount))
        program.setBufferAttribute(
            "aFramePosition",
            GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
        )
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            // Il rumore del dithering cambia a ogni fotogramma, così non forma motivi fissi.
            program.setFloatsUniform("uSeed", floatArrayOf(((presentationTimeUs / 1000) % 997).toFloat()))
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
uniform float uAmount;
uniform float uSeed;
varying vec2 vTexSamplingCoord;

float rand(vec2 p) {
  return fract(sin(dot(p + uSeed, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
  vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
  vec3 c = src.rgb;
  float y = dot(c, vec3(0.2126, 0.7152, 0.0722));
  // Curva sulle alte luci che comprime soltanto (pendenza mai sopra 1):
  // scurisce cielo e nuvole senza aumentarne il contrasto, quindi non fa
  // emergere la grana di compressione del video. Il bianco scende al massimo a 0,86.
  float k = 0.45;
  float t = max((y - k) / (1.0 - k), 0.0);
  float yo = y > k ? k + (1.0 - k) * t * (1.0 - 0.25 * uAmount * t) : y;
  vec3 chroma = (c - vec3(y)) * (yo / max(y, 0.0001));
  // Colore ravvivato solo dove ce n'è davvero (cielo azzurro), non sulle nuvole.
  vec3 dc = abs(c - vec3(y));
  float ch = max(dc.r, max(dc.g, dc.b));
  float s = 1.0 + 0.35 * uAmount * smoothstep(0.45, 0.8, y) * smoothstep(0.06, 0.25, ch);
  vec3 outc = vec3(yo) + chroma * s;
  // Dithering triangolare di ±1 livello su 255: rompe le bande senza vedersi.
  float n = rand(gl_FragCoord.xy) + rand(gl_FragCoord.yx + 17.0) - 1.0;
  outc += n / 255.0;
  gl_FragColor = vec4(clamp(outc, 0.0, 1.0), src.a);
}
"""
    }
}
