package it.fabriziodavi.vivovideo.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/*
 * Modello del progetto: descrive COSA c'è nel video, non come si disegna.
 * Kotlin puro, senza Android: si testa sul PC. Tutti i tempi sono in microsecondi.
 */

@Serializable
enum class ClipSpeed(val label: String) {
    NORMAL("1×"), SLOW("0,5×"), FAST("2×"),
    /** Curva disegnata a mano (nei progetti vecchi era la "Rampa" fissa). */
    RAMP("Curva");
}

/** Tipi di transizione. Agiscono sulla fine di una clip e sull'inizio della successiva. */
@Serializable
enum class TransitionType(val label: String) {
    /** Dissolvenza incrociata vera: le due clip si sovrappongono (usa i fotogrammi oltre il taglio). */
    CROSSFADE("Incrociata"),
    FADE_BLACK("Nero"),
    FLASH("Flash"),
    ZOOM("Zoom"),
    BLUR("Sfocatura"),
    WHIP("Scorrimento"),
    SPIN("Rotazione"),
    // Effetti "wow"
    GLITCH("Glitch"),
    PRISM("Prisma"),
    LEAK("Luce"),
    IRIS("Cerchio"),
    PIXELATE("Pixel"),
    SHAKE("Scossa"),
    // Premium 2026
    PUNCH("Zoom punch"),
    SWIRL("Vortice"),
    LIQUID("Liquido"),
    BURN("Pellicola bruciata"),
    CUBE("Cubo 3D"),
    LUMA("Luce che dissolve"),
    RGB("Schiaffo RGB"),
    SLIDE_UP("Scorrimento verticale"),
    TEAR("Strappo"),
    BURST("Esplosione di luce"),
    LENS("Lente"),
    STRIPES("Strisce"),
    SHUTTER("Otturatore"),
    // Sport e momenti dinamici
    SPEEDZOOM("Scatto di velocità"),
    SMASH("Sfondamento"),
    SQUASH("Schiacciata"),
    // Romantico e nostalgico
    HEART("Cuore"),
    BOKEHFADE("Dissolvenza bokeh"),
    FILMSLIP("Salto di pellicola"),
    REWIND("Riavvolgimento VHS"),
    // Tendenze 2026: stacchi secchi da velocity edit, niente forme 3D.
    BLACKOUT("Blackout"),
    EXPOSE("Flash esposizione"),
    CAMSHAKE("Scossa camera"),
    SNAPZOOM("Zoom secco"),
    // Tendenze 2026, morbide (documentari, viaggi, natura): niente onde né lampi.
    SOFTZOOM("Zoom morbido"),
    DRIFT("Scivolata");

    /** Transizioni adatte a uno slideshow "vario" (quelle più nervose solo se scelte apposta). */
    val soft: Boolean get() = this !in setOf(GLITCH, PIXELATE, SHAKE, RGB, PUNCH, TEAR, STRIPES, SHUTTER, BURST, SPEEDZOOM, SMASH, SQUASH, REWIND,
        BLACKOUT, EXPOSE, CAMSHAKE, SNAPZOOM)

    /** Transizioni d'impatto, da mettere sui colpi forti della musica. */
    val punchy: Boolean get() = this in setOf(PUNCH, FLASH, RGB, GLITCH, SHAKE, WHIP, SPIN, SLIDE_UP, ZOOM, SWIRL, TEAR, BURST, STRIPES, SHUTTER, SPEEDZOOM, SMASH, SQUASH, REWIND,
        BLACKOUT, EXPOSE, CAMSHAKE, SNAPZOOM)

    /** Di tendenza (2026): da mostrare per prime nel pannello. */
    val trend: Boolean get() = ordinal >= BLACKOUT.ordinal

    /** Di tendenza e morbide (adatte ai temi calmi). */
    val gentleTrend: Boolean get() = this == SOFTZOOM || this == DRIFT

    /** Premium: le novità da far vedere nel pannello in cima. */
    val premium: Boolean get() = ordinal >= PUNCH.ordinal
}

@Serializable
data class TransitionSpec(val type: TransitionType, val durationUs: Long = 500_000)

/** Andamento fra un keyframe e il successivo. */
@Serializable
enum class Easing(val label: String) {
    SMOOTH("Morbido"), LINEAR("Lineare"), EASE_IN("Accelera"), EASE_OUT("Rallenta"), HOLD("Scatto");

    fun apply(u: Float): Float = when (this) {
        LINEAR -> u
        SMOOTH -> u * u * (3 - 2 * u)
        EASE_IN -> u * u * u
        EASE_OUT -> 1 - (1 - u) * (1 - u) * (1 - u)
        HOLD -> 0f
    }
}

/** Stato della trasformazione di una clip in un istante. */
@Serializable
data class Transform(
    /** Spostamento in frazioni del fotogramma (1 = un fotogramma intero). */
    val x: Float = 0f,
    val y: Float = 0f,
    val scale: Float = 1f,
    /** Gradi, in senso orario. */
    val rotation: Float = 0f,
    /** 0 = nero, 1 = pieno. */
    val opacity: Float = 1f,
) {
    val isIdentity get() = x == 0f && y == 0f && scale == 1f && rotation == 0f && opacity == 1f

    fun lerp(o: Transform, u: Float) = Transform(
        x + (o.x - x) * u, y + (o.y - y) * u,
        // lo zoom si interpola in scala logaritmica: velocità di zoom costante a occhio
        kotlin.math.exp(kotlin.math.ln(scale) + (kotlin.math.ln(o.scale) - kotlin.math.ln(scale)) * u).toFloat(),
        rotation + (o.rotation - rotation) * u,
        opacity + (o.opacity - opacity) * u,
    )
}

/** Keyframe: [timeUs] dall'inizio della clip tagliata, sul tempo del video originale. */
@Serializable
data class Keyframe(val timeUs: Long, val value: Transform, val easing: Easing = Easing.SMOOTH)

object Keyframes {
    /** Valore della trasformazione al tempo t (sorgente, relativo all'inizio della clip). */
    fun valueAt(keys: List<Keyframe>, tUs: Long): Transform {
        if (keys.isEmpty()) return Transform()
        val k = keys.sortedBy { it.timeUs }
        if (tUs <= k.first().timeUs) return k.first().value
        if (tUs >= k.last().timeUs) return k.last().value
        val i = k.indexOfLast { it.timeUs <= tUs }
        val a = k[i]; val b = k[i + 1]
        val u = ((tUs - a.timeUs).toFloat() / (b.timeUs - a.timeUs).coerceAtLeast(1)).coerceIn(0f, 1f)
        return a.value.lerp(b.value, a.easing.apply(u))
    }

    /** Mette o aggiorna un keyframe al tempo t (entro [snapUs] riusa quello esistente). */
    fun set(keys: List<Keyframe>, tUs: Long, value: Transform, snapUs: Long = 40_000): List<Keyframe> {
        val existing = keys.firstOrNull { kotlin.math.abs(it.timeUs - tUs) <= snapUs }
        return if (existing != null) keys.map { if (it === existing) it.copy(value = value) else it }
        else (keys + Keyframe(tUs, value)).sortedBy { it.timeUs }
    }
}

/** Punto della curva di velocità: [t] posizione lungo la clip (0 inizio, 1 fine), [speed] velocità. */
@Serializable
data class SpeedPoint(val t: Float, val speed: Float)

object SpeedLimits {
    const val MIN = 0.2f
    const val MAX = 5f
}

/** Curve pronte, come negli editor professionali. */
enum class SpeedPreset(val label: String, val points: List<SpeedPoint>) {
    FLAT("Piatta 1×", listOf(SpeedPoint(0f, 1f), SpeedPoint(1f, 1f))),
    VALLEY("Rallenty centrale", listOf(SpeedPoint(0f, 2f), SpeedPoint(0.5f, 0.35f), SpeedPoint(1f, 2f))),
    HERO("Eroe", listOf(SpeedPoint(0f, 1f), SpeedPoint(0.38f, 1f), SpeedPoint(0.5f, 0.25f), SpeedPoint(0.62f, 1f), SpeedPoint(1f, 1f))),
    BULLET("Proiettile", listOf(SpeedPoint(0f, 3f), SpeedPoint(0.42f, 0.3f), SpeedPoint(0.58f, 0.3f), SpeedPoint(1f, 3f))),
    MONTAGE("Montaggio", listOf(SpeedPoint(0f, 0.5f), SpeedPoint(0.3f, 3f), SpeedPoint(0.7f, 3f), SpeedPoint(1f, 0.5f))),
    FLASH_IN("Entrata lampo", listOf(SpeedPoint(0f, 4f), SpeedPoint(0.3f, 1f), SpeedPoint(1f, 1f))),
    SLOW_END("Rallenty finale", listOf(SpeedPoint(0f, 1f), SpeedPoint(0.6f, 1f), SpeedPoint(1f, 0.25f))),
}

