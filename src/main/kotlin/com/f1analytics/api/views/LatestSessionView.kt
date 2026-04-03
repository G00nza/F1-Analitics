package com.f1analytics.api.views

import com.f1analytics.core.domain.model.Session
import com.f1analytics.core.service.SessionResolver
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable

@Serializable
data class SessionDto(
    val key: Int,
    val name: String,
    val type: String,
    val status: String?,
    val recorded: Boolean
)

class LatestSessionView(private val sessionResolver: SessionResolver) {

    suspend fun handle(call: ApplicationCall) {
        val session = sessionResolver.resolve()
        if (session != null) {
            call.respond(session.toSessionDto())
        } else {
            call.respond(HttpStatusCode.NoContent)
        }
    }

    private fun Session.toSessionDto() = SessionDto(
        key = key,
        name = name,
        type = type.name,
        status = status,
        recorded = recorded
    )
}
