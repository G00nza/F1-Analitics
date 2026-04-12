package com.f1analytics.api.usecase.charts

import com.f1analytics.core.domain.model.Lap

object TyreDegradationAnalyzer {

    const val LONG_RUN_MIN_LAPS = 5

    val SLOW_FLAGS = setOf("SC", "VSC", "YELLOW", "DOUBLE YELLOW", "RED")

    fun validLaps(laps: List<Lap>, flaggedLapNumbers: Set<Int> = emptySet()): List<Lap> {
        val candidates = laps.filter {
            !it.pitOutLap && !it.pitInLap && it.lapTimeMs != null && it.lapNumber !in flaggedLapNumbers
        }
        val fastest = candidates.minOfOrNull { it.lapTimeMs!! } ?: return emptyList()
        return candidates.filter { it.lapTimeMs!!.toDouble() <= fastest * 1.07 }
    }

    fun isLongRun(validLapCount: Int): Boolean = validLapCount > LONG_RUN_MIN_LAPS
}