@Serializable
data class Clip(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    /** Durata dell'intero file sorgente. */
    val sourceDurationUs: Long,
    val trimStartUs: Long = 0,
    val trimEndUs: Long = sourceDurationUs,
    val speed: ClipSpeed = ClipSpeed.NORMAL,
    /** Video girato in LOG: applica la conversione colore. */
    val log: Boolean = false,
    /** Video HDR: 0 = no (SDR), 1 = HLG, 2 = HDR10 (PQ). */
    val hdr: Int = 0,
    val muted: Boolean = false,
    /** Volume dell'audio originale: 1 = invariato, da 0 a 2. */
    val volume: Float = 1f,
    /** Punti della curva quando [speed] è RAMP ("Curva"); vuota = preset "Rallenty centrale". */
    val speedPoints: List<SpeedPoint> = emptyList(),
    /** Animazione di posizione, zoom, rotazione e opacità (tempi sul video originale della clip). */
    val keyframes: List<Keyframe> = emptyList(),
    /** Transizione verso la clip successiva (null = taglio netto). */
    val transitionOut: TransitionSpec? = null,
    /** Foto (immagine fissa): la durata è il taglio, niente velocità né audio. */
    val isPhoto: Boolean = false,
    /** Riempie il quadro tagliando i bordi, invece di mostrare bande nere. */
    val fill: Boolean = false,
    /** Adatta con, dietro, una copia sfocata della stessa immagine al posto delle bande nere. */
    val blurBg: Boolean = false,
    /** Correzione del colore di questa clip. */
    val grade: Grade = Grade(),
    /** Inquadratura fissa (zoom, posizione, rotazione), sotto ad eventuali keyframe. */
    val frame: Transform = Transform(),
    /** Effetti video della clip. */
    val fx: List<ClipFx> = emptyList(),
    /** Dissolvenza in entrata/uscita (dal nero o dal bianco), in µs del video finale; anche sull'audio se [fadeAudio]. */
    val fadeInUs: Long = 0,
    val fadeOutUs: Long = 0,
    val fadeColor: FadeColor = FadeColor.BLACK,
    val fadeAudio: Boolean = true,
    /** Pezzo già elaborato (dissolvenza preparata per l'anteprima): niente colori né effetti da riapplicare. */
    val baked: Boolean = false,
    /** Clip al contrario: qui c'è l'originale, per tornare al verso normale. */
    val reversed: ReversedSource? = null,
    /** Stabilizzazione (movimento della camera misurato), o null. */
    val stab: Stabilization? = null,
    /** Fermo immagine preso da un video (foto creata dall'app). */
    val freeze: Boolean = false,
    /** Segui il soggetto: con "Riempi", l'inquadratura si sposta per tenerlo in centro. */
    val reframe: Reframe? = null,
    /** Persona scontornata: cosa fare dello sfondo. */
    val person: PersonFx? = null,
    /** Nome e dimensione del file originale: servono a ritrovarlo se viene spostato. */
    val fileName: String = "",
    val fileSize: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val fps: Float = 30f,
) {
    val trimmedUs: Long get() = trimEndUs - trimStartUs

    /** Curva di velocità sul tempo della clip tagliata (0 = inizio del taglio). */
    val curvePoints: List<SpeedPoint> get() = speedPoints.ifEmpty { SpeedPreset.VALLEY.points }

    // Calcolati una volta per istanza (la timeline li chiede molte volte al secondo).
    private val cachedCurve: SpeedCurve by lazy { SpeedCurve.forClip(speed, trimmedUs, curvePoints) }
    private val cachedOutputUs: Long by lazy { cachedCurve.outputDurationUs() }

    fun speedCurve(): SpeedCurve = cachedCurve

    /** Durata di questa clip nel video finale. */
    val outputUs: Long get() = cachedOutputUs

    val isHeavy: Boolean get() = width.toLong() * height * fps >= 3840L * 2160 * 120

    companion object {
        /** "Durata del file" di una foto: il massimo a cui si può allungare. */
        const val PHOTO_MAX_US = 60_000_000L
        const val PHOTO_DEFAULT_US = 3_000_000L
    }
}

@Serializable
data class ColorSettings(
    val look: String = "NONE",
    val highlights: Int = 0,
    val denoise: Int = 0,
    /** File .cube importato e copiato nella cartella dell'app (percorso), o null. */
    val lutPath: String? = null,
    val lutName: String? = null,
)

/** Categorie degli effetti (per il pannello). */
enum class FxCategory(val label: String) { TREND("Di tendenza"), WOW("Wow"), BEAT("A ritmo"), COLOR("Colore"), STYLE("Stile"), OPTICS("Ottica"), SHAPE("Forma"), MOTION("Movimento") }

/** Effetti video per clip; [toggle] = acceso/spento senza intensità. L'ordine non conta (salvati per nome). */
enum class FxType(val label: String, val category: FxCategory, val toggle: Boolean = false, val defaultAmount: Float = 0.6f) {
    // Colore
    BW("Bianco e nero", FxCategory.COLOR, defaultAmount = 1f),
    SEPIA("Seppia", FxCategory.COLOR, defaultAmount = 0.8f),
    VIVID("Colori vivaci", FxCategory.COLOR, defaultAmount = 0.5f),
    DUOTONE("Bicromia", FxCategory.COLOR, defaultAmount = 0.8f),
    HUE("Arcobaleno", FxCategory.COLOR, defaultAmount = 0.5f),
    POSTERIZE("Poster", FxCategory.COLOR, defaultAmount = 0.5f),
    INVERT("Negativo", FxCategory.COLOR, toggle = true, defaultAmount = 1f),
    // Stile
    FILM("Pellicola", FxCategory.STYLE),
    OLDFILM("Vecchio film", FxCategory.STYLE, defaultAmount = 0.7f),
    VHS("VHS", FxCategory.STYLE),
    GLITCH("Glitch", FxCategory.STYLE, defaultAmount = 0.5f),
    CARTOON("Fumetto", FxCategory.STYLE, defaultAmount = 0.6f),
    HALFTONE("Retino", FxCategory.STYLE, defaultAmount = 0.5f),
    PIXEL("Pixel", FxCategory.STYLE, defaultAmount = 0.4f),
    LIGHTLEAK("Luce calda", FxCategory.STYLE, defaultAmount = 0.6f),
    GLOW("Bagliore", FxCategory.STYLE),
    // Ottica
    BLUR("Sfocatura", FxCategory.OPTICS, defaultAmount = 0.4f),
    ZOOMBLUR("Sfocatura zoom", FxCategory.OPTICS, defaultAmount = 0.4f),
    TILTSHIFT("Miniatura", FxCategory.OPTICS, defaultAmount = 0.6f),
    VIGNETTE("Vignettatura", FxCategory.OPTICS),
    FISHEYE("Grandangolo", FxCategory.OPTICS, defaultAmount = 0.5f),
    CHROMA("Aberrazione", FxCategory.OPTICS, defaultAmount = 0.4f),
    WAVE("Onde", FxCategory.OPTICS, defaultAmount = 0.4f),
    // Forma
    MIRROR("Specchio", FxCategory.SHAPE, toggle = true, defaultAmount = 1f),
    KALEIDO("Caleidoscopio", FxCategory.SHAPE, defaultAmount = 0.5f),
    GRID("Moltiplica", FxCategory.SHAPE, defaultAmount = 0.3f),
    // Movimento
    SHAKE("Scossa", FxCategory.MOTION, defaultAmount = 0.5f),
    PULSE("Pulsa a ritmo", FxCategory.MOTION),
    STROBE("Lampi a ritmo", FxCategory.MOTION, defaultAmount = 0.5f),
    // Wow
    HIT("Colpo a ritmo", FxCategory.WOW, defaultAmount = 0.7f),
    CINEMATIC("Cinematografico", FxCategory.WOW, defaultAmount = 0.8f),
    NEON("Contorni neon", FxCategory.WOW, defaultAmount = 0.8f),
    RAYS("Raggi di luce", FxCategory.WOW, defaultAmount = 0.6f),
    BOKEH("Luci bokeh", FxCategory.WOW, defaultAmount = 0.6f),
    SPARKLE("Polvere d'oro", FxCategory.WOW, defaultAmount = 0.6f),
    PRISM("Prisma", FxCategory.WOW, defaultAmount = 0.5f),
    SPEEDLINES("Linee di velocità", FxCategory.WOW, defaultAmount = 0.6f),
    // Premium 2026 (shader a parte: WowFxEffect)
    DREAMY("Sogno", FxCategory.WOW, defaultAmount = 0.6f),
    FILM35("Pellicola 35 mm", FxCategory.WOW, defaultAmount = 0.7f),
    DUOPOP("Duotono pop", FxCategory.WOW, defaultAmount = 0.8f),
    SWEEP("Lampo di luce", FxCategory.WOW, defaultAmount = 0.7f),
    SPLIT3("Tre schermi", FxCategory.WOW, toggle = true, defaultAmount = 1f),
    HOLO("Olografico", FxCategory.WOW, defaultAmount = 0.6f),
    ZOOMBEAT("Zoom a ritmo", FxCategory.BEAT, defaultAmount = 0.6f),
    SHAKEBEAT("Scossa a ritmo", FxCategory.BEAT, defaultAmount = 0.6f),
    RGBBEAT("RGB a ritmo", FxCategory.BEAT, defaultAmount = 0.6f),
    FLARE("Bagliore anamorfico", FxCategory.WOW, defaultAmount = 0.6f),
    DUST("Polvere di pellicola", FxCategory.WOW, defaultAmount = 0.6f),
    LETTERBOX("Bande cinema", FxCategory.WOW, toggle = true, defaultAmount = 1f),
    IMPACT("Impatto a ritmo", FxCategory.BEAT, defaultAmount = 0.7f),
    PROMIST("Luce soffusa", FxCategory.WOW, defaultAmount = 0.6f),
    PETALS("Petali", FxCategory.WOW, defaultAmount = 0.6f),
    HEARTS("Cuori di luce", FxCategory.WOW, defaultAmount = 0.5f),
    SUPER8("Super 8", FxCategory.WOW, defaultAmount = 0.8f),
    FILMFRAME("Bordo pellicola", FxCategory.WOW, toggle = true, defaultAmount = 1f),
    PROJECTOR("Proiettore", FxCategory.WOW, defaultAmount = 0.6f),
    GLOWBEAT("Bagliore a ritmo", FxCategory.BEAT, defaultAmount = 0.6f),
    // Natura e viaggi
    HAZE("Foschia", FxCategory.WOW, defaultAmount = 0.5f),
    WATER("Riflessi d'acqua", FxCategory.WOW, defaultAmount = 0.5f),
    SKYGRAD("Cielo drammatico", FxCategory.WOW, defaultAmount = 0.6f),
    SNOW("Neve", FxCategory.WOW, defaultAmount = 0.6f),
    LEAVES("Foglie d'autunno", FxCategory.WOW, defaultAmount = 0.6f),
    // Tendenze 2026 (velocity edit, look da social): lampi brevi, scatti puliti, pellicola moderna, fotocamera digitale.
    FLASHBEAT("Flash a ritmo", FxCategory.BEAT, defaultAmount = 0.7f),
    BWBEAT("B/N a ritmo", FxCategory.BEAT, defaultAmount = 1f),
    BOUNCE("Rimbalzo a ritmo", FxCategory.BEAT, defaultAmount = 0.6f),
    HALATION("Pellicola moderna", FxCategory.TREND, defaultAmount = 0.6f),
    DIGICAM("Fotocamera digitale", FxCategory.TREND, defaultAmount = 0.7f),
    GRAIN("Grana fine", FxCategory.TREND, defaultAmount = 0.5f),
    MUTED("Toni smorzati", FxCategory.TREND, defaultAmount = 0.7f);

