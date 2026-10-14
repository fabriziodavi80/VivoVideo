package it.fabriziodavi.vivovideo

import androidx.media3.common.C
import androidx.media3.common.audio.SpeedProvider
import kotlin.math.PI
import kotlin.math.cos

/** Modalità di velocità della prova tecnica. */
enum class SpeedMode(val label: String) {
    NORMAL("1×"),
    SLOW("0,5×"),
    FAST("2×"),
    RAMP("Rampa");

    /** null = velocità invariata. */
    fun provider(durationUs: Long): SpeedProvider? = when (this) {
        NORMAL -> null
        SLOW -> ConstantSpeed(0.5f)
        FAST -> ConstantSpeed(2f)
        RAMP -> RampSpeedProvider(durationUs, fast = 2f, slow = 0.35f)
    }
}

class ConstantSpeed(private val speed: Float) : SpeedProvider {
    override fun getSpeed(timeUs: Long): Float = speed
    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
}

/**
 * Speed ramp "veloce → lento → veloce": la velocità segue una curva a coseno
 * sul tempo del video originale, aggiornata ogni 40 ms così il passaggio è fluido.
 */
class RampSpeedProvider(
    private val durationUs: Long,
    private val fast: Float,
    private val slow: Float,
) : SpeedProvider {
    private val stepUs = 40_000L

    override fun getSpeed(timeUs: Long): Float {
        if (durationUs <= 0) return 1f
        val segmentStart = (timeUs.coerceAtLeast(0) / stepUs) * stepUs
        val t = segmentStart.coerceAtMost(durationUs).toDouble() / durationUs
        val c = 0.5 + 0.5 * cos(2 * PI * t) // 1 ai bordi, 0 al centro
        return (slow + (fast - slow) * c).toFloat()
    }

    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long {
        val next = (timeUs.coerceAtLeast(0) / stepUs + 1) * stepUs
        return if (next >= durationUs) C.TIME_UNSET else next
    }
}
