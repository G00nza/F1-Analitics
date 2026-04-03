package com.f1analytics.api.views

import com.f1analytics.api.SseSink
import com.f1analytics.core.domain.port.ReplayRepository
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import kotlinx.coroutines.delay

class ReplayEventView(private val replayRepo: ReplayRepository) {

    suspend fun handle(call: ApplicationCall) {
        val sessionKey = call.parameters["sessionKey"]?.toIntOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest)
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
}
