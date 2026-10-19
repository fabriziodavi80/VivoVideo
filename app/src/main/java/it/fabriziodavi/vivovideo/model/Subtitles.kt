package it.fabriziodavi.vivovideo.model

/** Una parola riconosciuta, con inizio e fine sulla timeline (µs). */
data class SpokenWord(val startUs: Long, val endUs: Long, val text: String)

/** Stili dei sottotitoli automatici. */
enum class SubStyle(val label: String, val maxWords: Int = 7) {
    // Tendenze 2026 per prime: due o tre parole alla volta, bianco bold con contorno; oppure minuscolo e quieto.
    SOCIAL("Social", maxWords = 3), MINIMAL("Minimal"),
    CLASSIC("Classico"), KARAOKE("Karaoke"), REVEAL("Parola per parola"), BOX("Riquadro");
}

/** Dove stanno i sottotitoli. */
enum class SubPosition(val label: String, val y: Float) { BOTTOM("In basso", 0.34f), MIDDLE("Al centro", 0.12f), TOP("In alto", -0.36f) }

object Subtitles {
    /** Colore della parola detta (karaoke): giallo burro, più attuale del giallo pieno. */
    const val HIGHLIGHT = 0xFFF5D76E.toInt()

    /** Toglie i segni che non sono parole ("[Musica]", "(applausi)", note…). */
    fun clean(words: List<SpokenWord>): List<SpokenWord> {
        var skip = false
        return words.mapNotNull { w ->
            val t = w.text.trim()
            if (t.startsWith("[") || t.startsWith("(")) skip = true
            val keep = !skip && t.isNotEmpty() && t.any { it.isLetterOrDigit() }
            if (t.endsWith("]") || t.endsWith(")")) skip = false
            if (keep) w.copy(text = t) else null
        }
    }

    /**
     * Divide le parole in righe da leggere: al massimo [maxChars] caratteri o [maxWords] parole,
     * e si va a capo a fine frase o dove c'è una pausa più lunga di [pauseUs].
     */
    fun group(words: List<SpokenWord>, maxChars: Int = 32, maxWords: Int = 7, pauseUs: Long = 600_000): List<List<SpokenWord>> {
        val out = mutableListOf<MutableList<SpokenWord>>()
        var cur = mutableListOf<SpokenWord>()
        fun len(l: List<SpokenWord>) = l.sumOf { it.text.length } + (l.size - 1).coerceAtLeast(0)
        for (w in words) {
            val prev = cur.lastOrNull()
            val breakHere = prev != null && (
                len(cur) + 1 + w.text.length > maxChars || cur.size >= maxWords ||
                    w.startUs - prev.endUs > pauseUs || prev.text.last() in ".?!"
                )
            if (breakHere) { out += cur; cur = mutableListOf() }
            cur += w
        }
        if (cur.isNotEmpty()) out += cur
        return out
    }

    /** Sottotitoli pronti (testi della timeline, marcati come sottotitoli) dalle parole riconosciute. */
    fun build(words: List<SpokenWord>, style: SubStyle, pos: SubPosition, projectUs: Long): List<TextItem> {
        val lines = group(clean(words), maxWords = style.maxWords)
        return lines.mapIndexedNotNull { i, line ->
            val start = line.first().startUs.coerceIn(0, (projectUs - TextItem.MIN_US).coerceAtLeast(0))
            // Resta a schermo fino all'inizio della riga dopo (al massimo 1,2 s dopo l'ultima parola).
            val nextStart = lines.getOrNull(i + 1)?.first()?.startUs ?: projectUs
            val end = minOf(maxOf(line.last().endUs + 300_000, start + 700_000), line.last().endUs + 1_200_000, nextStart, projectUs)
            if (end - start < TextItem.MIN_US) return@mapIndexedNotNull null
            val text = line.joinToString(" ") { it.text }.let { if (style == SubStyle.MINIMAL) it.lowercase() else it }
            val base = TextItem(
                text = text, startUs = start, durationUs = end - start, x = 0f, y = pos.y,
                font = TextFont.MODERNO, color = 0xFFFFFFFF.toInt(), size = 0.048f, look = TextLook.OMBRA,
                animIn = TextAnim.NONE, animOut = TextAnim.NONE, subtitle = true,
                wordsUs = line.map { (it.startUs - start).coerceAtLeast(0) },
            )
            when (style) {
                SubStyle.CLASSIC -> base.copy(animIn = TextAnim.FADE, animOut = TextAnim.FADE)
                // Karaoke: niente più tutto maiuscolo (oggi sa di vecchio), la parola detta si accende di giallo burro.
                SubStyle.KARAOKE -> base.copy(wordMode = 1, highlight = HIGHLIGHT, size = 0.052f, font = TextFont.POSTER)
                SubStyle.REVEAL -> base.copy(wordMode = 2, size = 0.055f, font = TextFont.POSTER)
                SubStyle.BOX -> base.copy(look = TextLook.RIQUADRO, animIn = TextAnim.FADE, animOut = TextAnim.FADE)
                // Social: poche parole alla volta, bold bianco con contorno nero, le parole compaiono mentre sono dette.
                SubStyle.SOCIAL -> base.copy(wordMode = 2, font = TextFont.POSTER, look = TextLook.CONTORNO, size = 0.06f)
                // Minimal: minuscolo, piccolo, su una riga, senza animazioni.
                SubStyle.MINIMAL -> base.copy(size = 0.04f)
            }
        }
    }

    /** Parola in corso a [localUs] dall'inizio del testo (indice), o -1 prima della prima. */
    fun wordAt(wordsUs: List<Long>, localUs: Long): Int {
        var k = -1
        for (i in wordsUs.indices) if (wordsUs[i] <= localUs) k = i else break
        return k
    }
}
