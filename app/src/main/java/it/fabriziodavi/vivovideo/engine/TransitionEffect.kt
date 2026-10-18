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
    private val clipStartUs: Long,
    private val clipOutputUs: Long,
    private val inType: TransitionType?, private val inUs: Long,
    private val outType: TransitionType?, private val outUs: Long,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        TransitionProgram(this, useHdr)

    /** Valori dello shader per l'istante t (tempo del montaggio, dopo la velocità). */
    internal fun params(presentationTimeUs: Long): Params {
        val tUs = localTimeUs(presentationTimeUs, clipStartUs)
        var p = Params()
        // Seme che cambia ~25 volte al secondo: per glitch e scossa.
        val seed = (presentationTimeUs / 40_000) % 1000
        if (inType != null && inUs > 0 && tUs < inUs) {
            val e = 1f - smooth(tUs.toFloat() / inUs)
            p = shape(inType, e, entering = true, seed)
        } else if (outType != null && outUs > 0 && tUs > clipOutputUs - outUs) {
            val e = smooth((tUs - (clipOutputUs - outUs)).toFloat() / outUs)
            p = shape(outType, e, entering = false, seed)
        }
        return p.copy(seed = seed.toFloat())
    }

    internal data class Params(
        val zoom: Float = 1f, val shift: Float = 0f, val rotation: Float = 0f,
        val blur: Float = 0f, val blurMode: Int = 0, val black: Float = 0f, val white: Float = 0f,
        val glitch: Float = 0f, val prism: Float = 0f, val leak: Float = 0f, val iris: Float = 0f, val pixel: Float = 0f,
        val seed: Float = 0f,
        val swirl: Float = 0f, val warp: Float = 0f, val burn: Float = 0f, val cube: Float = 0f, val luma: Float = 0f,
        val rgb: Float = 0f, val shiftY: Float = 0f,
        val tear: Float = 0f, val burst: Float = 0f, val lens: Float = 0f, val stripes: Float = 0f, val shutter: Float = 0f,
        val speedLines: Float = 0f, val smash: Float = 0f,
    )

    /** Numero pseudo-casuale stabile in -0,5…0,5 dato un seme. */
    private fun jitter(seed: Long, k: Int): Float {
        val x = kotlin.math.sin((seed * 12.9898 + k * 78.233)) * 43758.5453
        return (x - kotlin.math.floor(x)).toFloat() - 0.5f
    }

    private fun smooth(x: Float): Float { val u = x.coerceIn(0f, 1f); return u * u * (3 - 2 * u) }

    /** e = intensità, 1 esattamente sul taglio. */
    private fun shape(t: TransitionType, e: Float, entering: Boolean, seed: Long): Params = when (t) {
        TransitionType.CROSSFADE -> Params() // fatta dal compositore (due clip sovrapposte)
        TransitionType.FADE_BLACK -> Params(black = e)
        TransitionType.FLASH -> Params(white = e * e, zoom = 1f + 0.04f * e)
        TransitionType.ZOOM -> Params(zoom = 1f + 0.6f * e * e, blur = 0.035f * e, blurMode = 2)
        TransitionType.BLUR -> Params(blur = 0.025f * e, blurMode = 3)
        TransitionType.WHIP -> Params(shift = (if (entering) -0.7f else 0.7f) * e * e, blur = 0.09f * e, blurMode = 1)
        TransitionType.SPIN -> Params(rotation = (if (entering) -1f else 1f) * 0.9f * e * e, zoom = 1f + 0.25f * e, blur = 0.02f * e, blurMode = 3)
        TransitionType.GLITCH -> Params(glitch = e, shift = jitter(seed, 1) * 0.06f * e * e, white = 0.25f * e * e * e * e)
        TransitionType.PRISM -> Params(prism = e, zoom = 1f + 0.35f * e * e, blur = 0.02f * e, blurMode = 2)
        TransitionType.LEAK -> Params(leak = e, white = 0.9f * e * e * e * e, zoom = 1f + 0.05f * e)
        TransitionType.IRIS -> Params(iris = e)
        TransitionType.PIXELATE -> Params(pixel = e, white = 0.1f * e * e)
        TransitionType.SHAKE -> Params(
            shift = jitter(seed, 2) * 0.14f * e, rotation = jitter(seed, 3) * 0.18f * e,
            zoom = 1f + 0.15f * e, blur = 0.04f * e, blurMode = 1,
        )
        // Premium: zoom violento con scia radiale, prisma e lampo sul taglio.
        TransitionType.PUNCH -> Params(zoom = 1f + 1.4f * e * e * e, blur = 0.06f * e * e, blurMode = 2, prism = 0.9f * e, white = 0.55f * e * e * e * e)
        TransitionType.SWIRL -> Params(swirl = (if (entering) -1f else 1f) * e * e, zoom = 1f + 0.2f * e, blur = 0.015f * e, blurMode = 3)
        TransitionType.LIQUID -> Params(warp = e, prism = 0.35f * e, zoom = 1f + 0.08f * e)
        TransitionType.BURN -> Params(burn = e, zoom = 1f + 0.04f * e)
        TransitionType.CUBE -> Params(cube = (if (entering) -1f else 1f) * e)
        TransitionType.LUMA -> Params(luma = e, white = 0.9f * e * e * e * e * e)
        TransitionType.RGB -> Params(rgb = e, shift = jitter(seed, 4) * 0.05f * e, rotation = jitter(seed, 5) * 0.06f * e, zoom = 1f + 0.12f * e)
        TransitionType.SLIDE_UP -> Params(shiftY = (if (entering) 0.8f else -0.8f) * e * e, blur = 0.1f * e, blurMode = 4)
        TransitionType.TEAR -> Params(tear = e, rotation = jitter(seed, 6) * 0.04f * e, white = 0.5f * e * e * e * e * e)
        TransitionType.BURST -> Params(burst = e, zoom = 1f + 0.5f * e * e, blur = 0.03f * e, blurMode = 2, white = e * e * e * e)
        TransitionType.LENS -> Params(lens = e, zoom = 1f + 0.3f * e * e, prism = 0.5f * e)
        TransitionType.STRIPES -> Params(stripes = (if (entering) -1f else 1f) * e, white = 0.3f * e * e * e * e)
        TransitionType.SPEEDZOOM -> Params(speedLines = e, zoom = 1f + 1.1f * e * e, blur = 0.08f * e, blurMode = 2, prism = 0.4f * e, white = 0.35f * e * e * e * e * e)
        TransitionType.SMASH -> Params(smash = e, zoom = 1f + 0.7f * e * e * e, shift = jitter(seed, 7) * 0.03f * e, white = 0.9f * e * e * e * e * e * e)
        TransitionType.SHUTTER -> Params(shutter = e, zoom = 1f + 0.12f * e, white = if (e > 0.97f) 0.8f else 0f)
    }
}

