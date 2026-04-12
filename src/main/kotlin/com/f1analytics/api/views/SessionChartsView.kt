package com.f1analytics.api.views

import com.f1analytics.api.dto.SessionChartsDto
import com.f1analytics.api.usecase.charts.*
import com.f1analytics.core.domain.port.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

class SessionChartsView(
    private val lapRepository: LapRepository,
    private val driverRepository: SessionDriverRepository,
    private val stintRepository: StintRepository,
    private val positionRepository: PositionRepository,
    private val buildBestLaps: BuildBestLapsUseCase = BuildBestLapsUseCase(),
    private val buildLapTimesChart: BuildLapTimesChartUseCase = BuildLapTimesChartUseCase(),
    private val buildPositionsChart: BuildPositionsChartUseCase = BuildPositionsChartUseCase(),
    private val buildGapChart: BuildGapChartUseCase = BuildGapChartUseCase(),
    private val buildDegradationChart: BuildDegradationChartUseCase = BuildDegradationChartUseCase()
) {

    suspend fun handle(call: ApplicationCall) {
        val sessionKey = call.parameters["sessionKey"]?.toIntOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, "Invalid session key")

        val laps = lapRepository.findBySession(sessionKey)
        val drivers = driverRepository.findBySession(sessionKey)
        val stints = stintRepository.findBySession(sessionKey)
        val driverPositions = positionRepository.findAllPositionsByDriver(sessionKey)

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

        call.respond(
            SessionChartsDto(
                bestLaps = buildBestLaps.execute(lapContexts),
                charts = listOf(
                    buildLapTimesChart.execute(lapContexts),
                    buildPositionsChart.execute(drivers, driverPositions),
                    buildGapChart.execute(lapContexts),
                    buildDegradationChart.execute(lapContexts, stints, driverByNumber)
                )
            )
        )
    }
}
