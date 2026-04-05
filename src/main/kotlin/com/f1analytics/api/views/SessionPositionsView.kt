package com.f1analytics.com.f1analytics.api.views

import com.f1analytics.api.dto.RacePositionDto
import com.f1analytics.core.domain.model.DriverEntry
import com.f1analytics.core.domain.port.DriverPositionSnapshot
import com.f1analytics.core.domain.port.PositionRepository
import com.f1analytics.core.domain.port.SessionDriverRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

class SessionPositionsView(
    private val driverRepository: SessionDriverRepository,
    private val positionRepository: PositionRepository
) {
    suspend fun handle(call: ApplicationCall) {
        val sessionKey = call.parameters["SessionKey"]?.toIntOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, "Invalid session key")

        val drivers = driverRepository.findBySession(sessionKey)
        val positions = positionRepository.findAllPositionsByDriver(sessionKey)

        call.respond(buildPositionDto(drivers, positions))
    }

    private fun buildPositionDto(
        drivers: List<DriverEntry>,
        positions: Map<String, List<DriverPositionSnapshot>>
    ): List<RacePositionDto> {
        return drivers.flatMap { driver ->
            val driverPositions = positions[driver.number] ?: emptyList()
            driverPositions.mapIndexed { index, snapshot ->
                RacePositionDto(
                    driverNumber = driver.number,
                    driverCode = driver.code,
                    teamColor = "#${driver.teamColor ?: "000000"}",
                    lapNumber = index + 1,
                    position = snapshot.position!!
                )
            }
        }
    }
}