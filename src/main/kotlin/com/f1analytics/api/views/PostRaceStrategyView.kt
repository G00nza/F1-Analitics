package com.f1analytics.api.views

import com.f1analytics.api.usecase.BuildPostRaceStrategyUseCase
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

class PostRaceStrategyView(
    private val buildPostRaceStrategyUseCase: BuildPostRaceStrategyUseCase,
) {
    suspend fun handle(call: ApplicationCall) {
        val sessionKey = call.parameters["sessionKey"]?.toIntOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, "Invalid session key")

        call.respond(buildPostRaceStrategyUseCase.execute(sessionKey))
    }
}
