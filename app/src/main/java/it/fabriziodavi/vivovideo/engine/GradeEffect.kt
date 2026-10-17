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
import it.fabriziodavi.vivovideo.model.Grade
import it.fabriziodavi.vivovideo.model.GradeMath
import it.fabriziodavi.vivovideo.model.Wheel

/** Correzione del colore di una clip (stesso calcolo di [GradeMath], sulla GPU). */
class GradeEffect(val grade: Grade) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = GradeProgram(grade, useHdr)
}

private class GradeProgram(private val g: Grade, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    private val program: GlProgram = try { GlProgram(VERTEX, HdrGl.fragment(GRADE_FRAGMENT, useHdr)) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }

    init {
        program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTex", inputTexId, 0)
            program.setFloatsUniform("uBasic", floatArrayOf(g.exposure, g.contrast, g.highlights, g.shadows))
            program.setFloatsUniform("uWb", floatArrayOf(g.temperature, g.tint, g.saturation, g.vibrance))
            fun wheel(w: Wheel): FloatArray {
                val v = GradeMath.hueVec(w.hue)
                return floatArrayOf(v[0], v[1], v[2], w.amount)
            }
            program.setFloatsUniform("uWheelS", wheel(g.shadowWheel))
            program.setFloatsUniform("uWheelM", wheel(g.midWheel))
            program.setFloatsUniform("uWheelH", wheel(g.highWheel))
            val h = FloatArray(8) { g.hsl.getOrNull(it)?.hue ?: 0f }
            val s = FloatArray(8) { g.hsl.getOrNull(it)?.sat ?: 0f }
            val l = FloatArray(8) { g.hsl.getOrNull(it)?.lum ?: 0f }
            program.setFloatsUniform("uHslH0", h.copyOfRange(0, 4)); program.setFloatsUniform("uHslH1", h.copyOfRange(4, 8))
            program.setFloatsUniform("uHslS0", s.copyOfRange(0, 4)); program.setFloatsUniform("uHslS1", s.copyOfRange(4, 8))
            program.setFloatsUniform("uHslL0", l.copyOfRange(0, 4)); program.setFloatsUniform("uHslL1", l.copyOfRange(4, 8))
            program.setFloatsUniform("uHslOn", floatArrayOf(if (g.hsl.any { it.hue != 0f || it.sat != 0f || it.lum != 0f }) 1f else 0f))
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
    }
}

/** Shader della correzione: ogni passo come in GradeMath.apply. */
internal const val GRADE_FRAGMENT = """
precision highp float;
uniform sampler2D uTex;
uniform vec4 uBasic;   // esposizione, contrasto, alte luci, ombre
uniform vec4 uWb;      // temperatura, tinta, saturazione, vividezza
uniform vec4 uWheelS;  // colore (centrato sul grigio) + forza
uniform vec4 uWheelM;
uniform vec4 uWheelH;
uniform vec4 uHslH0, uHslH1, uHslS0, uHslS1, uHslL0, uHslL1;
uniform float uHslOn;
varying vec2 vUv;

float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
float bandW(float h, float center) {
  float d = mod(abs(h - center), 360.0);
  if (d > 180.0) d = 360.0 - d;
  float t = clamp(1.0 - d / 45.0, 0.0, 1.0);
  return t * t * (3.0 - 2.0 * t);
}

vec3 hslAdjust(vec3 c) {
  float mx = max(c.r, max(c.g, c.b));
  float mn = min(c.r, min(c.g, c.b));
  float d = mx - mn;
  if (d < 1e-5) return c;
  float h;
  if (mx == c.r) h = mod((c.g - c.b) / d, 6.0);
  else if (mx == c.g) h = (c.b - c.r) / d + 2.0;
  else h = (c.r - c.g) / d + 4.0;
  h *= 60.0;
  if (h < 0.0) h += 360.0;
  float s = mx > 0.0 ? d / mx : 0.0;
  float v = mx;
  float sw = min(1.0, clamp(s, 0.0, 1.0) * 3.0);
  vec4 w0 = vec4(bandW(h, 0.0), bandW(h, 30.0), bandW(h, 60.0), bandW(h, 120.0)) * sw;
  vec4 w1 = vec4(bandW(h, 180.0), bandW(h, 230.0), bandW(h, 275.0), bandW(h, 320.0)) * sw;
  float dh = dot(w0, uHslH0) + dot(w1, uHslH1);
  float ds = dot(w0, uHslS0) + dot(w1, uHslS1);
  float dl = dot(w0, uHslL0) + dot(w1, uHslL1);
  h = mod(h + dh * 30.0 + 360.0, 360.0);
  s = clamp(s * (1.0 + ds), 0.0, 1.0);
  v = clamp(v * (1.0 + dl * 0.5), 0.0, 1.0);
  float cc = v * s;
  float hp = h / 60.0;
  float x = cc * (1.0 - abs(mod(hp, 2.0) - 1.0));
  vec3 rgb;
  if (hp < 1.0) rgb = vec3(cc, x, 0.0);
  else if (hp < 2.0) rgb = vec3(x, cc, 0.0);
  else if (hp < 3.0) rgb = vec3(0.0, cc, x);
  else if (hp < 4.0) rgb = vec3(0.0, x, cc);
  else if (hp < 5.0) rgb = vec3(x, 0.0, cc);
  else rgb = vec3(cc, 0.0, x);
  return rgb + (v - cc);
}

void main() {
  vec3 c = texture2D(uTex, vUv).rgb;
  c *= exp2(uBasic.x);
  c *= vec3(1.0 + 0.12 * uWb.x + 0.05 * uWb.y, 1.0 - 0.08 * uWb.y, 1.0 - 0.12 * uWb.x + 0.05 * uWb.y);
  float l = luma(c);
  float sh = 1.0 - smoothstep(0.0, 0.55, l);
  float hi = smoothstep(0.45, 1.0, l);
  c *= (1.0 + uBasic.w * 0.9 * sh) * (1.0 + uBasic.z * 0.45 * hi);
  c = (c - 0.45) * (1.0 + uBasic.y) + 0.45;
  l = clamp(luma(c), 0.0, 1.0);
  float ws = (1.0 - l) * (1.0 - l);
  float wh = l * l;
  float wm = max(1.0 - ws - wh, 0.0);
  c += uWheelS.rgb * (ws * uWheelS.a * 0.35) + uWheelM.rgb * (wm * uWheelM.a * 0.35) + uWheelH.rgb * (wh * uWheelH.a * 0.35);
  l = luma(c);
  float sat = max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b));
  float k = (1.0 + uWb.z) * (1.0 + uWb.w * (1.0 - clamp(sat, 0.0, 1.0)));
  c = vec3(l) + (c - vec3(l)) * k;
  if (uHslOn > 0.5) c = hslAdjust(clamp(c, 0.0, 1.0));
  gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""
