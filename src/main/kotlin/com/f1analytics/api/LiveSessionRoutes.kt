package com.f1analytics.api

import com.f1analytics.core.domain.model.Session
import com.f1analytics.core.domain.port.ReplayRepository
import com.f1analytics.core.service.SessionResolver
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

fun Route.liveSessionRoutes(
    sseManager: SseManager,
    sessionResolver: SessionResolver,
    replayRepo: ReplayRepository
) {
    // F-00.6: Live SSE stream
    get("/api/events/live") {
        call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
        call.response.headers.append("X-Accel-Buffering", "no")
        call.respondTextWriter(contentType = ContentType("text", "event-stream")) {
            sseManager.handleLiveClient(SseSink(this))
        }
    }

    // F-00.5: Replay SSE stream
    get("/api/events/replay/{sessionKey}") {
        val sessionKey = call.parameters["sessionKey"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest)
        val speed = call.request.queryParameters["speed"]?.toDoubleOrNull() ?: 1.0

        call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
        call.response.headers.append("X-Accel-Buffering", "no")
        call.respondTextWriter(contentType = ContentType("text", "event-stream")) {
            val events = replayRepo.findAllEventsBySession(sessionKey)
            val sink = SseSink(this)
            var lastTimestamp = events.firstOrNull()?.timestamp

            for (event in events) {
                lastTimestamp?.let { prev ->
                    val gapMs = (event.timestamp - prev).inWholeMilliseconds
                    val delayMs = (gapMs / speed).toLong().coerceIn(0L, 5_000L)
                    if (delayMs > 0) delay(delayMs)
                }
                sink.sendEvent(data = event.json, event = event.topic)
                lastTimestamp = event.timestamp
            }
        }
    }

    // F-00.3: Latest session info for the UI
    get("/api/sessions/latest") {
        val session = sessionResolver.resolve()
        if (session != null) {
            call.respond(session.toSessionDto())
        } else {
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

@Serializable
data class SessionDto(
    val key: Int,
    val name: String,
    val type: String,
    val status: String?,
    val recorded: Boolean
)

private fun Session.toSessionDto() = SessionDto(
    key      = key,
    name     = name,
    type     = type.name,
    status   = status,
    recorded = recorded
)
