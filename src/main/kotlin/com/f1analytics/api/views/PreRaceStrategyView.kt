package com.f1analytics.api.views

import com.f1analytics.api.usecase.BuildPreRaceStrategyUseCase
import com.f1analytics.core.domain.port.RaceRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

class PreRaceStrategyView(
    private val raceRepository: RaceRepository,
    private val buildPreRaceStrategyUseCase: BuildPreRaceStrategyUseCase,
) {
    suspend fun handle(call: ApplicationCall) {
        val raceKey = call.parameters["raceKey"]?.toIntOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, "Invalid race key")

        val totalLaps = call.request.queryParameters["totalLaps"]?.toIntOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, "Missing or invalid totalLaps parameter")

        raceRepository.findByKey(raceKey)
            ?: return call.respond(HttpStatusCode.NotFound, "Race not found")

        call.respond(buildPreRaceStrategyUseCase.execute(raceKey, totalLaps))
    }
}
