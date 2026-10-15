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
import it.fabriziodavi.vivovideo.model.KeyColor
import it.fabriziodavi.vivovideo.model.Layer
import it.fabriziodavi.vivovideo.model.LayerBorder
import it.fabriziodavi.vivovideo.model.LayerMask

/**
 * Forma, cornice e scontorno per i video sovrapposti, fotogramma per fotogramma sulla GPU:
 * il risultato ha la trasparenza che il compositore usa per fondere il video sopra alla traccia principale.
 */
class LayerMaskEffect(private val layer: Layer) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = MaskProgram(layer, useHdr)
}

private class MaskProgram(private val l: Layer, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    private val program: GlProgram = try { GlProgram(VERTEX, FRAGMENT) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    private var w = 1f
    private var h = 1f

    init {
        program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
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
            program.setFloatsUniform("uShape", floatArrayOf(l.shape.ordinal.toFloat()))
            program.setFloatsUniform("uBorder", floatArrayOf(l.border.ordinal.toFloat()))
            program.setFloatsUniform("uKey", floatArrayOf(l.key.ordinal.toFloat()))
            program.setFloatsUniform("uTol", floatArrayOf(l.keyTolerance))
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
        init {
            // Gli indici nello shader seguono l'ordine delle enum.
            check(LayerMask.NONE.ordinal == 0 && LayerMask.ROUNDED.ordinal == 1 && LayerMask.CIRCLE.ordinal == 2 &&
                LayerMask.HEART.ordinal == 3 && LayerMask.STAR.ordinal == 4 && LayerMask.SOFT.ordinal == 5)
            check(LayerBorder.NONE.ordinal == 0 && LayerBorder.WHITE.ordinal == 1 && LayerBorder.BLACK.ordinal == 2)
            check(KeyColor.NONE.ordinal == 0 && KeyColor.GREEN.ordinal == 1 && KeyColor.BLUE.ordinal == 2 &&
                KeyColor.WHITE.ordinal == 3 && KeyColor.BLACK.ordinal == 4)
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
uniform vec2 uRes;
uniform float uShape, uBorder, uKey, uTol;
varying vec2 vUv;

float dot2(vec2 v) { return dot(v, v); }
float sdRoundBox(vec2 p, vec2 b, float r) { vec2 q = abs(p) - b + r; return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r; }
float sdHeart(vec2 p) {
  p.x = abs(p.x);
  if (p.y + p.x > 1.0) return sqrt(dot2(p - vec2(0.25, 0.75))) - sqrt(2.0) / 4.0;
  return sqrt(min(dot2(p - vec2(0.0, 1.0)), dot2(p - 0.5 * max(p.x + p.y, 0.0)))) * sign(p.x - p.y);
}
float sdStar5(vec2 p, float r, float rf) {
  vec2 k1 = vec2(0.809016994375, -0.587785252292);
  vec2 k2 = vec2(-k1.x, k1.y);
  p.x = abs(p.x);
  p -= 2.0 * max(dot(k1, p), 0.0) * k1;
  p -= 2.0 * max(dot(k2, p), 0.0) * k2;
  p.x = abs(p.x);
  p.y -= r;
  vec2 ba = rf * vec2(-k1.y, k1.x) - vec2(0.0, 1.0);
  float hh = clamp(dot(p, ba) / dot(ba, ba), 0.0, r);
  return length(p - ba * hh) * sign(p.y * ba.x - p.x * ba.y);
}

void main() {
  vec4 src = texture2D(uTex, vUv);
  vec3 c = src.rgb;
  float alpha = 1.0;
  float shortSide = min(uRes.x, uRes.y);
  vec2 pp = (vUv - 0.5) * uRes;           // pixel dal centro
  float d = -1e5;                          // distanza dal bordo della forma (negativa = dentro), in pixel
  int shape = int(uShape + 0.5);
  if (shape == 0) d = sdRoundBox(pp, uRes * 0.5, 0.0);
  else if (shape == 1) d = sdRoundBox(pp, uRes * 0.5, shortSide * 0.08);
  else if (shape == 2 || shape == 5) {
    float k = length(pp / (uRes * 0.5));
    d = (k - 1.0) * shortSide * 0.5;
    if (shape == 5) alpha = 1.0 - smoothstep(0.55, 1.0, k);
  } else if (shape == 3) {
    vec2 hp = vec2((vUv.x - 0.5) * 1.25, vUv.y * 1.05 - 0.02);
    d = sdHeart(hp) * uRes.y / 1.05;
  } else if (shape == 4) {
    vec2 sp = pp / (shortSide * 0.5);
    d = sdStar5(vec2(sp.x, sp.y), 0.95, 0.45) * shortSide * 0.5;
  }
  if (shape != 5) alpha = clamp(0.5 - d, 0.0, 1.0);
  // Cornice lungo il bordo interno della forma.
  int border = int(uBorder + 0.5);
  if (border > 0 && shape != 5) {
    float bw = shortSide * 0.025;
    float inBorder = clamp(d + bw + 0.5, 0.0, 1.0);
    c = mix(c, border == 1 ? vec3(1.0) : vec3(0.0), inBorder);
  }
  // Scontorno.
  int key = int(uKey + 0.5);
  if (key > 0) {
    float kd = -1.0;
    if (key == 1) kd = c.g - max(c.r, c.b);
    else if (key == 2) kd = c.b - max(c.r, c.g);
    else if (key == 3) kd = (min(c.r, min(c.g, c.b)) - 0.75) * 2.0;
    else if (key == 4) kd = (0.25 - max(c.r, max(c.g, c.b))) * 2.0;
    float t = 0.08 + uTol * 0.5;
    float ka = kd <= 0.0 ? 1.0 : clamp((t - kd) / 0.08, 0.0, 1.0);
    if (key == 1) c.g = min(c.g, max(c.r, c.b));
    if (key == 2) c.b = min(c.b, max(c.r, c.g));
    alpha *= ka;
  }
  gl_FragColor = vec4(c, alpha * src.a);
}
"""
    }
}
