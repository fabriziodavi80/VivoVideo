package it.fabriziodavi.vivovideo.model

import java.text.Normalizer

/** Come mostrare le due risposte a ogni domanda. */
enum class InterviewFormat(val label: String, val hint: String) {
    ALTERNATE("Alternata", "Cartello con la domanda, poi la risposta di A, poi quella di B, a tutto schermo."),
    SPLIT("Schermo diviso", "A e B insieme sullo schermo (affiancati, o uno sopra l'altro nei video verticali); parla uno alla volta, l'altro resta fermo e più scuro."),
}

/** Nomi e didascalie (in stile TV), formato e cartelli. */
data class InterviewOptions(
    val format: InterviewFormat = InterviewFormat.ALTERNATE,
    val nameA: String = "",
    val nameB: String = "",
    val captionA: String = "",
    val captionB: String = "",
    /** Durata del cartello con la domanda (0 = niente cartelli). */
    val cardUs: Long = 2_500_000,
)

/** Una domanda con le due risposte (tratti nel file di ciascun video; null = non trovata). */
data class InterviewQA(val question: String, val a: Span?, val b: Span?) {
    val usable: Boolean get() = a != null || b != null
}

/**
 * Intervista doppia (stile Le Iene): due video con le stesse domande. Le domande si trovano nel parlato (le frasi con il
 * punto di domanda, o quelle scritte dall'utente), la risposta è il tratto fino alla domanda successiva. Tutto qui è
 * senza Android: il ViewModel ci mette solo il riconoscimento vocale, i fotogrammi fermi e la foto nera.
 */
object Interview {
    /** Margini: la risposta parte poco dopo la fine della domanda e finisce poco prima della successiva. */
    const val LEAD_US = 150_000L
    const val TAIL_US = 400_000L
    const val MIN_ANSWER_US = 600_000L

    /** Nome e didascalia restano così a lungo all'inizio di ogni risposta (come in TV). */
    const val NAME_US = 3_500_000L

    /** Chi ascolta, nello schermo diviso: fermo e più scuro. */
    const val LISTENER_OPACITY = 0.45f

    /** Parola senza accenti, minuscola, solo lettere e cifre. */
    fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").filter { it.isLetterOrDigit() }

    private fun tokens(s: String): List<String> = s.split(Regex("\\s+")).map(::norm).filter { it.isNotEmpty() }

    /** Frasi del parlato (parole consecutive fino a . ? !), con il loro tratto. */
    data class Sentence(val text: String, val span: Span, val firstWord: Int, val lastWord: Int) {
        val isQuestion: Boolean get() = text.trimEnd().endsWith("?")
    }

    fun sentences(words: List<SpokenWord>): List<Sentence> {
        val out = mutableListOf<Sentence>()
        var from = 0
        for (i in words.indices) {
            val end = words[i].text.trimEnd().lastOrNull()
            val gap = words.getOrNull(i + 1)?.let { it.startUs - words[i].endUs } ?: 0L
            if (end == '.' || end == '?' || end == '!' || i == words.lastIndex || gap > 1_500_000) {
                val part = words.subList(from, i + 1)
                out += Sentence(part.joinToString(" ") { it.text.trim() }, Span(part.first().startUs, part.last().endUs), from, i)
                from = i + 1
            }
        }
        return out
    }

    /** Parole che aprono una domanda (senza accenti né apostrofi: "cos'è" → "cose", "perché" → "perche"). */
    private val ASK = setOf(
        "chi", "cosa", "cose", "che", "come", "dove", "quando", "quale", "quali", "qual",
        "quanto", "quanta", "quanti", "quante", "perche",
    )

    /** Pausa dopo cui, anche senza punteggiatura, comincia un'altra frase (chi risponde attacca dopo la domanda). */
    private const val TURN_US = 600_000L

    private fun endsTurn(words: List<SpokenWord>, i: Int): Boolean {
        val end = words[i].text.trimEnd().lastOrNull()
        val gap = words.getOrNull(i + 1)?.let { it.startUs - words[i].endUs } ?: 0L
        return end == '.' || end == '?' || end == '!' || gap > TURN_US
    }

    private fun asText(words: List<SpokenWord>, from: Int, to: Int): String {
        val t = words.subList(from, to + 1).joinToString(" ") { it.text.trim() }.trimEnd('.', ',', ';', ':', '!', ' ')
        return t.replaceFirstChar { it.uppercase() }.let { if (it.endsWith("?")) it else "$it?" }
    }

