package com.f1analytics.api.views

import com.f1analytics.api.usecase.BuildLiveStrategyTrackerUseCase
import com.f1analytics.core.service.LiveSessionStateManager
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

class LiveStrategyTrackerView(
    private val stateManager: LiveSessionStateManager,
    private val buildLiveStrategyTrackerUseCase: BuildLiveStrategyTrackerUseCase,
) {
    suspend fun handle(call: ApplicationCall) {
        val sessionKey = call.parameters["sessionKey"]?.toIntOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, "Invalid session key")

        val totalLaps = call.request.queryParameters["totalLaps"]?.toIntOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, "Missing or invalid totalLaps parameter")

        val state = stateManager.stateFlow.value
        if (state == null || state.sessionKey != sessionKey) {
            return call.respond(HttpStatusCode.NotFound)
        }

        call.respond(buildLiveStrategyTrackerUseCase.execute(state, totalLaps))
    }
}
