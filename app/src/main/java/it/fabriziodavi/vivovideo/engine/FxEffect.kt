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
import it.fabriziodavi.vivovideo.model.FadeColor
import it.fabriziodavi.vivovideo.model.FxType
import kotlin.math.exp

/**
 * Tutti gli effetti di una clip in un solo passaggio GPU, più le dissolvenze in entrata/uscita.
 * [beatsUs] = battiti della musica sul tempo del montaggio (per "Pulsa a ritmo" e "Lampi a ritmo");
 * [clipStartUs]/[clipOutputUs] servono alle dissolvenze (tempo del montaggio → tempo della clip).
 */
class FxEffect(
    private val fx: List<ClipFx>,
    private val beatsUs: List<Long>,
    private val clipStartUs: Long = 0,
    private val clipOutputUs: Long = 0,
    private val fadeInUs: Long = 0,
    private val fadeOutUs: Long = 0,
    private val fadeColor: FadeColor = FadeColor.BLACK,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = FxProgram(this, useHdr)

    internal fun amount(t: FxType): Float = fx.firstOrNull { it.type == t }?.amount ?: 0f

    /** Tempo dall'ultimo battito, in secondi (o un valore grande se non c'è). */
    private fun sinceBeat(timeUs: Long): Float {
        if (beatsUs.isEmpty()) return 99f
        var lo = 0; var hi = beatsUs.size - 1; var last = -1
        while (lo <= hi) { val m = (lo + hi) / 2; if (beatsUs[m] <= timeUs) { last = m; lo = m + 1 } else hi = m - 1 }
        return if (last < 0) 99f else (timeUs - beatsUs[last]) / 1e6f
    }

    /** Pulsazione: picco sul battito che si spegne in ~0,15 s. */
    internal fun pulseAt(timeUs: Long): Float {
        if (amount(FxType.PULSE) <= 0f) return 0f
        val dt = sinceBeat(timeUs)
        return if (dt > 0.6f) 0f else exp(-dt / 0.15f)
    }

    /** Lampo: brevissimo, sul battito. */
    internal fun strobeAt(timeUs: Long): Float {
        if (amount(FxType.STROBE) <= 0f) return 0f
        val dt = sinceBeat(timeUs)
        return if (dt > 0.3f) 0f else exp(-dt / 0.05f)
    }

    /** Quanto è coperta l'immagine dalla dissolvenza (0 = niente, 1 = tutto nero/bianco). */
    internal fun fadeAt(timeUs: Long): Float {
        if (fadeInUs <= 0 && fadeOutUs <= 0) return 0f
        val local = localTimeUs(timeUs, clipStartUs)
        var f = 0f
        if (fadeInUs > 0 && local < fadeInUs) f = maxOf(f, 1f - local.toFloat() / fadeInUs)
        if (fadeOutUs > 0 && clipOutputUs > 0 && local > clipOutputUs - fadeOutUs) {
            f = maxOf(f, (local - (clipOutputUs - fadeOutUs)).toFloat() / fadeOutUs)
        }
        val u = f.coerceIn(0f, 1f)
        return u * u * (3 - 2 * u)
    }

    internal val fadeWhite: Float get() = if (fadeColor == FadeColor.WHITE) 1f else 0f
}

private class FxProgram(private val effect: FxEffect, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    private val program: GlProgram = try { GlProgram(VERTEX, FX_FRAGMENT) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
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
            for ((type, name) in UNIFORMS) {
                val a = amounts.getValue(type) * when (type) {
                    FxType.PULSE -> effect.pulseAt(presentationTimeUs)
                    FxType.STROBE -> effect.strobeAt(presentationTimeUs)
                    else -> 1f
                }
                program.setFloatsUniform(name, floatArrayOf(a))
            }
            program.setFloatsUniform("uFade", floatArrayOf(effect.fadeAt(presentationTimeUs)))
            program.setFloatsUniform("uFadeWhite", floatArrayOf(effect.fadeWhite))
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
        val UNIFORMS get() = FX_UNIFORMS
    }
}

