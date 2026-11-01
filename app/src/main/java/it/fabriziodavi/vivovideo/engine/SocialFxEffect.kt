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
import it.fabriziodavi.vivovideo.model.FxCategory
import it.fabriziodavi.vivovideo.model.FxType
import kotlin.math.exp

/**
 * Effetti "Social 2026" per reel e stories (01/11/2026): zoom a scatti, eco a ritmo, griglia a ritmo, aura, termocamera,
 * cromo Y2K, colata di pixel, glitch a fette e iper-saturo. Shader a parte; molti seguono i battiti della musica
 * (senza musica, un battito ogni mezzo secondo, così l'effetto si vede lo stesso).
 */
class SocialFxEffect(
    private val fx: List<ClipFx>,
    private val beatsUs: List<Long>,
    private val clipStartUs: Long = 0,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = SocialProgram(this, useHdr)

    internal fun amount(t: FxType): Float = fx.firstOrNull { it.type == t }?.amount ?: 0f

    /** Battiti: quelli della musica o, senza musica, uno ogni mezzo secondo dall'inizio della clip. */
    private fun lastBeat(timeUs: Long): Pair<Int, Long> {
        if (beatsUs.isEmpty()) {
            val local = (timeUs - clipStartUs).coerceAtLeast(0)
            val n = (local / 500_000L).toInt()
            return n to (clipStartUs + n * 500_000L)
        }
        var lo = 0; var hi = beatsUs.size - 1; var last = -1
        while (lo <= hi) { val m = (lo + hi) / 2; if (beatsUs[m] <= timeUs) { last = m; lo = m + 1 } else hi = m - 1 }
        return if (last < 0) -1 to Long.MIN_VALUE else last to beatsUs[last]
    }

    /** Numero del battito in corso (da 0), -1 prima del primo. */
    internal fun beatIndex(timeUs: Long): Int = lastBeat(timeUs).first

    /** Impulso sul battito: 1 sul colpo, si spegne in [decayS] secondi. */
    internal fun pulse(timeUs: Long, decayS: Float): Float {
        val (i, at) = lastBeat(timeUs)
        if (i < 0) return 0f
        val dt = (timeUs - at) / 1e6f
        return if (dt > decayS * 6) 0f else exp(-dt / decayS)
    }

    companion object {
        val UNIFORMS: Map<FxType, String> = mapOf(
            FxType.STEPZOOM to "uStepZoom", FxType.ECHOBEAT to "uEcho", FxType.QUADBEAT to "uQuad", FxType.AURA to "uAura",
            FxType.THERMAL to "uThermal", FxType.CHROME to "uChrome", FxType.MELT to "uMelt", FxType.SLICEGLITCH to "uSliceGlitch",
            FxType.HYPER to "uHyper",
        )

        fun needed(fx: List<ClipFx>) = fx.any { it.type.category == FxCategory.SOCIAL && it.amount > 0f }

        /** Quanti scalini fa lo zoom a scatti prima di ripartire. */
        const val STEPS = 4

        internal const val FRAGMENT = """
precision highp float;
uniform sampler2D uTex;
uniform vec2 uRes;
uniform float uTime;
uniform float uBeat;
uniform float uPulse;
uniform float uStepZoom, uEcho, uQuad, uAura, uThermal, uChrome, uMelt, uSliceGlitch, uHyper;
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
  float sep = 1.0;
  float cellOn = 1.0;
  // Griglia a ritmo: quattro riquadri dello stesso video; a ogni battito se ne accende un altro, gli altri restano spenti.
#if defined(FX_QUADBEAT)
  if (uQuad > 0.0) {
    vec2 cell = floor(uv * 2.0);
    float idx = cell.x + (1.0 - cell.y) * 2.0;
    float on = mod(uBeat, 4.0);
    vec2 f = fract(uv * 2.0);
    uv = mix(uv, f, uQuad);
    sep = mix(1.0, smoothstep(0.0, 0.008, f.x) * smoothstep(0.0, 0.008, 1.0 - f.x) * smoothstep(0.0, 0.008, f.y) * smoothstep(0.0, 0.008, 1.0 - f.y), uQuad);
    cellOn = abs(idx - on) < 0.5 ? 1.0 : 0.0;
  }
#endif
  // Zoom a scatti: a ogni battito l'inquadratura avanza di uno scalino secco (quattro, poi riparte), con un filo di scia.
  float z = 1.0;
#if defined(FX_STEPZOOM)
  if (uStepZoom > 0.0) z = 1.0 + 0.09 * uStepZoom * mod(uBeat, 4.0);
#endif
  vec2 c0 = uv - 0.5;
  uv = c0 / z + 0.5;
  vec3 col = tex(uv);
#if defined(FX_STEPZOOM)
  if (uStepZoom > 0.0 && uPulse > 0.3) {
    vec3 acc = col;
    for (int i = 1; i < 6; i++) acc += tex(c0 / (z * (1.0 + 0.012 * float(i) * uPulse)) + 0.5);
    col = acc / 6.0;
  }
#endif
  // Glitch a fette: sul colpo alcune fasce orizzontali scattano di lato con i colori separati (pulito, non da VHS).
#if defined(FX_SLICEGLITCH)
  if (uSliceGlitch > 0.0 && uPulse > 0.05) {
    float band = floor(uv.y * 14.0);
    float h = hash(vec2(band, floor(uBeat)));
    if (h > 0.62) {
      float off = (hash(vec2(band, floor(uBeat) + 7.0)) - 0.5) * 0.16 * uSliceGlitch * uPulse;
      col = vec3(tex(uv + vec2(off + 0.008 * uPulse, 0.0)).r, tex(uv + vec2(off, 0.0)).g, tex(uv + vec2(off - 0.008 * uPulse, 0.0)).b);
    }
  }
#endif
  // Colata di pixel (pixel sorting): le luci colano verso il basso in strisce, più lunghe sul battito.
#if defined(FX_MELT)
  if (uMelt > 0.0) {
    float len = (0.06 + 0.16 * uPulse) * uMelt;
    float jitter = 0.6 + 0.8 * hash(vec2(floor(uv.x * uRes.x / 3.0), 1.0));
    vec3 best = col;
    for (int i = 1; i < 14; i++) {
      float k = float(i) / 13.0;
      vec3 s = tex(uv + vec2(0.0, k * len * jitter));
      float w = (1.0 - k) * step(0.58, lum(s));
      best = max(best, mix(col, s, w));
    }
    col = mix(col, best, uMelt);
  }
#endif
  // Eco a ritmo: sul colpo una copia più grande del fotogramma si allarga e svanisce, come un'onda.
#if defined(FX_ECHOBEAT)
  if (uEcho > 0.0) {
    float s = 1.0 + 0.3 * (1.0 - uEcho);
    vec3 g = tex(c0 / (z * s) + 0.5);
    col = 1.0 - (1.0 - col) * (1.0 - g * 0.55 * uEcho);
  }
#endif
  // Aura: macchie di colore morbide che si muovono piano (rosa, viola, arancio), come le foto "aura".
#if defined(FX_AURA)
  if (uAura > 0.0) {
    vec2 p = (vUv - 0.5) * vec2(aspect, 1.0);
    vec2 c1 = vec2(0.35 * sin(uTime * 0.31), 0.3 * cos(uTime * 0.23));
    vec2 c2 = vec2(0.3 * cos(uTime * 0.27 + 2.0), 0.35 * sin(uTime * 0.19 + 1.0));
    vec2 c3 = vec2(0.25 * sin(uTime * 0.21 + 4.0), 0.25 * cos(uTime * 0.29 + 3.0));
    vec3 g = vec3(1.0, 0.35, 0.65) * exp(-dot(p - c1, p - c1) * 6.0)
           + vec3(0.55, 0.35, 1.0) * exp(-dot(p - c2, p - c2) * 5.0)
           + vec3(1.0, 0.62, 0.25) * exp(-dot(p - c3, p - c3) * 7.0);
    col = 1.0 - (1.0 - col) * (1.0 - g * 0.55 * uAura);
    col += (hash(floor(vUv * uRes / 1.5) + floor(uTime * 24.0)) - 0.5) * 0.04 * uAura;
  }
#endif
  // Termocamera: la luce diventa calore (nero, viola, rosso, arancio, giallo, bianco).
#if defined(FX_THERMAL)
  if (uThermal > 0.0) {
    float l = clamp(lum(col) * 1.1, 0.0, 1.0);
    vec3 t = l < 0.25 ? mix(vec3(0.02, 0.0, 0.1), vec3(0.45, 0.0, 0.6), l / 0.25)
           : l < 0.5 ? mix(vec3(0.45, 0.0, 0.6), vec3(0.95, 0.1, 0.15), (l - 0.25) / 0.25)
           : l < 0.75 ? mix(vec3(0.95, 0.1, 0.15), vec3(1.0, 0.6, 0.0), (l - 0.5) / 0.25)
           : mix(vec3(1.0, 0.6, 0.0), vec3(1.0, 1.0, 0.85), (l - 0.75) / 0.25);
    col = mix(col, t, uThermal);
  }
#endif
  // Cromo Y2K: metallo liquido argentato con riflessi freddi e un filo di iridescenza.
#if defined(FX_CHROME)
  if (uChrome > 0.0) {
    float l = lum(col);
    float m = 0.5 + 0.5 * sin(l * 11.0 - 1.2);
    vec3 ch = mix(vec3(0.12, 0.14, 0.2), vec3(0.96, 0.98, 1.0), m);
    ch += 0.12 * (0.5 + 0.5 * cos(6.2831 * (vec3(0.0, 0.33, 0.67) + l * 2.0 + uTime * 0.1)));
    col = mix(col, ch, uChrome);
  }
#endif
  // Iper-saturo: colori spinti e neri profondi, luci che "bruciano" appena sul battito (look da reel 2026).
#if defined(FX_HYPER)
  if (uHyper > 0.0) {
    vec3 h = mix(vec3(lum(col)), col, 1.0 + 0.9 * uHyper);
    h = (h - 0.5) * (1.0 + 0.25 * uHyper) + 0.5;
    h *= 1.0 + 0.25 * uPulse * uHyper;
    col = h;
  }
#endif
#if defined(FX_QUADBEAT)
  if (uQuad > 0.0) col = mix(col, mix(vec3(lum(col)) * 0.35, col, cellOn), uQuad);
#endif
  col *= sep;
  gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
"""
    }
}