    /**
     * Le domande del parlato [words], in ordine. Prima le frasi con il punto di domanda e almeno tre parole (non "sì?",
     * "davvero?"). 11/11/2026 (collaudo: con il modello Massima precisione 0 domande): il riconoscimento non sempre
     * scrive il "?", quindi valgono anche i pezzi detti uguali nell'altro video [other] (chi intervista fa le stesse
     * domande a tutti e due), purché sembrino domande; e, se ancora niente, le frasi che cominciano con chi, cosa, come,
     * dove, quando, quale, quanto, perché.
     */
    fun questionsIn(words: List<SpokenWord>, other: List<SpokenWord> = emptyList()): List<String> {
        val found = mutableListOf<Pair<Int, String>>()
        val taken = BooleanArray(words.size)
        sentences(words).filter { it.isQuestion && tokens(it.text).size >= 3 }.forEach { q ->
            found += q.firstWord to q.text.trim()
            for (k in q.firstWord..q.lastWord) taken[k] = true
        }
        if (other.isNotEmpty()) shared(words, other).forEach { (a, b) ->
            if ((a..b).none { taken[it] }) { found += a to asText(words, a, b); for (k in a..b) taken[k] = true }
        }
        if (found.isEmpty()) {
            var from = 0
            for (i in words.indices) if (endsTurn(words, i) || i == words.lastIndex) {
                val t = (from..i).map { norm(words[it].text) }.filter { it.isNotEmpty() }
                if (t.size in 3..16 && t.take(2).any { it in ASK }) found += from to asText(words, from, i)
                from = i + 1
            }
        }
        return found.sortedBy { it.first }.map { it.second }
    }

    /**
     * Pezzi di almeno tre parole (due se cominciano con dove, come, quanti…) detti uguali (parole di fila) in [a] e in [b]: le domande ripetute dall'intervistatore.
     * Un pezzo si ferma alla fine della frase (punto o pausa), così non si porta dietro l'inizio di una risposta uguale
     * ("mi chiamo…"); per contare come domanda deve avere una parola da domanda o almeno cinque parole.
     */
    internal fun shared(a: List<SpokenWord>, b: List<SpokenWord>): List<Pair<Int, Int>> {
        val ta = a.map { norm(it.text) }
        val tb = b.map { norm(it.text) }
        if (ta.isEmpty() || tb.isEmpty()) return emptyList()
        val L = Array(ta.size + 1) { IntArray(tb.size + 1) }
        for (i in 1..ta.size) for (j in 1..tb.size) {
            if (ta[i - 1].isNotEmpty() && ta[i - 1] == tb[j - 1]) L[i][j] = L[i - 1][j - 1] + 1
        }
        // Pezzi uguali di almeno due parole: inizio e fine in A e in B.
        data class Run(var a0: Int, var a1: Int, var b0: Int, var b1: Int)
        val runs = mutableListOf<Run>()
        for (i in 1..ta.size) for (j in 1..tb.size) {
            val n = L[i][j]
            if (n < 2) continue
            if (i == ta.size || j == tb.size || L[i + 1][j + 1] == 0) runs += Run(i - n, i - 1, j - n, j - 1)
        }
        // 11/11/2026 (collaudo: "le domande risultano incomplete"): il riconoscimento scrive la stessa domanda un po'
        // diversa nei due video ("qual è" / "qual'è", "preferito" / "preferita"). Due pezzi uguali separati da una o due
        // parole diverse, in A e in B, sono la stessa domanda.
        runs.sortWith(compareBy({ it.a0 }, { it.b0 }))
        val merged = mutableListOf<Run>()
        for (r in runs) {
            val m = merged.lastOrNull { m ->
                r.a0 - m.a1 in 1..3 && r.b0 - m.b1 in 1..3 &&
                    (m.a1 until r.a0).none { endsSentence(a, it) } &&
                    (m.b1 until r.b0).none { endsSentence(b, it) }
            }
            if (m != null) { m.a1 = r.a1; m.b1 = r.b1 } else merged += r.copy()
        }
        val out = mutableListOf<Pair<Int, Int>>()
        val used = BooleanArray(ta.size)
        for (r in merged.sortedByDescending { it.a1 - it.a0 }) {
            var s = r.a0
            var e = r.a1
            // Dentro al pezzo: si ferma al punto, o a una pausa lunga nello stesso punto dei due video dopo una domanda già
            // fatta (lì attaccano tutti e due a rispondere: "come ti chiami / mi chiamo…"). Una pausa in un video solo è
            // chi intervista che si ferma a metà domanda.
            for (k in s until e) {
                val t = (s..k).map { ta[it] }.filter { it.isNotEmpty() }
                val bk = r.b0 + (k - r.a0)
                val bothPause = gapAfter(a, k) > TURN_US && bk in b.indices && gapAfter(b, bk) > TURN_US
                if (endsSentence(a, k) || (bothPause && t.size >= 3 && t.any { it in ASK })) { e = k; break }
            }
            // La domanda intera: indietro fino all'inizio della frase e avanti fino al punto, se sono a poche parole.
            (1..4).firstOrNull { d -> s - d < 0 || endsSentence(a, s - d) || gapAfter(a, s - d) > TURN_US }
                ?.let { d -> if (d > 1) s -= d - 1 }
            if (!endsSentence(a, e)) (1..3).firstOrNull { d -> e + d <= a.lastIndex && endsSentence(a, e + d) && (e until e + d).none { gapAfter(a, it) > TURN_US } }
                ?.let { d -> e += d }
            val t = (s..e).map { ta[it] }.filter { it.isNotEmpty() }
            val short = t.size == 2 && t[0] in ASK && t[0] != "che"
            if ((t.size < 3 && !short) || (t.none { it in ASK } && t.size < 5)) continue
            if ((s..e).any { used[it] }) continue
            for (k in s..e) used[k] = true
            out += s to e
        }
        return out.sortedBy { it.first }
    }

