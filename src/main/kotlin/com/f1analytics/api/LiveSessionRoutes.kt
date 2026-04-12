package com.f1analytics.api

import com.f1analytics.api.views.DriverWatchlistView
import com.f1analytics.api.views.SafetyCarImpactView
import com.f1analytics.api.views.LatestSessionView
import com.f1analytics.api.views.LiveEventView
import com.f1analytics.api.views.LiveStrategyTrackerView
import com.f1analytics.api.views.MeetingsView
import com.f1analytics.api.views.PreRaceStrategyView
import com.f1analytics.api.views.ReplayEventView
import com.f1analytics.api.views.SessionChartsView
import com.f1analytics.api.views.SessionStateView
import com.f1analytics.api.views.LapTimeProgressionView
import com.f1analytics.api.views.RacePaceView
import com.f1analytics.api.views.SectorComparisonView
import com.f1analytics.api.views.StrategyAlertsView
import com.f1analytics.api.views.TyreDegradationView
import com.f1analytics.api.views.WeekendSummaryView
import com.f1analytics.com.f1analytics.api.views.WeekendView
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import kotlinx.serialization.Serializable

@Serializable
private data class HealthDto(val status: String, val session: String)

fun Route.liveSessionRoutes(
    liveEventView: LiveEventView,
    replayEventView: ReplayEventView,
    latestSessionView: LatestSessionView,
    sessionStateView: SessionStateView,
    meetingsView: MeetingsView,
    sessionChartsView: SessionChartsView,
    weekendView: WeekendView,
    weekendSummaryView: WeekendSummaryView,
    lapTimeProgressionView: LapTimeProgressionView,
    tyreDegradationView: TyreDegradationView,
    racePaceView: RacePaceView,
    sectorComparisonView: SectorComparisonView,
    preRaceStrategyView: PreRaceStrategyView,
    liveStrategyTrackerView: LiveStrategyTrackerView,
    driverWatchlistView: DriverWatchlistView,
    strategyAlertsView: StrategyAlertsView,
    safetyCarImpactView: SafetyCarImpactView,
    isSessionActive: () -> Boolean = { false }
) {
    get("/ping") {
        call.respond(HttpStatusCode.OK, "pong")
    }

    get("/health") {
        val sessionStatus = if (isSessionActive()) "ACTIVE" else "IDLE"
        call.respond(HttpStatusCode.OK, HealthDto(status = "ok", session = sessionStatus))
    }

    get("/api/events/live") {
        liveEventView.handle(call)
    }

    get("/api/events/replay/{sessionKey}") {
        replayEventView.handle(call)
    }

    get("/api/sessions/latest") {
        latestSessionView.handle(call)
    }

    get("/api/sessions/{key}/state") {
        sessionStateView.handle(call)
    }

    get("/api/meetings") {
        meetingsView.handleList(call)
    }

    get("/api/meetings/current") {
        meetingsView.handleCurrent(call)
    }

    get("/api/sessions/{sessionKey}/charts") {
        sessionChartsView.handle(call)
    }

    get("/api/weekend") {
        weekendView.handle(call)
    }

    get("/api/weekend/summary") {
        weekendSummaryView.handle(call)
    }

    get("/api/weekend/progression") {
        lapTimeProgressionView.handle(call)
    }

    get("/api/sessions/{sessionKey}/tyre-degradation") {
        tyreDegradationView.handle(call)
    }

    get("/api/sessions/{sessionKey}/race-pace") {
        racePaceView.handle(call)
    }

    get("/api/sessions/{sessionKeyA}/sector-comparison/{sessionKeyB}") {
        sectorComparisonView.handle(call)
    }

    get("/api/races/{raceKey}/strategy/preview") {
        preRaceStrategyView.handle(call)
    }

    get("/api/sessions/{sessionKey}/strategy/tracker") {
        liveStrategyTrackerView.handle(call)
    }

    get("/api/strategy/watchlist") {
        driverWatchlistView.handleGetGlobal(call)
    }

    put("/api/strategy/watchlist") {
        driverWatchlistView.handleSetGlobal(call)
    }

    get("/api/sessions/{sessionKey}/strategy/watchlist") {
        driverWatchlistView.handleGetSession(call)
    }

    put("/api/sessions/{sessionKey}/strategy/watchlist") {
        driverWatchlistView.handleSetSession(call)
    }

    get("/api/sessions/{sessionKey}/strategy/alerts") {
        strategyAlertsView.handle(call)
    }

    get("/api/sessions/{sessionKey}/strategy/safety-car/live") {
        safetyCarImpactView.handleLive(call)
    }

    get("/api/sessions/{sessionKey}/strategy/safety-car/review") {
        safetyCarImpactView.handleReview(call)
    }
}
