package it.fabriziodavi.vivovideo.model

/** Una parola riconosciuta, con inizio e fine sulla timeline (µs). */
data class SpokenWord(val startUs: Long, val endUs: Long, val text: String)

/** Stili dei sottotitoli automatici. */
enum class SubStyle(val label: String, val maxWords: Int = 7, val kinetic: KineticStyle? = null) {
    // Cinetici 2027 per primi: una parola alla volta, grande, le parole importanti colorate e animate.
    POP("Cinetico Pop", maxWords = 4, kinetic = KineticStyle.POP),
    GLASS("Vetro liquido", maxWords = 4, kinetic = KineticStyle.GLASS),
    CHROME("Cromo", maxWords = 4, kinetic = KineticStyle.CHROME),
    GLITCHY("Glitch", maxWords = 4, kinetic = KineticStyle.GLITCH),
    WAVE("Onda", maxWords = 4, kinetic = KineticStyle.WAVE),
    // Tendenze 2026: due o tre parole alla volta, bianco bold con contorno; oppure minuscolo e quieto.
    SOCIAL("Social", maxWords = 3), MINIMAL("Minimal"),
    CLASSIC("Classico"), KARAOKE("Karaoke"), REVEAL("Parola per parola"), BOX("Riquadro");
}

/** Sottotitoli e testi cinetici (2027): come compaiono le parole. */
@kotlinx.serialization.Serializable
enum class KineticStyle(val label: String, val hint: String) {
    POP("Pop", "Una parola alla volta, enorme, che salta fuori con un rimbalzo"),
    GLASS("Vetro liquido", "Ogni parola su una lastra di vetro trasparente che sale"),
    CHROME("Cromo", "Lettere di metallo lucido con un riflesso che passa"),
    GLITCH("Glitch", "La parola arriva a scatti, con i colori sfasati"),
    WAVE("Onda", "La frase si scrive mentre parli e la parola detta salta su"),
}

/** Parole "importanti" (da colorare): numeri, parole lunghe che non sono articoli o congiunzioni, esclamazioni, maiuscole. */
object Keywords {
    private val stop = setOf(
        "perché", "quando", "quindi", "allora", "ancora", "sempre", "proprio", "abbiamo", "avevamo", "questo", "questa", "questi",
        "queste", "quello", "quella", "quelli", "quelle", "dentro", "insieme", "comunque", "invece", "infatti", "mentre", "essere",
        "stato", "stata", "siamo", "sarebbe", "potrebbe", "nostro", "nostra", "vostro", "vostra", "loro", "anche", "molto",
        "because", "really", "should", "would", "could", "people", "something",
    )
    fun isKey(word: String): Boolean {
        val w = word.trim()
        val core = w.trim { !it.isLetterOrDigit() }
        if (core.isEmpty()) return false
        if (core.any { it.isDigit() }) return true
        if (w.endsWith("!")) return true
        if (core.length >= 2 && core.all { !it.isLetter() || it.isUpperCase() } && core.any { it.isLetter() }) return true
        return core.length >= 7 && core.lowercase() !in stop
    }
}

/** Dove stanno i sottotitoli. */
enum class SubPosition(val label: String, val y: Float) { BOTTOM("In basso", 0.34f), MIDDLE("Al centro", 0.12f), TOP("In alto", -0.36f) }

object Subtitles {
    /** Colore della parola detta (karaoke): giallo burro, più attuale del giallo pieno. */
    const val HIGHLIGHT = 0xFFF5D76E.toInt()
    /** Colore delle parole importanti nei cinetici: lime acido, il colore dei titoli del 2026-27. */
    const val KINETIC_HIGHLIGHT = 0xFFC8FF3D.toInt()

    /** Tempi di parola per un testo scritto a mano (per il cinetico): una parola ogni ~0,35 s, entro i 3/4 della durata. */
    fun evenWords(text: String, durationUs: Long): List<Long> {
        val n = Regex("\\S+").findAll(text).count().coerceAtLeast(1)
        val step = minOf(350_000L, durationUs * 3 / 4 / n).coerceAtLeast(60_000L)
        return List(n) { it * step }
    }

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
                // Cinetici: una parola alla volta (Onda: la frase che si scrive), grandi, quasi al centro basso.
                SubStyle.POP, SubStyle.GLASS, SubStyle.CHROME, SubStyle.GLITCHY, SubStyle.WAVE -> base.copy(
                    kinetic = style.kinetic, wordMode = 2, font = TextFont.POSTER, size = if (style == SubStyle.WAVE) 0.06f else 0.075f,
                    look = TextLook.CONTORNO, highlight = KINETIC_HIGHLIGHT,
                    y = if (pos == SubPosition.BOTTOM) 0.24f else pos.y,
                )
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
