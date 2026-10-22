package it.fabriziodavi.vivovideo.model

import kotlinx.serialization.Serializable

/** Gruppi del mixer: ogni suono del progetto appartiene a uno di questi. */
@Serializable
enum class Bus(val label: String) {
    CLIPS("Audio delle clip"),
    VOICE("Voce fuori campo"),
    MUSIC("Musica"),
    DETACHED("Audio staccato"),
    OVERLAY("Video sovrapposti"),
}

/** Equalizzatore a 3 bande: bassi (scaffale 120 Hz), medi (campana 1,5 kHz), alti (scaffale 8 kHz), in dB. */
@Serializable
data class Eq(val lowDb: Float = 0f, val midDb: Float = 0f, val highDb: Float = 0f) {
    val flat: Boolean get() = lowDb == 0f && midDb == 0f && highDb == 0f
}

/** Equalizzazioni pronte. */
enum class EqPreset(val label: String, val eq: Eq) {
    FLAT("Piatto", Eq()),
    VOICE_CLEAR("Voce nitida", Eq(lowDb = -3f, midDb = 1f, highDb = 3f)),
    VOICE_WARM("Voce calda", Eq(lowDb = 2.5f, midDb = -1f, highDb = -1.5f)),
    UNDER_VOICE("Musica sotto la voce", Eq(lowDb = 0f, midDb = -4f, highDb = -1f)),
    NO_RUMBLE("Togli rimbombo", Eq(lowDb = -8f)),
    BRIGHT("Più brillante", Eq(highDb = 4f));

    companion object { fun of(eq: Eq) = entries.firstOrNull { it.eq == eq } }
}

/** Compressore: soglia (dBFS) e rapporto; il guadagno di compensazione è automatico. */
@Serializable
enum class Comp(val label: String, val thresholdDb: Float, val ratio: Float) {
    OFF("Spento", 0f, 1f),
    LIGHT("Leggero", -18f, 2f),
    MEDIUM("Medio", -22f, 3f),
    STRONG("Forte", -26f, 4.5f),
}

/** Regolazioni di un gruppo del mixer. */
@Serializable
data class BusMix(
    /** Volume in dB (da -30 a +12). */
    val gainDb: Float = 0f,
    val mute: Boolean = false,
    val solo: Boolean = false,
    /** Bilanciamento: -1 tutto a sinistra, 0 al centro, +1 tutto a destra. */
    val pan: Float = 0f,
    val eq: Eq = Eq(),
    val comp: Comp = Comp.OFF,
) {
    val neutral: Boolean get() = gainDb == 0f && !mute && !solo && pan == 0f && eq.flat && comp == Comp.OFF
}

/** Volume finale secondo la destinazione. */
@Serializable
enum class LoudTarget(val label: String, val lufs: Double?) {
    SOCIAL("Social e YouTube (–14 LUFS)", -14.0),
    PODCAST("Podcast (–16 LUFS)", -16.0),
    TV("TV e cinema (–23 LUFS)", -23.0),
    OFF("Come viene", null),
}

/** Velocità del ducking: quanto tiene giù la musica dopo la voce e quanto ci mette a risalire (blocchi da 10 ms). */
@Serializable
enum class DuckSpeed(val label: String, val holdBlocks: Int, val releaseBlocks: Int) {
    FAST("Veloce", 15, 25),
    NORMAL("Normale", 30, 50),
    SLOW("Lento", 60, 120),
}

/** Mixer del progetto: un canale per gruppo e l'uscita generale. */
@Serializable
data class Mix(
    val buses: Map<Bus, BusMix> = emptyMap(),
    val target: LoudTarget = LoudTarget.SOCIAL,
    /** Limitatore dei picchi sull'uscita (a -1 dBFS). */
    val limiter: Boolean = true,
    val duckSpeed: DuckSpeed = DuckSpeed.NORMAL,
) {
    fun bus(b: Bus): BusMix = buses[b] ?: BusMix()
    fun with(b: Bus, m: BusMix): Mix = copy(buses = if (m.neutral) buses - b else buses + (b to m))

    /** Nessun canale toccato. */
    val neutral: Boolean get() = buses.values.all { it.neutral }

    /** Il gruppo si sente? (muto, oppure qualcun altro in "solo"). */
    fun audible(b: Bus): Boolean {
        val anySolo = buses.values.any { it.solo }
        val m = bus(b)
        return !m.mute && (!anySolo || m.solo)
    }

    /** Guadagno lineare del canale (0 se non si sente), con il bilanciamento: sinistra, destra. */
    fun gains(b: Bus): Pair<Float, Float> {
        if (!audible(b)) return 0f to 0f
        val m = bus(b)
        val g = Math.pow(10.0, m.gainDb / 20.0).toFloat()
        val p = m.pan.coerceIn(-1f, 1f)
        return g * minOf(1f, 1f - p) to g * minOf(1f, 1f + p)
    }
}

/** Gruppo di un brano della timeline. */
fun MusicTrack.bus(): Bus = when {
    !dialog -> Bus.MUSIC
    uri.contains("/voice/") -> Bus.VOICE
    else -> Bus.DETACHED
}

/** Gruppi presenti nel progetto (le clip ci sono sempre). */
fun Project.buses(): List<Bus> {
    val set = mutableSetOf(Bus.CLIPS)
    music.forEach { set += it.bus() }
    if (layers.any { it.isVideo && !it.muted }) set += Bus.OVERLAY
    return Bus.entries.filter { it in set }
}
