package com.f1analytics.api.views

import com.f1analytics.api.dto.*
import com.f1analytics.core.domain.model.DriverEntry
import com.f1analytics.core.domain.model.Lap
import com.f1analytics.core.domain.model.Stint
import com.f1analytics.core.domain.port.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

class SessionChartsView(
    private val lapRepository: LapRepository,
    private val driverRepository: SessionDriverRepository,
    private val stintRepository: StintRepository,
    private val positionRepository: PositionRepository
) {

    suspend fun handle(call: ApplicationCall) {
        val sessionKey = call.parameters["sessionKey"]?.toIntOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, "Invalid session key")

        val laps = lapRepository.findBySession(sessionKey)
        val drivers = driverRepository.findBySession(sessionKey)
        val stints = stintRepository.findBySession(sessionKey)
        val driverPositions = positionRepository.findAllPositionsByDriver(sessionKey)

        call.respond(buildChartsDto(laps, drivers, stints, driverPositions))
    }

    private data class LapContext(
        val lap: Lap,
        val driver: DriverEntry,
        val stint: Stint,
        val gapToLeaderMs: Int?
    )

    private fun buildChartsDto(
        laps: List<Lap>,
        drivers: List<DriverEntry>,
        stints: List<Stint>,
        driverPositions: Map<String, List<DriverPositionSnapshot>>
    ): SessionChartsDto {
        val driverByNumber = drivers.associateBy { it.number }

        val lapContexts = laps
            .sortedWith(compareBy({ it.driverNumber }, { it.lapNumber }))
            .mapNotNull { lap ->
                val driver = driverByNumber[lap.driverNumber] ?: return@mapNotNull null
                val stint = stints.find { s ->
                    s.driverNumber == lap.driverNumber
                        && (s.lapStart ?: 0) <= lap.lapNumber
                        && lap.lapNumber <= (s.lapEnd ?: Int.MAX_VALUE)
                } ?: return@mapNotNull null
                val gapToLeaderMs = driverPositions[lap.driverNumber]
                    ?.getOrNull(lap.lapNumber - 1)
                    ?.gapToLeader?.toDoubleOrNull()
                    ?.let { (it * 1000).toInt() }
                LapContext(lap, driver, stint, gapToLeaderMs)
            }

        return SessionChartsDto(
            bestLaps = buildBestLaps(lapContexts),
            charts = listOf(
                buildLapTimesSection(lapContexts),
                buildPositionsSection(drivers, driverPositions),
                buildGapSection(lapContexts),
                buildDegradationSection(lapContexts, stints, driverByNumber)
            )
        )
    }

    private fun buildBestLaps(lapContexts: List<LapContext>): List<BestLapDto> {
        val bestByDriver = mutableMapOf<String, LapContext>()
        for (ctx in lapContexts) {
            if (ctx.lap.lapTimeMs == null || ctx.lap.pitOutLap || ctx.lap.pitInLap) continue
            val cur = bestByDriver[ctx.lap.driverNumber]
            if (cur == null || ctx.lap.lapTimeMs < cur.lap.lapTimeMs!!) {
                bestByDriver[ctx.lap.driverNumber] = ctx
            }
        }
        return bestByDriver.values
            .sortedBy { it.lap.lapTimeMs }
            .map { ctx ->
                BestLapDto(
                    driverCode = ctx.driver.code,
                    teamColor = "#${ctx.driver.teamColor ?: "000000"}",
                    lapTimeMs = ctx.lap.lapTimeMs!!,
                    compound = ctx.stint.compound,
                    lapNumber = ctx.lap.lapNumber
                )
            }
    }

    private fun buildLapTimesSection(lapContexts: List<LapContext>): ChartSectionDto {
        val byDriver = lapContexts.groupBy { it.driver.number }
        val datasets = byDriver.values.map { ctxList ->
            val driver = ctxList.first().driver
            val sorted = ctxList.sortedBy { it.lap.lapNumber }
            val points = mutableListOf<ChartPointDto>()
            for (ctx in sorted) {
                if (ctx.lap.pitOutLap) {
                    points.add(ChartPointDto(x = ctx.lap.lapNumber - 0.5, y = null))
                }
                points.add(
                    ChartPointDto(
                        x = ctx.lap.lapNumber.toDouble(),
                        y = ctx.lap.lapTimeMs?.let { it / 1000.0 },
                        pitOutLap = ctx.lap.pitOutLap,
                        pitInLap = ctx.lap.pitInLap,
                        isPersonalBest = ctx.lap.isPersonalBest,
                        compound = ctx.stint.compound
                    )
                )
            }
            ChartDatasetDto(label = driver.code, color = "#${driver.teamColor ?: "000000"}", points = points)
        }
        return ChartSectionDto(id = "lapTimes", title = "Lap Time Progression", type = "lapTimes", datasets = datasets)
    }

    private fun buildPositionsSection(
        drivers: List<DriverEntry>,
        driverPositions: Map<String, List<DriverPositionSnapshot>>
    ): ChartSectionDto {
        val datasets = drivers.mapNotNull { driver ->
            val positions = driverPositions[driver.number]?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            ChartDatasetDto(
                label = driver.code,
                color = "#${driver.teamColor ?: "000000"}",
                points = positions.mapIndexed { index, snapshot ->
                    ChartPointDto(x = (index + 1).toDouble(), y = snapshot.position?.toDouble())
                }
            )
        }
        return ChartSectionDto(id = "positions", title = "Position Progression", type = "positions", datasets = datasets)
    }

    private fun buildGapSection(lapContexts: List<LapContext>): ChartSectionDto {
        val byDriver = lapContexts.groupBy { it.driver.number }
        val datasets = byDriver.values.map { ctxList ->
            val driver = ctxList.first().driver
            val sorted = ctxList.sortedBy { it.lap.lapNumber }
            ChartDatasetDto(
                label = driver.code,
                color = "#${driver.teamColor ?: "000000"}",
                points = sorted.map { ctx ->
                    ChartPointDto(
                        x = ctx.lap.lapNumber.toDouble(),
                        y = ctx.gapToLeaderMs?.let { it / 1000.0 } ?: 0.0,
                        pitOutLap = ctx.lap.pitOutLap,
                        pitInLap = ctx.lap.pitInLap
                    )
                }
            )
        }
        return ChartSectionDto(id = "gap", title = "Gap to Leader", type = "gap", datasets = datasets)
    }

    private fun buildDegradationSection(
        lapContexts: List<LapContext>,
        stints: List<Stint>,
        driverByNumber: Map<String, DriverEntry>
    ): ChartSectionDto {
        val lapsByStintKey = lapContexts.groupBy { "${it.lap.driverNumber}-${it.stint.stintNumber}" }
        val datasets = stints.mapNotNull { stint ->
            val ctxList = lapsByStintKey["${stint.driverNumber}-${stint.stintNumber}"]
                ?: return@mapNotNull null
            val driver = driverByNumber[stint.driverNumber] ?: return@mapNotNull null

            val validLaps = ctxList
                .filter { !it.lap.pitOutLap && !it.lap.pitInLap && it.lap.lapTimeMs != null }
                .sortedBy { it.lap.lapNumber }

            if (validLaps.size <= 5) return@mapNotNull null

            val baseLapMs = validLaps.first().lap.lapTimeMs!!
            ChartDatasetDto(
                label = "${driver.code} S${stint.stintNumber} (${stint.compound})",
                color = "#${driver.teamColor ?: "000000"}",
                compound = stint.compound,
                points = validLaps.mapIndexed { index, ctx ->
                    ChartPointDto(
                        x = (index + 1).toDouble(),
                        y = (ctx.lap.lapTimeMs!! - baseLapMs) / 1000.0,
                        lapNumber = ctx.lap.lapNumber,
                        compound = ctx.stint.compound
                    )
                }
            )
        }
        return ChartSectionDto(id = "degradation", title = "Tyre Degradation", type = "degradation", datasets = datasets)
    }
}
