package com.f1analytics.api.usecase

import com.f1analytics.api.dto.DriverWeekendRowDto
import com.f1analytics.api.dto.SessionSummaryEntryDto
import com.f1analytics.api.dto.WeekendSummaryDto
import com.f1analytics.core.domain.model.Race
import com.f1analytics.core.domain.port.LapRepository
import com.f1analytics.core.domain.port.SessionDriverRepository
import com.f1analytics.core.domain.port.SessionRepository

class BuildWeekendSummaryUseCase(
    private val sessionRepository: SessionRepository,
    private val lapRepository: LapRepository,
    private val sessionDriverRepository: SessionDriverRepository,
) {

    suspend fun execute(race: Race): WeekendSummaryDto {
        val sessions = sessionRepository.findByRace(race.key)
            .sortedBy { it.type.ordinal }

        // Only sessions that have recorded laps
        val sessionsWithData = sessions.mapNotNull { session ->
            val bestLaps = lapRepository.findBestLaps(session.key)
            if (bestLaps.isEmpty()) null else session to bestLaps
        }

        val availableSessionTypes = sessionsWithData.map { (session, _) -> session.type.name }

        // Collect driver info: prefer the first session that has the driver's lap data
        val driverInfoMap = mutableMapOf<String, com.f1analytics.core.domain.model.DriverEntry>()
        for ((session, bestLaps) in sessionsWithData) {
            sessionDriverRepository.findBySession(session.key).forEach { driver ->
                if (driver.number in bestLaps) driverInfoMap.putIfAbsent(driver.number, driver)
            }
        }

        // Build per-session ranking: sessionType -> (driverNumber -> entry without isBestPosition)
        val sessionRankings: Map<String, Map<String, SessionSummaryEntryDto>> =
            sessionsWithData.associate { (session, bestLaps) ->
                val ranked = bestLaps.entries
                    .filter { it.value.lapTimeMs != null }
                    .sortedBy { it.value.lapTimeMs!! }
                val leaderMs = ranked.firstOrNull()?.value?.lapTimeMs

                session.type.name to ranked.mapIndexed { idx, (driverNumber, lap) ->
                    driverNumber to SessionSummaryEntryDto(
                        position = idx + 1,
                        bestLapMs = lap.lapTimeMs,
                        gapToLeaderMs = if (idx == 0) null else lap.lapTimeMs!! - leaderMs!!,
                        isBestPosition = false
                    )
                }.toMap()
            }

        val allDriverNumbers = sessionsWithData.flatMap { (_, laps) -> laps.keys }.toSet()

        // Build final driver rows with isBestPosition resolved
        val driverRows = allDriverNumbers.map { driverNumber ->
            val sessionData = availableSessionTypes
                .mapNotNull { sessionType ->
                    sessionRankings[sessionType]?.get(driverNumber)?.let { sessionType to it }
                }
                .toMap()

            val bestPos = sessionData.values.minOfOrNull { it.position }
            val finalData = sessionData.mapValues { (_, e) ->
                e.copy(isBestPosition = e.position == bestPos)
            }

            val info = driverInfoMap[driverNumber]
            DriverWeekendRowDto(
                driverNumber = driverNumber,
                driverCode = info?.code ?: driverNumber,
                team = info?.team,
                sessionData = finalData
            )
        }.sortedBy { row -> row.sessionData.values.minOfOrNull { it.position } ?: Int.MAX_VALUE }

        return WeekendSummaryDto(
            raceKey = race.key,
            meetingName = race.name,
            year = race.year,
            sessions = availableSessionTypes,
            drivers = driverRows
        )
    }
}
