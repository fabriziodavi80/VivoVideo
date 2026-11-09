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

    /**
     * Inizio delle parole spostato sulla voce vera (08/11/2026): il riconoscimento a volte fa iniziare la parola nel
     * silenzio prima (sottotitoli cinetici in anticipo). Con l'audio [pcm] mono a [rate] Hz: se l'inizio cade dove non
     * si parla, si sposta in avanti al primo punto in cui la voce sale (al massimo fino a 3/4 della parola).
     * 09/11/2026 (collaudo: "a volte in anticipo, a volte in ritardo"): anche il contrario. Se la parola risulta iniziare
     * a voce già partita da poco (al massimo [LATE_US]) dopo una pausa vera (almeno [PAUSE_US], più delle consonanti
     * doppie), l'inizio torna al punto in cui la voce è ripartita, purché dopo l'inizio della parola prima.
     */
    fun snapToVoice(words: List<SpokenWord>, pcm: FloatArray, rate: Int = 16_000): List<SpokenWord> {
        if (words.isEmpty() || pcm.isEmpty()) return ordered(words)
        val hop = rate / 100 // 10 ms
        val n = pcm.size / hop
        if (n < 10) return ordered(words)
        val rms = FloatArray(n) { f -> var e = 0.0; for (i in f * hop until (f + 1) * hop) e += pcm[i] * pcm[i]; kotlin.math.sqrt(e / hop).toFloat() }
        val sorted = rms.sorted()
        val floor = sorted[(n * 0.15).toInt()].coerceAtLeast(1e-4f)
        val loud = sorted[(n * 0.9).toInt()]
        // Voce: sopra il rumore di fondo con un buon margine, ma comunque una frazione del parlato forte.
        val thr = maxOf(floor * 3.5f, loud * 0.12f)
        fun voiced(f: Int) = f in 0 until n && rms[f] >= thr
        val pauseF = (PAUSE_US / 10_000).toInt()
        val lateF = (LATE_US / 10_000).toInt()
        var prevStart = Long.MIN_VALUE
        val out = words.map { w ->
            val f0 = (w.startUs / 10_000).toInt()
            val f1 = (w.endUs / 10_000).toInt().coerceAtMost(n - 1)
            val moved = if (voiced(f0) || voiced(f0 + 1) || voiced(f0 + 2)) {
                // Sulla voce: si guarda indietro se la voce è ripartita da poco dopo una pausa.
                var g = f0
                while (g > 0 && f0 - g < lateF && voiced(g - 1)) g--
                val onset = f0 - g < lateF && g > 0 && (1..pauseF).all { !voiced(g - it) }
                val start = (g - 2).coerceAtLeast(0) * 10_000L
                if (onset && g < f0 && start > prevStart + 60_000) w.copy(startUs = start) else w
            } else {
                val limit = f0 + maxOf(1, (f1 - f0) * 3 / 4)
                val first = (f0..limit).firstOrNull { voiced(it) && voiced(it + 1) }
                if (first == null) w else {
                    val start = (first - 2).coerceAtLeast(f0) * 10_000L
                    w.copy(startUs = start, endUs = maxOf(w.endUs, start + 60_000))
                }
            }
            prevStart = moved.startUs
            moved
        }
        return ordered(out)
    }

    /** Pausa minima prima di una parola perché il suo inizio possa tornare indietro (le consonanti doppie durano meno). */
    const val PAUSE_US = 160_000L
    /** Di quanto al massimo un inizio torna indietro sulla voce partita prima. */
    const val LATE_US = 300_000L

    /** Inizi sempre in ordine (l'allineamento può far cominciare una parola un soffio prima della precedente). */
    fun ordered(words: List<SpokenWord>): List<SpokenWord> {
        var prev = Long.MIN_VALUE
        return words.map { w ->
            val st = maxOf(w.startUs, prev)
            prev = st
            if (st == w.startUs) w else w.copy(startUs = st, endUs = maxOf(w.endUs, st + 60_000))
        }
    }

    /**
     * Toglie i segni che non sono parole ("[Musica]", "(applausi)", note…). Una parentesi aperta toglie le parole solo
     * se si chiude entro poche parole: una parentesi rimasta aperta non deve far sparire il resto del parlato.
     */
    fun clean(words: List<SpokenWord>): List<SpokenWord> {
        val out = mutableListOf<SpokenWord>()
        var i = 0
        while (i < words.size) {
            val t = words[i].text.trim()
            if (t.startsWith("[") || t.startsWith("(")) {
                val close = (i until minOf(words.size, i + 6)).firstOrNull { k -> words[k].text.trim().let { it.endsWith("]") || it.endsWith(")") } }
                if (close != null) { i = close + 1; continue }
            }
            if (t.isNotEmpty() && t.any { it.isLetterOrDigit() }) out += words[i].copy(text = t.trimStart('(', '['))
            i++
        }
        return out
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

    /**
     * Righe dei sottotitoli con i loro tempi: nessuna parola si perde. 09/11/2026 (collaudo: "a volte salta delle
     * parole"): una riga troppo breve per restare a schermo (es. «Sì.» detto subito prima della frase dopo) prima veniva
     * scartata; ora si unisce alla riga dopo (l'ultima, alla precedente).
     */
    fun timedLines(words: List<SpokenWord>, maxWords: Int, projectUs: Long): List<Triple<List<SpokenWord>, Long, Long>> {
        val lines = group(clean(words), maxWords = maxWords).map { it.toMutableList() }.toMutableList()
        fun startOf(l: List<SpokenWord>) = l.first().startUs.coerceIn(0, (projectUs - TextItem.MIN_US).coerceAtLeast(0))
        fun endOf(i: Int): Long {
            val line = lines[i]
            // Resta a schermo fino all'inizio della riga dopo (al massimo 1,2 s dopo l'ultima parola).
            val nextStart = lines.getOrNull(i + 1)?.first()?.startUs ?: projectUs
            return minOf(maxOf(line.last().endUs + 300_000, startOf(line) + 700_000), line.last().endUs + 1_200_000, nextStart, projectUs)
        }
        var i = 0
        while (i < lines.size) {
            if (endOf(i) - startOf(lines[i]) >= TextItem.MIN_US || lines.size == 1) { i++; continue }
            if (i + 1 < lines.size) { lines[i].addAll(lines.removeAt(i + 1)) }
            else { lines[i - 1].addAll(lines.removeAt(i)); i-- }
        }
        return lines.indices.map { k -> Triple(lines[k], startOf(lines[k]), endOf(k)) }
            .filter { (_, s, e) -> e - s >= TextItem.MIN_US }
    }

    /** Sottotitoli pronti (testi della timeline, marcati come sottotitoli) dalle parole riconosciute. */
    fun build(words: List<SpokenWord>, style: SubStyle, pos: SubPosition, projectUs: Long): List<TextItem> {
        return timedLines(words, style.maxWords, projectUs).map { (line, start, end) ->
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