    /** Effetti che seguono i battiti della musica. */
    val onBeats: Boolean get() = this == PULSE || this == STROBE || this == HIT || category == FxCategory.BEAT

    /**
     * Nella scheda "Di tendenza": i look del 2026 più gli effetti a ritmo e ottici che si usano oggi nelle velocity edit
     * (così si trovano tutti in un posto, anche se appartengono a un'altra categoria).
     */
    val trendy: Boolean get() = category == FxCategory.TREND ||
        this in setOf(FLASHBEAT, BWBEAT, BOUNCE, SHAKEBEAT, RGBBEAT, ZOOMBLUR, CHROMA, LETTERBOX)

    /** Effetti del secondo shader (premium). */
    val wow2: Boolean get() = ordinal >= DREAMY.ordinal
}

/** Colore delle dissolvenze in entrata/uscita della clip. */
enum class FadeColor(val label: String) { BLACK("Nero"), WHITE("Bianco") }

@Serializable
data class ClipFx(val type: FxType, val amount: Float = type.defaultAmount)

/** Movimenti pronti per foto (e video), come gli stili di diapositiva di ProShow. */
enum class MotionStyle(val label: String) {
    NONE("Fermo"), ZOOM_IN("Zoom avanti"), ZOOM_OUT("Zoom indietro"),
    PAN_RIGHT("Verso destra"), PAN_LEFT("Verso sinistra"), PAN_DOWN("Verso il basso"), PAN_UP("Verso l'alto"),
    DIAGONAL("Diagonale"), TILT("Inclinazione");

    /** Keyframe di inizio e fine per una clip di [durationUs] (tempo del video originale). */
    fun keyframes(durationUs: Long): List<Keyframe> {
        val (a, b) = when (this) {
            NONE -> return emptyList()
            // Movimenti leggeri, come nelle app professionali: si vede quasi tutta la foto (al massimo +10%).
            ZOOM_IN -> Transform() to Transform(scale = 1.08f)
            ZOOM_OUT -> Transform(scale = 1.08f) to Transform()
            PAN_RIGHT -> Transform(x = -0.035f, scale = 1.08f) to Transform(x = 0.035f, scale = 1.08f)
            PAN_LEFT -> Transform(x = 0.035f, scale = 1.08f) to Transform(x = -0.035f, scale = 1.08f)
            PAN_DOWN -> Transform(y = -0.035f, scale = 1.08f) to Transform(y = 0.035f, scale = 1.08f)
            PAN_UP -> Transform(y = 0.035f, scale = 1.08f) to Transform(y = -0.035f, scale = 1.08f)
            DIAGONAL -> Transform(x = -0.02f, y = -0.02f, scale = 1.05f) to Transform(x = 0.02f, y = 0.02f, scale = 1.1f)
            TILT -> Transform(scale = 1.06f, rotation = -1f) to Transform(scale = 1.08f, rotation = 1f)
        }
        return listOf(Keyframe(0, a, Easing.LINEAR), Keyframe(durationUs, b, Easing.LINEAR))
    }

    companion object {
        /** Movimenti per il "casuale": tutti tranne fermo, senza ripetere lo stesso due volte di fila. */
        val varied = entries.filter { it != NONE }

        /** Movimento pronto che corrisponde ai keyframe della clip (null se personalizzati). */
        fun matching(keyframes: List<Keyframe>, durationUs: Long): MotionStyle? =
            entries.firstOrNull { it.keyframes(durationUs) == keyframes }
    }
}

enum class TextFont(val label: String) {
    MODERNO("Moderno"), TITOLO("Titolo"), IMPATTO("Impatto"), POSTER("Poster"),
    ELEGANTE("Elegante"), CORSIVO("Corsivo"), PENNARELLO("Pennarello"), MACCHINA("Macchina"),
}

enum class TextLook(val label: String) { SEMPLICE("Semplice"), CONTORNO("Contorno"), OMBRA("Ombra"), RIQUADRO("Riquadro"), NEON("Neon") }

enum class TextAnim(val label: String, val inOnly: Boolean = false, val letters: Boolean = false) {
    NONE("Nessuna"), FADE("Dissolvenza"), POP("Pop"), SLIDE("Scorri"), ZOOM("Zoom"),
    TYPE("Macchina da scrivere", inOnly = true), WORDS("Parola per parola", inOnly = true),
    // Effetti "wow": lettera per lettera, glitch e luccichio.
    DROP("Lettere cadono", letters = true),
    RISE("Lettere sfumate", letters = true),
    WAVE("Onda", letters = true),
    GLITCH("Glitch"),
    SHINE("Luccichio", inOnly = true),
    // Premium 2026
    BOUNCE("Rimbalzo", letters = true),
    FLIP("Ribalta 3D", letters = true),
    SPIN("Vortice", letters = true),
    SCATTER("Esplosione", letters = true),
    BLUR("Messa a fuoco"),
    FLICKER("Neon tremolante", inOnly = true),
}

/** Testo sovrapposto al video, dal tempo [startUs] della timeline per [durationUs]. */
@Serializable
data class TextItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "Testo",
    val startUs: Long = 0,
    val durationUs: Long = 3_000_000,
    /** Centro del testo rispetto al centro del video, in frazioni del quadro (-0,5…0,5; y verso il basso). */
    val x: Float = 0f,
    val y: Float = 0f,
    /** Altezza dei caratteri rispetto all'altezza del video. */
    val size: Float = 0.06f,
    val rotation: Float = 0f,
    val color: Int = 0xFFFFFFFF.toInt(),
    val font: TextFont = TextFont.MODERNO,
    /** Font aggiunto dall'utente (file .ttf/.otf nella cartella dell'app): se c'è, vale al posto di [font]. */
    val fontFile: String? = null,
    val look: TextLook = TextLook.OMBRA,
    val animIn: TextAnim = TextAnim.FADE,
    val animOut: TextAnim = TextAnim.FADE,
    /** Movimento (come in ProShow): posizione finale; null = fermo. x, y assoluti, scala e rotazione relative. */
    val endPose: Transform? = null,
    /** Secondo colore per una sfumatura dall'alto in basso (null = colore pieno). */
    val gradientTo: Int? = null,
    /** Spaziatura fra le lettere, in frazioni della misura del carattere. */
    val spacing: Float = 0f,
    val upper: Boolean = false,
    /** Sottotitolo automatico (si può rifare o togliere in blocco). */
    val subtitle: Boolean = false,
    /** Inizio di ogni parola rispetto a [startUs] (sottotitoli con i tempi del parlato). */
    val wordsUs: List<Long> = emptyList(),
    /** 0 = testo intero; 1 = karaoke (la parola detta si colora di [highlight]); 2 = le parole compaiono mentre sono dette. */
    val wordMode: Int = 0,
    val highlight: Int = 0xFFF2B33D.toInt(),
    /** Titoli di coda: il testo (più righe) scorre dal basso in alto a velocità costante per tutta la durata. */
    val roll: Boolean = false,
) {
    val endUs: Long get() = startUs + durationUs

    companion object {
        const val MIN_US = 300_000L
    }
}

enum class LayerBorder(val label: String) { NONE("Nessuno"), WHITE("Bianco"), BLACK("Nero") }

/** Forma (maschera) dell'immagine. */
enum class LayerMask(val label: String) {
    NONE("Rettangolo"), ROUNDED("Arrotondata"), CIRCLE("Cerchio"), HEART("Cuore"), STAR("Stella"), SOFT("Sfumata"),
}

/** Scontorno: colore di sfondo da rendere trasparente. */
enum class KeyColor(val label: String) { NONE("Nessuno"), GREEN("Verde"), BLUE("Blu"), WHITE("Bianco"), BLACK("Nero") }