    private fun endsSentence(words: List<SpokenWord>, i: Int): Boolean =
        words[i].text.trimEnd().lastOrNull().let { it == '.' || it == '?' || it == '!' }

    private fun gapAfter(words: List<SpokenWord>, i: Int): Long =
        words.getOrNull(i + 1)?.let { it.startUs - words[i].endUs } ?: Long.MAX_VALUE / 4

    /**
     * Dove si dice la domanda [q] nel parlato, a partire dalla parola [from]: indice della prima e dell'ultima parola,
     * o null. Si confrontano le parole (senza accenti né punteggiatura): ne basta più di metà, nell'ordine giusto o quasi.
     */
    internal fun find(words: List<SpokenWord>, q: String, from: Int): Pair<Int, Int>? {
        val qt = tokens(q)
        if (qt.isEmpty()) return null
        val wt = words.map { norm(it.text) }
        val need = qt.toSet()
        var best: Pair<Int, Int>? = null
        var bestScore = 0.0
        val win = qt.size + 3
        for (i in from until wt.size) {
            if (wt[i] !in need) continue
            // La domanda finisce al suo punto interrogativo: le parole dopo sono già la risposta ("il risotto…").
            var end = minOf(wt.size, i + win)
            var mark = -1
            // Senza "?" (11/11/2026) la domanda finisce comunque alla fine della frase: punto o pausa prima della risposta.
            // Una pausa conta solo dopo buona parte della domanda: chi intervista può fermarsi un attimo a metà.
            val seen = HashSet<String>()
            for (j in i until end) {
                if (wt[j] in need) seen += wt[j]
                if (endsSentence(words, j) || (gapAfter(words, j) > TURN_US && seen.size >= need.size * 3 / 4)) { end = j + 1; mark = j; break }
            }
            val window = (i until end)
            val hit = window.filter { wt[it] in need }
            val score = hit.map { wt[it] }.toSet().size.toDouble() / need.size
            // Fine della domanda: il punto interrogativo, se c'è ("…preferito, dimmi?"), altrimenti l'ultima parola uguale.
            if (score > bestScore + 1e-9) { bestScore = score; best = i to maxOf(hit.last(), mark) }
            if (bestScore >= 0.999) break
        }
        return best?.takeIf { bestScore >= 0.55 }
    }

    /**
     * La risposta a ogni domanda nel parlato [words] di un video lungo [durationUs]: dalla fine della domanda all'inizio
     * della domanda successiva trovata (o alla fine del parlato). Le domande si cercano in ordine.
     */
    fun answers(words: List<SpokenWord>, questions: List<String>, durationUs: Long): List<Span?> {
        var from = 0
        val found = questions.map { q -> find(words, q, from)?.also { from = it.second + 1 } }
        return questions.indices.map { k ->
            val f = found[k] ?: return@map null
            val first = f.second + 1
            if (first > words.lastIndex) return@map null
            val nextStart = (k + 1 until questions.size).firstNotNullOfOrNull { found[it] }?.first
            val last = (nextStart ?: words.size) - 1
            if (last < first) return@map null
            val a = (words[first].startUs - LEAD_US).coerceAtLeast(words[f.second].endUs)
            val b = (words[last].endUs + TAIL_US).let { e -> nextStart?.let { minOf(e, words[it].startUs - 50_000) } ?: e }.coerceAtMost(durationUs)
            Span(a, b).takeIf { it.lengthUs >= MIN_ANSWER_US }
        }
    }

    private val NUMBERS = listOf(
        "zero", "uno", "due", "tre", "quattro", "cinque", "sei", "sette", "otto", "nove", "dieci", "undici", "dodici", "tredici",
        "quattordici", "quindici", "sedici", "diciassette", "diciotto", "diciannove", "venti",
    )

    /**
     * Parola per il confronto fra i due video: come [norm], più le forme che il riconoscimento scrive in due modi
     * ("10"/"dieci", "fra"/"tra", "qual'è"/"qual è", "un"/"uno").
     */
    internal fun key(s: String): String {
        val n = norm(s)
        n.toIntOrNull()?.let { v -> return when { v in 0..20 -> NUMBERS[v]; v == 30 -> "trenta"; v == 40 -> "quaranta"; v == 50 -> "cinquanta"; else -> n } }
        return when (n) {
            "fra" -> "tra"
            "quale", "qualè" -> "qual"
            "un", "uno" -> "un"
            "cos", "cose" -> "cosa"
            else -> n
        }
    }

