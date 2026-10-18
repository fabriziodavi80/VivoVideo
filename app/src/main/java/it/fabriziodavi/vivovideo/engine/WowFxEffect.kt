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
            FxType.RGBBEAT to "uRgbBeat", FxType.FLARE to "uFlare", FxType.DUST to "uDust", FxType.LETTERBOX to "uLetterbox", FxType.IMPACT to "uImpact",
            FxType.PROMIST to "uProMist", FxType.PETALS to "uPetals", FxType.HEARTS to "uHearts", FxType.SUPER8 to "uSuper8",
            FxType.FILMFRAME to "uFilmFrame", FxType.PROJECTOR to "uProjector", FxType.GLOWBEAT to "uGlowBeat",
            FxType.HAZE to "uHaze", FxType.WATER to "uWater", FxType.SKYGRAD to "uSkyGrad", FxType.SNOW to "uSnow", FxType.LEAVES to "uLeaves",
        )

        fun needed(fx: List<ClipFx>) = fx.any { it.type.wow2 && it.amount > 0f }

        internal const val FRAGMENT = """
precision highp float;
uniform sampler2D uTex;
uniform vec2 uRes;
uniform float uTime;
uniform float uSeed;
uniform float uDreamy, uFilm35, uDuoPop, uSweep, uSplit3, uHolo, uZoomBeat, uShakeBeat, uRgbBeat;
uniform float uFlare, uDust, uLetterbox, uImpact;
uniform float uProMist, uPetals, uHearts, uSuper8, uFilmFrame, uProjector, uGlowBeat;
uniform float uHaze, uWater, uSkyGrad, uSnow, uLeaves;
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
#if defined(FX_SPLIT3)
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
#endif
  // Super 8: il fotogramma "balla" un poco a ogni scatto (24 al secondo).
#if defined(FX_SUPER8)
  if (uSuper8 > 0.0) {
    float fr = floor(uTime * 18.0);
    uv += (vec2(hash(vec2(fr, 1.0)), hash(vec2(fr, 2.0))) - 0.5) * vec2(0.004, 0.007) * uSuper8;
  }
#endif
  // Riflessi d'acqua: l'immagine ondeggia appena, come vista attraverso l'acqua.
#if defined(FX_WATER)
  if (uWater > 0.0) {
    uv += vec2(sin(uv.y * 40.0 + uTime * 2.2), cos(uv.x * 35.0 + uTime * 1.7)) * 0.0025 * uWater;
  }
#endif
  // Zoom a ritmo: spinta in avanti sul colpo, con scia radiale (l'Impatto ne fa una più forte).
  vec2 c = uv - 0.5;
  float z = 1.0 + 0.18 * uZoomBeat + 0.28 * uImpact;
  uv = c / z + 0.5;
  // Scossa a ritmo: scatto in una direzione diversa a ogni colpo.
#if defined(FX_SHAKEBEAT) || defined(FX_IMPACT)
  if (uShakeBeat > 0.0 || uImpact > 0.0) {
    float a = hash(vec2(uSeed, 3.1)) * 6.2831;
    uv += vec2(cos(a), sin(a) * aspect) * (0.035 * uShakeBeat + 0.05 * uImpact);
  }
#endif
  vec3 col;
  if (uZoomBeat > 0.02 || uShakeBeat > 0.02 || uImpact > 0.02) {
    col = vec3(0.0);
    for (int i = 0; i < 8; i++) {
      float k = float(i) / 7.0;
      col += tex(uv - (uv - 0.5) * k * (0.06 * uZoomBeat + 0.1 * uImpact) - vec2(0.012, 0.0) * k * uShakeBeat);
    }
    col /= 8.0;
  } else col = tex(uv);
  // RGB a ritmo: canali che si separano sul colpo.
#if defined(FX_RGBBEAT)
  if (uRgbBeat > 0.0) {
    vec2 d = vec2(0.018 * uRgbBeat, 0.0);
    col.r = tex(uv + d).r; col.b = tex(uv - d).b;
  }
#endif
  // Sogno: alone morbido e luminoso, toni caldi sollevati.
#if defined(FX_DREAMY)
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
#endif
  // Pellicola 35 mm: grana fine, alone rosso attorno alle luci (halation), nero morbido, piccolo tremolio.
#if defined(FX_FILM35)
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
#endif
  // Duotono pop: due colori accesi che cambiano piano nel tempo.
#if defined(FX_DUOPOP)
  if (uDuoPop > 0.0) {
    float l = lum(col);
    float t = uTime * 0.15;
    vec3 a = 0.5 + 0.5 * cos(6.2831 * (vec3(0.0, 0.33, 0.67) + t));
    vec3 b = 0.5 + 0.5 * cos(6.2831 * (vec3(0.5, 0.83, 0.17) + t));
    vec3 duo = mix(a * 0.25, b * 1.05 + 0.1, smoothstep(0.05, 0.95, l));
    col = mix(col, duo, uDuoPop);
  }
#endif
  // Lampo di luce: una striscia diagonale che attraversa il quadro ogni 2,5 s.
#if defined(FX_SWEEP)
  if (uSweep > 0.0) {
    float ph = fract(uTime / 2.5);
    float pos = ph * 2.4 - 0.7;
    float d = (vUv.x + vUv.y * 0.6) / 1.6 - pos;
    float band = exp(-d * d * 900.0) + 0.35 * exp(-d * d * 120.0);
    col += vec3(1.0, 0.95, 0.85) * band * uSweep * 0.75;
  }
#endif
  // Olografico: riflessi iridescenti che scorrono sulle zone chiare.
#if defined(FX_HOLO)
  if (uHolo > 0.0) {
    float l = lum(col);
    vec3 iri = 0.5 + 0.5 * cos(6.2831 * (vec3(0.0, 0.33, 0.67) + vUv.x * 1.5 + vUv.y * 0.8 + uTime * 0.3 + l * 1.2));
    col = mix(col, col * 0.6 + iri * 0.55, uHolo * smoothstep(0.2, 0.9, l) * 0.8);
  }
#endif
  // Bagliore anamorfico: strisce orizzontali azzurre dalle luci forti, più un riflesso che scorre.
#if defined(FX_FLARE)
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
#endif
  // Polvere di pellicola: granelli e graffi che cambiano a ogni fotogramma (24 al secondo).
#if defined(FX_DUST)
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
#endif
  // Bande cinema: entrano nel primo mezzo secondo della clip (formato 2,39:1 in orizzontale).
#if defined(FX_LETTERBOX)
  if (uLetterbox > 0.5) {
    float target = aspect >= 1.0 ? max(0.0, (1.0 - aspect / 2.39) * 0.5) : 0.1;
    float h = target * smoothstep(0.0, 0.5, uLocal);
    col *= step(h, vUv.y) * step(h, 1.0 - vUv.y);
  }
#endif
  // Luce soffusa (filtro "Pro-Mist"): le luci si allargano in un alone morbido e caldo, il contrasto si addolcisce.
#if defined(FX_PROMIST) || defined(FX_GLOWBEAT)
  if (uProMist > 0.0 || uGlowBeat > 0.0) {
    vec3 g = vec3(0.0);
    for (int i = 0; i < 12; i++) { float a = float(i) * 0.5236; g += tex(uv + vec2(cos(a), sin(a) * aspect) * 0.025); }
    g /= 12.0;
    // Bagliore a ritmo: lo stesso alone, che si accende morbido su ogni colpo.
    float amt = uProMist + uGlowBeat * 0.9;
    col += max(g - 0.45, 0.0) * vec3(1.0, 0.93, 0.85) * 1.1 * amt;
    col = mix(col, col * 0.88 + 0.07, uProMist * 0.5);
    col += vec3(0.06, 0.045, 0.03) * uGlowBeat;
  }
#endif
  // Super 8: colori caldi sbiaditi, grana forte, luce che pulsa, angoli scuri e arrotondati.
#if defined(FX_SUPER8)
  if (uSuper8 > 0.0) {
    float fr = floor(uTime * 18.0);
    col = mix(col, vec3(lum(col)) * vec3(1.1, 0.98, 0.78) * 0.85 + col * 0.25 + vec3(0.06, 0.03, 0.0), uSuper8 * 0.65);
    col += (hash(floor(vUv * uRes / 2.0) + fr) - 0.5) * 0.14 * uSuper8;
    col *= 1.0 + (hash(vec2(fr, 9.0)) - 0.5) * 0.12 * uSuper8;
    vec2 cq = abs(vUv - 0.5) * 2.0;
    float corner = smoothstep(0.75, 1.02, length(pow(cq, vec2(3.0))));
    col *= 1.0 - 0.8 * corner * uSuper8;
  }
#endif
  // Proiettore: luce che sfarfalla, cono più chiaro al centro, granelli di polvere.
#if defined(FX_PROJECTOR)
  if (uProjector > 0.0) {
    float fr = floor(uTime * 24.0);
    col *= 1.0 + (hash(vec2(fr, 4.0)) - 0.5) * 0.18 * uProjector;
    float cone = 1.0 - smoothstep(0.2, 0.85, length((vUv - 0.5) * vec2(aspect * 0.8, 1.0)));
    col = mix(col, col * (0.55 + 0.6 * cone) + vec3(0.04, 0.03, 0.01), uProjector);
    float sp = step(0.9992, hash(floor(vUv * vec2(120.0 * aspect, 120.0)) + fr * 3.1));
    col = mix(col, vec3(0.9, 0.85, 0.7), sp * uProjector);
  }
#endif
  // Petali che cadono girando, rosa e bianchi.
#if defined(FX_PETALS)
  if (uPetals > 0.0) {
    for (int layer = 0; layer < 2; layer++) {
      float sc = layer == 0 ? 6.0 : 10.0;
      vec2 p = vUv * vec2(sc * aspect, sc) + vec2(sin(uTime * 0.5 + float(layer)) * 0.6, uTime * (layer == 0 ? 0.9 : 0.6));
      vec2 id = floor(p); vec2 f = fract(p) - 0.5;
      if (hash(id + float(layer) * 13.0) > 0.55) {
        float a = uTime * 1.5 + hash(id) * 6.28;
        vec2 q = vec2(cos(a) * f.x - sin(a) * f.y, sin(a) * f.x + cos(a) * f.y);
        q += (vec2(hash(id + 1.0), hash(id + 2.0)) - 0.5) * 0.4;
        float d = (q.x * q.x) / 0.014 + (q.y * q.y) / 0.005;
        float m = smoothstep(1.0, 0.7, d);
        vec3 pc = mix(vec3(1.0, 0.72, 0.8), vec3(1.0, 0.93, 0.95), hash(id + 5.0));
        col = mix(col, pc, m * 0.85 * uPetals * (layer == 0 ? 1.0 : 0.7));
      }
    }
  }
#endif
  // Cuori di luce: forme morbide che salgono piano.
#if defined(FX_HEARTS)
  if (uHearts > 0.0) {
    vec2 p = vUv * vec2(4.0 * aspect, 4.0) + vec2(0.0, -uTime * 0.25);
    vec2 id = floor(p); vec2 f = fract(p) - 0.5;
    if (hash(id + 21.0) > 0.6) {
      vec2 h = (f - (vec2(hash(id + 3.0), hash(id + 4.0)) - 0.5) * 0.3) * 3.2;
      h.y = -h.y * 1.1 + abs(h.x) * 0.15;
      float v = pow(h.x * h.x + h.y * h.y - 0.25, 3.0) - h.x * h.x * h.y * h.y * h.y * 0.5;
      float m = 1.0 - smoothstep(-0.02, 0.02, v);
      col += vec3(1.0, 0.55, 0.65) * m * 0.45 * uHearts * (0.6 + 0.4 * sin(uTime * 2.0 + hash(id) * 6.0));
    }
  }
#endif
  // Bordo pellicola: la striscia scura con i fori ai lati (in alto e in basso nei video verticali).
#if defined(FX_FILMFRAME)
  if (uFilmFrame > 0.5) {
    bool vert = aspect < 1.0;
    float side = vert ? min(vUv.y, 1.0 - vUv.y) : min(vUv.x, 1.0 - vUv.x);
    float along = vert ? vUv.x * aspect : vUv.y / aspect;
    float strip = 0.075;
    if (side < strip) {
      float hole = step(0.25, fract(along * 7.0 + uTime * 0.0)) * step(fract(along * 7.0), 0.75);
      float inHole = step(strip * 0.3, side) * step(side, strip * 0.75) * hole;
      col = mix(vec3(0.05, 0.04, 0.03), vec3(0.92, 0.9, 0.82), inHole);
    }
  }
#endif
  // Cielo drammatico: filtro sfumato che scurisce e satura il cielo (in alto), come i paesaggisti.
#if defined(FX_SKYGRAD)
  if (uSkyGrad > 0.0) {
    float g = smoothstep(0.45, 1.0, vUv.y);
    vec3 deep = mix(col, col * vec3(0.72, 0.8, 0.95), 0.9);
    deep = mix(vec3(lum(deep)), deep, 1.25);
    col = mix(col, deep, g * uSkyGrad);
  }
#endif
  // Foschia: veli di nebbia chiara che scorrono piano, più densi in basso.
#if defined(FX_HAZE)
  if (uHaze > 0.0) {
    vec2 p = vUv * vec2(3.0 * aspect, 3.0) + vec2(uTime * 0.05, 0.0);
    float n = 0.0; float a = 0.5;
    for (int i = 0; i < 4; i++) { vec2 ip = floor(p); vec2 fp = fract(p); fp = fp * fp * (3.0 - 2.0 * fp);
      n += a * mix(mix(hash(ip), hash(ip + vec2(1.0, 0.0)), fp.x), mix(hash(ip + vec2(0.0, 1.0)), hash(ip + vec2(1.0, 1.0)), fp.x), fp.y);
      p *= 2.03; a *= 0.5; }
    float dens = smoothstep(0.2, 0.9, n) * (0.45 + 0.55 * (1.0 - vUv.y));
    col = mix(col, vec3(0.86, 0.88, 0.9), dens * 0.5 * uHaze);
    col = mix(col, col * 0.9 + 0.08, uHaze * 0.3);
  }
#endif
  // Riflessi d'acqua: luci che tremolano sulle zone chiare.
#if defined(FX_WATER)
  if (uWater > 0.0) {
    vec2 p = vUv * vec2(14.0 * aspect, 14.0);
    float c1 = sin(p.x + uTime * 1.3 + sin(p.y * 0.7 + uTime)) * sin(p.y * 1.1 - uTime * 0.9 + sin(p.x * 0.5));
    float caus = pow(abs(c1), 6.0);
    col += vec3(0.75, 0.95, 1.0) * caus * 0.35 * uWater * smoothstep(0.25, 0.8, lum(col));
  }
#endif
  // Neve: fiocchi su tre piani, che cadono ondeggiando.
#if defined(FX_SNOW)
  if (uSnow > 0.0) {
    for (int layer = 0; layer < 3; layer++) {
      float sc = 8.0 + float(layer) * 7.0;
      vec2 p = vUv * vec2(sc * aspect, sc) + vec2(sin(uTime * 0.7 + float(layer) * 2.0) * 0.5, uTime * (1.2 - float(layer) * 0.3));
      vec2 id = floor(p); vec2 f = fract(p) - 0.5;
      vec2 off = (vec2(hash(id + float(layer)), hash(id + 9.0)) - 0.5) * 0.6;
      float r = 0.07 - float(layer) * 0.015;
      float m = smoothstep(r, r * 0.3, length(f - off)) * step(0.45, hash(id + 31.0));
      col = mix(col, vec3(1.0), m * uSnow * (1.0 - float(layer) * 0.25));
    }
  }
#endif
  // Foglie d'autunno: come i petali, arancio, rosso e giallo, più grandi e lente.
#if defined(FX_LEAVES)
  if (uLeaves > 0.0) {
    vec2 p = vUv * vec2(5.0 * aspect, 5.0) + vec2(sin(uTime * 0.4) * 0.8, uTime * 0.55);
    vec2 id = floor(p); vec2 f = fract(p) - 0.5;
    if (hash(id + 41.0) > 0.6) {
      float a = uTime * 1.1 + hash(id) * 6.28;
      vec2 q = vec2(cos(a) * f.x - sin(a) * f.y, sin(a) * f.x + cos(a) * f.y);
      q += (vec2(hash(id + 1.0), hash(id + 2.0)) - 0.5) * 0.35;
      float d = (q.x * q.x) / 0.03 + (q.y * q.y) / 0.008;
      float m = smoothstep(1.0, 0.75, d);
      vec3 lc = mix(vec3(0.95, 0.45, 0.1), mix(vec3(0.8, 0.15, 0.08), vec3(0.95, 0.75, 0.15), hash(id + 6.0)), hash(id + 5.0));
      col = mix(col, lc, m * 0.9 * uLeaves);
    }
  }
#endif
  // Impatto: lampo e contrasto sul colpo.
#if defined(FX_IMPACT)
  if (uImpact > 0.0) {
    col = mix(col, (col - 0.5) * 1.35 + 0.5, uImpact);
    col += vec3(0.35) * uImpact * uImpact;
  }
#endif
  col *= sep;
  gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
"""
    }
}