/**
 * Livello immagine (foto, logo, adesivo PNG con trasparenza) sopra al video.
 * [pose] = posizione iniziale (scala 1 = grande quanto il quadro), [endPose] = finale (movimento) o null.
 */
@Serializable
data class Layer(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val name: String = "Immagine",
    val width: Int,
    val height: Int,
    val startUs: Long = 0,
    val durationUs: Long = 4_000_000,
    val pose: Transform = Transform(scale = 0.5f),
    val endPose: Transform? = null,
    val border: LayerBorder = LayerBorder.NONE,
    val shadow: Boolean = false,
    val rounded: Boolean = false,
    val animIn: TextAnim = TextAnim.FADE,
    val animOut: TextAnim = TextAnim.FADE,
    /** Video sovrapposto (picture-in-picture) invece di immagine. */
    val isVideo: Boolean = false,
    /** Audio del video sovrapposto. */
    val muted: Boolean = false,
    val volume: Float = 1f,
    /** Video: punto di partenza nel file e durata del file. */
    val sourceStartUs: Long = 0,
    val sourceDurationUs: Long = 0,
    val mask: LayerMask = LayerMask.NONE,
    val key: KeyColor = KeyColor.NONE,
    /** Tolleranza dello scontorno (0..1). */
    val keyTolerance: Float = 0.35f,
    /** Video sovrapposto: si vede solo la persona (sfondo trasparente). */
    val person: PersonFx? = null,
    val fileName: String = "",
    val fileSize: Long = 0,
) {
    val endUs: Long get() = startUs + durationUs

    /** Forma effettiva (il vecchio "angoli arrotondati" vale come forma arrotondata). */
    val shape: LayerMask get() = if (mask == LayerMask.NONE && rounded) LayerMask.ROUNDED else mask

    /** Posizione al tempo [localUs] dall'inizio del livello (movimento morbido fra inizio e fine). */
    fun poseAt(localUs: Long): Transform {
        val e = endPose ?: return pose
        val u = (localUs.toFloat() / durationUs.coerceAtLeast(1)).coerceIn(0f, 1f)
        return pose.lerp(e, u * u * (3 - 2 * u))
    }

    companion object {
        const val MIN_US = 300_000L
    }
}

/** Impostazioni dello slideshow automatico. */
data class SlideshowOptions(
    /** Durata di ogni foto (se non si segue il ritmo). */
    val photoUs: Long = 3_000_000,
    /** Battiti per foto (0 = non seguire il ritmo). */
    val beatsPerPhoto: Int = 0,
    /** Movimento delle foto: null = vario (uno diverso per foto); NONE = ferme. */
    val motion: MotionStyle? = null,
    /** Transizione fra le clip: null = varie; [noTransitions] = nessuna. */
    val transition: TransitionType? = null,
    val noTransitions: Boolean = false,
    val transitionUs: Long = 600_000,
    val fill: Boolean = true,
)

/** Stili di titolo pronti: un tocco imposta carattere, colore, effetto, posizione, animazioni e movimento. */
enum class TextPreset(val label: String) {
    CINEMA("Cinema"), DIDASCALIA("Didascalia"), POP("Pop"), ELEGANTE("Elegante"), NEON("Neon"),
    IMPATTO("Impatto"), FIRMA("Firma"), DIARIO("Diario"),
    GLITCH("Glitch"), ONDA("Onda"), ORO("Oro"), CASCATA("Cascata"),
    CINEMA26("Cinema 2026"), SOGNO("Sogno"), TITOLO3D("Titolo 3D"), FESTA("Festa"), INSEGNA("Insegna"), ESPLOSIONE("Esplosione"),
    AMORE("Amore"), DATA("Data videocamera"), TAPPA("Tappa di viaggio"), STAMPA("Stampa"), PASSO("Passo"),
    // Tendenze 2026: bold senza grazie, bianco, niente sfumature né scritte storte, animazioni sobrie.
    PULITO("Pulito"), ETICHETTA("Etichetta"), TITOLONE("Titolone"), MINIMAL("Minimal"), SOCIAL("Social");

    /** Stili di tendenza (2026): in testa alla lista. */
    val trend: Boolean get() = ordinal >= PULITO.ordinal