    /** Parole con cui una domanda non finisce: articoli, possessivi, preposizioni, congiunzioni ("qual è il tuo…"). */
    private val OPEN = setOf(
        "il", "lo", "la", "i", "gli", "le", "l", "un", "uno", "una", "tuo", "tua", "tuoi", "tue", "suo", "sua", "vostro", "vostra",
        "di", "del", "della", "dei", "delle", "a", "al", "alla", "ai", "da", "dal", "dalla", "in", "nel", "nella", "nei", "con",
        "su", "sul", "sulla", "per", "tra", "fra", "e", "o", "ma", "che", "piu", "molto", "cosa", "quale", "qual", "come",
    )

    private fun openEnd(t: String) = t in OPEN

    /** Parole con cui comincia una risposta. */
    private val REPLY = setOf("si", "no", "beh", "be", "allora", "mah", "boh", "eh", "ehm", "dunque", "ok", "certo", "mmm", "uhm", "allora", "sicuramente", "ovviamente")

    /**
     * Parole uguali nei due video, allineate in ordine come quando si confrontano due versioni di un testo (punteggio:
     * +3 parola uguale, -1 parola diversa o in più). Restituisce le coppie (indice in A, indice in B).
     */
    internal fun align(ta: List<String>, tb: List<String>): List<Pair<Int, Int>> {
        val n = ta.size; val m = tb.size
        val S = Array(n + 1) { IntArray(m + 1) }
        for (i in 1..n) S[i][0] = -i
        for (j in 1..m) S[0][j] = -j
        fun eq(i: Int, j: Int) = ta[i].isNotEmpty() && ta[i] == tb[j]
        for (i in 1..n) for (j in 1..m)
            S[i][j] = maxOf(S[i - 1][j - 1] + if (eq(i - 1, j - 1)) 3 else -1, S[i - 1][j] - 1, S[i][j - 1] - 1)
        val out = mutableListOf<Pair<Int, Int>>()
        var i = n; var j = m
        while (i > 0 && j > 0) {
            val d = S[i - 1][j - 1] + if (eq(i - 1, j - 1)) 3 else -1
            when {
                S[i][j] == d -> { if (eq(i - 1, j - 1)) out += (i - 1) to (j - 1); i--; j-- }
                S[i][j] == S[i - 1][j] - 1 -> i--
                else -> j--
            }
        }
        return out.reversed()
    }

    /** Due parole scritte quasi uguali dal riconoscimento ("preferito"/"preferita", "lavoro"/"lavori"). */
    private fun near(x: String, y: String): Boolean {
        if (x == y) return true
        if (x.length < 5 || y.length < 5) return false
        val n = minOf(x.length, y.length)
        var k = 0
        while (k < n && x[k] == y[k]) k++
        return k >= n - 1 && kotlin.math.abs(x.length - y.length) <= 1
    }

    /** Parola che apre una domanda nuova (non "che": troppo comune dentro le frasi). */
    private fun starter(t: String) = t in ASK && t != "che"

    /**
     * Domande e risposte trovate confrontando il testo dei due video parola per parola (11/11/2026, collaudo con i
     * video veri: il riconoscimento non scrive la punteggiatura e i tempi delle parole non sono affidabili, quindi le
     * pause fra le parole non contano). Chi intervista dice le stesse domande a tutti e due: i tratti uguali sono le
     * domande, quelli diversi le risposte. Una domanda nuova comincia con una parola da domanda (cosa, come, qual…)
     * dopo parole diverse; una domanda non finisce con un articolo o una preposizione ("…preferito la | pasta": "la" è
     * già della risposta). Dove tagliare lo decide poi l'audio ([refine]), non i tempi delle parole.
     */
    internal fun paired(wa: List<SpokenWord>, wb: List<SpokenWord>, durA: Long, durB: Long): List<InterviewQA> =
        pairedIdx(wa, wb).let { qs -> qs.mapIndexed { k, q ->
            val next = qs.getOrNull(k + 1)
            InterviewQA(asText(wa, q[0], q[1]), wordSpan(wa, q[1], next?.get(0), durA), wordSpan(wb, q[3], next?.get(2), durB))
        } }

    /** Risposta dalle parole: dopo l'ultima parola della domanda fino a prima della domanda dopo. */
    private fun wordSpan(words: List<SpokenWord>, qEnd: Int, nextStart: Int?, dur: Long): Span? {
        val first = qEnd + 1
        val last = (nextStart ?: words.size) - 1
        if (first > words.lastIndex || last < first) return null
        val a = (words[first].startUs - LEAD_US).coerceAtLeast(words[qEnd].startUs + 60_000)
        val b = (words[last].endUs + TAIL_US).let { e -> nextStart?.let { minOf(e, words[it].startUs - 50_000) } ?: e }.coerceAtMost(dur)
        return Span(a, b).takeIf { it.lengthUs >= MIN_ANSWER_US }
    }

