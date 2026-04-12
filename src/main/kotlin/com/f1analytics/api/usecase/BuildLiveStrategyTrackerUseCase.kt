package com.f1analytics.api.usecase

import com.f1analytics.api.dto.DriverTrackerRowDto
import com.f1analytics.api.dto.LiveStrategyTrackerDto
import com.f1analytics.api.dto.PitWindowDto
import com.f1analytics.api.usecase.charts.TyreDegradationAnalyzer
import com.f1analytics.core.domain.model.DriverEntry
import com.f1analytics.core.domain.model.DriverLiveData
import com.f1analytics.core.domain.model.Lap
import com.f1analytics.core.domain.model.LiveSessionState
import com.f1analytics.core.domain.model.Session
import com.f1analytics.core.domain.model.SessionType
import com.f1analytics.core.domain.port.LapRepository
import com.f1analytics.core.domain.port.RaceControlRepository
import com.f1analytics.core.domain.port.SessionDriverRepository
import com.f1analytics.core.domain.port.SessionRepository
import com.f1analytics.core.domain.port.StintRepository
import kotlin.math.abs
import kotlin.math.roundToInt

class BuildLiveStrategyTrackerUseCase(
    private val sessionRepository: SessionRepository,
    private val stintRepository: StintRepository,
    private val lapRepository: LapRepository,
    private val sessionDriverRepository: SessionDriverRepository,
    private val raceControlRepository: RaceControlRepository,
) {

    companion object {
        private val DRY_COMPOUNDS = setOf("SOFT", "MEDIUM", "HARD")
        private val FP_SESSION_TYPES = setOf(SessionType.FP2, SessionType.FP3)
        private const val PIT_WINDOW_RADIUS = 2
    }

    suspend fun execute(liveState: LiveSessionState, totalLaps: Int): LiveStrategyTrackerDto {
        val sessionKey = liveState.sessionKey
        val currentLap = liveState.lapCount?.current

        val raceKey = sessionRepository.findByKey(sessionKey)?.raceKey
        val fpRates = if (raceKey != null) computeFpRates(raceKey, liveState) else emptyMap()

        val raceLaps = lapRepository.findBySession(sessionKey).groupBy { it.driverNumber }
        val raceFlags = raceControlRepository.findBySession(sessionKey)
            .filter { it.flag in TyreDegradationAnalyzer.SLOW_FLAGS }
            .mapNotNull { it.lap }
            .toSet()

        val drivers = liveState.drivers.entries.mapNotNull { (number, entry) ->
            val liveData = liveState.driverData[number] ?: return@mapNotNull null
            buildRow(number, entry, liveData, fpRates, raceLaps, raceFlags, currentLap, totalLaps)
        }.sortedBy { it.position ?: Int.MAX_VALUE }

        return LiveStrategyTrackerDto(
            sessionKey = sessionKey,
            currentLap = currentLap,
            totalLaps = totalLaps,
            drivers = drivers
        )
    }

    private fun buildRow(
        number: String,
        entry: DriverEntry,
        liveData: DriverLiveData,
        fpRates: Map<String, Map<String, Double>>,
        raceLaps: Map<String, List<Lap>>,
        raceFlags: Set<Int>,
        currentLap: Int?,
        totalLaps: Int
    ): DriverTrackerRowDto {
        val stintLaps = if (liveData.lapNumber != null && liveData.stintLapStart != null)
            liveData.lapNumber - liveData.stintLapStart + 1
        else null

        val noWindow = DriverTrackerRowDto(
            driverNumber = number,
            driverCode = entry.code,
            team = entry.team,
            position = liveData.position,
            compound = liveData.currentCompound,
            stintLaps = stintLaps,
            inPit = liveData.inPit,
            fpWindow = null,
            realWindow = null,
            isOverdue = false,
            windowsDiverge = false
        )

        val compound = liveData.currentCompound ?: return noWindow
        val stintLapStart = liveData.stintLapStart ?: return noWindow
        if (liveData.inPit || currentLap == null) return noWindow

        val driverFpRates = fpRates[number] ?: emptyMap()

        val fpOptimalLap = computeOptimalPitLap(compound, driverFpRates, stintLapStart, totalLaps)
        val fpWindow = fpOptimalLap?.let { pitWindow(it, totalLaps) }

        val stintRaceLaps = raceLaps[number]
            ?.filter { it.lapNumber in stintLapStart..currentLap }
            ?: emptyList()
        val validStintLaps = TyreDegradationAnalyzer.validLaps(stintRaceLaps, raceFlags).sortedBy { it.lapNumber }

        val realRate = if (TyreDegradationAnalyzer.isLongRun(validStintLaps.size)) {
            (validStintLaps.last().lapTimeMs!! - validStintLaps.first().lapTimeMs!!).toDouble() /
                (validStintLaps.size - 1)
        } else null

        val realOptimalLap = if (realRate != null) {
            val bestAltRate = driverFpRates.entries
                .filter { it.key != compound }
                .minByOrNull { it.value }?.value
            if (bestAltRate != null) {
                val remaining = totalLaps - stintLapStart + 1
                val total = realRate + bestAltRate
                val stintLength = if (total > 0)
                    (remaining * bestAltRate / total).roundToInt().coerceIn(1, remaining)
                else remaining / 2
                stintLapStart + stintLength - 1
            } else null
        } else null

        val realWindow = realOptimalLap?.let { pitWindow(it, totalLaps) }

        val refWindow = realWindow ?: fpWindow
        val isOverdue = refWindow != null && currentLap > refWindow.lapTo

        val windowsDiverge = fpOptimalLap != null && realOptimalLap != null &&
            abs(fpOptimalLap - realOptimalLap) > 2

        return DriverTrackerRowDto(
            driverNumber = number,
            driverCode = entry.code,
            team = entry.team,
            position = liveData.position,
            compound = compound,
            stintLaps = stintLaps,
            inPit = liveData.inPit,
            fpWindow = fpWindow,
            realWindow = realWindow,
            isOverdue = isOverdue,
            windowsDiverge = windowsDiverge
        )
    }

    private fun computeOptimalPitLap(
        compound: String,
        rates: Map<String, Double>,
        stintLapStart: Int,
        totalLaps: Int
    ): Int? {
        val currentRate = rates[compound] ?: return null
        val bestAltRate = rates.entries
            .filter { it.key != compound }
            .minByOrNull { it.value }?.value ?: return null
        val remaining = totalLaps - stintLapStart + 1
        val total = currentRate + bestAltRate
        val stintLength = if (total > 0)
            (remaining * bestAltRate / total).roundToInt().coerceIn(1, remaining)
        else remaining / 2
        return stintLapStart + stintLength - 1
    }

    private fun pitWindow(optimalLap: Int, totalLaps: Int) = PitWindowDto(
        lapFrom = (optimalLap - PIT_WINDOW_RADIUS).coerceAtLeast(1),
        lapTo = (optimalLap + PIT_WINDOW_RADIUS).coerceAtMost(totalLaps - 1)
    )

    private suspend fun computeFpRates(
        raceKey: Int,
        liveState: LiveSessionState
    ): Map<String, Map<String, Double>> {
        val allSessions = sessionRepository.findByRace(raceKey)
        val fpSessions = allSessions.filter { it.type in FP_SESSION_TYPES }
        if (fpSessions.isEmpty()) return emptyMap()

        val allDrivers = liveState.drivers.values.toList()
        val rawRates = collectRawDegRates(fpSessions)

        return allDrivers.associate { driver ->
            driver.number to DRY_COMPOUNDS.mapNotNull { compound ->
                resolveRate(driver.number, compound, rawRates, allDrivers)?.let { compound to it }
            }.toMap()
        }
    }

    private suspend fun collectRawDegRates(fpSessions: List<Session>): Map<String, Map<String, Double>> {
        val accumulator = mutableMapOf<String, MutableMap<String, MutableList<Double>>>()

        for (session in fpSessions) {
            val flaggedLaps = raceControlRepository.findBySession(session.key)
                .filter { it.flag in TyreDegradationAnalyzer.SLOW_FLAGS }
                .mapNotNull { it.lap }
                .toSet()

            val stints = stintRepository.findBySession(session.key)
            val lapsByDriver = lapRepository.findBySession(session.key).groupBy { it.driverNumber }

            for (stint in stints) {
                val compound = stint.compound ?: continue
                if (compound !in DRY_COMPOUNDS) continue

                val driverLaps = lapsByDriver[stint.driverNumber] ?: continue
                val stintLaps = driverLaps.filter { lap ->
                    (stint.lapStart ?: 0) <= lap.lapNumber &&
                        lap.lapNumber <= (stint.lapEnd ?: Int.MAX_VALUE)
                }
                val valid = TyreDegradationAnalyzer.validLaps(stintLaps, flaggedLaps).sortedBy { it.lapNumber }

                if (!TyreDegradationAnalyzer.isLongRun(valid.size)) continue

                val degPerLapMs = (valid.last().lapTimeMs!! - valid.first().lapTimeMs!!).toDouble() /
                    (valid.size - 1)
                accumulator
                    .getOrPut(stint.driverNumber) { mutableMapOf() }
                    .getOrPut(compound) { mutableListOf() }
                    .add(degPerLapMs)
            }
        }

        return accumulator.mapValues { (_, cm) -> cm.mapValues { (_, r) -> r.average() } }
    }

    private fun resolveRate(
        driverNumber: String,
        compound: String,
        rawRates: Map<String, Map<String, Double>>,
        allDrivers: List<DriverEntry>
    ): Double? {
        rawRates[driverNumber]?.get(compound)?.let { return it }

        val team = allDrivers.find { it.number == driverNumber }?.team
        if (team != null) {
            val teammateRates = allDrivers
                .filter { it.team == team && it.number != driverNumber }
                .mapNotNull { rawRates[it.number]?.get(compound) }
            if (teammateRates.isNotEmpty()) return teammateRates.average()
        }

        val globalRates = rawRates.entries
            .filter { it.key != driverNumber }
            .mapNotNull { it.value[compound] }
        if (globalRates.isNotEmpty()) return globalRates.average()

        return null
    }
}
