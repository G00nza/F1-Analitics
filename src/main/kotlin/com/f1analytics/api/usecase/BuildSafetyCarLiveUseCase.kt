package com.f1analytics.api.usecase

import com.f1analytics.api.dto.SafetyCarDriverRecommendationDto
import com.f1analytics.api.dto.SafetyCarLiveDto
import com.f1analytics.api.usecase.SafetyCarScoring.computeScore
import com.f1analytics.api.usecase.SafetyCarScoring.detectScWindows
import com.f1analytics.api.usecase.SafetyCarScoring.hasNewTyresAvailable
import com.f1analytics.api.usecase.SafetyCarScoring.scoreToMessage
import com.f1analytics.core.domain.model.DriverEntry
import com.f1analytics.core.domain.model.DriverLiveData

import com.f1analytics.core.domain.model.LiveSessionState
import com.f1analytics.core.domain.port.SessionDriverRepository
import com.f1analytics.core.domain.port.StintRepository

class BuildSafetyCarLiveUseCase(
    private val stintRepository: StintRepository,
    private val sessionDriverRepository: SessionDriverRepository,
) {
    /**
     * Returns the current SC impact analysis for an active session.
     * Returns null if no SC is currently active in the live state.
     */
    suspend fun execute(liveState: LiveSessionState): SafetyCarLiveDto? {
        val windows = detectScWindows(liveState.raceControlMessages)
        // The active SC is the last window that has no endLap (not yet cleared)
        val activeWindow = windows.lastOrNull { it.endLap == null } ?: return null

        val sessionKey = liveState.sessionKey
        val currentLap = liveState.lapCount?.current
        val totalLaps  = liveState.lapCount?.total
        val lapsRemaining = if (currentLap != null && totalLaps != null) totalLaps - currentLap else null

        // Used compounds per driver: from DB race stints
        val raceStints  = stintRepository.findBySession(sessionKey)
        val usedByDriver = raceStints
            .filter { it.compound != null }
            .groupBy { it.driverNumber }
            .mapValues { (_, stints) -> stints.mapNotNull { it.compound }.toSet() }

        val driverMeta = sessionDriverRepository.findBySession(sessionKey).associateBy { it.number }

        // Build sorted list for gap-to-car-behind lookup
        val sortedByPos = liveState.driverData.entries
            .filter { it.value.position != null && !it.value.inPit }
            .sortedBy { it.value.position }

        // Map: position → interval (gap that driver has to the car in front = gap car-behind has to it)
        val intervalByDriver: Map<String, Double?> = sortedByPos.associate { (num, data) ->
            num to data.interval?.toDoubleOrNull()
        }

        // For each driver, gapToCarBehind = the interval of the driver directly behind
        fun gapToCarBehind(number: String): Double? {
            val pos = liveState.driverData[number]?.position ?: return null
            val carBehind = sortedByPos.firstOrNull { it.value.position == pos + 1 }
            return carBehind?.let { intervalByDriver[it.key] }
        }

        val drivers = liveState.driverData.entries.mapNotNull { (number, liveData) ->
            buildRecommendation(
                number        = number,
                liveData      = liveData,
                usedCompounds = usedByDriver[number] ?: emptySet(),
                driverMeta    = driverMeta,
                lapsRemaining = lapsRemaining,
                gapBehind     = gapToCarBehind(number)
            )
        }.sortedBy { it.position ?: Int.MAX_VALUE }

        return SafetyCarLiveDto(
            sessionKey = sessionKey,
            scLap      = activeWindow.startLap,
            drivers    = drivers
        )
    }

    private fun buildRecommendation(
        number: String,
        liveData: DriverLiveData,
        usedCompounds: Set<String>,
        driverMeta: Map<String, DriverEntry>,
        lapsRemaining: Int?,
        gapBehind: Double?
    ): SafetyCarDriverRecommendationDto {
        val currentLap = liveData.lapNumber
        val tyreAgeLaps = if (currentLap != null && liveData.stintLapStart != null)
            currentLap - liveData.stintLapStart + 1
        else null

        val allUsed = usedCompounds + setOfNotNull(liveData.currentCompound)
        val hasNew = hasNewTyresAvailable(allUsed)
        val score  = computeScore(tyreAgeLaps, lapsRemaining, gapBehind, hasNew)

        return SafetyCarDriverRecommendationDto(
            driverNumber           = number,
            driverCode             = driverMeta[number]?.code,
            team                   = driverMeta[number]?.team,
            position               = liveData.position,
            compound               = liveData.currentCompound,
            tyreAgeLaps            = tyreAgeLaps,
            hasNewTyresAvailable   = hasNew,
            gapToCarBehindSeconds  = gapBehind,
            lapsRemaining          = lapsRemaining,
            score                  = score,
            message                = scoreToMessage(score)
        )
    }
}