    /** Le domande come indici di parole: [inizio A, fine A, inizio B, fine B]. */
    internal fun pairedIdx(wa: List<SpokenWord>, wb: List<SpokenWord>): List<IntArray> {
        val ta = wa.map { key(it.text) }; val tb = wb.map { key(it.text) }
        val pairs = align(ta, tb)
        if (pairs.size < 2) return emptyList()
        // Pausa vera nello stesso punto dei due video (quando i tempi ci sono): lì attacca chi risponde.
        fun pause(w: List<SpokenWord>, i: Int) = gapAfter(w, i) > PAUSE_US
        val groups = mutableListOf(mutableListOf(pairs[0]))
        for (k in 1 until pairs.size) {
            val (pa, pb) = pairs[k - 1]; val (ca, cb) = pairs[k]
            val ua = ca - pa - 1; val ub = cb - pb - 1
            // Parole diverse in mezzo ma quasi uguali a coppie ("preferito"/"preferita"): sono la stessa frase.
            val similar = ua == ub && ua in 1..2 && (1..ua).all { d -> near(ta[pa + d], tb[pb + d]) }
            val split = !similar && (
                // Tre o più parole diverse da una parte: è una risposta ("andrei a fare | una passeggiata").
                ua >= 3 || ub >= 3 ||
                    // Una parola da domanda dopo parole diverse, o dopo una frase già lunga: domanda nuova.
                    (starter(ta[ca]) && (ua + ub >= 1 || groups.last().size >= 3)) ||
                    (ta[ca] == "che" && ua >= 1 && ub >= 1) ||
                    ((pa until ca).any { endsSentence(wa, it) } && (pb until cb).any { endsSentence(wb, it) })
                )
            if (split) groups += mutableListOf(pairs[k]) else groups.last() += pairs[k]
        }
        // Il resto di una domanda spezzata (chi intervista si ferma a metà): subito dopo, senza parola da domanda, al
        // massimo due parole diverse per parte, nessun punto in mezzo; e la domanda di prima resta a metà ("qual è il
        // tuo… piatto") oppure la pausa è in un video solo.
        val merged = mutableListOf(groups[0])
        for (g in groups.drop(1)) {
            val p = merged.last()
            val (pa, pb) = p.last(); val (ca, cb) = g.first()
            val ok = !starter(ta[ca]) && !starter(tb[cb]) && ca - pa - 1 <= 2 && cb - pb - 1 <= 2 &&
                (pa until ca).none { endsSentence(wa, it) } && (pb until cb).none { endsSentence(wb, it) } &&
                (openEnd(ta[pa]) || !((pa until ca).any { pause(wa, it) } && (pb until cb).any { pause(wb, it) }))
            if (ok) p += g else merged += g
        }
        val out = mutableListOf<IntArray>()
        for (g in merged) {
            var a0 = g.first().first; var a1 = g.last().first; var b0 = g.first().second; var b1 = g.last().second
            // La parola da domanda subito prima, scritta diversa nei due video ("che faresti" / "cosa faresti").
            val pa1 = out.lastOrNull()?.get(1) ?: -1; val pb1 = out.lastOrNull()?.get(3) ?: -1
            if (a0 - 1 > pa1 && ta[a0 - 1] in ASK && !endsSentence(wa, a0 - 1)) a0--
            if (b0 - 1 > pb1 && tb[b0 - 1] in ASK && !endsSentence(wb, b0 - 1)) b0--
            // Inizio: dalla parola da domanda, se è fra le prime ("passeggiata | come ti vedi", "allora | qual è").
            (a0..minOf(a1, a0 + 3)).firstOrNull { starter(ta[it]) }?.let { a0 = it }
            (b0..minOf(b1, b0 + 3)).firstOrNull { starter(tb[it]) }?.let { b0 = it }
            // Fine: niente articoli o preposizioni in coda, sono l'inizio della risposta ("…preferito la | pasta").
            // Neanche le parole con cui si comincia a rispondere ("sì", "beh", "allora"), se dette uguali da tutti e due.
            while (a1 > a0 && (openEnd(ta[a1]) || ta[a1] in REPLY) && !endsSentence(wa, a1)) a1--
            while (b1 > b0 && (openEnd(tb[b1]) || tb[b1] in REPLY) && !endsSentence(wb, b1)) b1--
            // Ultima parola scritta quasi uguale nei due video ("preferito"/"preferita") e fino al "?" vicino.
            if (a1 + 1 <= wa.lastIndex && b1 + 1 <= wb.lastIndex && ta[a1 + 1] != tb[b1 + 1] && near(ta[a1 + 1], tb[b1 + 1])) { a1++; b1++ }
            fun toMark(w: List<SpokenWord>, e: Int): Int =
                (1..2).firstOrNull { d -> e + d <= w.lastIndex && w[e + d].text.trimEnd().endsWith("?") && (e until e + d).none { endsSentence(w, it) } }?.let { e + it } ?: e
            a1 = toMark(wa, a1); b1 = toMark(wb, b1)
            val matched = g.count { it.first in a0..a1 }
            val head = (a0..minOf(a1, a0 + 2)).map { ta[it] } + (b0..minOf(b1, b0 + 2)).map { tb[it] }
            val asks = head.any { it in ASK } || wa[a1].text.trimEnd().endsWith("?") || wb[b1].text.trimEnd().endsWith("?")
            if (matched < 2 || (!asks && matched < 4)) continue
            val prev = out.lastOrNull()
            if (prev != null && (a0 <= prev[1] || b0 <= prev[3])) continue
            out += intArrayOf(a0, a1, b0, b1)
        }
        return out
    }

