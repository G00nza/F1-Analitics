package com.f1analytics.com.f1analytics.api.views

import com.f1analytics.api.dto.LapDataDto
import com.f1analytics.core.domain.model.DriverEntry
import com.f1analytics.core.domain.model.Lap
import com.f1analytics.core.domain.model.Stint
import com.f1analytics.core.domain.port.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

class SessionLapsView(
    private val lapRepository: LapRepository,
    private val driverRepository: SessionDriverRepository,
    private val stintRepository: StintRepository,
    private val positionRepository: PositionRepository
) {

    suspend fun handle(call: ApplicationCall) {
        val sessionKey = call.parameters["SessionKey"]?.toIntOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, "Invalid session key")

        val laps = lapRepository.findBySession(sessionKey)
        val drivers = driverRepository.findBySession(sessionKey)
        val stints = stintRepository.findBySession(sessionKey)
        val driverPositions = positionRepository.findAllPositionsByDriver(sessionKey)


        call.respond(buildLapDto(laps, drivers, stints, driverPositions))
    }

    private fun buildLapDto(
        laps: List<Lap>,
        drivers: List<DriverEntry>,
        stints: List<Stint>,
        driverPositions: Map<String, List<DriverPositionSnapshot>>
    ): List<LapDataDto> {
        val driverByNumber = drivers.associateBy { it.number }
        return laps.sortedWith(compareBy({ it.driverNumber }, { it.lapNumber })).map { lap ->
            val driver = driverByNumber[lap.driverNumber]!!
            val stint = stints.find { s ->
                s.driverNumber == lap.driverNumber
                        && (s.lapStart ?: 0) <= lap.lapNumber
                        && lap.lapNumber <= (s.lapEnd ?: Int.MAX_VALUE)
            }!!
            val lapDriverPosition = driverPositions[driver.number]?.getOrNull(lap.lapNumber - 1)

            LapDataDto(
                driverNumber   = lap.driverNumber,
                driverCode     = driver.code,
                teamColor      = "#${driver.teamColor ?: "000000"}",
                lapNumber      = lap.lapNumber,
                lapTimeMs      = lap.lapTimeMs,
                pitOutLap      = lap.pitOutLap,
                pitInLap       = lap.pitInLap,
                isPersonalBest = lap.isPersonalBest,
                compound       = stint.compound,
                stintNumber    = stint.stintNumber,
                gapToLeaderMs  = lapDriverPosition?.gapToLeader?.toDoubleOrNull()?.let { (it * 1000).toInt() }
            )
        }
    }
}