    fun applyTo(t: TextItem): TextItem = when (this) {
        CINEMA -> t.copy(font = TextFont.TITOLO, color = WHITE, gradientTo = 0xFFF2B33D.toInt(), spacing = 0.12f, upper = true, look = TextLook.OMBRA,
            size = 0.11f, x = 0f, y = 0f, rotation = 0f, animIn = TextAnim.FADE, animOut = TextAnim.FADE, endPose = Transform(x = 0f, y = 0f, scale = 1.12f))
        DIDASCALIA -> t.copy(gradientTo = null, spacing = 0f, upper = false, font = TextFont.MODERNO, color = WHITE, look = TextLook.RIQUADRO, size = 0.045f, x = 0f, y = 0.36f, rotation = 0f,
            animIn = TextAnim.SLIDE, animOut = TextAnim.FADE, endPose = null)
        POP -> t.copy(font = TextFont.POSTER, color = 0xFFFFE14D.toInt(), gradientTo = 0xFFFF7AB6.toInt(), spacing = 0f, upper = true, look = TextLook.CONTORNO,
            size = 0.08f, x = 0f, y = 0f, rotation = -4f, animIn = TextAnim.WORDS, animOut = TextAnim.POP, endPose = null)
        ELEGANTE -> t.copy(font = TextFont.ELEGANTE, color = WHITE, gradientTo = null, spacing = 0.04f, upper = false, look = TextLook.OMBRA, size = 0.07f, x = 0f, y = 0.05f, rotation = 0f,
            animIn = TextAnim.FADE, animOut = TextAnim.FADE, endPose = Transform(x = 0f, y = -0.02f, scale = 1.04f))
        NEON -> t.copy(gradientTo = null, spacing = 0f, upper = false, font = TextFont.IMPATTO, color = 0xFFFF7AB6.toInt(), look = TextLook.NEON, size = 0.09f, x = 0f, y = 0f, rotation = 0f,
            animIn = TextAnim.ZOOM, animOut = TextAnim.FADE, endPose = null)
        IMPATTO -> t.copy(font = TextFont.IMPATTO, color = WHITE, gradientTo = null, spacing = 0.02f, upper = true, look = TextLook.CONTORNO, size = 0.14f, x = 0f, y = 0f, rotation = 0f,
            animIn = TextAnim.ZOOM, animOut = TextAnim.ZOOM, endPose = null)
        FIRMA -> t.copy(gradientTo = null, spacing = 0f, upper = false, font = TextFont.CORSIVO, color = WHITE, look = TextLook.OMBRA, size = 0.06f, x = 0.2f, y = 0.38f, rotation = -3f,
            animIn = TextAnim.FADE, animOut = TextAnim.FADE, endPose = null)
        DIARIO -> t.copy(gradientTo = null, spacing = 0f, upper = false, font = TextFont.MACCHINA, color = WHITE, look = TextLook.RIQUADRO, size = 0.04f, x = 0f, y = 0.3f, rotation = 0f,
            animIn = TextAnim.TYPE, animOut = TextAnim.FADE, endPose = null)
        GLITCH -> t.copy(font = TextFont.IMPATTO, color = WHITE, gradientTo = null, spacing = 0.04f, upper = true, look = TextLook.CONTORNO, size = 0.1f, x = 0f, y = 0f, rotation = 0f,
            animIn = TextAnim.GLITCH, animOut = TextAnim.GLITCH, endPose = null)
        ONDA -> t.copy(font = TextFont.POSTER, color = 0xFFFFE14D.toInt(), gradientTo = 0xFFFF5A4E.toInt(), spacing = 0.02f, upper = true, look = TextLook.CONTORNO,
            size = 0.09f, x = 0f, y = 0f, rotation = 0f, animIn = TextAnim.WAVE, animOut = TextAnim.DROP, endPose = null)
        ORO -> t.copy(font = TextFont.TITOLO, color = 0xFFFFE7A3.toInt(), gradientTo = 0xFFC28A2C.toInt(), spacing = 0.15f, upper = true, look = TextLook.OMBRA,
            size = 0.1f, x = 0f, y = 0f, rotation = 0f, animIn = TextAnim.SHINE, animOut = TextAnim.FADE, endPose = Transform(x = 0f, y = 0f, scale = 1.06f))
        CASCATA -> t.copy(font = TextFont.ELEGANTE, color = WHITE, gradientTo = null, spacing = 0.02f, upper = false, look = TextLook.OMBRA, size = 0.075f, x = 0f, y = 0f, rotation = 0f,
            animIn = TextAnim.DROP, animOut = TextAnim.RISE, endPose = null)
        CINEMA26 -> t.copy(font = TextFont.TITOLO, color = 0xFFFFF4DA.toInt(), gradientTo = 0xFFE0A94A.toInt(), spacing = 0.28f, upper = true, look = TextLook.OMBRA,
            size = 0.1f, x = 0f, y = 0f, rotation = 0f, animIn = TextAnim.BLUR, animOut = TextAnim.BLUR, endPose = Transform(x = 0f, y = 0f, scale = 1.1f))
        SOGNO -> t.copy(font = TextFont.ELEGANTE, color = WHITE, gradientTo = 0xFFFFD6E8.toInt(), spacing = 0.06f, upper = false, look = TextLook.OMBRA,
            size = 0.08f, x = 0f, y = 0f, rotation = 0f, animIn = TextAnim.BLUR, animOut = TextAnim.RISE, endPose = Transform(x = 0f, y = -0.015f, scale = 1.05f))
        TITOLO3D -> t.copy(font = TextFont.POSTER, color = WHITE, gradientTo = 0xFF5BC0FF.toInt(), spacing = 0.05f, upper = true, look = TextLook.CONTORNO,
            size = 0.1f, x = 0f, y = 0f, rotation = 0f, animIn = TextAnim.FLIP, animOut = TextAnim.FLIP, endPose = null)
        FESTA -> t.copy(font = TextFont.POSTER, color = 0xFFFFE14D.toInt(), gradientTo = 0xFFFF5AA8.toInt(), spacing = 0.02f, upper = true, look = TextLook.CONTORNO,
            size = 0.1f, x = 0f, y = 0f, rotation = -3f, animIn = TextAnim.BOUNCE, animOut = TextAnim.SCATTER, endPose = null)
        INSEGNA -> t.copy(font = TextFont.CORSIVO, color = 0xFF5BF0FF.toInt(), gradientTo = null, spacing = 0f, upper = false, look = TextLook.NEON,
            size = 0.1f, x = 0f, y = 0f, rotation = -4f, animIn = TextAnim.FLICKER, animOut = TextAnim.FADE, endPose = null)
        AMORE -> t.copy(font = TextFont.CORSIVO, color = 0xFFFFF6EE.toInt(), gradientTo = 0xFFF7B2C4.toInt(), spacing = 0.02f, upper = false, look = TextLook.OMBRA,
            size = 0.1f, x = 0f, y = 0f, rotation = -3f, animIn = TextAnim.BLUR, animOut = TextAnim.RISE, endPose = Transform(x = 0f, y = -0.01f, scale = 1.06f))
        DATA -> t.copy(font = TextFont.MACCHINA, color = 0xFFFF9F1C.toInt(), gradientTo = null, spacing = 0.06f, upper = true, look = TextLook.OMBRA,
            size = 0.042f, x = 0.3f, y = 0.4f, rotation = 0f, animIn = TextAnim.NONE, animOut = TextAnim.NONE, endPose = null)
        TAPPA -> t.copy(font = TextFont.MODERNO, color = WHITE, gradientTo = null, spacing = 0.22f, upper = true, look = TextLook.OMBRA,
            size = 0.032f, x = -0.28f, y = 0.4f, rotation = 0f, animIn = TextAnim.BLUR, animOut = TextAnim.FADE, endPose = null)
        ESPLOSIONE -> t.copy(font = TextFont.IMPATTO, color = WHITE, gradientTo = 0xFFFF7A3D.toInt(), spacing = 0.03f, upper = true, look = TextLook.CONTORNO,
            size = 0.12f, x = 0f, y = 0f, rotation = 0f, animIn = TextAnim.SPIN, animOut = TextAnim.SCATTER, endPose = null)
        // Stampa (grunge): macchina da scrivere, maiuscolo spaziato, entra tremolando come una pellicola.
        STAMPA -> t.copy(font = TextFont.MACCHINA, color = 0xFFF2EFE6.toInt(), gradientTo = null, spacing = 0.14f, upper = true, look = TextLook.OMBRA,
            size = 0.06f, x = 0f, y = 0f, rotation = 0f, animIn = TextAnim.FLICKER, animOut = TextAnim.FADE, endPose = Transform(x = 0f, y = 0f, scale = 1.04f))
        // Passo (tutorial): etichetta chiara in alto a sinistra, testo scuro su pillola bianca.
        PASSO -> t.copy(font = TextFont.MODERNO, color = 0xFF111111.toInt(), gradientTo = null, spacing = 0f, upper = false, look = TextLook.RIQUADRO,
            size = 0.04f, x = -0.22f, y = -0.38f, rotation = 0f, animIn = TextAnim.SLIDE, animOut = TextAnim.FADE, endPose = null)
        // Pulito: bold senza grazie, lettere strette, entra a fuoco e si avvicina appena.
        PULITO -> t.copy(font = TextFont.POSTER, color = WHITE, gradientTo = null, spacing = -0.02f, upper = false, look = TextLook.OMBRA,
            size = 0.075f, x = 0f, y = 0f, rotation = 0f, animIn = TextAnim.BLUR, animOut = TextAnim.FADE, endPose = Transform(x = 0f, y = 0f, scale = 1.03f))
        // Etichetta: testo scuro su pillola bianca (il riquadro diventa chiaro con il testo scuro).
        ETICHETTA -> t.copy(font = TextFont.MODERNO, color = 0xFF111111.toInt(), gradientTo = null, spacing = 0f, upper = false, look = TextLook.RIQUADRO,
            size = 0.045f, x = 0f, y = 0.3f, rotation = 0f, animIn = TextAnim.POP, animOut = TextAnim.FADE, endPose = null)
        // Titolone: enorme, condensato, a tutto schermo; entra a fuoco e spinge piano in avanti.
        TITOLONE -> t.copy(font = TextFont.IMPATTO, color = WHITE, gradientTo = null, spacing = -0.01f, upper = true, look = TextLook.OMBRA,
            size = 0.13f, x = 0f, y = 0f, rotation = 0f, animIn = TextAnim.BLUR, animOut = TextAnim.FADE, endPose = Transform(x = 0f, y = 0f, scale = 1.06f))
        // Minimal: piccolo, maiuscolo spaziato, senza effetti.
        MINIMAL -> t.copy(font = TextFont.MODERNO, color = WHITE, gradientTo = null, spacing = 0.3f, upper = true, look = TextLook.OMBRA,
            size = 0.03f, x = 0f, y = 0f, rotation = 0f, animIn = TextAnim.FADE, animOut = TextAnim.FADE, endPose = null)
        // Social: come le didascalie dei reel, bianco con contorno nero, parola per parola.
        SOCIAL -> t.copy(font = TextFont.POSTER, color = WHITE, gradientTo = null, spacing = 0f, upper = false, look = TextLook.CONTORNO,
            size = 0.065f, x = 0f, y = 0.25f, rotation = 0f, animIn = TextAnim.WORDS, animOut = TextAnim.FADE, endPose = null)
    }

    companion object {
        private const val WHITE = 0xFFFFFFFF.toInt()

        /** Stile pronto che corrisponde esattamente al testo (null se ritoccato a mano). */
        fun matching(t: TextItem): TextPreset? = entries.firstOrNull { it.applyTo(t) == t }
    }
}

/** Distribuisce elementi su righe senza sovrapposizioni (ordinati per inizio). */
fun <T> packLanes(items: List<T>, start: (T) -> Long, end: (T) -> Long): List<List<T>> {
    val lanes = mutableListOf<MutableList<T>>()
    items.sortedBy(start).forEach { t ->
        val lane = lanes.firstOrNull { end(it.last()) <= start(t) }
        if (lane != null) lane += t else lanes += mutableListOf(t)
    }
    return lanes
}

/** Brano musicale sulla timeline: suona da [startUs] per [lengthUs], partendo da [offsetUs] nel file. */
@Serializable
data class MusicTrack(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val name: String,
    val sourceDurationUs: Long,
    val startUs: Long = 0,
    val offsetUs: Long = 0,
    val lengthUs: Long = sourceDurationUs,
    /** 1 = invariato, da 0 a 2. */
    val volume: Float = 1f,
    val fadeInUs: Long = 0,
    val fadeOutUs: Long = 0,
    /** Battiti rilevati (tempo nel file del brano) e tempo in BPM; vuoto = non ancora analizzato. */
    val beatsUs: List<Long> = emptyList(),
    val bpm: Float = 0f,
    /** Energia del brano a ogni battito (0…1), per la regia automatica. */
    val energy: List<Float> = emptyList(),
    /** Usa un battito ogni N (1 = tutti, 2, 4 = uno per battuta). */
    val beatEvery: Int = 1,
    /** Spostamento di tutti i battiti automatici (correzione fine), in µs. */
    val beatShiftUs: Long = 0,
    /** Battiti segnati a mano (tempo nel file del brano). */
    val markersUs: List<Long> = emptyList(),
    /** Per i brani presi in rete: autore, licenza e pagina da citare. */
    val credit: String? = null,
    /** Voce (fuori campo o audio staccato da una clip): fa abbassare la musica, non viene abbassata. */
    val dialog: Boolean = false,
    /** Battiti automatici tolti a mano. */
    val hiddenUs: List<Long> = emptyList(),
) {
    val endUs: Long get() = startUs + lengthUs

    /** Battiti automatici in uso (tempo nel file, già spostati). */
    fun autoBeatsSrc(): List<Long> = beatsUs
        .filterIndexed { i, _ -> i % beatEvery.coerceAtLeast(1) == 0 }
        .filter { b -> hiddenUs.none { kotlin.math.abs(it - b) < 20_000 } }
        .map { it + beatShiftUs }

    private fun toTimeline(src: List<Long>, projectUs: Long): List<Long> {
        val play = playUs(projectUs)
        return src.map { startUs + it - offsetUs }.filter { it >= startUs && it < startUs + play }
    }

    /** Battiti automatici sulla timeline del video, dentro la parte che suona. */
    fun timelineAutoBeats(projectUs: Long): List<Long> = toTimeline(autoBeatsSrc(), projectUs)

    /** Battiti segnati a mano, sulla timeline. */
    fun timelineMarkers(projectUs: Long): List<Long> = toTimeline(markersUs, projectUs)

    /** Tutti i battiti in uso (automatici e a mano) sulla timeline, ordinati. */
    fun timelineBeats(projectUs: Long): List<Long> =
        (timelineAutoBeats(projectUs) + timelineMarkers(projectUs)).sorted()

    /**
     * Segna o toglie un battito nel punto [srcUs] del brano:
     * se c'è già un battito vicino (entro [tolUs]) lo toglie, altrimenti ne aggiunge uno a mano.
     */
    fun toggleBeatAt(srcUs: Long, tolUs: Long = 80_000): MusicTrack {
        markersUs.minByOrNull { kotlin.math.abs(it - srcUs) }?.let { m ->
            if (kotlin.math.abs(m - srcUs) <= tolUs) return copy(markersUs = markersUs - m)
        }
        autoBeatsSrc().minByOrNull { kotlin.math.abs(it - srcUs) }?.let { b ->
            if (kotlin.math.abs(b - srcUs) <= tolUs) return copy(hiddenUs = hiddenUs + (b - beatShiftUs))
        }
        return copy(markersUs = (markersUs + srcUs).sorted())
    }

    /** Aggiunge un battito a mano (tap durante l'ascolto); ignora un doppio tocco ravvicinato. */
    fun addMarker(srcUs: Long): MusicTrack =
        if (markersUs.any { kotlin.math.abs(it - srcUs) < 60_000 }) this else copy(markersUs = (markersUs + srcUs).sorted())

    val hasBeats: Boolean get() = beatsUs.isNotEmpty() || markersUs.isNotEmpty()

    /** Durata che suona davvero: si ferma alla fine del video. */
    fun playUs(projectUs: Long): Long = minOf(lengthUs, projectUs - startUs).coerceAtLeast(0)

    /** Guadagno (volume e dissolvenze) a [t] µs dall'inizio del brano sulla timeline. */
    fun gainAt(t: Long, projectUs: Long): Float {
        val len = playUs(projectUs)
        var g = volume
        if (fadeInUs > 0 && t < fadeInUs) g *= (t.toFloat() / fadeInUs).coerceIn(0f, 1f)
        val fo = minOf(fadeOutUs, len)
        if (fo > 0 && t > len - fo) g *= ((len - t).toFloat() / fo).coerceIn(0f, 1f)
        return g
    }

    companion object {
        const val MIN_US = 500_000L
    }
}