private class TransitionProgram(private val effect: TransitionEffect, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    private val program: GlProgram = try { GlProgram(VERTEX, HdrGl.fragment(FRAGMENT, useHdr)) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
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
            program.setFloatsUniform("uGlitch", floatArrayOf(p.glitch))
            program.setFloatsUniform("uPrism", floatArrayOf(p.prism))
            program.setFloatsUniform("uLeak", floatArrayOf(p.leak))
            program.setFloatsUniform("uIris", floatArrayOf(p.iris))
            program.setFloatsUniform("uPix", floatArrayOf(p.pixel))
            program.setFloatsUniform("uSeed", floatArrayOf(p.seed))
            program.setFloatsUniform("uSwirl", floatArrayOf(p.swirl))
            program.setFloatsUniform("uWarp", floatArrayOf(p.warp))
            program.setFloatsUniform("uBurn", floatArrayOf(p.burn))
            program.setFloatsUniform("uCube", floatArrayOf(p.cube))
            program.setFloatsUniform("uLuma", floatArrayOf(p.luma))
            program.setFloatsUniform("uRgb", floatArrayOf(p.rgb))
            program.setFloatsUniform("uShiftY", floatArrayOf(p.shiftY))
            program.setFloatsUniform("uTear", floatArrayOf(p.tear))
            program.setFloatsUniform("uBurst", floatArrayOf(p.burst))
            program.setFloatsUniform("uLens", floatArrayOf(p.lens))
            program.setFloatsUniform("uStripes", floatArrayOf(p.stripes))
            program.setFloatsUniform("uShutter", floatArrayOf(p.shutter))
            program.setFloatsUniform("uSpeedLines", floatArrayOf(p.speedLines))
            program.setFloatsUniform("uSmash", floatArrayOf(p.smash))
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
uniform float uGlitch;
uniform float uPrism;
uniform float uLeak;
uniform float uIris;
uniform float uPix;
uniform float uSeed;
uniform float uSwirl;
uniform float uWarp;
uniform float uBurn;
uniform float uCube;
uniform float uLuma;
uniform float uRgb;
uniform float uShiftY;
uniform float uTear;
uniform float uBurst;
uniform float uLens;
uniform float uStripes;
uniform float uShutter;
uniform float uSpeedLines;
uniform float uSmash;
varying vec2 vTexSamplingCoord;

float hash(vec2 p) {
  vec3 p3 = fract(vec3(p.xyx) * 0.1031);
  p3 += dot(p3, p3.yzx + 33.33);
  return fract((p3.x + p3.y) * p3.z);
}

// Coordinate trasformate (zoom, rotazione in proporzioni reali, scorrimento); fuori dal quadro = specchio.
vec2 xf(vec2 uv) {
  vec2 c = uv - 0.5;
  c.x *= uAspect;
  float s = sin(uRot), co = cos(uRot);
  c = vec2(co * c.x - s * c.y, s * c.x + co * c.y) / uZoom;
  c.x /= uAspect;
  vec2 r = c + 0.5 + vec2(uShift, uShiftY);
  return 1.0 - abs(1.0 - mod(r, 2.0));
}

float vnoise(vec2 p) {
  vec2 i = floor(p); vec2 f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}
float fbm(vec2 p) { return 0.5 * vnoise(p) + 0.25 * vnoise(p * 2.03) + 0.125 * vnoise(p * 4.01) + 0.0625 * vnoise(p * 8.07); }

void main() {
  vec2 uv = vTexSamplingCoord;
  float edge = 1.0;
  // Vortice: l'immagine gira attorno al centro, di più vicino al centro.
  if (uSwirl != 0.0) {
    vec2 q = uv - 0.5; q.x *= uAspect;
    float rr = length(q);
    float a = uSwirl * 7.0 * pow(max(0.0, 1.0 - rr * 1.3), 2.0);
    float s = sin(a), co = cos(a);
    q = vec2(co * q.x - s * q.y, s * q.x + co * q.y);
    q.x /= uAspect; uv = q + 0.5;
  }
  // Liquido: onde che deformano l'immagine.
  if (uWarp > 0.0) {
    uv += vec2(sin(uv.y * 18.0 + uSeed * 0.7), cos(uv.x * 14.0 + uSeed * 0.5)) * 0.045 * uWarp * uWarp
       + vec2(fbm(uv * 4.0 + uSeed * 0.05) - 0.5) * 0.12 * uWarp;
  }
  // Lente: rigonfiamento verso chi guarda (distorsione a barile che cresce).
  if (uLens > 0.0) {
    vec2 q = uv - 0.5; q.x *= uAspect;
    float r2 = dot(q, q);
    q *= 1.0 - uLens * 0.9 * r2 / (1.0 + r2);
    q /= 1.0 + 0.25 * uLens;
    q.x /= uAspect; uv = q + 0.5;
  }
  // Strisce: bande orizzontali che scivolano a destra e a sinistra.
  float gap = 0.0;
  if (uStripes != 0.0) {
    float band = floor(uv.y * 8.0);
    float dirS = mod(band, 2.0) < 1.0 ? 1.0 : -1.0;
    float k = abs(uStripes);
    uv.x += dirS * sign(uStripes) * k * k * (0.6 + 0.4 * fract(band * 0.37));
  }
  // Strappo: il quadro si spacca in due lungo una linea frastagliata, le metà si allontanano.
  if (uTear > 0.0) {
    float jag = (hash(vec2(floor(uv.x * 40.0), 3.0)) - 0.5) * 0.06 + sin(uv.x * 9.0) * 0.02;
    float line = 0.5 + jag;
    float open = uTear * uTear * 0.55;
    if (uv.y > line) { uv.y -= open; if (uv.y < line) gap = 1.0; }
    else { uv.y += open; if (uv.y > line) gap = 1.0; }
  }
  // Sfondamento: crepe che partono dal centro, i pezzi si spostano verso l'esterno.
  float crack = 0.0;
  if (uSmash > 0.0) {
    vec2 q = uv - 0.5; q.x *= uAspect;
    float ang = atan(q.y, q.x);
    float sector = floor((ang + 3.1416) / 6.2832 * 11.0);
    float rr = length(q);
    float ring = floor(rr * 7.0 + hash(vec2(sector, 2.0)) * 0.8);
    vec2 cellId = vec2(sector, ring);
    vec2 push = normalize(q + 1e-4) * uSmash * uSmash * (0.04 + 0.08 * hash(cellId));
    float a = (hash(cellId + 5.0) - 0.5) * 0.25 * uSmash * uSmash;
    q -= push;
    q = vec2(cos(a) * q.x - sin(a) * q.y, sin(a) * q.x + cos(a) * q.y);
    float edgeA = abs(fract((ang + 3.1416) / 6.2832 * 11.0) - 0.5);
    float edgeR = abs(fract(rr * 7.0 + hash(vec2(sector, 2.0)) * 0.8) - 0.5);
    crack = (1.0 - smoothstep(0.0, 0.03, 0.5 - max(edgeA, edgeR))) * smoothstep(0.05, 0.4, uSmash);
    q.x /= uAspect; uv = q + 0.5;
  }
  // Cubo 3D: la faccia ruota via, con prospettiva (fuori dalla faccia è nero).
  if (uCube != 0.0) {
    float k = abs(uCube);
    float dir = sign(uCube);
    float w = 1.0 - k * 0.92;
    float x = dir > 0.0 ? uv.x / w : (uv.x - (1.0 - w)) / w;
    float persp = 1.0 + k * 0.35 * (dir > 0.0 ? x : 1.0 - x);
    float y = (uv.y - 0.5) * persp + 0.5;
    edge = step(0.0, x) * step(x, 1.0) * step(0.0, y) * step(y, 1.0) * (1.0 - 0.55 * k);
    uv = vec2(clamp(x, 0.0, 1.0), clamp(y, 0.0, 1.0));
  }
  if (uPix > 0.0) { float n = mix(220.0, 10.0, uPix * uPix); vec2 cells = vec2(n, n / uAspect); uv = (floor(uv * cells) + 0.5) / cells; }
  if (uGlitch > 0.0) {
    float row = floor(uv.y * 22.0);
    if (hash(vec2(row, uSeed)) < 0.55 * uGlitch) uv.x += (hash(vec2(uSeed, row)) - 0.5) * 0.35 * uGlitch;
  }
  vec3 acc = vec3(0.0);
  if (uBlur <= 0.0001) {
    acc = texture2D(uTexSampler, xf(uv)).rgb;
  } else {
    for (int i = 0; i < 12; i++) {
      float k = (float(i) / 11.0) - 0.5;
      vec2 o;
      if (uMode < 1.5) o = vec2(k * uBlur, 0.0);                                  // orizzontale
      else if (uMode < 2.5) o = (uv - 0.5) * k * uBlur * 4.0;                     // radiale (zoom)
      else if (uMode > 3.5) o = vec2(0.0, k * uBlur);                             // verticale
      else { float a = float(i) * 2.3999; o = vec2(cos(a), sin(a) * uAspect) * uBlur * sqrt(float(i) / 11.0); } // disco
      acc += texture2D(uTexSampler, xf(uv + o)).rgb;
    }
    acc /= 12.0;
  }
  if (uPrism > 0.0 || uGlitch > 0.0) {
    vec2 d = (uv - 0.5) * 0.06 * uPrism + vec2(0.02 * uGlitch * (hash(vec2(uSeed, 9.0)) - 0.2), 0.0);
    acc.r = texture2D(uTexSampler, xf(uv + d)).r;
    acc.b = texture2D(uTexSampler, xf(uv - d)).b;
  }
  if (uLeak > 0.0) {
    vec2 lp = uv - vec2(0.15, 0.85); lp.x *= uAspect;
    vec3 leak = vec3(1.0, 0.5, 0.18) * exp(-dot(lp, lp) * (3.0 - 2.4 * uLeak)) * uLeak * 1.5;
    vec2 lp2 = uv - vec2(0.9, 0.2); lp2.x *= uAspect;
    leak += vec3(1.0, 0.25, 0.35) * exp(-dot(lp2, lp2) * 4.0) * uLeak * 0.8;
    acc = 1.0 - (1.0 - clamp(acc, 0.0, 1.0)) * (1.0 - clamp(leak, 0.0, 1.0));
  }
  // Esplosione di luce: raggi bianchi dal centro che crescono fino a coprire tutto.
  if (uBurst > 0.0) {
    vec2 q = uv - 0.5; q.x *= uAspect;
    float ang = atan(q.y, q.x);
    float rays = pow(abs(sin(ang * 9.0 + uSeed * 0.01)), 8.0) + pow(abs(sin(ang * 5.0 - 1.3)), 12.0);
    float core = exp(-length(q) * (6.0 - 5.0 * uBurst));
    acc += vec3(1.0, 0.95, 0.85) * (rays * uBurst * 0.9 + core * uBurst * 1.4) * uBurst;
  }
  // Otturatore: due lame che si chiudono dall'alto e dal basso.
  if (uShutter > 0.0) {
    float h = uShutter * uShutter * 0.5;
    float m = smoothstep(h - 0.004, h, uv.y) * smoothstep(h - 0.004, h, 1.0 - uv.y);
    acc *= m;
  }
  // Linee di velocità: strisce radiali bianche che scorrono verso l'esterno.
  if (uSpeedLines > 0.0) {
    vec2 q = vTexSamplingCoord - 0.5; q.x *= uAspect;
    float ang = atan(q.y, q.x);
    float lines = step(0.82, hash(vec2(floor(ang * 60.0), floor(uSeed * 0.5))));
    float fall = smoothstep(0.15, 0.7, length(q));
    acc = mix(acc, vec3(1.0), lines * fall * uSpeedLines * 0.85);
  }
  acc = mix(acc, vec3(1.0), crack * 0.85);
  // Nella fessura dello strappo si vede il bianco.
  acc = mix(acc, vec3(1.0, 0.98, 0.94), gap);
  // Schiaffo RGB: canali spostati di molto, con righe di scansione.
  if (uRgb > 0.0) {
    float d = 0.07 * uRgb;
    acc.r = texture2D(uTexSampler, xf(uv + vec2(d, 0.0))).r;
    acc.b = texture2D(uTexSampler, xf(uv - vec2(d, 0.0))).b;
    acc *= 1.0 - 0.25 * uRgb * step(0.5, fract(uv.y * 160.0));
    acc += vec3(0.15, 0.0, 0.25) * uRgb * uRgb;
  }
  // Luce che dissolve: prima spariscono nel bianco le zone più chiare.
  if (uLuma > 0.0) {
    float l = dot(acc, vec3(0.2126, 0.7152, 0.0722));
    float th = 1.05 - uLuma * 1.25;
    acc = mix(acc, vec3(1.0, 0.97, 0.92), smoothstep(th, th + 0.18, l));
  }
  // Pellicola bruciata: macchie calde che si allargano, bordo incandescente, poi bianco.
  if (uBurn > 0.0) {
    float n = fbm(uv * vec2(3.0 * uAspect, 3.0) + vec2(uSeed * 0.002, 0.0));
    float front = uBurn * 1.25 - 0.1;
    float glowE = smoothstep(front - 0.18, front, n) * (1.0 - smoothstep(front, front + 0.02, n));
    float burnt = 1.0 - smoothstep(front - 0.02, front, n);
    acc = mix(acc, vec3(1.0, 0.55, 0.15), glowE * 0.9);
    acc = mix(acc, vec3(1.0, 0.92, 0.75), burnt);
    acc += vec3(0.25, 0.08, 0.0) * uBurn;
  }
  acc *= edge;
  if (uIris > 0.0) {
    vec2 ip = uv - 0.5; ip.x *= uAspect;
    float rr = (1.0 - uIris) * 0.5 * sqrt(uAspect * uAspect + 1.0) * 1.02;
    acc *= 1.0 - smoothstep(rr - 0.012, rr, length(ip));
  }
  acc = mix(acc, vec3(0.0), uBlack);
  acc = mix(acc, vec3(1.0), uWhite);
  gl_FragColor = vec4(acc, 1.0);
}
"""
    }
}
