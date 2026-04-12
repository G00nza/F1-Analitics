package com.f1analytics.api.usecase.charts

import com.f1analytics.core.domain.model.Lap

object TyreDegradationAnalyzer {

    const val LONG_RUN_MIN_LAPS = 5

    val SLOW_FLAGS = setOf("SC", "VSC", "YELLOW", "DOUBLE YELLOW", "RED")

    /** Relative lap time penalty per lap compared to SOFT (in ms). */
    val COMPOUND_BASE_TIME_MS = mapOf(
        "SOFT"   to 0.0,
        "MEDIUM" to 500.0,
        "HARD"   to 1500.0,
    )

    fun validLaps(laps: List<Lap>, flaggedLapNumbers: Set<Int> = emptySet()): List<Lap> {
        val candidates = laps.filter {
            !it.pitOutLap && !it.pitInLap && it.lapTimeMs != null && it.lapNumber !in flaggedLapNumbers
        }
        val fastest = candidates.minOfOrNull { it.lapTimeMs!! } ?: return emptyList()
        return candidates.filter { it.lapTimeMs!!.toDouble() <= fastest * 1.07 }
    }

    fun isLongRun(validLapCount: Int): Boolean = validLapCount > LONG_RUN_MIN_LAPS

    /**
     * Calculates the average degradation rate in ms/lap for a list of valid laps.
     * Returns null if fewer than 2 laps are provided (not enough data).
     * Negative rates (improving lap times) are capped at 0.
     */
    fun calculateDegRate(validLaps: List<Lap>): Double? {
        if (validLaps.size < 2) return null
        val sorted = validLaps.sortedBy { it.lapNumber }
        val rate = (sorted.last().lapTimeMs!! - sorted.first().lapTimeMs!!).toDouble() / (sorted.size - 1)
        return maxOf(0.0, rate)
    }
}
