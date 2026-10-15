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
    FADE_BLACK("Nero"),
    FLASH("Flash"),
    ZOOM("Zoom"),
    BLUR("Sfocatura"),
    WHIP("Scorrimento"),
    SPIN("Rotazione"),
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
    /** Inquadratura fissa (zoom, posizione, rotazione), sotto ad eventuali keyframe. */
    val frame: Transform = Transform(),
    /** Effetti video della clip. */
    val fx: List<ClipFx> = emptyList(),
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

/** Effetti video per clip; [toggle] = acceso/spento senza intensità. */
enum class FxType(val label: String, val toggle: Boolean = false, val defaultAmount: Float = 0.6f) {
    BLUR("Sfocatura", defaultAmount = 0.4f), VIGNETTE("Vignettatura"), FILM("Pellicola"), BW("Bianco e nero", defaultAmount = 1f),
    GLOW("Bagliore"), GLITCH("Glitch", defaultAmount = 0.5f), VHS("VHS"), SHAKE("Scossa", defaultAmount = 0.5f),
    PULSE("Pulsa a ritmo"), PIXEL("Pixel", defaultAmount = 0.4f), MIRROR("Specchio", toggle = true, defaultAmount = 1f),
}

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
            ZOOM_IN -> Transform() to Transform(scale = 1.2f)
            ZOOM_OUT -> Transform(scale = 1.2f) to Transform()
            PAN_RIGHT -> Transform(x = -0.08f, scale = 1.22f) to Transform(x = 0.08f, scale = 1.22f)
            PAN_LEFT -> Transform(x = 0.08f, scale = 1.22f) to Transform(x = -0.08f, scale = 1.22f)
            PAN_DOWN -> Transform(y = -0.07f, scale = 1.22f) to Transform(y = 0.07f, scale = 1.22f)
            PAN_UP -> Transform(y = 0.07f, scale = 1.22f) to Transform(y = -0.07f, scale = 1.22f)
            DIAGONAL -> Transform(x = -0.06f, y = -0.05f, scale = 1.12f) to Transform(x = 0.05f, y = 0.05f, scale = 1.3f)
            TILT -> Transform(scale = 1.18f, rotation = -2.5f) to Transform(scale = 1.25f, rotation = 2.5f)
        }
        return listOf(Keyframe(0, a, Easing.LINEAR), Keyframe(durationUs, b, Easing.LINEAR))
    }

    companion object {
        /** Movimenti per il "casuale": tutti tranne fermo, senza ripetere lo stesso due volte di fila. */
        val varied = entries.filter { it != NONE }
    }
}

enum class TextFont(val label: String) {
    MODERNO("Moderno"), TITOLO("Titolo"), IMPATTO("Impatto"), POSTER("Poster"),
    ELEGANTE("Elegante"), CORSIVO("Corsivo"), PENNARELLO("Pennarello"), MACCHINA("Macchina"),
}

enum class TextLook(val label: String) { SEMPLICE("Semplice"), CONTORNO("Contorno"), OMBRA("Ombra"), RIQUADRO("Riquadro"), NEON("Neon") }

enum class TextAnim(val label: String) {
    NONE("Nessuna"), FADE("Dissolvenza"), POP("Pop"), SLIDE("Scorri"), ZOOM("Zoom"), TYPE("Macchina da scrivere"),
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
    val look: TextLook = TextLook.OMBRA,
    val animIn: TextAnim = TextAnim.FADE,
    val animOut: TextAnim = TextAnim.FADE,
    /** Movimento (come in ProShow): posizione finale; null = fermo. x, y assoluti, scala e rotazione relative. */
    val endPose: Transform? = null,
) {
    val endUs: Long get() = startUs + durationUs

    companion object {
        const val MIN_US = 300_000L
    }
}

enum class LayerBorder(val label: String) { NONE("Nessuno"), WHITE("Bianco"), BLACK("Nero") }

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
) {
    val endUs: Long get() = startUs + durationUs

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
    IMPATTO("Impatto"), FIRMA("Firma"), DIARIO("Diario");

    fun applyTo(t: TextItem): TextItem = when (this) {
        CINEMA -> t.copy(font = TextFont.TITOLO, color = WHITE, look = TextLook.OMBRA, size = 0.11f, x = 0f, y = 0f, rotation = 0f,
            animIn = TextAnim.FADE, animOut = TextAnim.FADE, endPose = Transform(x = 0f, y = 0f, scale = 1.12f))
        DIDASCALIA -> t.copy(font = TextFont.MODERNO, color = WHITE, look = TextLook.RIQUADRO, size = 0.045f, x = 0f, y = 0.36f, rotation = 0f,
            animIn = TextAnim.SLIDE, animOut = TextAnim.FADE, endPose = null)
        POP -> t.copy(font = TextFont.POSTER, color = 0xFFFFE14D.toInt(), look = TextLook.CONTORNO, size = 0.08f, x = 0f, y = 0f, rotation = -4f,
            animIn = TextAnim.POP, animOut = TextAnim.POP, endPose = null)
        ELEGANTE -> t.copy(font = TextFont.ELEGANTE, color = WHITE, look = TextLook.OMBRA, size = 0.07f, x = 0f, y = 0.05f, rotation = 0f,
            animIn = TextAnim.FADE, animOut = TextAnim.FADE, endPose = Transform(x = 0f, y = -0.02f, scale = 1.04f))
        NEON -> t.copy(font = TextFont.IMPATTO, color = 0xFFFF7AB6.toInt(), look = TextLook.NEON, size = 0.09f, x = 0f, y = 0f, rotation = 0f,
            animIn = TextAnim.ZOOM, animOut = TextAnim.FADE, endPose = null)
        IMPATTO -> t.copy(font = TextFont.IMPATTO, color = WHITE, look = TextLook.CONTORNO, size = 0.14f, x = 0f, y = 0f, rotation = 0f,
            animIn = TextAnim.ZOOM, animOut = TextAnim.ZOOM, endPose = null)
        FIRMA -> t.copy(font = TextFont.CORSIVO, color = WHITE, look = TextLook.OMBRA, size = 0.06f, x = 0.2f, y = 0.38f, rotation = -3f,
            animIn = TextAnim.FADE, animOut = TextAnim.FADE, endPose = null)
        DIARIO -> t.copy(font = TextFont.MACCHINA, color = WHITE, look = TextLook.RIQUADRO, size = 0.04f, x = 0f, y = 0.3f, rotation = 0f,
            animIn = TextAnim.TYPE, animOut = TextAnim.FADE, endPose = null)
    }

    private companion object { const val WHITE = 0xFFFFFFFF.toInt() }
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
    /** Usa un battito ogni N (1 = tutti, 2, 4 = uno per battuta). */
    val beatEvery: Int = 1,
    /** Spostamento di tutti i battiti automatici (correzione fine), in µs. */
    val beatShiftUs: Long = 0,
    /** Battiti segnati a mano (tempo nel file del brano). */
    val markersUs: List<Long> = emptyList(),
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
) {
    fun replaceLayer(l: Layer): Project = copy(layers = layers.map { if (it.id == l.id) l else it })

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
                c = c.copy(trimStartUs = 0, trimEndUs = dur, fill = o.fill, keyframes = style.keyframes(dur))
            }
            if (i < clips.lastIndex) {
                val type = when {
                    o.noTransitions -> null
                    o.transition != null -> o.transition
                    else -> TransitionType.entries.filter { it != prevT }.random(rnd)
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
