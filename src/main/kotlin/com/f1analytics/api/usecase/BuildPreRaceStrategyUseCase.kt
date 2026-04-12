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
        private const val PIT_TIME_MS = 23_000.0
        private val FP_SESSION_TYPES = setOf(SessionType.FP1, SessionType.FP2, SessionType.FP3, SessionType.SPRINT)
    }

    suspend fun execute(raceKey: Int, totalLaps: Int): PreRaceStrategyDto {
        val sessions = sessionRepository.findByRace(raceKey)
        val sessionsWithStints = sessions.filter { it.type in FP_SESSION_TYPES }

        if (sessionsWithStints.isEmpty()) {
            return PreRaceStrategyDto(raceKey, totalLaps, hasData = false, drivers = emptyList())
        }

        val allDrivers = sessions
            .filter { it.type == SessionType.RACE }
            .flatMap { sessionDriverRepository.findBySession(it.key) }
            .distinctBy { it.number }

        if (allDrivers.isEmpty()) {
            return PreRaceStrategyDto(raceKey, totalLaps, hasData = false, drivers = emptyList())
        }

        val rawRates = collectRawDegRates(sessionsWithStints)

        val driverStrategies = allDrivers.mapNotNull { driver ->
            val rates = DRY_COMPOUNDS.mapNotNull { compound ->
                resolveRate(driver.number, compound, rawRates, allDrivers)?.let { compound to it }
            }.toMap()

            if (rates.size < 2) return@mapNotNull null

            val oneStop = generateBestOneStop(rates, totalLaps) ?: return@mapNotNull null
            val twoStop = generateBestTwoStop(rates, totalLaps)

            val (expectedStrategy, altStrategy) = selectStrategies(oneStop, twoStop, rates)

            DriverStrategyDto(
                driverNumber = driver.number,
                driverCode = driver.code,
                team = driver.team,
                expectedStrategy = expectedStrategy,
                altStrategy = altStrategy
            )
        }.sortedByDescending { it.team }

        return PreRaceStrategyDto(
            raceKey = raceKey,
            totalLaps = totalLaps,
            hasData = driverStrategies.isNotEmpty(),
            drivers = driverStrategies
        )
    }

    private suspend fun collectRawDegRates(sessions: List<Session>): Map<String, Map<String, Double>> {
        val accumulator = mutableMapOf<String, MutableMap<String, MutableList<Double>>>()

        for (session in sessions) {
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

                val degPerLapMs = TyreDegradationAnalyzer.calculateDegRate(valid) ?: continue
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
        // 1. Driver's own data
        rawRates[driverNumber]?.get(compound)?.let { return it }

        // 2. Teammate's data for this compound
        val team = allDrivers.find { it.number == driverNumber }?.team
        if (team != null) {
            val teammateRates = allDrivers
                .filter { it.team == team && it.number != driverNumber }
                .mapNotNull { rawRates[it.number]?.get(compound) }
            if (teammateRates.isNotEmpty()) return teammateRates.average()
        }

        // 3. Global average for this compound across all drivers
        val globalRates = rawRates.entries
            .filter { it.key != driverNumber }
            .mapNotNull { it.value[compound] }
        if (globalRates.isNotEmpty()) return globalRates.average()

        // 4. No team ran this compound — use average of all available rates as proxy
        val globalAllRates = rawRates.values.flatMap { it.values }
        if (globalAllRates.isNotEmpty()) return globalAllRates.average()

        return null
    }

    /** Returns the best 1-stop strategy (2 stints). */
    private fun generateBestOneStop(rates: Map<String, Double>, totalLaps: Int): List<StrategyStintDto>? {
        val compounds = rates.keys.toList()
        if (compounds.size < 2) return null

        val pairs = compounds.flatMap { a -> compounds.filter { it != a }.map { b -> a to b } }

        val best = pairs.minWithOrNull(
            compareBy<Pair<String, String>> { (c1, c2) ->
                oneStopTotalCost(c1, c2, rates, totalLaps)
            }.thenByDescending { (c1, _) -> rates[c1]!! }
            .thenBy { (c1, _) -> COMPOUND_CANONICAL_ORDER.indexOf(c1).takeIf { it >= 0 } ?: Int.MAX_VALUE }
        ) ?: return null

        val (c1, c2) = best
        val pitLap = optimalPitLap(rates[c1]!!, rates[c2]!!, baseTime(c1), baseTime(c2), totalLaps)

        return listOf(
            StrategyStintDto(c1, pitLap, pitWindow(pitLap, totalLaps)),
            StrategyStintDto(c2, totalLaps - pitLap, null)
        )
    }

    /** Returns the best 2-stop strategy (3 stints). Repeated compounds are allowed except all-same triples. */
    private fun generateBestTwoStop(rates: Map<String, Double>, totalLaps: Int): List<StrategyStintDto>? {
        val compounds = rates.keys.toList()
        if (compounds.size < 2) return null

        val sequences = compounds.flatMap { a ->
            compounds.flatMap { b ->
                compounds.map { c -> Triple(a, b, c) }
            }
        }.filter { (a, b, c) -> !(a == b && b == c) }

        val best = sequences.minWithOrNull(
            compareBy<Triple<String, String, String>> { (c1, c2, c3) ->
                twoStopTotalCost(c1, c2, c3, rates, totalLaps)
            }.thenByDescending { (c1, _, _) -> rates[c1]!! }
            .thenBy { (c1, _, _) -> COMPOUND_CANONICAL_ORDER.indexOf(c1).takeIf { it >= 0 } ?: Int.MAX_VALUE }
        ) ?: return null

        val (c1, c2, c3) = best
        val p1 = optimalPitLap(rates[c1]!!, rates[c2]!! + rates[c3]!!, baseTime(c1), baseTime(c2) + baseTime(c3), totalLaps)
            .coerceIn(5, totalLaps - 10)
        val p2 = (p1 + optimalPitLap(rates[c2]!!, rates[c3]!!, baseTime(c2), baseTime(c3), totalLaps - p1))
            .coerceIn(p1 + 5, totalLaps - 5)

        return listOf(
            StrategyStintDto(c1, p1, pitWindow(p1, totalLaps)),
            StrategyStintDto(c2, p2 - p1, pitWindow(p2, totalLaps)),
            StrategyStintDto(c3, totalLaps - p2, null)
        )
    }

    /**
     * Selects which strategy is expected (faster) and which is the alternative.
     * Total cost includes degradation, compound base times, and pit stop time per stop.
     */
    private fun selectStrategies(
        oneStop: List<StrategyStintDto>,
        twoStop: List<StrategyStintDto>?,
        rates: Map<String, Double>
    ): Pair<List<StrategyStintDto>, List<StrategyStintDto>?> {
        if (twoStop == null) return oneStop to null

        val oneStopCost = fullRaceCost(oneStop, rates)
        val twoStopCost = fullRaceCost(twoStop, rates)

        return if (twoStopCost < oneStopCost) twoStop to oneStop else oneStop to twoStop
    }

    /** Total race cost: sum of stint costs + pit stop time per pit. */
    private fun fullRaceCost(stints: List<StrategyStintDto>, rates: Map<String, Double>): Double {
        val stintCost = stints.sumOf { stintTotalCost(it.compound, it.laps, rates[it.compound] ?: 0.0) }
        val pitCost = (stints.size - 1) * PIT_TIME_MS
        return stintCost + pitCost
    }

    /** Cost of a single stint: compound base time penalty per lap + degradation accumulation. */
    private fun stintTotalCost(compound: String, laps: Int, degPerLap: Double): Double {
        val basePenalty = baseTime(compound) * laps
        val degCost = laps * (laps + 1) / 2.0 * degPerLap
        return basePenalty + degCost
    }

    private fun oneStopTotalCost(c1: String, c2: String, rates: Map<String, Double>, totalLaps: Int): Double {
        val p = optimalPitLap(rates[c1]!!, rates[c2]!!, baseTime(c1), baseTime(c2), totalLaps)
        return stintTotalCost(c1, p, rates[c1]!!) + stintTotalCost(c2, totalLaps - p, rates[c2]!!)
    }

    private fun twoStopTotalCost(c1: String, c2: String, c3: String, rates: Map<String, Double>, totalLaps: Int): Double {
        val p1 = optimalPitLap(rates[c1]!!, rates[c2]!! + rates[c3]!!, baseTime(c1), baseTime(c2) + baseTime(c3), totalLaps)
            .coerceIn(5, totalLaps - 10)
        val p2 = (p1 + optimalPitLap(rates[c2]!!, rates[c3]!!, baseTime(c2), baseTime(c3), totalLaps - p1))
            .coerceIn(p1 + 5, totalLaps - 5)
        return stintTotalCost(c1, p1, rates[c1]!!) +
            stintTotalCost(c2, p2 - p1, rates[c2]!!) +
            stintTotalCost(c3, totalLaps - p2, rates[c3]!!)
    }

    /**
     * Optimal pit lap derived from d(cost)/dp = 0, accounting for both degradation rates
     * and compound base time difference between the two stints.
     */
    private fun optimalPitLap(deg1: Double, deg2: Double, base1: Double, base2: Double, totalLaps: Int): Int {
        val total = deg1 + deg2
        if (total == 0.0) return totalLaps / 2
        val numerator = deg2 * totalLaps + (base2 - base1)
        return (numerator / total).roundToInt().coerceIn(5, totalLaps - 5)
    }

    private fun baseTime(compound: String): Double =
        TyreDegradationAnalyzer.COMPOUND_BASE_TIME_MS[compound] ?: 0.0

    private fun pitWindow(pitLap: Int, totalLaps: Int) = PitWindowDto(
        lapFrom = (pitLap - PIT_WINDOW_RADIUS).coerceAtLeast(1),
        lapTo = (pitLap + PIT_WINDOW_RADIUS).coerceAtMost(totalLaps - 1)
    )
}