@Serializable
data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val clips: List<Clip> = emptyList(),
    val color: ColorSettings = ColorSettings(),
    val music: List<MusicTrack> = emptyList(),
    val texts: List<TextItem> = emptyList(),
    val layers: List<Layer> = emptyList(),
    /** Se il progetto è un video automatico: come rifarlo (per "Altra proposta"). */
    val auto: AutoRecipe? = null,
    /** Formato del video e preimpostazioni per le clip con lo stesso orientamento o con quello opposto. */
    val format: VideoFormat = VideoFormat.AUTO,
    val sameMode: FrameMode = FrameMode.FILL,
    val otherMode: FrameMode = FrameMode.BLUR,
    /** Ducking: la musica si abbassa di [duckDb] dB dove si parla (clip, voce fuori campo, audio staccato). */
    val duck: Boolean = false,
    val duckDb: Int = 12,
) {
    /** Orientamento del video finito. */
    fun outputOrientation(): Orientation = when (format) {
        VideoFormat.VERTICAL -> Orientation.PORTRAIT
        VideoFormat.HORIZONTAL -> Orientation.LANDSCAPE
        VideoFormat.SQUARE -> Orientation.SQUARE
        VideoFormat.AUTO -> (clips.firstOrNull { !it.isPhoto } ?: clips.firstOrNull())?.orientation ?: Orientation.LANDSCAPE
    }

    /** Come mettere nel quadro una clip, secondo le preimpostazioni (nel quadrato tutte sono "dello stesso" orientamento solo se quadrate). */
    fun presetModeFor(c: Clip): FrameMode = if (c.orientation == outputOrientation()) sameMode else otherMode

    /** Applica le preimpostazioni alle clip indicate (tutte se null). */
    fun applyFramePresets(ids: Set<String>? = null): Project =
        copy(clips = clips.map { if (ids == null || it.id in ids) it.withMode(presetModeFor(it)) else it })

    /** Tutte le clip seguono le preimpostazioni. */
    fun followsPresets(): Boolean = clips.all { it.frameMode == presetModeFor(it) }

    fun replaceLayer(l: Layer): Project = copy(layers = layers.map { if (it.id == l.id) l else it })

    /**
     * Dissolvenze incrociate: per il taglio dopo la clip [index], quanto prima del taglio si vede già
     * l'inizio della clip successiva ([beforeUs], dal materiale prima del suo punto d'ingresso) e quanto
     * dopo si vede ancora la fine della clip precedente ([afterUs], dal materiale oltre il suo punto d'uscita).
     */
    data class Crossfade(val index: Int, val cutUs: Long, val beforeUs: Long, val afterUs: Long)

    fun crossfades(): List<Crossfade> {
        val starts = clipStartsUs()
        return clips.indices.mapNotNull { i ->
            val spec = clips[i].transitionOut ?: return@mapNotNull null
            if (spec.type != TransitionType.CROSSFADE) return@mapNotNull null
            val a = clips[i]
            val b = clips.getOrNull(i + 1) ?: return@mapNotNull null
            val half = spec.durationUs / 2
            // Materiale disponibile: le foto non finiscono mai; i video hanno i margini fuori dal taglio.
            val headRoom = if (b.isPhoto) Long.MAX_VALUE else b.trimStartUs
            val tailRoom = if (a.isPhoto) Long.MAX_VALUE else a.sourceDurationUs - a.trimEndUs
            var before = minOf(half, headRoom, a.outputUs / 2)
            var after = minOf(half, tailRoom, b.outputUs / 2)
            // Se manca materiale da un lato, si usa di più l'altro.
            if (before < half) after = minOf(spec.durationUs - before, tailRoom, b.outputUs / 2)
            if (after < half) before = minOf(spec.durationUs - after, headRoom, a.outputUs / 2)
            if (before + after < 100_000) return@mapNotNull null
            Crossfade(i, starts[i] + a.outputUs, before.coerceAtLeast(0), after.coerceAtLeast(0))
        }
    }

    fun replaceText(t: TextItem): Project = copy(texts = texts.map { if (it.id == t.id) t else it })

    val durationUs: Long get() = clips.sumOf { it.outputUs }

    /** Brani che suonano davvero (dentro la durata del video). */
    fun audibleMusic(): List<MusicTrack> = durationUs.let { d -> music.filter { it.startUs < d && it.playUs(d) > 0 } }

    /**
     * Distribuisce i brani su righe senza sovrapposizioni (per la timeline e per l'anteprima):
     * ogni riga è una lista ordinata per inizio.
     */
    fun musicLanes(tracks: List<MusicTrack> = music): List<List<MusicTrack>> {
        val lanes = mutableListOf<MutableList<MusicTrack>>()
        tracks.sortedBy { it.startUs }.forEach { t ->
            val lane = lanes.firstOrNull { it.last().endUs <= t.startUs }
            if (lane != null) lane += t else lanes += mutableListOf(t)
        }
        return lanes
    }

    fun replaceMusic(t: MusicTrack): Project = copy(music = music.map { if (it.id == t.id) t else it })

    /** Tutti i battiti in uso sulla timeline, ordinati. */
    fun beatTimes(withinVideo: Boolean = true): List<Long> =
        (if (withinVideo) durationUs else Long.MAX_VALUE / 4).let { d -> music.flatMap { it.timelineBeats(d) }.distinct().sorted() }

    /**
     * Cambia il taglio della clip [i] perché duri [targetOutUs] nel video finale,
     * spostando la fine (o l'inizio) nel video originale. Ricerca binaria: vale anche con le curve di velocità.
     */
    fun fitClipOutput(i: Int, targetOutUs: Long, moveEnd: Boolean = true): Project? {
        val c = clips.getOrNull(i) ?: return null
        val min = 100_000L
        fun out(v: Long) = (if (moveEnd) c.copy(trimEndUs = v) else c.copy(trimStartUs = v)).outputUs
        var lo: Long; var hi: Long
        if (moveEnd) { lo = c.trimStartUs + min; hi = c.sourceDurationUs } else { lo = 0; hi = c.trimEndUs - min }
        if (lo > hi) return null
        // Con la fine: più grande = più lunga. Con l'inizio: più grande = più corta.
        val maxOut = if (moveEnd) out(hi) else out(lo)
        val minOut = if (moveEnd) out(lo) else out(hi)
        if (targetOutUs > maxOut + 20_000 || targetOutUs < minOut - 20_000) return null
        repeat(40) {
            val mid = (lo + hi) / 2
            val longer = out(mid) < targetOutUs
            if (longer == moveEnd) lo = mid else hi = mid
        }
        val v = (lo + hi) / 2
        return replaceClip(if (moveEnd) c.copy(trimEndUs = v) else c.copy(trimStartUs = v))
    }

    /**
     * Allinea ogni taglio fra le clip (e la fine del video) al battito più vicino,
     * allungando o accorciando la clip prima del taglio. Restituisce null se non c'è nulla da allineare.
     */
    fun alignCutsToBeats(minClipUs: Long = 300_000): Project? {
        // Tutta la musica conta: allungando una clip il video può arrivare a battiti oltre la fine attuale.
        val beats = beatTimes(withinVideo = false)
        if (beats.isEmpty()) return null
        var p = this
        var changed = false
        for (i in clips.indices) {
            val start = p.clipStartsUs()[i]
            val c = p.clips[i]
            val end = start + c.outputUs
            val maxEnd = start + c.copy(trimEndUs = c.sourceDurationUs).outputUs
            val target = beats.filter { it >= start + minClipUs && it <= maxEnd }
                .minByOrNull { kotlin.math.abs(it - end) } ?: continue
            if (kotlin.math.abs(target - end) < 5_000) continue
            p = p.fitClipOutput(i, target - start) ?: continue
            changed = true
        }
        return if (changed) p else null
    }

    /**
     * Slideshow automatico: durata delle foto (fissa o a ritmo), movimento, riempimento del quadro
     * e transizioni fra tutte le clip. A ritmo, anche i tagli dei video si allineano ai battiti.
     */
    fun slideshow(o: SlideshowOptions, seed: Int = 7): Project {
        val beats = beatTimes(withinVideo = false)
        val rnd = kotlin.random.Random(seed)
        var q = this
        var prev: MotionStyle? = null
        var prevT: TransitionType? = null
        for (i in clips.indices) {
            var c = q.clips[i]
            if (c.isPhoto) {
                val start = q.clipStartsUs()[i]
                var dur = o.photoUs
                if (o.beatsPerPhoto > 0 && beats.isNotEmpty()) {
                    val j0 = beats.indexOfFirst { it > start + 150_000 }
                    if (j0 >= 0) dur = beats[(j0 + o.beatsPerPhoto - 1).coerceAtMost(beats.lastIndex)] - start
                }
                dur = dur.coerceIn(300_000L, Clip.PHOTO_MAX_US)
                val style = o.motion ?: MotionStyle.varied.filter { it != prev }.random(rnd)
                prev = style
                c = c.copy(trimStartUs = 0, trimEndUs = dur, keyframes = style.keyframes(dur)).let { x -> if (o.fill) x.withMode(q.presetModeFor(x)) else x }
            }
            if (i < clips.lastIndex) {
                val type = when {
                    o.noTransitions -> null
                    o.transition != null -> o.transition
                    else -> TransitionType.entries.filter { it != prevT && it.soft }.random(rnd)
                }
                prevT = type
                c = c.copy(transitionOut = type?.let { TransitionSpec(it, o.transitionUs) })
            }
            q = q.replaceClip(c)
        }
        if (o.beatsPerPhoto > 0) q = q.alignCutsToBeats() ?: q
        return q
    }

    /** Divide i video su ogni battito fra [fromUs] e [toUs] (per montare un video lungo a ritmo). */
    fun splitAtBeats(fromUs: Long = 0, toUs: Long = Long.MAX_VALUE): Project? {
        var p = this
        var n = 0
        beatTimes().filter { it in fromUs..toUs }.forEach { b ->
            p.splitAt(b)?.let { p = it; n++ }
        }
        return if (n > 0) p else null
    }

    /** Inizio di ogni clip sulla timeline finale. */
    fun clipStartsUs(): List<Long> {
        var acc = 0L
        return clips.map { c -> acc.also { acc += c.outputUs } }
    }

    /** Clip che contiene il tempo t della timeline, con l'offset dentro la clip. */
    fun locate(timeUs: Long): Pair<Int, Long>? {
        if (clips.isEmpty()) return null
        var acc = 0L
        clips.forEachIndexed { i, c ->
            val out = c.outputUs
            if (timeUs < acc + out || i == clips.lastIndex) return i to (timeUs - acc).coerceIn(0, out)
            acc += out
        }
        return null
    }

    fun touch() = copy(updatedAt = System.currentTimeMillis())

    /**
     * Durata effettiva di metà transizione su ciascun lato del taglio fra la clip [i] e la successiva:
     * mai più di metà della clip più corta, così due transizioni non si sovrappongono.
     */
    fun transitionHalfUs(i: Int): Long {
        val spec = clips.getOrNull(i)?.transitionOut ?: return 0
        val next = clips.getOrNull(i + 1) ?: return 0
        val half = spec.durationUs / 2
        return minOf(half, clips[i].outputUs / 2, next.outputUs / 2).coerceAtLeast(0)
    }

    // ---------------- Modifiche (restituiscono un nuovo progetto: undo facile) ----------------

    fun addClips(newClips: List<Clip>, atIndex: Int = clips.size): Project =
        copy(clips = clips.toMutableList().apply { addAll(atIndex.coerceIn(0, size), newClips) })

    fun removeClip(id: String): Project = copy(clips = clips.filterNot { it.id == id })

    fun replaceClip(clip: Clip): Project = copy(clips = clips.map { if (it.id == clip.id) clip else it })

    fun moveClip(id: String, delta: Int): Project {
        val i = clips.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in clips.indices) return this
        val list = clips.toMutableList()
        list.add(j, list.removeAt(i))
        return copy(clips = list)
    }

    /**
     * Divide la clip che si trova al tempo t della timeline.
     * Restituisce null se t cade troppo vicino a un bordo o se la clip ha una rampa
     * (la rampa è definita sull'intera clip e dividerla cambierebbe il risultato).
     */
    // Le scelte "a tutte" restano illuminate finché tutte le clip (o i testi) sono uguali a quella selezionata.
    fun fxOnAll(c: Clip): Boolean = clips.size > 1 && clips.all { it.fx == c.fx }
    fun fadesOnAll(c: Clip): Boolean = clips.size > 1 && clips.all {
        it.fadeInUs == minOf(c.fadeInUs, it.outputUs / 2) && it.fadeOutUs == minOf(c.fadeOutUs, it.outputUs / 2) &&
            it.fadeColor == c.fadeColor && it.fadeAudio == c.fadeAudio
    }
    fun frameOnAll(c: Clip): Boolean = clips.size > 1 && clips.all { it.frame == c.frame && it.fill == c.fill && it.blurBg == c.blurBg }
    fun transitionOnAllCuts(spec: TransitionSpec?): Boolean = clips.size > 2 && clips.dropLast(1).all { it.transitionOut == spec }
    fun textStyleOnAll(t: TextItem): Boolean = texts.size > 1 && texts.all {
        it.font == t.font && it.look == t.look && it.color == t.color && it.size == t.size && it.animIn == t.animIn && it.animOut == t.animOut
    }

    /** Crediti dei brani ed effetti presi in rete (da mettere nella descrizione del video). */
    fun credits(): List<String> = music.mapNotNull { it.credit }.distinct()

    /** Il video si apre dal nero/bianco e si chiude (prima clip con entrata, ultima con uscita). */
    fun videoEndsFaded(): Boolean = clips.isNotEmpty() && clips.first().fadeInUs > 0 && clips.last().fadeOutUs > 0

    fun splitAt(timeUs: Long, minPieceUs: Long = 100_000): Project? {
        val (index, offsetOut) = locate(timeUs) ?: return null
        val clip = clips[index]
        val offsetSrc = clip.speedCurve().sourceTimeAt(offsetOut)
        val cut = clip.trimStartUs + offsetSrc
        if (cut - clip.trimStartUs < minPieceUs || clip.trimEndUs - cut < minPieceUs) return null
        // I keyframe seguono il video: ogni metà tiene i suoi, più il valore nel punto di taglio.
        val local = cut - clip.trimStartUs
        val atCut = Keyframes.valueAt(clip.keyframes, local)
        val k1 = if (clip.keyframes.isEmpty()) emptyList() else
            clip.keyframes.filter { it.timeUs < local } + Keyframe(local, atCut)
        val k2 = if (clip.keyframes.isEmpty()) emptyList() else
            listOf(Keyframe(0, atCut)) + clip.keyframes.filter { it.timeUs > local }.map { it.copy(timeUs = it.timeUs - local) }
        var first = clip.copy(trimEndUs = cut, keyframes = k1, transitionOut = null)
        var second = clip.copy(id = UUID.randomUUID().toString(), trimStartUs = cut, keyframes = k2)
        if (clip.speed == ClipSpeed.RAMP) {
            // La curva si divide in due: ogni metà conserva la sua parte di curva.
            val f = offsetSrc.toFloat() / clip.trimmedUs
            val (a, b) = splitPoints(clip.curvePoints, f)
            first = first.copy(speedPoints = a)
            second = second.copy(speedPoints = b)
        }
        val list = clips.toMutableList()
        list[index] = first
        list.add(index + 1, second)
        return copy(clips = list)
    }

    fun toJson(): String = JSON.encodeToString(serializer(), this)

    companion object {
        val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
        fun fromJson(text: String): Project = JSON.decodeFromString(serializer(), text)
    }
}

