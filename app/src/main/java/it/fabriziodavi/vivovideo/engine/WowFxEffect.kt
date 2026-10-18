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
 * Effetti premium (2026): sogno, pellicola 35 mm, duotono pop, lampo di luce, tre schermi, olografico,
 * e quelli che seguono i battiti (zoom, scossa, RGB). Shader separato dagli effetti classici.
 */
class WowFxEffect(
    private val fx: List<ClipFx>,
    private val beatsUs: List<Long>,
    private val clipStartUs: Long = 0,
) : GlEffect {
    internal val startUs: Long get() = clipStartUs
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = WowProgram(this, useHdr)

    internal fun amount(t: FxType): Float = fx.firstOrNull { it.type == t }?.amount ?: 0f

    private fun sinceBeat(timeUs: Long): Float {
        if (beatsUs.isEmpty()) return 99f
        var lo = 0; var hi = beatsUs.size - 1; var last = -1
        while (lo <= hi) { val m = (lo + hi) / 2; if (beatsUs[m] <= timeUs) { last = m; lo = m + 1 } else hi = m - 1 }
        return if (last < 0) 99f else (timeUs - beatsUs[last]) / 1e6f
    }

    /** Impulso sul battito (1 sul colpo, si spegne in [decayS] secondi). */
    internal fun beatAt(timeUs: Long, decayS: Float): Float {
        val dt = sinceBeat(timeUs)
        return if (dt > decayS * 6) 0f else exp(-dt / decayS)
    }

    /** Numero pseudo-casuale stabile per il battito in corso (direzione della scossa). */
    internal fun beatSeed(timeUs: Long): Float = ((timeUs - (sinceBeat(timeUs) * 1e6f).toLong()) / 1000 % 997).toFloat()

    companion object {
        /** Ogni effetto premium ha la sua uniforme (tutte usate nello shader). */
        val UNIFORMS: Map<FxType, String> = mapOf(
            FxType.DREAMY to "uDreamy", FxType.FILM35 to "uFilm35", FxType.DUOPOP to "uDuoPop", FxType.SWEEP to "uSweep",
            FxType.SPLIT3 to "uSplit3", FxType.HOLO to "uHolo", FxType.ZOOMBEAT to "uZoomBeat", FxType.SHAKEBEAT to "uShakeBeat",
            FxType.RGBBEAT to "uRgbBeat", FxType.FLARE to "uFlare", FxType.DUST to "uDust", FxType.LETTERBOX to "uLetterbox",
        )

        fun needed(fx: List<ClipFx>) = fx.any { it.type.wow2 && it.amount > 0f }

        internal const val FRAGMENT = """
precision highp float;
uniform sampler2D uTex;
uniform vec2 uRes;
uniform float uTime;
uniform float uSeed;
uniform float uDreamy, uFilm35, uDuoPop, uSweep, uSplit3, uHolo, uZoomBeat, uShakeBeat, uRgbBeat;
uniform float uFlare, uDust, uLetterbox;
uniform float uLocal;
varying vec2 vUv;

float hash(vec2 p) {
  vec3 p3 = fract(vec3(p.xyx) * 0.1031);
  p3 += dot(p3, p3.yzx + 33.33);
  return fract((p3.x + p3.y) * p3.z);
}
vec2 mirrorUv(vec2 uv) { return 1.0 - abs(1.0 - mod(uv, 2.0)); }
vec3 tex(vec2 uv) { return texture2D(uTex, mirrorUv(uv)).rgb; }
float lum(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

void main() {
  vec2 uv = vUv;
  float aspect = uRes.x / uRes.y;
  // Tre schermi: tre copie dello stesso fotogramma, quella centrale piena (separatori sottili).
  float sep = 1.0;
  if (uSplit3 > 0.5) {
    bool vert = aspect < 1.0;
    float p = vert ? uv.y : uv.x;
    float cell = floor(p * 3.0);
    float f = fract(p * 3.0);
    float q = (f - 0.5) / 3.0 + 0.5;
    if (vert) uv.y = q; else uv.x = q;
    sep = smoothstep(0.0, 0.012, f) * smoothstep(0.0, 0.012, 1.0 - f);
    if (cell != 1.0) sep *= 0.82;
  }
  // Zoom a ritmo: spinta in avanti sul colpo, con scia radiale.
  vec2 c = uv - 0.5;
  float z = 1.0 + 0.18 * uZoomBeat;
  uv = c / z + 0.5;
  // Scossa a ritmo: scatto in una direzione diversa a ogni colpo.
  if (uShakeBeat > 0.0) {
    float a = hash(vec2(uSeed, 3.1)) * 6.2831;
    uv += vec2(cos(a), sin(a) * aspect) * 0.035 * uShakeBeat;
  }
  vec3 col;
  if (uZoomBeat > 0.02 || uShakeBeat > 0.02) {
    col = vec3(0.0);
    for (int i = 0; i < 8; i++) {
      float k = float(i) / 7.0;
      col += tex(uv - (uv - 0.5) * k * 0.06 * uZoomBeat - vec2(0.012, 0.0) * k * uShakeBeat);
    }
    col /= 8.0;
  } else col = tex(uv);
  // RGB a ritmo: canali che si separano sul colpo.
  if (uRgbBeat > 0.0) {
    vec2 d = vec2(0.018 * uRgbBeat, 0.0);
    col.r = tex(uv + d).r; col.b = tex(uv - d).b;
  }
  // Sogno: alone morbido e luminoso, toni caldi sollevati.
  if (uDreamy > 0.0) {
    vec3 g = vec3(0.0);
    for (int i = 0; i < 12; i++) {
      float a = float(i) * 0.5236;
      g += tex(uv + vec2(cos(a), sin(a) * aspect) * 0.012);
    }
    g /= 12.0;
    vec3 bloom = max(g - 0.45, 0.0) * 1.8;
    col = mix(col, 1.0 - (1.0 - col) * (1.0 - g * 0.6), 0.45 * uDreamy) + bloom * uDreamy * 0.6;
    col = mix(col, col * vec3(1.06, 1.0, 0.92) + 0.04, uDreamy * 0.5);
  }
  // Pellicola 35 mm: grana fine, alone rosso attorno alle luci (halation), nero morbido, piccolo tremolio.
  if (uFilm35 > 0.0) {
    vec3 h = vec3(0.0);
    for (int i = 0; i < 8; i++) { float a = float(i) * 0.785; h += tex(uv + vec2(cos(a), sin(a) * aspect) * 0.006); }
    h /= 8.0;
    float hl = max(lum(h) - 0.6, 0.0);
    col += vec3(1.0, 0.25, 0.1) * hl * 0.9 * uFilm35;
    col = mix(col, col * 0.92 + 0.05, uFilm35 * 0.6);
    float grain = hash(floor(vUv * uRes / 1.5) + floor(uTime * 24.0)) - 0.5;
    col += grain * 0.07 * uFilm35;
    col *= 1.0 + (hash(vec2(floor(uTime * 24.0), 7.0)) - 0.5) * 0.04 * uFilm35;
  }
  // Duotono pop: due colori accesi che cambiano piano nel tempo.
  if (uDuoPop > 0.0) {
    float l = lum(col);
    float t = uTime * 0.15;
    vec3 a = 0.5 + 0.5 * cos(6.2831 * (vec3(0.0, 0.33, 0.67) + t));
    vec3 b = 0.5 + 0.5 * cos(6.2831 * (vec3(0.5, 0.83, 0.17) + t));
    vec3 duo = mix(a * 0.25, b * 1.05 + 0.1, smoothstep(0.05, 0.95, l));
    col = mix(col, duo, uDuoPop);
  }
  // Lampo di luce: una striscia diagonale che attraversa il quadro ogni 2,5 s.
  if (uSweep > 0.0) {
    float ph = fract(uTime / 2.5);
    float pos = ph * 2.4 - 0.7;
    float d = (vUv.x + vUv.y * 0.6) / 1.6 - pos;
    float band = exp(-d * d * 900.0) + 0.35 * exp(-d * d * 120.0);
    col += vec3(1.0, 0.95, 0.85) * band * uSweep * 0.75;
  }
  // Olografico: riflessi iridescenti che scorrono sulle zone chiare.
  if (uHolo > 0.0) {
    float l = lum(col);
    vec3 iri = 0.5 + 0.5 * cos(6.2831 * (vec3(0.0, 0.33, 0.67) + vUv.x * 1.5 + vUv.y * 0.8 + uTime * 0.3 + l * 1.2));
    col = mix(col, col * 0.6 + iri * 0.55, uHolo * smoothstep(0.2, 0.9, l) * 0.8);
  }
  // Bagliore anamorfico: strisce orizzontali azzurre dalle luci forti, più un riflesso che scorre.
  if (uFlare > 0.0) {
    vec3 streak = vec3(0.0);
    for (int i = -6; i <= 6; i++) {
      float k = float(i) / 6.0;
      vec3 s = tex(vec2(uv.x + k * 0.12, uv.y));
      streak += max(s - 0.75, 0.0) * (1.0 - abs(k));
    }
    col += streak * vec3(0.35, 0.65, 1.0) * 0.9 * uFlare;
    float fx = fract(uTime * 0.07) * 1.6 - 0.3;
    vec2 fp = vec2(fx, 0.35 + 0.1 * sin(uTime * 0.5));
    vec2 dd = vUv - fp; dd.x *= aspect;
    col += vec3(0.4, 0.7, 1.0) * exp(-dd.y * dd.y * 2500.0) * exp(-abs(dd.x) * 1.5) * 0.5 * uFlare;
    col += vec3(1.0, 0.85, 0.6) * exp(-dot(dd, dd) * 300.0) * 0.4 * uFlare;
  }
  // Polvere di pellicola: granelli e graffi che cambiano a ogni fotogramma (24 al secondo).
  if (uDust > 0.0) {
    float fr = floor(uTime * 24.0);
    vec2 cell = floor(vUv * vec2(90.0 * aspect, 90.0));
    float h = hash(cell + fr * 7.13);
    float speck = step(0.9985, h);
    float lineX = hash(vec2(fr, 1.0));
    float scratch = step(0.6, hash(vec2(fr, 2.0))) * exp(-abs(vUv.x - lineX) * uRes.x * 0.8);
    col = mix(col, vec3(0.08), speck * 0.8 * uDust);
    col += vec3(0.6) * scratch * 0.5 * uDust;
    col *= 1.0 - 0.12 * uDust * smoothstep(0.4, 0.95, length((vUv - 0.5) * vec2(aspect, 1.0)));
  }
  // Bande cinema: entrano nel primo mezzo secondo della clip (formato 2,39:1 in orizzontale).
  if (uLetterbox > 0.5) {
    float target = aspect >= 1.0 ? max(0.0, (1.0 - aspect / 2.39) * 0.5) : 0.1;
    float h = target * smoothstep(0.0, 0.5, uLocal);
    col *= step(h, vUv.y) * step(h, 1.0 - vUv.y);
  }
  col *= sep;
  gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
"""
    }
}

private class WowProgram(private val effect: WowFxEffect, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    private val program: GlProgram = try { GlProgram(VERTEX, HdrGl.fragment(WowFxEffect.FRAGMENT, useHdr)) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    private var w = 1f
    private var h = 1f
    private val amounts = WowFxEffect.UNIFORMS.keys.associateWith { effect.amount(it) }

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
            program.setFloatsUniform("uTime", floatArrayOf(((presentationTimeUs / 1000) % 100_000) / 1000f))
            program.setFloatsUniform("uSeed", floatArrayOf(effect.beatSeed(presentationTimeUs)))
            program.setFloatsUniform("uLocal", floatArrayOf(localTimeUs(presentationTimeUs, effect.startUs) / 1e6f))
            for ((type, name) in WowFxEffect.UNIFORMS) {
                val a = amounts.getValue(type) * when (type) {
                    FxType.ZOOMBEAT -> effect.beatAt(presentationTimeUs, 0.14f)
                    FxType.SHAKEBEAT -> effect.beatAt(presentationTimeUs, 0.09f)
                    FxType.RGBBEAT -> effect.beatAt(presentationTimeUs, 0.1f)
                    else -> 1f
                }
                program.setFloatsUniform(name, floatArrayOf(a))
            }
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
