package com.f1analytics.api.views

import com.f1analytics.api.usecase.BuildSectorComparisonUseCase
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

class SectorComparisonView(
    private val buildSectorComparisonUseCase: BuildSectorComparisonUseCase,
) {

    suspend fun handle(call: ApplicationCall) {
        val sessionKeyA = call.parameters["sessionKeyA"]?.toIntOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, "Invalid session key A")
        val sessionKeyB = call.parameters["sessionKeyB"]?.toIntOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, "Invalid session key B")
        val driverNumber = call.request.queryParameters["driver"]
            ?: return call.respond(HttpStatusCode.BadRequest, "Missing driver parameter")

        val result = buildSectorComparisonUseCase.execute(sessionKeyA, sessionKeyB, driverNumber)
            ?: return call.respond(HttpStatusCode.NotFound)

        call.respond(result)
    }
}
