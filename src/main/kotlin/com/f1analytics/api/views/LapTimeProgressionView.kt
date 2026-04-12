package com.f1analytics.api.views

import com.f1analytics.api.usecase.BuildLapTimeProgressionUseCase
import com.f1analytics.core.domain.port.RaceRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

class LapTimeProgressionView(
    private val raceRepository: RaceRepository,
    private val buildLapTimeProgressionUseCase: BuildLapTimeProgressionUseCase,
) {

    suspend fun handle(call: ApplicationCall) {
        val raceKey = call.request.queryParameters["raceKey"]?.toIntOrNull()
        val race = if (raceKey != null)
            raceRepository.findByKey(raceKey) ?: return call.respond(HttpStatusCode.NotFound)
        else
            raceRepository.findCurrent() ?: return call.respond(HttpStatusCode.NotFound)

        call.respond(buildLapTimeProgressionUseCase.execute(race))
    }
}
