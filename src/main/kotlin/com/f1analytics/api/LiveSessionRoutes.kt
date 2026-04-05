package com.f1analytics.api

import com.f1analytics.api.views.LatestSessionView
import com.f1analytics.api.views.LiveEventView
import com.f1analytics.api.views.MeetingsView
import com.f1analytics.api.views.ReplayEventView
import com.f1analytics.api.views.SessionStateView
import com.f1analytics.com.f1analytics.api.views.SessionLapsView
import com.f1analytics.com.f1analytics.api.views.SessionPositionsView
import com.f1analytics.com.f1analytics.api.views.SessionStintsView
import com.f1analytics.com.f1analytics.api.views.WeekendView
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
private data class HealthDto(val status: String, val session: String)

fun Route.liveSessionRoutes(
    liveEventView: LiveEventView,
    replayEventView: ReplayEventView,
    latestSessionView: LatestSessionView,
    sessionStateView: SessionStateView,
    meetingsView: MeetingsView,
    sessionLapsView: SessionLapsView,
    sessionPositionsView: SessionPositionsView,
    sessionStintsView: SessionStintsView,
    weekendView: WeekendView,
    isSessionActive: () -> Boolean = { false }
) {
    get("/ping") {
        call.respond(HttpStatusCode.OK, "pong")
    }

    // F-06.1: Health check
    get("/health") {
        val sessionStatus = if (isSessionActive()) "ACTIVE" else "IDLE"
        call.respond(HttpStatusCode.OK, HealthDto(status = "ok", session = sessionStatus))
    }

    // F-00.6: Live SSE stream
    get("/api/events/live") {
        liveEventView.handle(call)
    }

    // F-00.5: Replay SSE stream
    get("/api/events/replay/{sessionKey}") {
        replayEventView.handle(call)
    }

    // F-00.3 / F-06.3: Latest session info for the UI
    get("/api/sessions/latest") {
        latestSessionView.handle(call)
    }

    // F-06.3: Snapshot of current session state
    get("/api/sessions/{key}/state") {
        sessionStateView.handle(call)
    }

    // F-06.3: Meetings/races by year
    get("/api/meetings") {
        meetingsView.handleList(call)
    }

    // F-06.3: Current meeting
    get("/api/meetings/current") {
        meetingsView.handleCurrent(call)
    }

    // F-09: Chart data endpoints (hardcoded stubs — real data wired in future epic)
    get("/api/sessions/{sessionKey}/laps") {
        sessionLapsView.handle(call)
    }

    get("/api/sessions/{sessionKey}/stints") {
        sessionStintsView.handle(call)
    }

    get("/api/sessions/{sessionKey}/positions") {
        sessionPositionsView.handle(call)
    }

    get("/api/weekend") {
        weekendView.handle(call)
    }
}