private class WowProgram(private val effect: WowFxEffect, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    /** Solo gli effetti usati dalla scena entrano nello shader: si prepara in fretta anche con decine di scene. */
    private val enabled = WowFxEffect.UNIFORMS.keys.filter { effect.amount(it) > 0f }
    private val program: GlProgram = try {
        val defines = enabled.joinToString("") { "#define FX_${it.name}\n" }
        GlProgram(VERTEX, defines + HdrGl.fragment(WowFxEffect.FRAGMENT, useHdr))
    } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
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
            // Con solo alcuni effetti nello shader, queste possono non servire (e allora il driver le toglie).
            runCatching { program.setFloatsUniform("uRes", floatArrayOf(w, h)) }
            runCatching { program.setFloatsUniform("uTime", floatArrayOf(((presentationTimeUs / 1000) % 100_000) / 1000f)) }
            runCatching { program.setFloatsUniform("uSeed", floatArrayOf(effect.beatSeed(presentationTimeUs))) }
            runCatching { program.setFloatsUniform("uLocal", floatArrayOf(localTimeUs(presentationTimeUs, effect.startUs) / 1e6f)) }
            for (type in enabled) {
                val name = WowFxEffect.UNIFORMS.getValue(type)
                val a = amounts.getValue(type) * when (type) {
                    FxType.ZOOMBEAT -> effect.beatAt(presentationTimeUs, 0.14f)
                    FxType.SHAKEBEAT -> effect.beatAt(presentationTimeUs, 0.09f)
                    FxType.RGBBEAT -> effect.beatAt(presentationTimeUs, 0.1f)
                    FxType.IMPACT -> effect.beatAt(presentationTimeUs, 0.11f)
                    FxType.GLOWBEAT -> effect.beatAt(presentationTimeUs, 0.3f)
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
