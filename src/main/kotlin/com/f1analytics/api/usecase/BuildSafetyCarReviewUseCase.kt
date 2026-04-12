package com.f1analytics.api.usecase

import com.f1analytics.api.dto.SafetyCarDriverResultDto
import com.f1analytics.api.dto.SafetyCarReviewDto
import com.f1analytics.api.dto.SafetyCarReviewEventDto
import com.f1analytics.api.usecase.SafetyCarScoring.ScWindow
import com.f1analytics.api.usecase.SafetyCarScoring.computeScore
import com.f1analytics.api.usecase.SafetyCarScoring.detectScWindows
import com.f1analytics.api.usecase.SafetyCarScoring.hasNewTyresAvailable
import com.f1analytics.api.usecase.SafetyCarScoring.scoreToMessage
import com.f1analytics.core.domain.model.Stint
import com.f1analytics.core.domain.port.PositionRepository
import com.f1analytics.core.domain.port.RaceControlRepository
import com.f1analytics.core.domain.port.SessionDriverRepository
import com.f1analytics.core.domain.port.StintRepository

class BuildSafetyCarReviewUseCase(
    private val raceControlRepository: RaceControlRepository,
    private val stintRepository: StintRepository,
    private val sessionDriverRepository: SessionDriverRepository,
    private val positionRepository: PositionRepository,
) {
    suspend fun execute(sessionKey: Int): SafetyCarReviewDto {
        val rcMessages = raceControlRepository.findBySession(sessionKey)
        val windows    = detectScWindows(rcMessages)

        if (windows.isEmpty()) return SafetyCarReviewDto(sessionKey = sessionKey, events = emptyList())

        val stints     = stintRepository.findBySession(sessionKey)
        val driverMeta = sessionDriverRepository.findBySession(sessionKey).associateBy { it.number }
        val finalPositions = positionRepository.findLatestPositions(sessionKey)

        val events = windows.map { window ->
            buildEvent(window, stints, driverMeta, finalPositions, sessionKey)
        }

        return SafetyCarReviewDto(sessionKey = sessionKey, events = events)
    }

    private suspend fun buildEvent(
        window: ScWindow,
        stints: List<Stint>,
        driverMeta: Map<String, com.f1analytics.core.domain.model.DriverEntry>,
        finalPositions: Map<String, com.f1analytics.core.domain.port.DriverPositionSnapshot>,
        sessionKey: Int
    ): SafetyCarReviewEventDto {
        val positionsAtSc = positionRepository.findPositionAtTimestamp(sessionKey, window.startTimestamp)

        val lapsRemaining: Int? = null   // not available from DB without total lap count

        val drivers = driverMeta.keys.map { number ->
            buildDriverResult(
                number        = number,
                window        = window,
                stints        = stints.filter { it.driverNumber == number },
                driverMeta    = driverMeta,
                positionAtSc  = positionsAtSc[number],
                finalPosition = finalPositions[number]?.position,
                lapsRemaining = lapsRemaining
            )
        }.sortedWith(compareBy(nullsLast()) { it.positionAtSc })

        return SafetyCarReviewEventDto(
            scLap    = window.startLap,
            scEndLap = window.endLap,
            drivers  = drivers
        )
    }

    private fun buildDriverResult(
        number: String,
        window: ScWindow,
        stints: List<Stint>,
        driverMeta: Map<String, com.f1analytics.core.domain.model.DriverEntry>,
        positionAtSc: Int?,
        finalPosition: Int?,
        lapsRemaining: Int?
    ): SafetyCarDriverResultDto {
        val scLap = window.startLap

        // Active stint at SC deployment: highest stintNumber with lapStart <= scLap
        val activeStint = stints
            .filter { it.lapStart != null && scLap != null && it.lapStart <= scLap }
            .maxByOrNull { it.stintNumber }

        val tyreAgeLaps = if (activeStint?.lapStart != null && scLap != null)
            scLap - activeStint.lapStart + 1
        else null

        // Compounds used before (and including) the SC lap
        val usedCompounds = stints
            .filter { it.lapStart != null && scLap != null && it.lapStart <= scLap }
            .mapNotNull { it.compound }
            .toSet()

        val hasNew = hasNewTyresAvailable(usedCompounds)
        val score  = computeScore(tyreAgeLaps, lapsRemaining, null, hasNew)

        // Pitted during SC window: a new stint started within [scLap, scEndLap]
        val scEnd = window.endLap
        val pittedDuringSc = stints.any { s ->
            s.lapStart != null &&
            scLap != null &&
            s.lapStart > scLap &&                              // must be after SC
            (scEnd == null || s.lapStart <= scEnd)             // within SC window
        }

        val capitalizedCorrectly = if (positionAtSc != null && finalPosition != null)
            finalPosition <= positionAtSc
        else null

        val meta = driverMeta[number]
        return SafetyCarDriverResultDto(
            driverNumber         = number,
            driverCode           = meta?.code,
            team                 = meta?.team,
            compound             = activeStint?.compound,
            tyreAgeLaps          = tyreAgeLaps,
            hasNewTyresAvailable = hasNew,
            score                = score,
            message              = scoreToMessage(score),
            pittedDuringSc       = pittedDuringSc,
            positionAtSc         = positionAtSc,
            finalPosition        = finalPosition,
            capitalizedCorrectly = capitalizedCorrectly
        )
    }
}