object SpeedPoints {
    /** Mette o aggiorna un punto in posizione t (entro [snap] riusa quello vicino; i bordi restano bordi). */
    fun set(points: List<SpeedPoint>, t: Float, speed: Float, snap: Float = 0.02f): List<SpeedPoint> {
        val s = speed.coerceIn(SpeedLimits.MIN, SpeedLimits.MAX)
        val near = points.indices.minByOrNull { kotlin.math.abs(points[it].t - t) }
            ?.takeIf { kotlin.math.abs(points[it].t - t) <= snap }
        return if (near != null) points.mapIndexed { i, p -> if (i == near) p.copy(speed = s) else p }
        else (points + SpeedPoint(t.coerceIn(0f, 1f), s)).sortedBy { it.t }
    }

    fun nearest(points: List<SpeedPoint>, t: Float, snap: Float = 0.02f): SpeedPoint? =
        points.minByOrNull { kotlin.math.abs(it.t - t) }?.takeIf { kotlin.math.abs(it.t - t) <= snap }
}

/** Divide i punti di una curva nella posizione f (0..1), riscalando le due metà. */
fun splitPoints(points: List<SpeedPoint>, f: Float): Pair<List<SpeedPoint>, List<SpeedPoint>> {
    // Oltre ai punti esistenti, campiona la curva originale dentro ogni metà:
    // così ciascuna metà mantiene la stessa forma (e quindi la stessa durata).
    fun half(from: Float, to: Float): List<SpeedPoint> {
        val inner = points.filter { it.t > from && it.t < to }.map { it.t }
        val samples = (1..3).map { from + (to - from) * it / 4f }.filter { s -> inner.none { abs(it - s) < (to - from) * 0.08f } }
        val ts = (listOf(from, to) + inner + samples).sorted()
        return ts.map { t -> SpeedPoint((t - from) / (to - from), SpeedInterp.valueAt(points, t)) }
    }
    return half(0f, f) to half(f, 1f)
}

