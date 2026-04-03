package com.f1analytics.api.views

import com.f1analytics.api.SseManager
import com.f1analytics.api.SseSink
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondTextWriter

class LiveEventView(private val sseManager: SseManager) {

    suspend fun handle(call: ApplicationCall) {
        call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
        call.response.headers.append("X-Accel-Buffering", "no")
        call.respondTextWriter(contentType = ContentType("text", "event-stream")) {
            sseManager.handleLiveClient(SseSink(this))
        }
    }
}
