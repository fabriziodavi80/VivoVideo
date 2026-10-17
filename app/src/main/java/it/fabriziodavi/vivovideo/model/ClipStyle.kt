package it.fabriziodavi.vivovideo.model

/** Gruppi di attributi di una clip che si possono copiare e incollare su altre clip. */
enum class ClipAttr(val label: String) {
    EFFECTS("Effetti"),
    COLOR("Colore"),
    TRANSITION("Transizione"),
    FADES("Apri e chiudi"),
    FRAMING("Inquadratura"),
    MOTION("Keyframe"),
    SPEED("Velocità"),
    AUDIO("Volume");
}

object ClipStyle {
    /** Gruppi in cui [c] ha qualcosa di diverso dal normale (quelli che vale la pena incollare). */
    fun present(c: Clip): Set<ClipAttr> = buildSet {
        if (c.fx.isNotEmpty()) add(ClipAttr.EFFECTS)
        if (!c.grade.isIdentity) add(ClipAttr.COLOR)
        if (c.transitionOut != null) add(ClipAttr.TRANSITION)
        if (c.fadeInUs > 0 || c.fadeOutUs > 0) add(ClipAttr.FADES)
        if (c.fill || c.blurBg || c.frame != Transform()) add(ClipAttr.FRAMING)
        if (c.keyframes.isNotEmpty()) add(ClipAttr.MOTION)
        if (!c.isPhoto && c.speed != ClipSpeed.NORMAL) add(ClipAttr.SPEED)
        if (!c.isPhoto && (c.volume != 1f || c.muted)) add(ClipAttr.AUDIO)
    }

    /** [dst] con i gruppi [attrs] presi da [src]; velocità e volume non toccano le foto. */
    fun paste(src: Clip, dst: Clip, attrs: Set<ClipAttr>): Clip {
        var d = dst
        if (ClipAttr.EFFECTS in attrs) d = d.copy(fx = src.fx)
        if (ClipAttr.COLOR in attrs) d = d.copy(grade = src.grade)
        if (ClipAttr.TRANSITION in attrs) d = d.copy(transitionOut = src.transitionOut)
        // Prima la velocità: cambia la durata, e le dissolvenze dipendono da quella.
        if (ClipAttr.SPEED in attrs && !d.isPhoto && !src.isPhoto) d = d.copy(speed = src.speed, speedPoints = src.speedPoints)
        if (ClipAttr.FADES in attrs) {
            // Le dissolvenze non possono superare metà della clip di arrivo.
            val half = d.outputUs / 2
            d = d.copy(
                fadeInUs = src.fadeInUs.coerceAtMost(half), fadeOutUs = src.fadeOutUs.coerceAtMost(half),
                fadeColor = src.fadeColor, fadeAudio = src.fadeAudio,
            )
        }
        if (ClipAttr.FRAMING in attrs) d = d.copy(fill = src.fill, blurBg = src.blurBg, frame = src.frame)
        if (ClipAttr.MOTION in attrs) {
            // Stesso movimento, in proporzione alla durata della clip di arrivo.
            val k = if (src.trimmedUs > 0) d.trimmedUs.toDouble() / src.trimmedUs else 1.0
            d = d.copy(keyframes = src.keyframes.map { it.copy(timeUs = (it.timeUs * k).toLong().coerceIn(0, d.trimmedUs)) })
        }
        if (ClipAttr.AUDIO in attrs && !d.isPhoto && !src.isPhoto) d = d.copy(volume = src.volume, muted = src.muted)
        return d
    }
}
