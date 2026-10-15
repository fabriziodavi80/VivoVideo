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
import it.fabriziodavi.vivovideo.model.ClipFx
import it.fabriziodavi.vivovideo.model.FxType
import kotlin.math.exp

/**
 * Tutti gli effetti di una clip in un solo passaggio GPU (sfocatura, vignettatura, pellicola,
 * bianco e nero, bagliore, glitch, VHS, scossa, pulsazione a ritmo, pixel, specchio).
 * [beatsUs] = battiti della musica sul tempo del montaggio (per "Pulsa a ritmo").
 */
class FxEffect(private val fx: List<ClipFx>, private val beatsUs: List<Long>) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = FxProgram(this, useHdr)

    internal fun amount(t: FxType): Float = fx.firstOrNull { it.type == t }?.amount ?: 0f

    /** Pulsazione: picco sul battito che si spegne in ~0,15 s. */
    internal fun pulseAt(timeUs: Long): Float {
        if (amount(FxType.PULSE) <= 0f || beatsUs.isEmpty()) return 0f
        var lo = 0; var hi = beatsUs.size - 1; var last = -1
        while (lo <= hi) { val m = (lo + hi) / 2; if (beatsUs[m] <= timeUs) { last = m; lo = m + 1 } else hi = m - 1 }
        if (last < 0) return 0f
        val dt = (timeUs - beatsUs[last]) / 1e6f
        return if (dt > 0.6f) 0f else exp(-dt / 0.15f)
    }
}

private class FxProgram(private val effect: FxEffect, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    private val program: GlProgram = try { GlProgram(VERTEX, FRAGMENT) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    private var w = 1f
    private var h = 1f
    private val amounts = FxType.entries.associateWith { effect.amount(it) }

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
            // Tempo in secondi, ripiegato per non perdere precisione nello shader.
            program.setFloatsUniform("uTime", floatArrayOf(((presentationTimeUs / 1000) % 100_000) / 1000f))
            program.setFloatsUniform("uBlur", floatArrayOf(amounts.getValue(FxType.BLUR)))
            program.setFloatsUniform("uVignette", floatArrayOf(amounts.getValue(FxType.VIGNETTE)))
            program.setFloatsUniform("uGrain", floatArrayOf(amounts.getValue(FxType.FILM)))
            program.setFloatsUniform("uBW", floatArrayOf(amounts.getValue(FxType.BW)))
            program.setFloatsUniform("uGlow", floatArrayOf(amounts.getValue(FxType.GLOW)))
            program.setFloatsUniform("uGlitch", floatArrayOf(amounts.getValue(FxType.GLITCH)))
            program.setFloatsUniform("uVHS", floatArrayOf(amounts.getValue(FxType.VHS)))
            program.setFloatsUniform("uShake", floatArrayOf(amounts.getValue(FxType.SHAKE)))
            program.setFloatsUniform("uPulse", floatArrayOf(amounts.getValue(FxType.PULSE) * effect.pulseAt(presentationTimeUs)))
            program.setFloatsUniform("uPixel", floatArrayOf(amounts.getValue(FxType.PIXEL)))
            program.setFloatsUniform("uMirror", floatArrayOf(amounts.getValue(FxType.MIRROR)))
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
uniform vec2 uRes;
uniform float uTime, uBlur, uVignette, uGrain, uBW, uGlow, uGlitch, uVHS, uShake, uPulse, uPixel, uMirror;
varying vec2 vUv;

// Hash senza seno: stabile anche sulle GPU dei telefoni.
float hash(vec2 p) {
  vec3 p3 = fract(vec3(p.xyx) * 0.1031);
  p3 += dot(p3, p3.yzx + 33.33);
  return fract((p3.x + p3.y) * p3.z);
}
float noise1(float x) { float i = floor(x); float f = fract(x); return mix(hash(vec2(i, 1.7)), hash(vec2(i + 1.0, 1.7)), f * f * (3.0 - 2.0 * f)); }
vec2 mirrorUv(vec2 uv) { return 1.0 - abs(1.0 - mod(uv, 2.0)); }
vec3 tex(vec2 uv) { return texture2D(uTex, mirrorUv(uv)).rgb; }

void main() {
  vec2 uv = vUv;
  if (uShake > 0.0) uv += (vec2(noise1(uTime * 9.0), noise1(uTime * 11.0 + 5.0)) - 0.5) * 0.03 * uShake;
  if (uPulse > 0.0) uv = (uv - 0.5) / (1.0 + 0.09 * uPulse) + 0.5;
  if (uMirror > 0.5 && uv.x > 0.5) uv.x = 1.0 - uv.x;
  if (uPixel > 0.0) {
    float n = mix(160.0, 18.0, uPixel);
    vec2 cells = vec2(n, n * uRes.y / uRes.x);
    uv = (floor(uv * cells) + 0.5) / cells;
  }
  if (uGlitch > 0.0) {
    float tt = floor(uTime * 12.0);
    float row = floor(uv.y * 28.0);
    if (hash(vec2(row, tt)) < 0.22 * uGlitch) uv.x += (hash(vec2(tt, row)) - 0.5) * 0.18 * uGlitch;
  }
  vec3 c;
  if (uBlur > 0.0) {
    c = vec3(0.0);
    float r = 0.025 * uBlur;
    for (int i = 0; i < 16; i++) {
      float a = float(i) * 2.3999;
      float d = sqrt((float(i) + 0.5) / 16.0);
      c += tex(uv + vec2(cos(a), sin(a) * uRes.x / uRes.y) * r * d);
    }
    c /= 16.0;
  } else {
    c = tex(uv);
  }
  float split = 0.008 * uGlitch * step(0.6, hash(vec2(floor(uTime * 8.0), 3.0))) + 0.0035 * uVHS;
  if (split > 0.0) { c.r = tex(uv + vec2(split, 0.0)).r; c.b = tex(uv - vec2(split, 0.0)).b; }
  if (uGlow > 0.0) {
    vec3 g = vec3(0.0);
    for (int i = 0; i < 12; i++) {
      float a = float(i) * 0.5236;
      g += max(tex(uv + vec2(cos(a), sin(a) * uRes.x / uRes.y) * 0.02) - 0.55, 0.0);
    }
    c += g / 12.0 * 2.2 * uGlow;
  }
  float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
  c = mix(c, vec3(luma), uBW);
  if (uVHS > 0.0) {
    c *= 1.0 - 0.10 * uVHS * (0.5 + 0.5 * sin(vUv.y * uRes.y * 1.6));
    c = mix(c, c * vec3(1.06, 0.97, 1.1) + vec3(0.02, 0.0, 0.04), uVHS);
    float band = step(0.985, fract(vUv.y * 0.7 - uTime * 0.35));
    c += band * 0.12 * uVHS * hash(vUv * uRes + uTime);
  }
  if (uGrain > 0.0) {
    c = mix(c, c * vec3(1.04, 0.98, 0.9) * 0.92 + 0.05, 0.6 * uGrain);
    c += (hash(vUv * uRes + fract(uTime * 37.0) * 100.0) - 0.5) * 0.14 * uGrain;
  }
  if (uVignette > 0.0) {
    vec2 d = vUv - 0.5;
    d.x *= uRes.x / uRes.y;
    c *= 1.0 - uVignette * smoothstep(0.3, 0.95, length(d) * 1.1);
  }
  gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""
    }
}
