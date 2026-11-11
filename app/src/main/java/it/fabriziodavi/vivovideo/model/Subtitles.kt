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
    // Instagram 2026-27: la frase intera, bianca, che si colora lettera per lettera mentre è detta.
    FILL("Si colora mentre parli", maxWords = 4),
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
    /** Colori di risalto d'impatto (2027) per le parole dette e le parole importanti. */
    val ACCENTS: List<Pair<String, Int>> = listOf(
        "Lime acido" to KINETIC_HIGHLIGHT, "Ciano elettrico" to 0xFF00E5FF.toInt(), "Magenta" to 0xFFFF2EC4.toInt(),
        "Arancio fluo" to 0xFFFF7A1A.toInt(), "Viola elettrico" to 0xFFA66BFF.toInt(), "Rosso corallo" to 0xFFFF4D5E.toInt(),
        "Verde menta" to 0xFF3DFFB0.toInt(), "Giallo burro" to HIGHLIGHT,
    )

    /**
     * Quante lettere del testo sono già colorate nello stile «Si colora mentre parli» a [local] µs dall'inizio: le parole
     * già dette tutte, quella in corso lettera per lettera fino all'inizio della parola dopo (l'ultima in circa 70 ms a
     * lettera). [ranges] sono le parole nel testo, [times] i loro inizi.
     */
    fun filledChars(ranges: List<IntRange>, times: List<Long>, local: Long, durationUs: Long): Int {
        val k = wordAt(times, local).coerceAtMost(ranges.lastIndex)
        if (k < 0) return 0
        val r = ranges[k]
        val len = r.last - r.first + 1
        val t0 = times[k]
        val t1 = times.getOrNull(k + 1) ?: (t0 + (len * 70_000L).coerceIn(200_000L, 600_000L)).coerceAtMost(durationUs)
        val f = ((local - t0).toDouble() / (t1 - t0).coerceAtLeast(1)).coerceIn(0.0, 1.0)
        return r.first + kotlin.math.ceil(f * len).toInt().coerceIn(1, len)
    }

    fun build(words: List<SpokenWord>, style: SubStyle, pos: SubPosition, projectUs: Long, accent: Int? = null): List<TextItem> {
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
                // Si colora mentre parli: bold bianco con contorno, il colore riempie le parole mentre sono dette.
                SubStyle.FILL -> base.copy(wordMode = 3, font = TextFont.POSTER, look = TextLook.CONTORNO, size = 0.062f, highlight = KINETIC_HIGHLIGHT)
            }.let { t -> if (accent != null && (t.wordMode == 1 || t.wordMode == 3 || t.kinetic != null)) t.copy(highlight = accent) else t }
        }
    }

    /** Tempo minimo a schermo di una parola nei cinetici e nel karaoke (3 fotogrammi a 30 fps). */
    const val WORD_MIN_US = 100_000L

    /**
     * Inizi delle [n] parole del testo, pronti da mostrare (09/11/2026, collaudo: "salta delle parole, anche se le
     * aggiungo a mano"). Se i tempi della voce sono meno delle parole scritte, le parole in più si distribuiscono
     * fino alla fine del testo invece di non comparire mai; se due parole iniziano insieme (o quasi), ognuna resta
     * comunque a schermo almeno [WORD_MIN_US], spostando le vicine quanto basta.
     */
    fun wordTimes(times: List<Long>, n: Int, durationUs: Long): List<Long> {
        if (n <= 0) return emptyList()
        val end = durationUs.coerceAtLeast(1)
        val base = times.take(n).map { it.coerceIn(0, end) }.toMutableList()
        if (base.size < n) {
            val from = if (base.isEmpty()) 0L else base.last()
            val stop = maxOf(from + 1, end * 9 / 10)
            val missing = n - base.size
            if (base.isEmpty()) for (i in 0 until missing) base += stop * i / missing
            else for (i in 1..missing) base += from + (stop - from) * i / missing
        }
        return spread(base, end)
    }

    /** Ogni parola almeno [WORD_MIN_US] dopo la precedente, senza uscire dal testo; se non c'è posto, passo regolare. */
    private fun spread(ts: MutableList<Long>, end: Long): List<Long> {
        val n = ts.size
        val gap = WORD_MIN_US
        if (n == 1) return ts
        if ((n - 1) * gap > end - gap / 2) return List(n) { it * (end - gap / 2).coerceAtLeast(0) / (n - 1).coerceAtLeast(1) }
        for (i in 1 until n) ts[i] = maxOf(ts[i], ts[i - 1] + gap)
        val last = end - gap
        if (ts[n - 1] > last) {
            ts[n - 1] = last
            for (i in n - 2 downTo 0) ts[i] = minOf(ts[i], ts[i + 1] - gap)
        }
        if (ts[0] < 0) {
            val shift = -ts[0]
            for (i in 0 until n) ts[i] += shift
        }
        return ts
    }

    private fun norm(w: String) = w.lowercase().filter { it.isLetterOrDigit() }

    /**
     * Tempi delle parole dopo che il testo di un sottotitolo è stato corretto a mano. Le parole rimaste uguali tengono
     * il loro tempo; una parola cambiata al posto di un'altra ne prende il tempo; le parole aggiunte si mettono tra la
     * parola prima e quella dopo. Così una parola scritta a mano compare anche nei cinetici e nel karaoke.
     */
    fun retime(oldText: String, newText: String, times: List<Long>, durationUs: Long): List<Long> {
        val a = Regex("\\S+").findAll(oldText).map { norm(it.value) }.toList()
        val b = Regex("\\S+").findAll(newText).map { norm(it.value) }.toList()
        if (b.isEmpty()) return emptyList()
        val t = wordTimes(times, a.size, durationUs)
        if (a.isEmpty()) return wordTimes(emptyList(), b.size, durationUs)
        // Parole in comune, in ordine (sottosequenza comune più lunga).
        val L = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.size - 1 downTo 0) for (j in b.size - 1 downTo 0)
            L[i][j] = if (a[i] == b[j] && a[i].isNotEmpty()) L[i + 1][j + 1] + 1 else maxOf(L[i + 1][j], L[i][j + 1])
        val pairs = mutableListOf<Pair<Int, Int>>()
        var i = 0; var j = 0
        while (i < a.size && j < b.size) {
            if (a[i] == b[j] && a[i].isNotEmpty()) { pairs += i to j; i++; j++ }
            else if (L[i + 1][j] >= L[i][j + 1]) i++ else j++
        }
        pairs += a.size to b.size
        val out = LongArray(b.size)
        var pi = -1; var pj = -1
        for ((ai, bj) in pairs) {
            val newGap = (pj + 1 until bj).toList()
            val oldGap = (pi + 1 until ai).toList()
            if (newGap.isNotEmpty()) {
                if (oldGap.size == newGap.size) newGap.forEachIndexed { k, x -> out[x] = t[oldGap[k]] }
                else {
                    // Da dove partono le parole nuove e dove finiscono: dalla parola prima (o dal primo tempo sostituito).
                    val left = if (oldGap.isNotEmpty()) t[oldGap.first()] else if (pi >= 0) t[pi] else 0L
                    val right = if (ai < a.size) t[ai] else maxOf(left + 1, durationUs * 9 / 10)
                    val slots = if (oldGap.isNotEmpty() || pi < 0) newGap.size else newGap.size + 1
                    val first = if (oldGap.isNotEmpty() || pi < 0) 0 else 1
                    newGap.forEachIndexed { k, x -> out[x] = left + (right - left) * (k + first) / slots }
                }
            }
            if (ai < a.size) out[bj] = t[ai]
            pi = ai; pj = bj
        }
        return spread(out.toMutableList(), durationUs.coerceAtLeast(1))
    }

    /** Parola in corso a [localUs] dall'inizio del testo (indice), o -1 prima della prima. */
    fun wordAt(wordsUs: List<Long>, localUs: Long): Int {
        var k = -1
        for (i in wordsUs.indices) if (wordsUs[i] <= localUs) k = i else break
        return k
    }
}