    /** Pausa fra due parole che, nello stesso punto dei due video, separa la domanda dalla risposta. */
    private const val PAUSE_US = 300_000L

    // ---------------- Tagli sull'audio ----------------

    /** Pausa minima fra chi chiede e chi risponde. */
    private const val TURN_PAUSE_US = 250_000L

    /** Livello (dB) di ogni tratto di 10 ms dell'audio mono [pcm] a [rate] Hz. */
    internal fun levels(pcm: FloatArray, rate: Int): FloatArray {
        val hop = rate / 100
        val n = pcm.size / hop
        return FloatArray(n) { f ->
            var e = 0.0
            for (k in f * hop until (f + 1) * hop) e += pcm[k] * pcm[k]
            (10 * kotlin.math.log10(e / hop + 1e-10)).toFloat()
        }
    }

    /** Soglia fra voce e silenzio: poco sopra il fondo (10° percentile) verso la voce (90°). */
    internal fun threshold(db: FloatArray): Float {
        if (db.isEmpty()) return 0f
        val s = db.sorted()
        val floor = s[(s.size * 0.10).toInt().coerceIn(0, s.lastIndex)]
        val voice = s[(s.size * 0.90).toInt().coerceIn(0, s.lastIndex)]
        return floor + (voice - floor) * 0.3f
    }

    /**
     * Le pause (silenzi di almeno [minUs]) fra [fromUs] e [toUs]; [first] sceglie la prima lunga almeno [TURN_PAUSE_US],
     * altrimenti l'ultima così: la pausa fra domanda e risposta è la prima dopo la domanda, quella prima
     * della domanda dopo è l'ultima, anche se dentro la risposta c'è una pausa più lunga.
     */
    internal fun pauseAt(db: FloatArray, thr: Float, fromUs: Long, toUs: Long, first: Boolean, minUs: Long = 150_000): Pair<Long, Long>? {
        val f0 = (fromUs / 10_000).toInt().coerceIn(0, db.size); val f1 = (toUs / 10_000).toInt().coerceIn(0, db.size)
        val runs = mutableListOf<Pair<Int, Int>>()
        var run = -1
        for (f in f0..f1) {
            val quiet = f < f1 && db[f] < thr
            if (quiet && run < 0) run = f
            if (!quiet && run >= 0) { if ((f - run) * 10_000L >= minUs) runs += run to f; run = -1 }
        }
        if (runs.isEmpty()) return null
        // Una pausa vera fra due persone dura almeno un quarto di secondo; se nessuna arriva, la più lunga.
        val ok = runs.filter { (it.second - it.first) * 10_000L >= TURN_PAUSE_US }.ifEmpty { listOf(runs.maxBy { it.second - it.first }) }
        val r = if (first) ok.first() else ok.last()
        return r.first * 10_000L to r.second * 10_000L
    }

    /**
     * Tagli delle risposte messi nelle pause vere dell'audio (11/11/2026, collaudo: "l'audio delle domande è tagliato",
     * con i tempi delle parole sballati anche di un secondo). L'inizio della risposta è la fine della pausa più lunga fra
     * l'ultima parola della domanda e la seconda della risposta; la fine è l'inizio della pausa più lunga prima della
     * domanda dopo (o la fine della voce). Senza pausa chiara restano i tempi delle parole.
     */
    internal fun refine(words: List<SpokenWord>, pcm: FloatArray?, rate: Int, qEnd: Int, nextStart: Int?, span: Span?, dur: Long): Span? {
        if (span == null || pcm == null || pcm.isEmpty()) return span
        val db = levels(pcm, rate)
        val thr = threshold(db)
        val first = qEnd + 1
        val last = (nextStart ?: words.size) - 1
        if (first > last) return span
        val inTo = words.getOrNull(first + 1)?.startUs?.takeIf { first + 1 <= last } ?: (words[first].startUs + 1_500_000)
        val a = pauseAt(db, thr, words[qEnd].startUs + 100_000, inTo, first = true)?.let { (it.second - 80_000).coerceAtLeast(it.first) } ?: span.startUs
        val b = if (nextStart != null) {
            pauseAt(db, thr, words[last].startUs + 100_000, words[nextStart].startUs + 600_000, first = false)?.let { it.first + 200_000 } ?: span.endUs
        } else {
            // Ultima risposta: fino a dove la voce finisce davvero, più un respiro.
            val f0 = (words[last].startUs / 10_000).toInt().coerceIn(0, db.size)
            val lastVoice = (db.size - 1 downTo f0).firstOrNull { db[it] >= thr } ?: f0
            (lastVoice * 10_000L + 300_000)
        }
        val out = Span(a.coerceIn(0, dur), b.coerceIn(0, dur))
        return if (out.lengthUs >= MIN_ANSWER_US) out else span
    }

