package it.fabriziodavi.vivovideo.model

/**
 * Titoli di coda: un rullo (un testo su più righe che scorre) alla fine del video,
 * sopra le ultime scene oppure su uno sfondo nero aggiunto dopo l'ultima clip.
 */
object Credits {
    /** Id della clip nera aggiunta per i titoli "su nero" (per ritrovarla e sostituirla). */
    const val BLACK_ID = "titoli-di-coda"

    /** Una riga dei titoli: ruolo (es. "Riprese") e nomi. */
    data class Row(val role: String, val names: String)

    /** Il testo del rullo: titolo, righe "RUOLO / nomi", poi i crediti dei brani. */
    fun text(title: String, rows: List<Row>, music: List<String>): String = buildString {
        if (title.isNotBlank()) { append(title.trim()); append("\n\n") }
        rows.filter { it.role.isNotBlank() || it.names.isNotBlank() }.forEach { r ->
            if (r.role.isNotBlank()) append(r.role.trim().uppercase()).append('\n')
            if (r.names.isNotBlank()) append(r.names.trim()).append('\n')
            append('\n')
        }
        if (music.isNotEmpty()) {
            append("MUSICA\n")
            music.forEach { append(it.trim()).append('\n') }
        }
    }.trimEnd()

    /** Durata consigliata: abbastanza per leggere (circa 0,9 s a riga), fra 6 e 30 s. */
    fun suggestedUs(text: String): Long = (text.lines().size * 900_000L).coerceIn(6_000_000L, 30_000_000L)

    /** Il testo come livello della timeline: bianco, pulito, centrato, che scorre. */
    fun item(text: String, startUs: Long, durationUs: Long): TextItem = TextItem(
        text = text, startUs = startUs, durationUs = durationUs.coerceAtLeast(TextItem.MIN_US),
        font = TextFont.MODERNO, color = 0xFFFFFFFF.toInt(), size = 0.038f, look = TextLook.OMBRA, spacing = 0.04f,
        animIn = TextAnim.NONE, animOut = TextAnim.NONE, roll = true,
    )
}

/** I titoli di coda già nel progetto (null se non ci sono). */
val Project.creditsRoll: TextItem? get() = texts.firstOrNull { it.roll }

/**
 * Mette (o rifà) i titoli di coda: [onBlack] = dopo il video, su una clip nera [black] (foto nera già pronta, senza durata);
 * altrimenti sopra le ultime scene. Toglie quelli di prima.
 */
fun Project.withCredits(text: String, durationUs: Long, onBlack: Boolean, black: Clip?): Project {
    var p = withoutCredits()
    if (text.isBlank() || p.clips.isEmpty()) return p
    val start: Long
    val dur: Long
    if (onBlack && black != null) {
        start = p.durationUs
        dur = durationUs
        val last = p.clips.last()
        // L'ultima scena sfuma nel nero dei titoli.
        val clips = p.clips.dropLast(1) + last.copy(fadeOutUs = 0, transitionOut = TransitionSpec(TransitionType.FADE_BLACK, 800_000)) +
            black.copy(id = Credits.BLACK_ID, trimStartUs = 0, trimEndUs = durationUs, fx = emptyList(), keyframes = emptyList(), transitionOut = null, fadeInUs = 0, fadeOutUs = 0)
        p = p.copy(clips = clips)
    } else {
        dur = minOf(durationUs, p.durationUs)
        start = p.durationUs - dur
    }
    return p.copy(texts = p.texts + Credits.item(text, start, dur))
}

/** Toglie i titoli di coda e l'eventuale sfondo nero aggiunto per loro. */
fun Project.withoutCredits(): Project =
    copy(texts = texts.filterNot { it.roll }, clips = clips.filterNot { it.id == Credits.BLACK_ID })
