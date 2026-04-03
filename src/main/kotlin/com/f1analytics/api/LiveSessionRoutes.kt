package com.f1analytics.api

import com.f1analytics.api.views.LatestSessionView
import com.f1analytics.api.views.LiveEventView
import com.f1analytics.api.views.ReplayEventView
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

fun Route.liveSessionRoutes(
    liveEventView: LiveEventView,
    replayEventView: ReplayEventView,
    latestSessionView: LatestSessionView
) {
    // F-00.6: Live SSE stream
    get("/api/events/live") {
        liveEventView.handle(call)
    }

    // F-00.5: Replay SSE stream
    get("/api/events/replay/{sessionKey}") {
        replayEventView.handle(call)
    }

    // F-00.3: Latest session info for the UI
    get("/api/sessions/latest") {
        latestSessionView.handle(call)
    }
}