/** Nome dell'uniforme di ogni effetto nello shader (ognuna dev'essere usata, altrimenti il driver la toglie). */
internal val FX_UNIFORMS: Map<FxType, String> = mapOf(
            FxType.BW to "uBW", FxType.SEPIA to "uSepia", FxType.VIVID to "uVivid", FxType.DUOTONE to "uDuo",
            FxType.HUE to "uHue", FxType.POSTERIZE to "uPoster", FxType.INVERT to "uInvert",
            FxType.FILM to "uGrain", FxType.OLDFILM to "uOld", FxType.VHS to "uVHS", FxType.GLITCH to "uGlitch",
            FxType.CARTOON to "uCartoon", FxType.HALFTONE to "uHalftone", FxType.PIXEL to "uPixel",
            FxType.LIGHTLEAK to "uLeak", FxType.GLOW to "uGlow",
            FxType.BLUR to "uBlur", FxType.ZOOMBLUR to "uZoomBlur", FxType.TILTSHIFT to "uTilt", FxType.VIGNETTE to "uVignette",
            FxType.FISHEYE to "uFish", FxType.CHROMA to "uChroma", FxType.WAVE to "uWave",
            FxType.MIRROR to "uMirror", FxType.KALEIDO to "uKaleido", FxType.GRID to "uGrid",
            FxType.SHAKE to "uShake", FxType.PULSE to "uPulse", FxType.STROBE to "uStrobe",
        )

