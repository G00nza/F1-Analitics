package com.f1analytics.com.f1analytics.api.views

import com.f1analytics.api.dto.StintDataDto
import com.f1analytics.core.domain.model.DriverEntry
import com.f1analytics.core.domain.model.Stint
import com.f1analytics.core.domain.port.PositionRepository
import com.f1analytics.core.domain.port.SessionDriverRepository
import com.f1analytics.core.domain.port.StintRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

class SessionStintsView(
    private val stintRepository: StintRepository,
    private val driverRepository: SessionDriverRepository,
) {

    suspend fun handle(call: ApplicationCall){
        val sessionKey = call.parameters["SessionKey"]?.toIntOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, "Invalid session key")

        val stints = stintRepository.findBySession(sessionKey)
        val drivers = driverRepository.findBySession(sessionKey)

        call.respond(buildStintDtos(stints, drivers))
    }

    private fun buildStintDtos(stints: List<Stint>, drivers: List<DriverEntry>): List<StintDataDto> {

        return stints.map {stint ->
            val driver = drivers.find { it.number == stint.driverNumber}!!
            StintDataDto(
                driverNumber = driver.number,
                driverCode = driver.code,
                stintNumber = stint.stintNumber,
                compound = stint.compound,
                lapStart = stint.lapStart,
                lapEnd = stint.lapEnd,
                isNew = stint.isNew
            )
        }

    }
}