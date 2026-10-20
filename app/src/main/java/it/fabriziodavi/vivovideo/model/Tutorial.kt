package it.fabriziodavi.vivovideo.model

/**
 * Strumenti per i tutorial: zoom morbido su un punto dello schermo (come i registratori dello schermo professionali),
 * evidenziazioni pronte (freccia, cerchio, riquadro, numero) e passi numerati.
 */
object Tutorial {
    /** Dove ingrandire: nove zone del quadro (x e y fra -0,5 e 0,5; y in giù). */
    enum class Spot(val label: String, val x: Float, val y: Float) {
        TOP_LEFT("↖", -0.3f, -0.3f), TOP("↑", 0f, -0.3f), TOP_RIGHT("↗", 0.3f, -0.3f),
        LEFT("←", -0.3f, 0f), CENTER("•", 0f, 0f), RIGHT("→", 0.3f, 0f),
        BOTTOM_LEFT("↙", -0.3f, 0.3f), BOTTOM("↓", 0f, 0.3f), BOTTOM_RIGHT("↘", 0.3f, 0.3f),
    }

    /** Tempo per arrivare allo zoom e per tornare indietro. */
    const val RAMP_US = 450_000L

    /** Trasformazione che porta il punto ([px], [py]) al centro ingrandito [scale] volte, senza mai mostrare i bordi. */
    fun zoomOn(px: Float, py: Float, scale: Float): Transform {
        val s = scale.coerceIn(1f, 4f)
        val lim = (s - 1f) / 2f
        fun clean(v: Float) = if (v == 0f) 0f else v // niente "-0" (darebbe trasformazioni diverse per nulla)
        return Transform(x = clean((-s * px).coerceIn(-lim, lim)), y = clean((-s * py).coerceIn(-lim, lim)), scale = s)
    }

    /**
     * Keyframe della clip con uno zoom sul punto: parte da [atUs] (tempo nella clip), arriva in [RAMP_US], resta [holdUs] e torna.
     * I keyframe che c'erano in quel tratto si tolgono; prima e dopo resta tutto com'era.
     */
    fun zoomKeyframes(existing: List<Keyframe>, atUs: Long, clipUs: Long, px: Float, py: Float, scale: Float, holdUs: Long): List<Keyframe> {
        val t0 = atUs.coerceIn(0, (clipUs - 2 * RAMP_US).coerceAtLeast(0))
        val t1 = (t0 + RAMP_US).coerceAtMost(clipUs)
        val t3 = (t1 + holdUs + RAMP_US).coerceAtMost(clipUs)
        val t2 = (t3 - RAMP_US).coerceAtLeast(t1)
        val before = Keyframes.valueAt(existing, t0)
        val after = Keyframes.valueAt(existing, t3)
        val zoom = zoomOn(px, py, scale)
        val kept = existing.filter { it.timeUs < t0 || it.timeUs > t3 }
        return (kept + Keyframe(t0, before) + Keyframe(t1, zoom) + Keyframe(t2, zoom) + Keyframe(t3, after)).sortedBy { it.timeUs }
    }

    /** Evidenziazioni pronte (immagini con trasparenza disegnate dall'app). */
    enum class Mark(val label: String) { ARROW("Freccia"), CIRCLE("Cerchio"), BOX("Riquadro"), NUMBER("Numero") }

    /** Prossimo numero libero per i passi (testi "1. …", "2. …") e per i numeri tondi. */
    fun nextStep(p: Project): Int {
        val fromTexts = p.texts.mapNotNull { Regex("^(\\d+)\\.").find(it.text)?.groupValues?.get(1)?.toIntOrNull() }
        val fromMarks = p.layers.mapNotNull { Regex("^Numero (\\d+)$").find(it.name)?.groupValues?.get(1)?.toIntOrNull() }
        return ((fromTexts + fromMarks).maxOrNull() ?: 0) + 1
    }

    /** Passo numerato: etichetta chiara in alto a sinistra. */
    fun stepText(n: Int, startUs: Long, durationUs: Long): TextItem =
        TextPreset.PASSO.applyTo(TextItem(text = "$n. Scrivi il passo", startUs = startUs, durationUs = durationUs))
}