private const val VERTEX = """
attribute vec4 aFramePosition;
varying vec2 vUv;
void main() {
  gl_Position = aFramePosition;
  vUv = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
}
"""
internal const val FX_FRAGMENT = """
precision highp float;
uniform sampler2D uTex;
uniform vec2 uRes;
uniform float uTime;
uniform float uBW, uSepia, uVivid, uDuo, uHue, uPoster, uInvert;
uniform float uGrain, uOld, uVHS, uGlitch, uCartoon, uHalftone, uPixel, uLeak, uGlow;
uniform float uBlur, uZoomBlur, uTilt, uVignette, uFish, uChroma, uWave;
uniform float uMirror, uKaleido, uGrid, uShake, uPulse, uStrobe;
uniform float uFade, uFadeWhite;
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
float lum(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
vec3 rgb2hsv(vec3 c) {
  vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
  vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
  vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
  float d = q.x - min(q.w, q.y);
  return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + 1e-10)), d / (q.x + 1e-10), q.x);
}
vec3 hsv2rgb(vec3 c) {
  vec3 p = abs(fract(c.xxx + vec3(1.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
  return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y);
}

void main() {
  vec2 uv = vUv;
  float aspect = uRes.x / uRes.y;
  // --- Forma ---
  if (uGrid > 0.0) { float n = floor(mix(2.0, 4.99, uGrid)); uv = fract(uv * n); }
  if (uKaleido > 0.0) {
    vec2 p = uv - 0.5; p.x *= aspect;
    float r = length(p);
    float seg = 6.2831853 / floor(mix(4.0, 12.99, uKaleido));
    float a = mod(atan(p.y, p.x) + uTime * 0.1, seg);
    a = abs(a - seg * 0.5);
    p = vec2(cos(a), sin(a)) * r; p.x /= aspect;
    uv = p + 0.5;
  }
  if (uMirror > 0.5 && uv.x > 0.5) uv.x = 1.0 - uv.x;
  // --- Movimento ---
  if (uShake > 0.0) uv += (vec2(noise1(uTime * 9.0), noise1(uTime * 11.0 + 5.0)) - 0.5) * 0.03 * uShake;
  if (uPulse > 0.0) uv = (uv - 0.5) / (1.0 + 0.09 * uPulse) + 0.5;
  // --- Ottica che deforma ---
  if (uFish > 0.0) {
    vec2 p = (uv - 0.5) * 2.0; p.x *= aspect;
    float r2 = dot(p, p);
    p *= (1.0 - 0.25 * uFish) + 0.125 * uFish * r2;
    p.x /= aspect;
    uv = p * 0.5 + 0.5;
  }
  if (uWave > 0.0) uv += vec2(sin(uv.y * 18.0 + uTime * 3.0), cos(uv.x * 14.0 + uTime * 2.5)) * 0.012 * uWave;
  if (uPixel > 0.0) {
    float n = mix(160.0, 18.0, uPixel);
    vec2 cells = vec2(n, n / aspect);
    uv = (floor(uv * cells) + 0.5) / cells;
  }
  if (uGlitch > 0.0) {
    float tt = floor(uTime * 12.0);
    float row = floor(uv.y * 28.0);
    if (hash(vec2(row, tt)) < 0.22 * uGlitch) uv.x += (hash(vec2(tt, row)) - 0.5) * 0.18 * uGlitch;
  }
  // --- Campionamento (sfocature) ---
  vec3 c;
  if (uBlur > 0.0) {
    c = vec3(0.0);
    float r = 0.025 * uBlur;
    for (int i = 0; i < 16; i++) {
      float a = float(i) * 2.3999;
      float d = sqrt((float(i) + 0.5) / 16.0);
      c += tex(uv + vec2(cos(a), sin(a) * aspect) * r * d);
    }
    c /= 16.0;
  } else if (uZoomBlur > 0.0) {
    c = vec3(0.0);
    for (int i = 0; i < 12; i++) c += tex(mix(uv, vec2(0.5), float(i) / 11.0 * 0.12 * uZoomBlur));
    c /= 12.0;
  } else if (uTilt > 0.0) {
    float band = smoothstep(0.12, 0.42, abs(uv.y - 0.5));
    float r = 0.022 * uTilt * band;
    c = vec3(0.0);
    for (int i = 0; i < 12; i++) {
      float a = float(i) * 2.3999;
      float d = sqrt((float(i) + 0.5) / 12.0);
      c += tex(uv + vec2(cos(a), sin(a) * aspect) * r * d);
    }
    c /= 12.0;
    c = mix(vec3(lum(c)), c, 1.0 + 0.35 * uTilt);
  } else {
    c = tex(uv);
  }
  if (uChroma > 0.0) { vec2 d = (uv - 0.5) * 0.025 * uChroma; c.r = tex(uv + d).r; c.b = tex(uv - d).b; }
  float split = 0.008 * uGlitch * step(0.6, hash(vec2(floor(uTime * 8.0), 3.0))) + 0.0035 * uVHS;
  if (split > 0.0) { c.r = tex(uv + vec2(split, 0.0)).r; c.b = tex(uv - vec2(split, 0.0)).b; }
  if (uGlow > 0.0) {
    vec3 g = vec3(0.0);
    for (int i = 0; i < 12; i++) {
      float a = float(i) * 0.5236;
      g += max(tex(uv + vec2(cos(a), sin(a) * aspect) * 0.02) - 0.55, 0.0);
    }
    c += g / 12.0 * 2.2 * uGlow;
  }
  // --- Stile ---
  if (uCartoon > 0.0) {
    vec2 px = 1.5 / uRes;
    float gx = lum(tex(uv + vec2(px.x, 0.0))) - lum(tex(uv - vec2(px.x, 0.0)));
    float gy = lum(tex(uv + vec2(0.0, px.y))) - lum(tex(uv - vec2(0.0, px.y)));
    float edge = smoothstep(0.06, 0.22, length(vec2(gx, gy)));
    c = mix(c, floor(c * 5.0 + 0.5) / 5.0, uCartoon);
    c *= 1.0 - edge * uCartoon;
  }
  if (uHalftone > 0.0) {
    float s = mix(6.0, 18.0, uHalftone);
    vec2 f = fract(vUv * uRes / s) - 0.5;
    float rad = sqrt(1.0 - clamp(lum(c), 0.0, 1.0)) * 0.6;
    float dd = 1.0 - smoothstep(rad - 0.08, rad + 0.08, length(f));
    c = mix(c, mix(vec3(1.0), c * 0.55, dd), uHalftone);
  }
  if (uPoster > 0.0) { float lv = mix(12.0, 3.0, uPoster); c = floor(c * lv + 0.5) / lv; }
  // --- Colore ---
  if (uVivid > 0.0) {
    c = mix(vec3(lum(c)), c, 1.0 + uVivid);
    vec3 cc = clamp(c, 0.0, 1.0);
    c = mix(c, cc * cc * (3.0 - 2.0 * cc), 0.4 * uVivid);
  }
  if (uHue > 0.0) {
    vec3 hv = rgb2hsv(clamp(c, 0.0, 1.0));
    hv.x = fract(hv.x + uTime * 0.15 * uHue + vUv.x * 0.3 * uHue);
    hv.y = min(1.0, hv.y + 0.2 * uHue);
    c = hsv2rgb(hv);
  }
  c = mix(c, vec3(lum(c)), uBW);
  if (uSepia > 0.0) {
    vec3 sp = vec3(dot(c, vec3(0.393, 0.769, 0.189)), dot(c, vec3(0.349, 0.686, 0.168)), dot(c, vec3(0.272, 0.534, 0.131)));
    c = mix(c, sp, uSepia);
  }
  if (uDuo > 0.0) {
    vec3 duo = mix(vec3(0.10, 0.05, 0.35), vec3(1.0, 0.62, 0.30), smoothstep(0.0, 1.0, lum(c)));
    c = mix(c, duo, uDuo);
  }
  if (uInvert > 0.5) c = 1.0 - clamp(c, 0.0, 1.0);
  if (uVHS > 0.0) {
    c *= 1.0 - 0.10 * uVHS * (0.5 + 0.5 * sin(vUv.y * uRes.y * 1.6));
    c = mix(c, c * vec3(1.06, 0.97, 1.1) + vec3(0.02, 0.0, 0.04), uVHS);
    float bandV = step(0.985, fract(vUv.y * 0.7 - uTime * 0.35));
    c += bandV * 0.12 * uVHS * hash(vUv * uRes + uTime);
  }
  if (uGrain > 0.0) {
    c = mix(c, c * vec3(1.04, 0.98, 0.9) * 0.92 + 0.05, 0.6 * uGrain);
    c += (hash(vUv * uRes + fract(uTime * 37.0) * 100.0) - 0.5) * 0.14 * uGrain;
  }
  if (uOld > 0.0) {
    float fr = floor(uTime * 24.0);
    vec3 sp2 = vec3(dot(c, vec3(0.393, 0.769, 0.189)), dot(c, vec3(0.349, 0.686, 0.168)), dot(c, vec3(0.272, 0.534, 0.131)));
    c = mix(c, sp2 * 0.95, 0.7 * uOld);
    c *= 1.0 - 0.10 * uOld * noise1(uTime * 24.0);
    float sx = hash(vec2(fr, 7.0));
    float scratch = (1.0 - smoothstep(0.0, 0.0018, abs(vUv.x - sx))) * step(0.55, hash(vec2(fr, 3.0)));
    c = mix(c, vec3(0.92), scratch * 0.8 * uOld);
    float dust = step(0.9992, hash(floor(vUv * uRes / 3.0) + fr));
    c = mix(c, vec3(0.08), dust * uOld);
    c += (hash(vUv * uRes + fr) - 0.5) * 0.12 * uOld;
    vec2 dv = vUv - 0.5; dv.x *= aspect;
    c *= 1.0 - 0.5 * uOld * smoothstep(0.35, 1.0, length(dv));
  }
  if (uLeak > 0.0) {
    vec2 lp = vUv - vec2(0.15 + 0.25 * sin(uTime * 0.4), 0.85 - 0.1 * cos(uTime * 0.3));
    lp.x *= aspect;
    vec3 leak = vec3(1.0, 0.55, 0.22) * exp(-dot(lp, lp) * 2.5) * uLeak;
    c = 1.0 - (1.0 - clamp(c, 0.0, 1.0)) * (1.0 - leak);
  }
  if (uVignette > 0.0) {
    vec2 d = vUv - 0.5;
    d.x *= aspect;
    c *= 1.0 - uVignette * smoothstep(0.3, 0.95, length(d) * 1.1);
  }
  if (uStrobe > 0.0) c = mix(c, vec3(1.0), uStrobe);
  c = mix(c, vec3(uFadeWhite), uFade);
  gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""
