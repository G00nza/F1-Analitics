package com.f1analytics.api.views

import com.f1analytics.api.usecase.BuildSafetyCarLiveUseCase
import com.f1analytics.api.usecase.BuildSafetyCarReviewUseCase
import com.f1analytics.core.service.LiveSessionStateManager
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

class SafetyCarImpactView(
    private val stateManager: LiveSessionStateManager,
    private val buildLiveUseCase: BuildSafetyCarLiveUseCase,
    private val buildReviewUseCase: BuildSafetyCarReviewUseCase,
) {
    suspend fun handleLive(call: ApplicationCall) {
        val sessionKey = call.parameters["sessionKey"]?.toIntOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, "Invalid session key")

        val state = stateManager.stateFlow.value
        if (state == null || state.sessionKey != sessionKey) {
            return call.respond(HttpStatusCode.NotFound)
        }

        val result = buildLiveUseCase.execute(state)
            ?: return call.respond(HttpStatusCode.NotFound)  // no active SC

        call.respond(result)
    }

    suspend fun handleReview(call: ApplicationCall) {
        val sessionKey = call.parameters["sessionKey"]?.toIntOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, "Invalid session key")

        call.respond(buildReviewUseCase.execute(sessionKey))
    }
}