    /** Domande e risposte dei due video. Senza domande scritte, quelle del primo video (o del secondo, se il primo non ne ha). */
    fun match(
        wordsA: List<SpokenWord>, wordsB: List<SpokenWord>, durA: Long, durB: Long, typed: List<String> = emptyList(),
        pcmA: FloatArray? = null, pcmB: FloatArray? = null, rate: Int = 16_000,
    ): List<InterviewQA> {
        val written = typed.map { it.trim() }.filter { it.isNotEmpty() }
        if (written.isEmpty()) {
            val qs = pairedIdx(wordsA, wordsB)
            if (qs.isNotEmpty()) return qs.mapIndexed { k, q ->
                val next = qs.getOrNull(k + 1)
                InterviewQA(
                    asText(wordsA, q[0], q[1]),
                    refine(wordsA, pcmA, rate, q[1], next?.get(0), wordSpan(wordsA, q[1], next?.get(0), durA), durA),
                    refine(wordsB, pcmB, rate, q[3], next?.get(2), wordSpan(wordsB, q[3], next?.get(2), durB), durB),
                )
            }
        }
        val qs = written.ifEmpty { questionsIn(wordsA, wordsB).ifEmpty { questionsIn(wordsB, wordsA) } }
        // Anche con le domande scritte, i tagli vanno nelle pause vere dell'audio.
        fun spans(words: List<SpokenWord>, pcm: FloatArray?, dur: Long): List<Span?> {
            val base = answers(words, qs, dur)
            var from = 0
            val found = qs.map { q -> find(words, q, from)?.also { from = it.second + 1 } }
            return qs.indices.map { k ->
                val f = found[k] ?: return@map base[k]
                val next = (k + 1 until qs.size).firstNotNullOfOrNull { found[it] }?.first
                refine(words, pcm, rate, f.second, next, base[k], dur)
            }
        }
        val a = spans(wordsA, pcmA, durA)
        val b = spans(wordsB, pcmB, durB)
        return qs.indices.map { InterviewQA(qs[it], a[it], b[it]) }
    }

    /**
     * Dove sta ciascuno nello schermo diviso: centro (x, y) e scala del livello (1 = adattato a tutto il quadro).
     * Video verticale: A sopra e B sotto; orizzontale o quadrato: A a sinistra e B a destra.
     */
    fun half(w: Int, h: Int, outW: Int, outH: Int, first: Boolean): Transform {
        val stacked = outH > outW
        val (rw, rh) = if (stacked) outW.toFloat() to outH / 2f else outW / 2f to outH.toFloat()
        val kFrame = minOf(outW.toFloat() / w.coerceAtLeast(1), outH.toFloat() / h.coerceAtLeast(1))
        val kHalf = minOf(rw / w.coerceAtLeast(1), rh / h.coerceAtLeast(1))
        val s = kHalf / kFrame
        val d = if (first) -0.25f else 0.25f
        return if (stacked) Transform(y = d, scale = s) else Transform(x = d, scale = s)
    }

    /** Cartello con la domanda: numero piccolo sopra, domanda grande al centro. */
    internal fun card(n: Int, q: String, startUs: Long, durUs: Long): List<TextItem> = listOf(
        TextPreset.MINIMAL.applyTo(TextItem(text = "Domanda $n")).copy(startUs = startUs, durationUs = durUs, y = -0.1f, color = 0xFFF2B33D.toInt()),
        TextPreset.PULITO.applyTo(TextItem(text = q)).copy(startUs = startUs, durationUs = durUs, y = 0.02f, size = 0.058f),
    )

    /** Nome (etichetta chiara) e didascalia sotto, in basso a sinistra del tratto [x], [y]. */
    internal fun nameplate(name: String, caption: String, startUs: Long, durUs: Long, x: Float, y: Float): List<TextItem> = buildList {
        if (name.isNotBlank()) add(TextPreset.ETICHETTA.applyTo(TextItem(text = name.trim())).copy(startUs = startUs, durationUs = durUs, x = x, y = y, size = 0.042f))
        if (caption.isNotBlank()) add(TextPreset.DIDASCALIA.applyTo(TextItem(text = caption.trim())).copy(startUs = startUs, durationUs = durUs, x = x, y = y + 0.045f, size = 0.032f))
    }