/**
 * Interpolazione morbida della curva (cubica monotona sul logaritmo della velocità):
 * passa esattamente dai punti e non "rimbalza" mai oltre i loro valori.
 */
object SpeedInterp {
    fun valueAt(points: List<SpeedPoint>, t: Float): Float = sampler(points)(t)

    /** Prepara la curva una volta sola; la funzione restituita si valuta velocemente molte volte. */
    fun sampler(points: List<SpeedPoint>): (Float) -> Float {
        val p = points.sortedBy { it.t }
        if (p.isEmpty()) return { 1f }
        if (p.size == 1) return { p[0].speed }
        val n = p.size
        val xs = DoubleArray(n) { p[it].t.toDouble() }
        val ys = DoubleArray(n) { kotlin.math.ln(p[it].speed.toDouble()) }
        val d = DoubleArray(n - 1) { (ys[it + 1] - ys[it]) / (xs[it + 1] - xs[it]).coerceAtLeast(1e-6) }
        val m = DoubleArray(n)
        m[0] = d[0]; m[n - 1] = d[n - 2]
        for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0) 0.0 else (d[i - 1] + d[i]) / 2
        // Fritsch–Carlson: evita sovraelongazioni.
        for (i in 0 until n - 1) {
            if (d[i] == 0.0) { m[i] = 0.0; m[i + 1] = 0.0; continue }
            val a = m[i] / d[i]; val b = m[i + 1] / d[i]
            val s = a * a + b * b
            if (s > 9) { val k = 3 / kotlin.math.sqrt(s); m[i] = k * a * d[i]; m[i + 1] = k * b * d[i] }
        }
        return { tf ->
            val t = tf.toDouble()
            when {
                t <= xs[0] -> p[0].speed
                t >= xs[n - 1] -> p[n - 1].speed
                else -> {
                    var k = 0
                    while (k < n - 2 && t > xs[k + 1]) k++
                    val h = (xs[k + 1] - xs[k]).coerceAtLeast(1e-6)
                    val u = (t - xs[k]) / h
                    val h00 = 2 * u * u * u - 3 * u * u + 1
                    val h10 = u * u * u - 2 * u * u + u
                    val h01 = -2 * u * u * u + 3 * u * u
                    val h11 = u * u * u - u * u
                    val y = h00 * ys[k] + h10 * h * m[k] + h01 * ys[k + 1] + h11 * h * m[k + 1]
                    kotlin.math.exp(y).toFloat().coerceIn(SpeedLimits.MIN, SpeedLimits.MAX)
                }
            }
        }
    }
}

/**
 * Velocità a tratti costanti sul tempo della clip (sorgente).
 * È la stessa curva che si passa a Media3 per il video e al motore audio nativo,
 * così immagini e suono restano sincronizzati.
 */
class SpeedCurve(val segEndUs: LongArray, val speeds: FloatArray, val durationUs: Long) {

    fun speedAt(tUs: Long): Float {
        for (i in segEndUs.indices) if (tUs < segEndUs[i]) return speeds[i]
        return speeds.lastOrNull() ?: 1f
    }

    /** Fine del tratto che contiene t, o null se è l'ultimo. */
    fun nextChangeUs(tUs: Long): Long? {
        for (i in segEndUs.indices) if (tUs < segEndUs[i]) return if (i == segEndUs.lastIndex) null else segEndUs[i]
        return null
    }

    fun outputDurationUs(): Long = outputTimeAt(durationUs)

    /** Tempo nel video finale corrispondente al tempo sorgente t. */
    fun outputTimeAt(tUs: Long): Long {
        var acc = 0.0
        var start = 0L
        for (i in segEndUs.indices) {
            val end = minOf(segEndUs[i], durationUs)
            if (tUs <= end) return (acc + (tUs - start) / speeds[i]).toLong()
            acc += (end - start) / speeds[i]
            start = end
        }
        return acc.toLong()
    }

    /** Tempo sorgente corrispondente al tempo t del video finale (inversa). */
    fun sourceTimeAt(outUs: Long): Long {
        var acc = 0.0
        var start = 0L
        for (i in segEndUs.indices) {
            val end = minOf(segEndUs[i], durationUs)
            val segOut = (end - start) / speeds[i]
            if (outUs <= acc + segOut) return (start + (outUs - acc) * speeds[i]).toLong().coerceAtMost(durationUs)
            acc += segOut
            start = end
        }
        return durationUs
    }

    companion object {
        private const val RAMP_STEP_US = 40_000L

        fun constant(speed: Float, durationUs: Long) =
            SpeedCurve(longArrayOf(durationUs), floatArrayOf(speed), durationUs)

        /** Rampa veloce → lento → veloce (2× → 0,35× → 2×), a gradini di 40 ms. */
        fun ramp(durationUs: Long, fast: Float = 2f, slow: Float = 0.35f): SpeedCurve {
            val n = ((durationUs + RAMP_STEP_US - 1) / RAMP_STEP_US).toInt().coerceAtLeast(1)
            val ends = LongArray(n)
            val sp = FloatArray(n)
            for (i in 0 until n) {
                val start = i * RAMP_STEP_US
                ends[i] = minOf(start + RAMP_STEP_US, durationUs)
                val t = start.toDouble() / durationUs
                val c = 0.5 + 0.5 * cos(2 * PI * t)
                sp[i] = (slow + (fast - slow) * c).toFloat()
            }
            return SpeedCurve(ends, sp, durationUs)
        }

        /** Curva disegnata, campionata a gradini di 40 ms (al centro di ogni gradino). */
        fun fromPoints(points: List<SpeedPoint>, durationUs: Long): SpeedCurve {
            val sampler = SpeedInterp.sampler(points)
            val n = ((durationUs + RAMP_STEP_US - 1) / RAMP_STEP_US).toInt().coerceAtLeast(1)
            val ends = LongArray(n)
            val sp = FloatArray(n)
            for (i in 0 until n) {
                val start = i * RAMP_STEP_US
                ends[i] = minOf(start + RAMP_STEP_US, durationUs)
                val mid = (start + ends[i]) / 2.0 / durationUs
                sp[i] = sampler(mid.toFloat())
            }
            return SpeedCurve(ends, sp, durationUs)
        }

        fun forClip(speed: ClipSpeed, durationUs: Long, points: List<SpeedPoint> = SpeedPreset.VALLEY.points): SpeedCurve = when (speed) {
            ClipSpeed.NORMAL -> constant(1f, durationUs)
            ClipSpeed.SLOW -> constant(0.5f, durationUs)
            ClipSpeed.FAST -> constant(2f, durationUs)
            ClipSpeed.RAMP -> fromPoints(points, durationUs)
        }
    }
}

/** Come una foto o un video sta nel quadro. */
enum class FrameMode(val label: String) { FILL("Riempi"), FIT("Adatta"), BLUR("Sfondo sfocato") }

val Clip.frameMode: FrameMode get() = when { blurBg -> FrameMode.BLUR; fill -> FrameMode.FILL; else -> FrameMode.FIT }
fun Clip.withMode(m: FrameMode): Clip = copy(fill = m == FrameMode.FILL, blurBg = m == FrameMode.BLUR)

/** Orientamento di una clip (o del video): verticale, orizzontale o quasi quadrato. */
enum class Orientation { PORTRAIT, LANDSCAPE, SQUARE }

fun orientationOf(w: Int, h: Int): Orientation = when {
    w <= 0 || h <= 0 -> Orientation.LANDSCAPE
    h > w * 1.1f -> Orientation.PORTRAIT
    w > h * 1.1f -> Orientation.LANDSCAPE
    else -> Orientation.SQUARE
}
val Clip.orientation: Orientation get() = orientationOf(width, height)

/** Formato del video finito. AUTO = come la prima clip (com'era prima). */
enum class VideoFormat(val label: String, val short: String, val aspect: Float?) {
    AUTO("Automatico", "Auto", null), VERTICAL("Verticale 9:16", "9:16", 9f / 16f),
    HORIZONTAL("Orizzontale 16:9", "16:9", 16f / 9f), SQUARE("Quadrato 1:1", "1:1", 1f);

    companion object {
        /** Il formato adatto a un insieme di clip: l'orientamento più frequente. */
        fun majority(clips: List<Clip>): VideoFormat {
            val p = clips.count { it.orientation == Orientation.PORTRAIT }
            val l = clips.count { it.orientation == Orientation.LANDSCAPE }
            return if (p > l) VERTICAL else HORIZONTAL
        }
    }
}
