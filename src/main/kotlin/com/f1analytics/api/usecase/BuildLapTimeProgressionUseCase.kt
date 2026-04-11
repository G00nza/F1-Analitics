package com.f1analytics.api.usecase

import com.f1analytics.api.dto.DriverProgressionRowDto
import com.f1analytics.api.dto.LapTimeProgressionDto
import com.f1analytics.core.domain.model.DriverEntry
import com.f1analytics.core.domain.model.Race
import com.f1analytics.core.domain.model.SessionType
import com.f1analytics.core.domain.port.LapRepository
import com.f1analytics.core.domain.port.SessionDriverRepository
import com.f1analytics.core.domain.port.SessionRepository

class BuildLapTimeProgressionUseCase(
    private val sessionRepository: SessionRepository,
    private val lapRepository: LapRepository,
    private val sessionDriverRepository: SessionDriverRepository,
) {

    companion object {
        const val FP_DATA_WARNING =
            "FP lap times may mix qualifying simulations and race simulations. Interpretation requires manual context."
    }

    suspend fun execute(race: Race): LapTimeProgressionDto {
        val sessions = sessionRepository.findByRace(race.key)
            .sortedBy { it.type.ordinal }

        val sessionsWithData = sessions.mapNotNull { session ->
            val bestLaps = lapRepository.findBestLaps(session.key)
            if (bestLaps.isEmpty()) null else session to bestLaps
        }

        val availableSessionTypes = sessionsWithData.map { (session, _) -> session.type.name }

        val driverInfoMap = mutableMapOf<String, DriverEntry>()
        for ((session, bestLaps) in sessionsWithData) {
            sessionDriverRepository.findBySession(session.key).forEach { driver ->
                if (driver.number in bestLaps) driverInfoMap.putIfAbsent(driver.number, driver)
            }
        }

        val sessionBestLaps: Map<String, Map<String, Int?>> =
            sessionsWithData.associate { (session, bestLaps) ->
                session.type.name to bestLaps.mapValues { (_, lap) -> lap.lapTimeMs }
            }

        val allDriverNumbers = sessionsWithData.flatMap { (_, laps) -> laps.keys }.toSet()

        val drivers = allDriverNumbers.map { driverNumber ->
            val lapTimes: Map<String, Int?> = availableSessionTypes.associateWith { sessionType ->
                sessionBestLaps[sessionType]?.get(driverNumber)
            }

            val fp1Time = lapTimes[SessionType.FP1.name]
            val qualiTime = lapTimes[SessionType.QUALIFYING.name]
            val delta = if (fp1Time != null && qualiTime != null) qualiTime - fp1Time else null

            val info = driverInfoMap[driverNumber]
            DriverProgressionRowDto(
                driverNumber = driverNumber,
                driverCode = info?.code ?: driverNumber,
                team = info?.team,
                lapTimes = lapTimes,
                deltaFp1ToQualiMs = delta
            )
        }.sortedBy { row -> row.lapTimes.values.filterNotNull().minOrNull() ?: Int.MAX_VALUE }

        return LapTimeProgressionDto(
            raceKey = race.key,
            meetingName = race.name,
            year = race.year,
            sessions = availableSessionTypes,
            fpDataWarning = FP_DATA_WARNING,
            drivers = drivers
        )
    }
}
