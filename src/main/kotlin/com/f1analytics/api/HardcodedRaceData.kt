package com.f1analytics.api

import com.f1analytics.api.dto.*

/**
 * Deterministic hardcoded race data for demo/development (30-lap Bahrain simulation).
 * All endpoints using this return the same data regardless of session key.
 */
object HardcodedRaceData {

    private data class DriverCfg(
        val number: String, val code: String, val color: String,
        val pitLap: Int, val stint1: String, val stint2: String, val baseMs: Int
    )

    private val DRIVERS = listOf(
        DriverCfg("1",  "VER", "#3671C6", 15, "MEDIUM", "SOFT",   97200),
        DriverCfg("16", "LEC", "#E8002D", 12, "MEDIUM", "SOFT",   97400),
        DriverCfg("4",  "NOR", "#FF8000", 14, "HARD",   "SOFT",   97500),
        DriverCfg("44", "HAM", "#27F4D2", 18, "MEDIUM", "SOFT",   97700),
    )

    private const val TOTAL_LAPS = 30

    // Small deterministic noise: [-200ms, +200ms]
    private fun noiseMs(dIdx: Int, lap: Int): Int = ((dIdx * 37 + lap * 53) % 400) - 200

    private fun degradRate(compound: String) = when (compound) {
        "SOFT" -> 60; "HARD" -> 40; else -> 50
    }

    private fun compoundOffset(compound: String) = when (compound) {
        "SOFT" -> -1500; "HARD" -> 500; else -> 0
    }

    private fun rawLaps(): List<LapDataDto> {
        return DRIVERS.flatMapIndexed { di, cfg ->
            var stintLap = 0
            (1..TOTAL_LAPS).map { lap ->
                val pitIn  = lap == cfg.pitLap
                val pitOut = lap == cfg.pitLap + 1
                if (pitOut) stintLap = 0
                stintLap++
                val stintNum = if (lap <= cfg.pitLap) 1 else 2
                val compound = if (stintNum == 1) cfg.stint1 else cfg.stint2
                val lapTimeMs = when {
                    pitIn  -> null
                    pitOut -> cfg.baseMs + compoundOffset(compound) + 3500 + noiseMs(di, lap)
                    else   -> cfg.baseMs + compoundOffset(compound) + (stintLap - 1) * degradRate(compound) + noiseMs(di, lap)
                }
                LapDataDto(
                    driverNumber   = cfg.number,
                    driverCode     = cfg.code,
                    teamColor      = cfg.color,
                    lapNumber      = lap,
                    lapTimeMs      = lapTimeMs,
                    pitOutLap      = pitOut,
                    pitInLap       = pitIn,
                    isPersonalBest = false,
                    compound       = compound,
                    stintNumber    = stintNum,
                    gapToLeaderMs  = null
                )
            }
        }
    }

    fun laps(): List<LapDataDto> {
        val raw = rawLaps().toMutableList()

        // Mark personal bests per driver
        DRIVERS.forEach { cfg ->
            val best = raw
                .filter { it.driverNumber == cfg.number && it.lapTimeMs != null && !it.pitOutLap }
                .minByOrNull { it.lapTimeMs!! }
            if (best != null) {
                val idx = raw.indexOf(best)
                raw[idx] = best.copy(isPersonalBest = true)
            }
        }

        // Compute cumulative gaps relative to leader ("1")
        val cum = DRIVERS.associate { it.number to 0L }.toMutableMap()
        val result = mutableListOf<LapDataDto>()

        (1..TOTAL_LAPS).forEach { lapNum ->
            val lapGroup = raw.filter { it.lapNumber == lapNum }
            lapGroup.forEach { lap ->
                val t = if (lap.pitInLap) 25_000L else lap.lapTimeMs?.toLong() ?: 0L
                cum[lap.driverNumber] = (cum[lap.driverNumber] ?: 0L) + t
            }
            val leaderCum = cum["1"] ?: 0L
            lapGroup.forEach { lap ->
                val driverCum = cum[lap.driverNumber] ?: 0L
                val gap = if (lap.driverNumber == "1") null
                          else (driverCum - leaderCum).toInt().coerceAtLeast(0)
                result.add(lap.copy(gapToLeaderMs = gap))
            }
        }

        return result
    }

    fun stints(): List<StintDataDto> = DRIVERS.flatMap { cfg ->
        listOf(
            StintDataDto(cfg.number, cfg.code, 1, cfg.stint1, 1,             cfg.pitLap - 1, true),
            StintDataDto(cfg.number, cfg.code, 2, cfg.stint2, cfg.pitLap + 1, TOTAL_LAPS,    true)
        )
    }

    fun positions(): List<RacePositionDto> {
        val raw = rawLaps()
        val cum = DRIVERS.associate { it.number to 0L }.toMutableMap()
        val result = mutableListOf<RacePositionDto>()

        (1..TOTAL_LAPS).forEach { lapNum ->
            raw.filter { it.lapNumber == lapNum }.forEach { lap ->
                val t = if (lap.pitInLap) 25_000L else lap.lapTimeMs?.toLong() ?: 0L
                cum[lap.driverNumber] = (cum[lap.driverNumber] ?: 0L) + t
            }
            cum.entries.sortedBy { it.value }.forEachIndexed { i, (num, _) ->
                val d = DRIVERS.first { it.number == num }
                result.add(RacePositionDto(num, d.code, d.color, lapNum, i + 1))
            }
        }
        return result
    }

    val WEEKEND = WeekendInfoDto(
        meetingName = "Bahrain Grand Prix",
        circuitName = "Bahrain International Circuit",
        year        = 2024,
        sessions    = listOf(
            WeekendSessionDto(9149, "Practice 1",  "FP1",        "FINISHED"),
            WeekendSessionDto(9150, "Practice 2",  "FP2",        "FINISHED"),
            WeekendSessionDto(9151, "Practice 3",  "FP3",        "FINISHED"),
            WeekendSessionDto(9152, "Qualifying",  "QUALIFYING", "FINISHED"),
            WeekendSessionDto(9153, "Race",        "RACE",       "FINISHED"),
        )
    )
}
