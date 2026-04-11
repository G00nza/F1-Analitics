package com.f1analytics.api

import com.f1analytics.api.dto.WeekendSummaryDto
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeekendSummaryViewTest : ViewTestBase() {

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun setupRaceWithSessions() {
        insertRace(key = 1, name = "Bahrain Grand Prix", year = 2026, dateStart = "2026-03-16")
        insertSession(key = 9001, raceKey = 1, name = "Practice 1",  type = "FP1",       status = "Finished")
        insertSession(key = 9002, raceKey = 1, name = "Practice 2",  type = "FP2",       status = "Finished")
        insertSession(key = 9003, raceKey = 1, name = "Qualifying",  type = "QUALIFYING", status = "Finished")
        insertSession(key = 9004, raceKey = 1, name = "Race",        type = "RACE",       status = "Finished")
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `GET returns 404 when no races exist`() = testApp { client ->
        val response = client.get("/api/weekend/summary")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET returns summary of most recent past race when no active race`() = testApp { client ->
        insertRace(key = 1, name = "Old Race",  year = 2024, dateStart = "2024-01-01")
        insertRace(key = 2, name = "Last Race", year = 2025, dateStart = "2025-03-01")
        insertSession(key = 9001, raceKey = 2, name = "Race", type = "RACE", status = "Finished")
        insertLap(sessionKey = 9001, driverNumber = "1", lapTimeMs = 90000)
        insertSessionDriver(sessionKey = 9001, number = "1", code = "VER")

        val dto = client.get("/api/weekend/summary").body<WeekendSummaryDto>()

        assertEquals("Last Race", dto.meetingName)
        assertEquals(2025, dto.year)
    }

    @Test
    fun `GET returns race metadata`() = testApp { client ->
        insertRace(key = 5, name = "Monaco Grand Prix", year = 2026, dateStart = "2026-03-25")
        insertSession(key = 9001, raceKey = 5, name = "Race", type = "RACE", status = "Finished")
        insertLap(sessionKey = 9001, driverNumber = "1", lapTimeMs = 75000)
        insertSessionDriver(sessionKey = 9001, number = "1", code = "VER")

        val dto = client.get("/api/weekend/summary").body<WeekendSummaryDto>()

        assertEquals(5,                  dto.raceKey)
        assertEquals("Monaco Grand Prix", dto.meetingName)
        assertEquals(2026,               dto.year)
    }

    @Test
    fun `GET excludes sessions with no lap data`() = testApp { client ->
        setupRaceWithSessions()
        // Only FP1 and QUALIFYING have laps
        insertLap(sessionKey = 9001, driverNumber = "1", lapTimeMs = 90000)
        insertLap(sessionKey = 9003, driverNumber = "1", lapTimeMs = 88000)
        insertSessionDriver(sessionKey = 9001, number = "1", code = "VER")
        insertSessionDriver(sessionKey = 9003, number = "1", code = "VER")

        val dto = client.get("/api/weekend/summary").body<WeekendSummaryDto>()

        assertEquals(listOf("FP1", "QUALIFYING"), dto.sessions)
    }

    @Test
    fun `GET lists available sessions in chronological order`() = testApp { client ->
        insertRace(key = 1, name = "Bahrain Grand Prix", year = 2026, dateStart = "2026-03-16")
        // Insert sessions out of order
        insertSession(key = 9004, raceKey = 1, name = "Race",       type = "RACE",       status = "Finished")
        insertSession(key = 9001, raceKey = 1, name = "Practice 1", type = "FP1",        status = "Finished")
        insertSession(key = 9003, raceKey = 1, name = "Qualifying", type = "QUALIFYING",  status = "Finished")
        insertSession(key = 9002, raceKey = 1, name = "Practice 2", type = "FP2",        status = "Finished")

        listOf(9001, 9002, 9003, 9004).forEach { key ->
            insertLap(sessionKey = key, driverNumber = "1", lapTimeMs = 90000)
            insertSessionDriver(sessionKey = key, number = "1", code = "VER")
        }

        val dto = client.get("/api/weekend/summary").body<WeekendSummaryDto>()

        assertEquals(listOf("FP1", "FP2", "QUALIFYING", "RACE"), dto.sessions)
    }

    @Test
    fun `GET ranks drivers by best lap time within each session`() = testApp { client ->
        insertRace(key = 1, name = "Bahrain Grand Prix", year = 2026, dateStart = "2026-03-16")
        insertSession(key = 9001, raceKey = 1, name = "Practice 1", type = "FP1", status = "Finished")

        insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = 1, lapTimeMs = 91000)
        insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = 2, lapTimeMs = 90000) // VER best = 90s
        insertLap(sessionKey = 9001, driverNumber = "4", lapNumber = 1, lapTimeMs = 89000) // NOR best = 89s -> P1

        insertSessionDriver(sessionKey = 9001, number = "1", code = "VER")
        insertSessionDriver(sessionKey = 9001, number = "4", code = "NOR")

        val dto = client.get("/api/weekend/summary").body<WeekendSummaryDto>()

        val norRow = dto.drivers.first { it.driverCode == "NOR" }
        val verRow = dto.drivers.first { it.driverCode == "VER" }

        assertEquals(1, norRow.sessionData["FP1"]!!.position)
        assertEquals(2, verRow.sessionData["FP1"]!!.position)
    }

    @Test
    fun `GET leader has null gap, second driver has positive gapToLeaderMs`() = testApp { client ->
        insertRace(key = 1, name = "Bahrain Grand Prix", year = 2026, dateStart = "2026-03-16")
        insertSession(key = 9001, raceKey = 1, name = "Practice 1", type = "FP1", status = "Finished")

        insertLap(sessionKey = 9001, driverNumber = "1", lapTimeMs = 90000) // leader
        insertLap(sessionKey = 9001, driverNumber = "4", lapTimeMs = 90500) // +500ms

        insertSessionDriver(sessionKey = 9001, number = "1", code = "VER")
        insertSessionDriver(sessionKey = 9001, number = "4", code = "NOR")

        val dto = client.get("/api/weekend/summary").body<WeekendSummaryDto>()

        val verEntry = dto.drivers.first { it.driverCode == "VER" }.sessionData["FP1"]!!
        val norEntry = dto.drivers.first { it.driverCode == "NOR" }.sessionData["FP1"]!!

        assertNull(verEntry.gapToLeaderMs)
        assertEquals(500, norEntry.gapToLeaderMs)
    }

    @Test
    fun `GET works with partial weekend where only early sessions have data`() = testApp { client ->
        setupRaceWithSessions()
        // Only FP1 and FP2 have laps — QUALIFYING and RACE have none (race in progress or future)
        insertLap(sessionKey = 9001, driverNumber = "1", lapTimeMs = 90000)
        insertLap(sessionKey = 9002, driverNumber = "1", lapTimeMs = 89500)
        insertSessionDriver(sessionKey = 9001, number = "1", code = "VER")
        insertSessionDriver(sessionKey = 9002, number = "1", code = "VER")

        val dto = client.get("/api/weekend/summary").body<WeekendSummaryDto>()

        assertEquals(listOf("FP1", "FP2"), dto.sessions)
        assertTrue(dto.drivers.isNotEmpty())
        assertEquals("VER", dto.drivers.first().driverCode)
    }

    @Test
    fun `GET marks isBestPosition for each driver best result across sessions`() = testApp { client ->
        setupRaceWithSessions()
        // VER: P2 in FP1, P1 in FP2
        // NOR: P1 in FP1, P2 in FP2
        insertLap(sessionKey = 9001, driverNumber = "1", lapTimeMs = 90500) // VER FP1 -> P2
        insertLap(sessionKey = 9001, driverNumber = "4", lapTimeMs = 90000) // NOR FP1 -> P1

        insertLap(sessionKey = 9002, driverNumber = "1", lapTimeMs = 89000) // VER FP2 -> P1
        insertLap(sessionKey = 9002, driverNumber = "4", lapTimeMs = 89500) // NOR FP2 -> P2

        insertSessionDriver(sessionKey = 9001, number = "1", code = "VER")
        insertSessionDriver(sessionKey = 9001, number = "4", code = "NOR")
        insertSessionDriver(sessionKey = 9002, number = "1", code = "VER")
        insertSessionDriver(sessionKey = 9002, number = "4", code = "NOR")

        val dto = client.get("/api/weekend/summary").body<WeekendSummaryDto>()

        val verRow = dto.drivers.first { it.driverCode == "VER" }
        val norRow = dto.drivers.first { it.driverCode == "NOR" }

        // VER best is FP2 (P1), FP1 is not best
        assertEquals(false, verRow.sessionData["FP1"]!!.isBestPosition)
        assertEquals(true,  verRow.sessionData["FP2"]!!.isBestPosition)

        // NOR best is FP1 (P1), FP2 is not best
        assertEquals(true,  norRow.sessionData["FP1"]!!.isBestPosition)
        assertEquals(false, norRow.sessionData["FP2"]!!.isBestPosition)
    }
}
