package com.f1analytics.api

import com.f1analytics.api.views.LatestSessionView
import com.f1analytics.api.views.LiveEventView
import com.f1analytics.api.views.MeetingsView
import com.f1analytics.api.views.ReplayEventView
import com.f1analytics.api.views.SessionStateView
import com.f1analytics.core.service.LiveSessionStateManager
import com.f1analytics.core.service.SessionResolver
import com.f1analytics.data.db.DatabaseFactory
import com.f1analytics.data.db.repository.ExposedLapRepository
import com.f1analytics.data.db.repository.ExposedPositionRepository
import com.f1analytics.data.db.repository.ExposedRaceControlRepository
import com.f1analytics.data.db.repository.ExposedRaceRepository
import com.f1analytics.data.db.repository.ExposedReplayRepository
import com.f1analytics.data.db.repository.ExposedSessionDriverRepository
import com.f1analytics.data.db.repository.ExposedSessionRepository
import com.f1analytics.data.db.repository.ExposedStintRepository
import com.f1analytics.data.db.repository.ExposedWeatherRepository
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class MeetingsViewTest : ViewTestBase() {

    @Test
    fun `GET meetings returns empty list when no races for requested year`() = testApp {
        val response = client.get("/api/meetings?year=2020")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText())
    }

    @Test
    fun `GET meetings returns meetings with sessions for the given year`() = testApp {
        insertSession(key = 9001, name = "Race", type = "RACE")
        insertSession(key = 9002, name = "Qualifying", type = "QUALIFYING")

        val response = client.get("/api/meetings?year=2026")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "\"name\":\"Bahrain Grand Prix\"")
        assertContains(body, "\"circuit\":\"Bahrain International Circuit\"")
        assertContains(body, "\"name\":\"Race\"")
        assertContains(body, "\"name\":\"Qualifying\"")
    }

    @Test
    fun `GET meetings returns race with empty sessions list when race has no sessions`() = testApp {
        val response = client.get("/api/meetings?year=2026")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "\"key\":1")
        assertContains(body, "\"sessions\":[]")
    }

    @Test
    fun `GET meetings current returns 204 when no races exist`() {
        val emptyDbFile = File.createTempFile("f1empty", ".db")
        try {
            val emptyDb = DatabaseFactory.init("jdbc:sqlite:${emptyDbFile.absolutePath}")
            val emptyStateManager = LiveSessionStateManager(
                driverRepo      = ExposedSessionDriverRepository(emptyDb),
                lapRepo         = ExposedLapRepository(emptyDb),
                stintRepo       = ExposedStintRepository(emptyDb),
                raceControlRepo = ExposedRaceControlRepository(emptyDb),
                weatherRepo     = ExposedWeatherRepository(emptyDb),
                positionRepo    = ExposedPositionRepository(emptyDb)
            )
            testApplication {
                install(ContentNegotiation) { json() }
                routing {
                    liveSessionRoutes(
                        liveEventView     = LiveEventView(SseManager(emptyStateManager)),
                        replayEventView   = ReplayEventView(ExposedReplayRepository(emptyDb)),
                        latestSessionView = LatestSessionView(SessionResolver(ExposedSessionRepository(emptyDb))),
                        sessionStateView  = SessionStateView(emptyStateManager),
                        meetingsView      = MeetingsView(ExposedRaceRepository(emptyDb), ExposedSessionRepository(emptyDb))
                    )
                }
                val response = client.get("/api/meetings/current")
                assertEquals(HttpStatusCode.NoContent, response.status)
            }
        } finally {
            emptyDbFile.delete()
            File("${emptyDbFile.absolutePath}-wal").delete()
            File("${emptyDbFile.absolutePath}-shm").delete()
        }
    }

    @Test
    fun `GET meetings current returns the nearest race with its sessions`() = testApp {
        insertSession(key = 9001, name = "Race", type = "RACE")

        val response = client.get("/api/meetings/current")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "\"name\":\"Bahrain Grand Prix\"")
        assertContains(body, "\"key\":1")
        assertContains(body, "\"name\":\"Race\"")
    }
}
