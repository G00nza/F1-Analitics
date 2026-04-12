package com.f1analytics.api.views

import com.f1analytics.api.usecase.BuildRacePaceUseCase
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

class RacePaceView(
    private val buildRacePaceUseCase: BuildRacePaceUseCase,
) {

    suspend fun handle(call: ApplicationCall) {
        val sessionKey = call.parameters["sessionKey"]?.toIntOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, "Invalid session key")

        call.respond(buildRacePaceUseCase.execute(sessionKey))
    }
}
