package com.f1analytics.api.usecase

import com.f1analytics.api.dto.DriverRaceStintDto
import com.f1analytics.api.dto.DriverRaceStrategyDto
import com.f1analytics.api.dto.PostRaceStrategyDto
import com.f1analytics.api.dto.ScBeneficiaryDto
import com.f1analytics.api.dto.StrategyComparisonDto
import com.f1analytics.api.dto.StrategyGroupDto
import com.f1analytics.api.dto.UndercutResultDto
import com.f1analytics.api.usecase.SafetyCarScoring.detectScWindows
import com.f1analytics.core.domain.port.PositionRepository
import com.f1analytics.core.domain.port.RaceControlRepository
import com.f1analytics.core.domain.port.RaceRepository
import com.f1analytics.core.domain.port.SessionDriverRepository
import com.f1analytics.core.domain.port.SessionRepository
import com.f1analytics.core.domain.port.StrategyAlertRepository
import com.f1analytics.core.domain.port.StintRepository

class BuildPostRaceStrategyUseCase(
    private val sessionRepository: SessionRepository,
    private val raceRepository: RaceRepository,
    private val stintRepository: StintRepository,
    private val sessionDriverRepository: SessionDriverRepository,
    private val positionRepository: PositionRepository,
    private val raceControlRepository: RaceControlRepository,
    private val strategyAlertRepository: StrategyAlertRepository,
) {
    suspend fun execute(sessionKey: Int): PostRaceStrategyDto {
        val session    = sessionRepository.findByKey(sessionKey)
        val raceName   = session?.raceKey?.let { raceRepository.findByKey(it)?.name }
        val driverMeta = sessionDriverRepository.findBySession(sessionKey).associateBy { it.number }
        val stints     = stintRepository.findBySession(sessionKey)
        val finalPositions = positionRepository.findLatestPositions(sessionKey)

        // ── Per-driver strategies ─────────────────────────────────────────
        val drivers = driverMeta.keys.map { number ->
            val driverStints = stints
                .filter { it.driverNumber == number }
                .sortedBy { it.stintNumber }

            val stintDtos = driverStints.map { s ->
                val laps = if (s.lapStart != null && s.lapEnd != null) s.lapEnd - s.lapStart + 1 else null
                DriverRaceStintDto(compound = s.compound, lapStart = s.lapStart, lapEnd = s.lapEnd, laps = laps)
            }

            DriverRaceStrategyDto(
                driverNumber  = number,
                driverCode    = driverMeta[number]?.code,
                team          = driverMeta[number]?.team,
                finalPosition = finalPositions[number]?.position,
                stints        = stintDtos,
                stops         = (driverStints.size - 1).coerceAtLeast(0)
            )
        }.sortedWith(compareBy(nullsLast()) { it.finalPosition })

        // ── Strategy comparison ───────────────────────────────────────────
        val strategyComparison = buildStrategyComparison(drivers)

        // ── Undercut results ──────────────────────────────────────────────
        val alerts = strategyAlertRepository.findBySession(sessionKey)
            .filter { it.type == "UNDERCUT" }

        val undercutResults = alerts.map { alert ->
            UndercutResultDto(
                lap                       = alert.lap,
                instigatorCode            = driverMeta[alert.instigatorNumber]?.code,
                rivalCode                 = driverMeta[alert.rivalNumber]?.code,
                predictedOutcome          = alert.predictedOutcome,
                instigatorFinalPosition   = finalPositions[alert.instigatorNumber]?.position,
                rivalFinalPosition        = finalPositions[alert.rivalNumber]?.position
            )
        }

        // ── SC beneficiaries ──────────────────────────────────────────────
        val rcMessages = raceControlRepository.findBySession(sessionKey)
        val scWindows  = detectScWindows(rcMessages)

        val scBeneficiaries = scWindows.flatMap { window ->
            val positionsAtSc = positionRepository.findPositionAtTimestamp(sessionKey, window.startTimestamp)

            driverMeta.keys.mapNotNull { number ->
                val atSc    = positionsAtSc[number] ?: return@mapNotNull null
                val atEnd   = finalPositions[number]?.position ?: return@mapNotNull null
                val gained  = atSc - atEnd   // positive = gained positions
                if (gained <= 0) return@mapNotNull null   // only include actual gainers

                ScBeneficiaryDto(
                    scLap           = window.startLap,
                    driverCode      = driverMeta[number]?.code,
                    positionAtSc    = atSc,
                    finalPosition   = atEnd,
                    positionsGained = gained
                )
            }.sortedByDescending { it.positionsGained }
        }

        return PostRaceStrategyDto(
            sessionKey          = sessionKey,
            sessionName         = session?.name,
            raceName            = raceName,
            drivers             = drivers,
            strategyComparison  = strategyComparison,
            undercutResults     = undercutResults,
            scBeneficiaries     = scBeneficiaries
        )
    }

    private fun buildStrategyComparison(drivers: List<DriverRaceStrategyDto>): StrategyComparisonDto {
        fun group(stopCount: IntRange): StrategyGroupDto? {
            val matching = drivers.filter { it.stops in stopCount }
            if (matching.isEmpty()) return null
            val avg = matching.mapNotNull { it.finalPosition }.let {
                if (it.isEmpty()) null else it.average()
            }
            return StrategyGroupDto(
                driverCount        = matching.size,
                avgFinishPosition  = avg,
                drivers            = matching.mapNotNull { it.driverCode }
            )
        }
        return StrategyComparisonDto(
            oneStop      = group(1..1),
            twoStop      = group(2..2),
            threeOrMore  = group(3..Int.MAX_VALUE)
        )
    }
}
