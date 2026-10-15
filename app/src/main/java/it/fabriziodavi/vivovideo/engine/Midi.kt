package it.fabriziodavi.vivovideo.engine

/**
 * Lettura dei file MIDI: tempo e battiti esatti (dalla mappa dei tempi del file),
 * senza bisogno di analizzare l'audio.
 */
object Midi {
    data class Info(val bpm: Float, val beatsUs: List<Long>, val durationUs: Long, val beatsPerBar: Int)

    fun looksLikeMidi(name: String?, mime: String?): Boolean {
        val n = name?.lowercase().orEmpty()
        return mime?.contains("midi") == true || n.endsWith(".mid") || n.endsWith(".midi") || n.endsWith(".kar") || n.endsWith(".rmi")
    }

    fun isMidi(bytes: ByteArray): Boolean =
        bytes.size > 14 && String(bytes, 0, 4, Charsets.ISO_8859_1) == "MThd"

    /** Restituisce null se il file non è un MIDI leggibile (o usa il tempo SMPTE). */
    fun parse(bytes: ByteArray): Info? = runCatching { parseOrThrow(bytes) }.getOrNull()

    private class Reader(val b: ByteArray, var p: Int = 0) {
        fun u8(): Int = b[p++].toInt() and 0xFF
        fun u16(): Int = (u8() shl 8) or u8()
        fun u32(): Int = (u16() shl 16) or u16()
        fun vlq(): Int { var v = 0; repeat(4) { val c = u8(); v = (v shl 7) or (c and 0x7F); if (c and 0x80 == 0) return v }; return v }
        fun tag(): String = String(b, p, 4, Charsets.ISO_8859_1).also { p += 4 }
    }

    private fun parseOrThrow(bytes: ByteArray): Info? {
        val r = Reader(bytes)
        if (r.tag() != "MThd") return null
        val hdrLen = r.u32()
        r.u16() // formato
        val tracks = r.u16()
        val division = r.u16()
        r.p += hdrLen - 6
        if (division and 0x8000 != 0) return null // tempo SMPTE: raro, si usa l'analisi audio
        val tempos = sortedMapOf(0L to 500_000) // tick → µs per semiminima
        var beatsPerBar = 4
        var endTick = 0L
        repeat(tracks) {
            if (r.p + 8 > bytes.size) return@repeat
            val id = r.tag()
            val len = r.u32()
            val end = r.p + len
            if (id != "MTrk") { r.p = end; return@repeat }
            var tick = 0L
            var running = 0
            while (r.p < end) {
                tick += r.vlq()
                var status = r.u8()
                if (status < 0x80) { r.p--; status = running } // running status
                when {
                    status == 0xFF -> {
                        val type = r.u8()
                        val l = r.vlq()
                        if (type == 0x51 && l >= 3) {
                            tempos[tick] = (r.u8() shl 16) or (r.u8() shl 8) or r.u8()
                            r.p += l - 3
                        } else if (type == 0x58 && l >= 2 && tick == 0L) {
                            val num = r.u8(); val den = 1 shl r.u8()
                            // In semiminime per battuta (6/8 → 3).
                            beatsPerBar = maxOf(1, num * 4 / den)
                            r.p += l - 2
                        } else r.p += l
                        if (type == 0x2F) break
                    }
                    status == 0xF0 || status == 0xF7 -> r.p += r.vlq()
                    else -> {
                        running = status
                        val kind = status and 0xF0
                        r.p += if (kind == 0xC0 || kind == 0xD0) 1 else 2
                    }
                }
            }
            if (tick > endTick) endTick = tick
            r.p = end
        }
        if (endTick <= 0) return null
        // Tick → µs con la mappa dei tempi.
        val entries = tempos.entries.toList()
        fun usAt(t: Long): Long {
            var us = 0.0
            for (i in entries.indices) {
                val (start, tempo) = entries[i]
                if (start >= t) break
                val stop = minOf(t, entries.getOrNull(i + 1)?.key ?: Long.MAX_VALUE)
                us += (stop - start).toDouble() * tempo / division
            }
            return us.toLong()
        }
        val beats = (0..endTick / division).map { usAt(it * division) }
        val bpm = 60_000_000f / tempos[0L]!!
        return Info(bpm, beats, usAt(endTick), beatsPerBar)
    }
}
