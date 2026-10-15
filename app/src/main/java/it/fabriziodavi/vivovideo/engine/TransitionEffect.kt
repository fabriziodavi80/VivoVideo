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

/**
 * Metà di transizione applicata a una clip: [inType] nei primi [inUs] microsecondi (in entrata dal taglio
 * precedente) e [outType] negli ultimi [outUs] (in uscita verso il taglio successivo).
 * La clip prima e quella dopo fanno ciascuna la propria metà, così il taglio sparisce nel movimento.
 */
class TransitionEffect(
    private val clipOutputUs: Long,
    private val inType: TransitionType?, private val inUs: Long,
    private val outType: TransitionType?, private val outUs: Long,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        TransitionProgram(this, useHdr)

    /** Valori dello shader per l'istante t (dall'inizio della clip, dopo la velocità). */
    internal fun params(tUs: Long): Params {
        var p = Params()
        if (inType != null && inUs > 0 && tUs < inUs) {
            val e = 1f - smooth(tUs.toFloat() / inUs)
            p = shape(inType, e, entering = true)
        } else if (outType != null && outUs > 0 && tUs > clipOutputUs - outUs) {
            val e = smooth((tUs - (clipOutputUs - outUs)).toFloat() / outUs)
            p = shape(outType, e, entering = false)
        }
        return p
    }

    internal data class Params(
        val zoom: Float = 1f, val shift: Float = 0f, val rotation: Float = 0f,
        val blur: Float = 0f, val blurMode: Int = 0, val black: Float = 0f, val white: Float = 0f,
    )

    private fun smooth(x: Float): Float { val u = x.coerceIn(0f, 1f); return u * u * (3 - 2 * u) }

    /** e = intensità, 1 esattamente sul taglio. */
    private fun shape(t: TransitionType, e: Float, entering: Boolean): Params = when (t) {
        TransitionType.FADE_BLACK -> Params(black = e)
        TransitionType.FLASH -> Params(white = e * e, zoom = 1f + 0.04f * e)
        TransitionType.ZOOM -> Params(zoom = 1f + 0.6f * e * e, blur = 0.035f * e, blurMode = 2)
        TransitionType.BLUR -> Params(blur = 0.025f * e, blurMode = 3)
        TransitionType.WHIP -> Params(shift = (if (entering) -0.7f else 0.7f) * e * e, blur = 0.09f * e, blurMode = 1)
        TransitionType.SPIN -> Params(rotation = (if (entering) -1f else 1f) * 0.9f * e * e, zoom = 1f + 0.25f * e, blur = 0.02f * e, blurMode = 3)
    }
}

private class TransitionProgram(private val effect: TransitionEffect, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    private val program: GlProgram = try { GlProgram(VERTEX, FRAGMENT) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    private var aspect = 1f

    init {
        program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        aspect = inputWidth.toFloat() / inputHeight
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val p = effect.params(presentationTimeUs)
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setFloatsUniform("uZoom", floatArrayOf(p.zoom))
            program.setFloatsUniform("uShift", floatArrayOf(p.shift))
            program.setFloatsUniform("uRot", floatArrayOf(p.rotation))
            program.setFloatsUniform("uBlur", floatArrayOf(p.blur))
            program.setFloatsUniform("uMode", floatArrayOf(p.blurMode.toFloat()))
            program.setFloatsUniform("uBlack", floatArrayOf(p.black))
            program.setFloatsUniform("uWhite", floatArrayOf(p.white))
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
uniform float uZoom;
uniform float uShift;
uniform float uRot;
uniform float uBlur;
uniform float uMode;
uniform float uBlack;
uniform float uWhite;
uniform float uAspect;
varying vec2 vTexSamplingCoord;

// Coordinate trasformate (zoom, rotazione in proporzioni reali, scorrimento); fuori dal quadro = specchio.
vec2 xf(vec2 uv) {
  vec2 c = uv - 0.5;
  c.x *= uAspect;
  float s = sin(uRot), co = cos(uRot);
  c = vec2(co * c.x - s * c.y, s * c.x + co * c.y) / uZoom;
  c.x /= uAspect;
  vec2 r = c + 0.5 + vec2(uShift, 0.0);
  return 1.0 - abs(1.0 - mod(r, 2.0));
}

void main() {
  vec2 uv = vTexSamplingCoord;
  vec3 acc = vec3(0.0);
  if (uBlur <= 0.0001) {
    acc = texture2D(uTexSampler, xf(uv)).rgb;
  } else {
    for (int i = 0; i < 12; i++) {
      float k = (float(i) / 11.0) - 0.5;
      vec2 o;
      if (uMode < 1.5) o = vec2(k * uBlur, 0.0);                                  // orizzontale
      else if (uMode < 2.5) o = (uv - 0.5) * k * uBlur * 4.0;                     // radiale (zoom)
      else { float a = float(i) * 2.3999; o = vec2(cos(a), sin(a) * uAspect) * uBlur * sqrt(float(i) / 11.0); } // disco
      acc += texture2D(uTexSampler, xf(uv + o)).rgb;
    }
    acc /= 12.0;
  }
  acc = mix(acc, vec3(0.0), uBlack);
  acc = mix(acc, vec3(1.0), uWhite);
  gl_FragColor = vec4(acc, 1.0);
}
"""
    }
}
