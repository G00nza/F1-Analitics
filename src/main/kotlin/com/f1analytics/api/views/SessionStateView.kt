package com.f1analytics.api.views

import com.f1analytics.api.dto.toDto
import com.f1analytics.core.service.LiveSessionStateManager
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

class SessionStateView(private val stateManager: LiveSessionStateManager) {

    suspend fun handle(call: ApplicationCall) {
        val key = call.parameters["key"]?.toIntOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, "Invalid session key")

        val state = stateManager.stateFlow.value
        if (state != null && state.sessionKey == key) {
            call.respond(state.toDto())
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}