    /**
     * Il montaggio. [a] e [b]: i due video come importati; [black]: una foto nera grande quanto il video (cartelli e fondo
     * dello schermo diviso); [still]: fotogramma fermo di un video a un istante del file (per chi ascolta nello schermo
     * diviso), come livello immagine già pronto, o null.
     */
    fun build(
        name: String,
        a: Clip,
        b: Clip,
        qas: List<InterviewQA>,
        o: InterviewOptions,
        black: Clip,
        outW: Int,
        outH: Int,
        still: (who: Clip, srcUs: Long) -> Layer? = { _, _ -> null },
    ): Project {
        val clips = mutableListOf<Clip>()
        val texts = mutableListOf<TextItem>()
        val layers = mutableListOf<Layer>()
        var t = 0L
        val vertical = outH > outW
        fun addCard(n: Int, q: String) {
            if (o.cardUs <= 0) return
            clips += black.copy(id = java.util.UUID.randomUUID().toString(), trimStartUs = 0, trimEndUs = o.cardUs,
                transitionOut = TransitionSpec(TransitionType.FADE_BLACK, 400_000))
            texts += card(n, q, t, o.cardUs)
            t += o.cardUs
        }
        qas.filter { it.usable }.forEachIndexed { k, qa ->
            addCard(k + 1, qa.question)
            if (o.format == InterviewFormat.ALTERNATE) {
                listOf(Triple(a, qa.a, true), Triple(b, qa.b, false)).forEach { (src, span, isA) ->
                    if (span == null) return@forEach
                    clips += src.copy(id = java.util.UUID.randomUUID().toString(), trimStartUs = span.startUs, trimEndUs = span.endUs)
                    val nm = if (isA) o.nameA else o.nameB
                    val cap = if (isA) o.captionA else o.captionB
                    texts += nameplate(nm, cap, t, minOf(NAME_US, span.lengthUs), x = -0.2f, y = if (vertical) 0.3f else 0.32f)
                    t += span.lengthUs
                }
            } else {
                val dA = qa.a?.lengthUs ?: 0L
                val dB = qa.b?.lengthUs ?: 0L
                val seg = dA + dB
                clips += black.copy(id = java.util.UUID.randomUUID().toString(), trimStartUs = 0, trimEndUs = seg)
                val pa = half(a.width, a.height, outW, outH, first = true)
                val pb = half(b.width, b.height, outW, outH, first = false)
                fun video(src: Clip, span: Span, at: Long, pose: Transform, nm: String) = Layer(
                    uri = src.uri, name = nm.ifBlank { "Intervista" }, width = src.width, height = src.height, isVideo = true,
                    sourceStartUs = span.startUs, sourceDurationUs = src.sourceDurationUs, startUs = at, durationUs = span.lengthUs,
                    pose = pose, animIn = TextAnim.NONE, animOut = TextAnim.NONE, fileName = src.fileName, fileSize = src.fileSize,
                )
                fun listener(src: Clip, srcUs: Long, at: Long, dur: Long, pose: Transform) {
                    still(src, srcUs)?.let { l ->
                        layers += l.copy(startUs = at, durationUs = dur, pose = pose.copy(opacity = LISTENER_OPACITY), animIn = TextAnim.NONE, animOut = TextAnim.NONE)
                    }
                }
                qa.a?.let { sa ->
                    layers += video(a, sa, t, pa, o.nameA)
                    // B ascolta: fermo sull'inizio della sua risposta (o sul primo fotogramma, se non ha risposto).
                    listener(b, qa.b?.startUs ?: 0L, t, sa.lengthUs, pb)
                }
                qa.b?.let { sb ->
                    layers += video(b, sb, t + dA, pb, o.nameB)
                    // A ascolta: fermo sulla fine della sua risposta.
                    listener(a, qa.a?.let { it.endUs - 40_000 } ?: 0L, t + dA, sb.lengthUs, pa)
                }
                // Nomi su ciascuna metà per tutto il tratto.
                val (ax, ay, bx, by) = if (vertical) listOf(-0.2f, -0.06f, -0.2f, 0.42f) else listOf(-0.42f + 0.12f, 0.38f, 0.08f + 0.12f, 0.38f)
                texts += nameplate(o.nameA, o.captionA, t, seg, ax, ay)
                texts += nameplate(o.nameB, o.captionB, t, seg, bx, by)
                t += seg
            }
        }
        val format = if (vertical) VideoFormat.VERTICAL else if (outW == outH) VideoFormat.SQUARE else VideoFormat.HORIZONTAL
        var p = Project(name = name, clips = clips, texts = texts, layers = layers, format = format, originals = listOf(a, b))
        // Alternata: le risposte riempiono il quadro come le altre clip (riempi o sfondo sfocato secondo l'orientamento).
        if (o.format == InterviewFormat.ALTERNATE) p = p.applyFramePresets(p.clips.filter { !it.isPhoto }.map { it.id }.toSet())
        // Ultima clip: nessuna transizione verso il nulla.
        return p.copy(clips = p.clips.mapIndexed { i, c -> if (i == p.clips.lastIndex) c.copy(transitionOut = null) else c })
    }
}
