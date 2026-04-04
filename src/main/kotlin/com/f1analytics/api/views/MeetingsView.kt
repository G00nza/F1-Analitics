package com.f1analytics.api.views

import com.f1analytics.core.domain.model.Race
import com.f1analytics.core.domain.model.Session
import com.f1analytics.core.domain.port.RaceRepository
import com.f1analytics.core.domain.port.SessionRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class MeetingDto(
    val key: Int,
    val name: String,
    val circuit: String,
    val country: String?,
    val dateStart: String?,
    val sessions: List<MeetingSessionDto>
)

@Serializable
data class MeetingSessionDto(
    val key: Int,
    val name: String,
    val type: String,
    val status: String?,
    val dateStart: String?
)

class MeetingsView(
    private val raceRepo: RaceRepository,
    private val sessionRepo: SessionRepository
) {

    suspend fun handleList(call: ApplicationCall) {
        val year = call.request.queryParameters["year"]?.toIntOrNull()
            ?: Clock.System.now().toLocalDateTime(TimeZone.UTC).year

        val races = raceRepo.findByYear(year)
        val meetings = buildMeetingDtos(races)
        call.respond(meetings)
    }

    suspend fun handleCurrent(call: ApplicationCall) {
        val race = raceRepo.findCurrent()
            ?: return call.respond(HttpStatusCode.NoContent)

        val meeting = buildMeetingDtos(listOf(race)).firstOrNull()
            ?: return call.respond(HttpStatusCode.NoContent)

        call.respond(meeting)
    }

    private suspend fun buildMeetingDtos(races: List<Race>): List<MeetingDto> {
        return races.map { race ->
            val sessions = sessionRepo.findByRace(race.key)
            MeetingDto(
                key      = race.key,
                name     = race.name,
                circuit  = race.circuit,
                country  = race.country,
                dateStart = race.dateStart,
                sessions = sessions.map { it.toMeetingSessionDto() }
            )
        }
    }

    private fun Session.toMeetingSessionDto() = MeetingSessionDto(
        key       = key,
        name      = name,
        type      = type.name,
        status    = status,
        dateStart = dateStart?.toString()
    )
}
