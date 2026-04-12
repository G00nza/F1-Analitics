package com.f1analytics.com.f1analytics.api.views

import com.f1analytics.api.dto.WeekendInfoDto
import com.f1analytics.api.dto.WeekendSessionDto
import com.f1analytics.core.domain.model.Race
import com.f1analytics.core.domain.model.Session
import com.f1analytics.core.domain.port.RaceRepository
import com.f1analytics.core.domain.port.SessionRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

class WeekendView(
    private val raceRepository: RaceRepository,
    private val sessionRepository: SessionRepository,
) {

    suspend fun handle(call: ApplicationCall) {
        val raceKey = call.request.queryParameters["raceKey"]?.toIntOrNull()
        val currentRace = if (raceKey != null)
            raceRepository.findByKey(raceKey) ?: return call.respond(HttpStatusCode.NotFound)
        else
            raceRepository.findCurrent() ?: return call.respond(HttpStatusCode.NotFound)
        val sessions = sessionRepository.findByRace(currentRace.key)

        call.respond(buildWeekendDto(currentRace, sessions))
    }

    private fun buildWeekendDto(currentRace: Race, sessions: List<Session>): WeekendInfoDto {
        return WeekendInfoDto(
            meetingName = currentRace.name,
            circuitName = currentRace.officialName!!,
            year = currentRace.year,
            sessions = sessions.map { session ->
                WeekendSessionDto(
                    key = session.key,
                    name = session.name,
                    type = session.type.name,
                    status = session.status!!,
                )
            }

        )
    }
}