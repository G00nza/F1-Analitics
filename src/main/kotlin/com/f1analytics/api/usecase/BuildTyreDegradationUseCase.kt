package com.f1analytics.api.usecase

import com.f1analytics.api.dto.TyreDegradationDto
import com.f1analytics.api.dto.TyreLongRunDto
import com.f1analytics.api.dto.TyreShortRunDto
import com.f1analytics.api.usecase.charts.TyreDegradationAnalyzer
import com.f1analytics.core.domain.port.LapRepository
import com.f1analytics.core.domain.port.RaceControlRepository
import com.f1analytics.core.domain.port.SessionDriverRepository
import com.f1analytics.core.domain.port.StintRepository

class BuildTyreDegradationUseCase(
    private val stintRepository: StintRepository,
    private val lapRepository: LapRepository,
    private val sessionDriverRepository: SessionDriverRepository,
    private val raceControlRepository: RaceControlRepository,
) {

    suspend fun execute(sessionKey: Int): TyreDegradationDto {
        val stints = stintRepository.findBySession(sessionKey)

        if (stints.isEmpty()) {
            return TyreDegradationDto(sessionKey, hasStintData = false, longRuns = emptyList(), shortRuns = emptyList())
        }

        val flaggedLapNumbers = raceControlRepository.findBySession(sessionKey)
            .filter { it.flag in TyreDegradationAnalyzer.SLOW_FLAGS }
            .mapNotNull { it.lap }
            .toSet()

        val allLaps = lapRepository.findBySession(sessionKey)
        val drivers = sessionDriverRepository.findBySession(sessionKey).associateBy { it.number }
        val lapsByDriver = allLaps.groupBy { it.driverNumber }

        val longRuns = mutableListOf<TyreLongRunDto>()
        val shortRuns = mutableListOf<TyreShortRunDto>()

        for (stint in stints) {
            val driverLaps = lapsByDriver[stint.driverNumber] ?: continue
            val stintLaps = driverLaps.filter { lap ->
                (stint.lapStart ?: 0) <= lap.lapNumber &&
                lap.lapNumber <= (stint.lapEnd ?: Int.MAX_VALUE)
            }

            val valid = TyreDegradationAnalyzer.validLaps(stintLaps, flaggedLapNumbers).sortedBy { it.lapNumber }
            val driver = drivers[stint.driverNumber]

            if (TyreDegradationAnalyzer.isLongRun(valid.size)) {
                val firstLapMs = valid.first().lapTimeMs!!
                val lastLapMs = valid.last().lapTimeMs!!
                val avgLapMs = valid.map { it.lapTimeMs!! }.average().toInt()
                val degPerLapMs = (lastLapMs - firstLapMs).toDouble() / (valid.size - 1)

                longRuns += TyreLongRunDto(
                    driverNumber = stint.driverNumber,
                    driverCode = driver?.code ?: stint.driverNumber,
                    team = driver?.team,
                    compound = stint.compound,
                    stintNumber = stint.stintNumber,
                    lapCount = valid.size,
                    firstLapMs = firstLapMs,
                    lastLapMs = lastLapMs,
                    avgLapMs = avgLapMs,
                    degPerLapMs = degPerLapMs
                )
            } else if (valid.isNotEmpty()) {
                shortRuns += TyreShortRunDto(
                    driverNumber = stint.driverNumber,
                    driverCode = driver?.code ?: stint.driverNumber,
                    compound = stint.compound,
                    stintNumber = stint.stintNumber,
                    lapCount = valid.size
                )
            }
        }

        return TyreDegradationDto(sessionKey, hasStintData = true, longRuns = longRuns, shortRuns = shortRuns)
    }
}
