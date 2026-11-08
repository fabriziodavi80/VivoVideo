package it.fabriziodavi.vivovideo.model

import kotlin.math.max
import kotlin.math.min

/**
 * Testo dietro la persona, posizione automatica (08/11/2026): come in CapCut/Edits il testo grande sta sopra le spalle
 * e la testa ne copre solo la parte bassa, così si legge. Si guarda dove sta la persona (maschere unite nel tempo del
 * testo) e si mette il testo largo quasi quanto il quadro, con circa il [OVERLAP] della sua altezza dietro la testa.
 * Se sopra la testa non c'è posto (persona che riempie il quadro), il testo va in alto e si legge un po' attraverso
 * la persona ([Spot.show]).
 */
object BehindPlacement {
    /** Parte dell'altezza delle lettere coperta dalla testa. */
    const val OVERLAP = 0.4f
    /** Margine minimo dal bordo alto, in frazioni del quadro. */
    const val TOP = 0.05f

    /** [x], [y] e [size] come in TextItem; [show] = quanto si legge attraverso la persona (0 = per niente); [tight] = niente posto sopra la testa. */
    data class Spot(val x: Float, val y: Float, val size: Float, val show: Float, val tight: Boolean)

    /** Parte alta della persona: riga della testa (0…1 dall'alto) e centro orizzontale della testa (0…1), o null se non c'è nessuno. */
    fun head(masks: List<ByteArray>, side: Int): Pair<Float, Float>? {
        if (masks.isEmpty() || side <= 0) return null
        val union = BooleanArray(side * side)
        for (m in masks) for (i in 0 until min(m.size, union.size)) if ((m[i].toInt() and 0xFF) >= 128) union[i] = true
        val cov = FloatArray(side) { r -> (0 until side).count { union[r * side + it] }.toFloat() / side }
        // Prima riga con persona, confermata dalle righe sotto (un granello isolato non conta).
        val top = (0 until side - 3).firstOrNull { r -> cov[r] >= 0.012f && (1..3).all { cov[r + it] >= 0.012f } } ?: return null
        var sx = 0f; var n = 0
        for (r in top until min(side, top + max(2, side / 8))) for (c in 0 until side) if (union[r * side + c]) { sx += c + 0.5f; n++ }
        return top.toFloat() / side to (if (n > 0) sx / n / side else 0.5f)
    }

    /** Dove mettere [text] dietro alla persona in un quadro [frameW]×[frameH]. */
    fun place(masks: List<ByteArray>, side: Int, frameW: Int, frameH: Int, text: String, upper: Boolean): Spot? {
        val (topF, _) = head(masks, side) ?: return null
        val chars = text.trim().lines().maxOfOrNull { it.length }?.coerceAtLeast(1) ?: return null
        val lines = text.trim().lines().size.coerceAtLeast(1)
        val wide = if (upper || text == text.uppercase()) 0.66f else 0.56f
        // Largo circa il 88% del quadro.
        var size = (0.88f * frameW / (wide * chars * frameH)).coerceIn(0.06f, 0.30f)
        val headPx = topF * frameH
        fun capH(s: Float) = 0.75f * s * frameH * lines
        // Spazio sopra la testa: le lettere (meno la parte coperta) devono stare sotto il margine alto.
        val room = headPx - TOP * frameH
        if (capH(size) * (1f - OVERLAP) > room) size = (room / (1f - OVERLAP) / (0.75f * frameH * lines)).coerceAtMost(size)
        return if (size >= 0.06f) {
            val center = headPx + capH(size) * (OVERLAP - 0.5f)
            Spot(0f, center / frameH - 0.5f, size, 0f, false)
        } else {
            // La testa tocca il bordo alto: testo grande in alto, si legge anche attraverso la persona.
            val s = (0.88f * frameW / (wide * chars * frameH)).coerceIn(0.06f, 0.22f)
            val center = TOP * frameH + capH(s) / 2f
            Spot(0f, center / frameH - 0.5f, s, 0.35f, true)
        }
    }
}