private class SocialProgram(private val effect: SocialFxEffect, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    private val enabled = SocialFxEffect.UNIFORMS.keys.filter { effect.amount(it) > 0f }
    private val program: GlProgram = try {
        val defines = enabled.joinToString("") { "#define FX_${it.name}\n" }
        GlProgram(VERTEX, defines + HdrGl.fragment(SocialFxEffect.FRAGMENT, useHdr))
    } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
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
            runCatching { program.setFloatsUniform("uRes", floatArrayOf(w, h)) }
            runCatching { program.setFloatsUniform("uTime", floatArrayOf(((presentationTimeUs / 1000) % 100_000) / 1000f)) }
            runCatching { program.setFloatsUniform("uBeat", floatArrayOf(effect.beatIndex(presentationTimeUs).coerceAtLeast(0).toFloat())) }
            runCatching { program.setFloatsUniform("uPulse", floatArrayOf(effect.pulse(presentationTimeUs, 0.09f))) }
            for (type in enabled) {
                val a = effect.amount(type) * when (type) {
                    FxType.ECHOBEAT -> effect.pulse(presentationTimeUs, 0.18f)
                    else -> 1f
                }
                program.setFloatsUniform(SocialFxEffect.UNIFORMS.getValue(type), floatArrayOf(a))
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
