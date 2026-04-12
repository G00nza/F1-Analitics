package com.f1analytics.api

import com.f1analytics.api.dto.RacePaceDto
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RacePaceViewTest : ViewTestBase() {

    private fun setupLongRun(
        sessionKey: Int,
        driverNumber: String,
        code: String,
        team: String?,
        stintNumber: Int = 1,
        baseLapMs: Int = 90000,
        lapCount: Int = 7,
    ) {
        insertSessionDriver(sessionKey = sessionKey, number = driverNumber, code = code, team = team)
        insertStint(sessionKey = sessionKey, driverNumber = driverNumber, stintNumber = stintNumber,
            lapStart = 1, lapEnd = lapCount)
        (1..lapCount).forEach { i ->
            insertLap(sessionKey = sessionKey, driverNumber = driverNumber, lapNumber = i, lapTimeMs = baseLapMs)
        }
    }

    @Test
    fun `GET returns 400 when session key is invalid`() = testApp { client ->
        val response = client.get("/api/sessions/abc/race-pace")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET returns warning message`() = testApp { client ->
        insertRace()
        insertSession(key = 9001, raceKey = 1, type = "FP2")

        val dto = client.get("/api/sessions/9001/race-pace").body<RacePaceDto>()

        assertTrue(dto.warning.isNotBlank())
    }

    @Test
    fun `GET returns hasStintData false when no stints exist`() = testApp { client ->
        insertRace()
        insertSession(key = 9001, raceKey = 1, type = "FP2")

        val dto = client.get("/api/sessions/9001/race-pace").body<RacePaceDto>()

        assertEquals(false, dto.hasStintData)
        assertTrue(dto.teams.isEmpty())
    }

    @Test
    fun `GET ranks teams by best average long run pace`() = testApp { client ->
        insertRace()
        insertSession(key = 9001, raceKey = 1, type = "FP2")
        // Red Bull faster than Ferrari
        setupLongRun(9001, "1", "VER", "Red Bull", baseLapMs = 90000)
        setupLongRun(9001, "16", "LEC", "Ferrari", stintNumber = 1, baseLapMs = 90500)

        val dto = client.get("/api/sessions/9001/race-pace").body<RacePaceDto>()

        assertEquals(2, dto.teams.size)
        assertEquals("Red Bull", dto.teams[0].team)
        assertEquals(1, dto.teams[0].rank)
        assertEquals("Ferrari", dto.teams[1].team)
        assertEquals(2, dto.teams[1].rank)
    }

    @Test
    fun `GET gapToLeaderMs is null for rank 1 and positive for others`() = testApp { client ->
        insertRace()
        insertSession(key = 9001, raceKey = 1, type = "FP2")
        setupLongRun(9001, "1",  "VER", "Red Bull", baseLapMs = 90000)
        setupLongRun(9001, "16", "LEC", "Ferrari",  stintNumber = 1, baseLapMs = 90500)

        val dto = client.get("/api/sessions/9001/race-pace").body<RacePaceDto>()

        assertNull(dto.teams[0].gapToLeaderMs)
        assertEquals(500, dto.teams[1].gapToLeaderMs)
    }

    @Test
    fun `GET excludes drivers with no team from team ranking`() = testApp { client ->
        insertRace()
        insertSession(key = 9001, raceKey = 1, type = "FP2")
        setupLongRun(9001, "1", "VER", team = null, baseLapMs = 89000) // no team → excluded
        setupLongRun(9001, "16", "LEC", "Ferrari", stintNumber = 1, baseLapMs = 90500)

        val dto = client.get("/api/sessions/9001/race-pace").body<RacePaceDto>()

        assertEquals(1, dto.teams.size)
        assertEquals("Ferrari", dto.teams.single().team)
    }

    @Test
    fun `GET uses best driver average when a team has multiple drivers`() = testApp { client ->
        insertRace()
        insertSession(key = 9001, raceKey = 1, type = "FP2")
        // Both drivers in Red Bull; Perez is slower — team should show Verstappen's pace
        setupLongRun(9001, "1",  "VER", "Red Bull", stintNumber = 1, baseLapMs = 90000)
        setupLongRun(9001, "11", "PER", "Red Bull", stintNumber = 2, baseLapMs = 90800)

        val dto = client.get("/api/sessions/9001/race-pace").body<RacePaceDto>()

        assertEquals(1, dto.teams.size)
        assertEquals(90000, dto.teams.single().avgLapMs)
    }

    @Test
    fun `GET SC-flagged laps are excluded from team pace calculation`() = testApp { client ->
        insertRace()
        insertSession(key = 9001, raceKey = 1, type = "FP2")
        // 8-lap stint; lap 4 under SC → avgLapMs computed over remaining 7 laps
        insertSessionDriver(sessionKey = 9001, number = "1", code = "VER", team = "Red Bull")
        insertStint(sessionKey = 9001, driverNumber = "1", stintNumber = 1, lapStart = 1, lapEnd = 8)
        (1..7).forEach { i -> insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = i, lapTimeMs = 90000) }
        // lap 8 is the SC lap — slower but the flag is what removes it, not the time
        insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = 8, lapTimeMs = 95000)
        insertRaceControlMessage(sessionKey = 9001, lapNumber = 8, flag = "SC")

        val dto = client.get("/api/sessions/9001/race-pace").body<RacePaceDto>()

        // Only the 7 normal laps at 90000ms contribute; SC lap excluded
        assertEquals(1, dto.teams.size)
        assertEquals(90000, dto.teams.single().avgLapMs)
    }

    @Test
    fun `GET stint with too few valid laps after flag exclusion is not counted`() = testApp { client ->
        insertRace()
        insertSession(key = 9001, raceKey = 1, type = "FP2")
        // 7-lap stint; 2 flagged → 5 remain → not a long run → team has no long runs → excluded
        insertSessionDriver(sessionKey = 9001, number = "1", code = "VER", team = "Red Bull")
        insertStint(sessionKey = 9001, driverNumber = "1", stintNumber = 1, lapStart = 1, lapEnd = 7)
        (1..7).forEach { i -> insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = i, lapTimeMs = 90000) }
        insertRaceControlMessage(sessionKey = 9001, lapNumber = 3, flag = "SC")
        insertRaceControlMessage(sessionKey = 9001, lapNumber = 5, flag = "YELLOW")

        val dto = client.get("/api/sessions/9001/race-pace").body<RacePaceDto>()

        assertTrue(dto.teams.isEmpty())
    }
}
