package com.f1analytics.api.usecase

import com.f1analytics.api.dto.DriverStrategyDto
import com.f1analytics.api.dto.PitWindowDto
import com.f1analytics.api.dto.PreRaceStrategyDto
import com.f1analytics.api.dto.StrategyStintDto
import com.f1analytics.api.usecase.charts.TyreDegradationAnalyzer
import com.f1analytics.core.domain.model.DriverEntry
import com.f1analytics.core.domain.model.Session
import com.f1analytics.core.domain.model.SessionType
import com.f1analytics.core.domain.port.LapRepository
import com.f1analytics.core.domain.port.RaceControlRepository
import com.f1analytics.core.domain.port.SessionDriverRepository
import com.f1analytics.core.domain.port.SessionRepository
import com.f1analytics.core.domain.port.StintRepository
import kotlin.math.roundToInt

class BuildPreRaceStrategyUseCase(
    private val sessionRepository: SessionRepository,
    private val stintRepository: StintRepository,
    private val lapRepository: LapRepository,
    private val sessionDriverRepository: SessionDriverRepository,
    private val raceControlRepository: RaceControlRepository,
) {

    companion object {
        val DRY_COMPOUNDS = setOf("SOFT", "MEDIUM", "HARD")
        val COMPOUND_CANONICAL_ORDER = listOf("SOFT", "MEDIUM", "HARD")
        private const val PIT_WINDOW_RADIUS = 2
        private val FP_SESSION_TYPES = setOf(SessionType.FP2, SessionType.FP3)
    }

    suspend fun execute(raceKey: Int, totalLaps: Int): PreRaceStrategyDto {
        val allSessions = sessionRepository.findByRace(raceKey)
        val fpSessions = allSessions.filter { it.type in FP_SESSION_TYPES }

        if (fpSessions.isEmpty()) {
            return PreRaceStrategyDto(raceKey, totalLaps, hasData = false, drivers = emptyList())
        }

        val allDrivers = allSessions
            .flatMap { sessionDriverRepository.findBySession(it.key) }
            .distinctBy { it.number }

        if (allDrivers.isEmpty()) {
            return PreRaceStrategyDto(raceKey, totalLaps, hasData = false, drivers = emptyList())
        }

        val rawRates = collectRawDegRates(fpSessions)

        val driverStrategies = allDrivers.mapNotNull { driver ->
            val rates = DRY_COMPOUNDS.mapNotNull { compound ->
                resolveRate(driver.number, compound, rawRates, allDrivers)?.let { compound to it }
            }.toMap()

            if (rates.size < 2) return@mapNotNull null

            val expected = generateOneStopStrategy(rates, totalLaps) ?: return@mapNotNull null
            val alt = if (rates.size >= 3) generateTwoStopStrategy(rates, totalLaps) else null

            DriverStrategyDto(
                driverNumber = driver.number,
                driverCode = driver.code,
                team = driver.team,
                expectedStrategy = expected,
                altStrategy = alt
            )
        }

        return PreRaceStrategyDto(
            raceKey = raceKey,
            totalLaps = totalLaps,
            hasData = driverStrategies.isNotEmpty(),
            drivers = driverStrategies
        )
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

                val degPerLapMs = (valid.last().lapTimeMs!! - valid.first().lapTimeMs!!).toDouble() / (valid.size - 1)
                accumulator
                    .getOrPut(stint.driverNumber) { mutableMapOf() }
                    .getOrPut(compound) { mutableListOf() }
                    .add(degPerLapMs)
            }
        }

        return accumulator.mapValues { (_, compoundMap) ->
            compoundMap.mapValues { (_, rates) -> rates.average() }
        }
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

    private fun generateOneStopStrategy(rates: Map<String, Double>, totalLaps: Int): List<StrategyStintDto>? {
        val compounds = rates.keys.toList()
        if (compounds.size < 2) return null

        val pairs = compounds.flatMap { a -> compounds.filter { it != a }.map { b -> a to b } }

        val best = pairs.minWithOrNull(
            compareBy<Pair<String, String>> { (c1, c2) -> totalDegCost(rates[c1]!!, rates[c2]!!, totalLaps) }
                .thenByDescending { (c1, _) -> rates[c1]!! }
                .thenBy { (c1, _) -> COMPOUND_CANONICAL_ORDER.indexOf(c1).takeIf { it >= 0 } ?: Int.MAX_VALUE }
        ) ?: return null

        val (c1, c2) = best
        val pitLap = optimalPitLap(rates[c1]!!, rates[c2]!!, totalLaps)

        return listOf(
            StrategyStintDto(c1, pitLap, pitWindow(pitLap, totalLaps)),
            StrategyStintDto(c2, totalLaps - pitLap, null)
        )
    }

    private fun generateTwoStopStrategy(rates: Map<String, Double>, totalLaps: Int): List<StrategyStintDto>? {
        val compounds = rates.keys.toList()
        if (compounds.size < 3) return null

        val triples = compounds.flatMap { a ->
            compounds.filter { it != a }.flatMap { b ->
                compounds.filter { it != a && it != b }.map { c -> Triple(a, b, c) }
            }
        }

        val best = triples.minWithOrNull(
            compareBy<Triple<String, String, String>> { (c1, c2, c3) ->
                twoStopDegCost(rates[c1]!!, rates[c2]!!, rates[c3]!!, totalLaps)
            }.thenByDescending { (c1, _, _) -> rates[c1]!! }
            .thenBy { (c1, _, _) -> COMPOUND_CANONICAL_ORDER.indexOf(c1).takeIf { it >= 0 } ?: Int.MAX_VALUE }
        ) ?: return null

        val (c1, c2, c3) = best
        val p1 = optimalPitLap(rates[c1]!!, rates[c2]!! + rates[c3]!!, totalLaps).coerceIn(5, totalLaps - 10)
        val p2 = (p1 + optimalPitLap(rates[c2]!!, rates[c3]!!, totalLaps - p1)).coerceIn(p1 + 5, totalLaps - 5)

        return listOf(
            StrategyStintDto(c1, p1, pitWindow(p1, totalLaps)),
            StrategyStintDto(c2, p2 - p1, pitWindow(p2, totalLaps)),
            StrategyStintDto(c3, totalLaps - p2, null)
        )
    }

    private fun optimalPitLap(deg1: Double, deg2: Double, totalLaps: Int): Int {
        val total = deg1 + deg2
        if (total == 0.0) return totalLaps / 2
        return (totalLaps * deg2 / total).roundToInt().coerceIn(5, totalLaps - 5)
    }

    private fun totalDegCost(deg1: Double, deg2: Double, totalLaps: Int): Double {
        val p = optimalPitLap(deg1, deg2, totalLaps)
        return stintDegCost(deg1, p) + stintDegCost(deg2, totalLaps - p)
    }

    private fun twoStopDegCost(deg1: Double, deg2: Double, deg3: Double, totalLaps: Int): Double {
        val p1 = optimalPitLap(deg1, deg2 + deg3, totalLaps).coerceIn(5, totalLaps - 10)
        val p2 = (p1 + optimalPitLap(deg2, deg3, totalLaps - p1)).coerceIn(p1 + 5, totalLaps - 5)
        return stintDegCost(deg1, p1) + stintDegCost(deg2, p2 - p1) + stintDegCost(deg3, totalLaps - p2)
    }

    private fun stintDegCost(degPerLap: Double, laps: Int): Double = laps * (laps + 1) / 2.0 * degPerLap

    private fun pitWindow(pitLap: Int, totalLaps: Int) = PitWindowDto(
        lapFrom = (pitLap - PIT_WINDOW_RADIUS).coerceAtLeast(1),
        lapTo = (pitLap + PIT_WINDOW_RADIUS).coerceAtMost(totalLaps - 1)
    )
}
