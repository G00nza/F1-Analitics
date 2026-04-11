package com.f1analytics.api

import com.f1analytics.api.dto.LapTimeProgressionDto
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LapTimeProgressionViewTest : ViewTestBase() {

    private fun setupRaceWithSessions() {
        insertRace(key = 1, name = "Bahrain Grand Prix", year = 2026, dateStart = "2026-03-16")
        insertSession(key = 9001, raceKey = 1, name = "Practice 1",  type = "FP1",        status = "Finished")
        insertSession(key = 9002, raceKey = 1, name = "Practice 2",  type = "FP2",        status = "Finished")
        insertSession(key = 9003, raceKey = 1, name = "Qualifying",  type = "QUALIFYING",  status = "Finished")
        insertSession(key = 9004, raceKey = 1, name = "Race",        type = "RACE",        status = "Finished")
    }

    @Test
    fun `GET returns 404 when no races exist`() = testApp { client ->
        val response = client.get("/api/weekend/progression")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET response includes fpDataWarning field`() = testApp { client ->
        insertRace(key = 1, name = "Bahrain Grand Prix", year = 2026, dateStart = "2026-03-16")
        insertSession(key = 9001, raceKey = 1, name = "Practice 1", type = "FP1", status = "Finished")
        insertLap(sessionKey = 9001, driverNumber = "1", lapTimeMs = 92000)
        insertSessionDriver(sessionKey = 9001, number = "1", code = "VER")

        val dto = client.get("/api/weekend/progression").body<LapTimeProgressionDto>()

        assertTrue(dto.fpDataWarning.isNotBlank())
    }

    @Test
    fun `GET returns sessions ordered by session type`() = testApp { client ->
        insertRace(key = 1, name = "Bahrain Grand Prix", year = 2026, dateStart = "2026-03-16")
        // Insert out of order
        insertSession(key = 9003, raceKey = 1, name = "Qualifying", type = "QUALIFYING", status = "Finished")
        insertSession(key = 9001, raceKey = 1, name = "Practice 1", type = "FP1",        status = "Finished")
        insertSession(key = 9002, raceKey = 1, name = "Practice 2", type = "FP2",        status = "Finished")

        listOf(9001, 9002, 9003).forEach { key ->
            insertLap(sessionKey = key, driverNumber = "1", lapTimeMs = 90000)
            insertSessionDriver(sessionKey = key, number = "1", code = "VER")
        }

        val dto = client.get("/api/weekend/progression").body<LapTimeProgressionDto>()

        assertEquals(listOf("FP1", "FP2", "QUALIFYING"), dto.sessions)
    }

    @Test
    fun `GET excludes sessions with no lap data`() = testApp { client ->
        setupRaceWithSessions()
        // Only FP1 and QUALIFYING have laps
        insertLap(sessionKey = 9001, driverNumber = "1", lapTimeMs = 92000)
        insertLap(sessionKey = 9003, driverNumber = "1", lapTimeMs = 89000)
        insertSessionDriver(sessionKey = 9001, number = "1", code = "VER")
        insertSessionDriver(sessionKey = 9003, number = "1", code = "VER")

        val dto = client.get("/api/weekend/progression").body<LapTimeProgressionDto>()

        assertEquals(listOf("FP1", "QUALIFYING"), dto.sessions)
    }

    @Test
    fun `GET uses best lap per session per driver`() = testApp { client ->
        insertRace(key = 1, name = "Bahrain Grand Prix", year = 2026, dateStart = "2026-03-16")
        insertSession(key = 9001, raceKey = 1, name = "Practice 1", type = "FP1", status = "Finished")

        insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = 1, lapTimeMs = 92000)
        insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = 2, lapTimeMs = 90000) // personal best
        insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = 3, lapTimeMs = 91000)
        insertSessionDriver(sessionKey = 9001, number = "1", code = "VER")

        val dto = client.get("/api/weekend/progression").body<LapTimeProgressionDto>()

        val verRow = dto.drivers.first { it.driverCode == "VER" }
        assertEquals(90000, verRow.lapTimes["FP1"])
    }

    @Test
    fun `GET includes null lapTime for driver absent from a session`() = testApp { client ->
        insertRace(key = 1, name = "Bahrain Grand Prix", year = 2026, dateStart = "2026-03-16")
        insertSession(key = 9001, raceKey = 1, name = "Practice 1", type = "FP1",       status = "Finished")
        insertSession(key = 9002, raceKey = 1, name = "Qualifying",  type = "QUALIFYING", status = "Finished")

        insertLap(sessionKey = 9001, driverNumber = "1", lapTimeMs = 92000)
        insertSessionDriver(sessionKey = 9001, number = "1", code = "VER")

        // Only FP1 has VER data; QUALIFYING has NOR
        insertLap(sessionKey = 9002, driverNumber = "4", lapTimeMs = 89000)
        insertSessionDriver(sessionKey = 9002, number = "4", code = "NOR")

        val dto = client.get("/api/weekend/progression").body<LapTimeProgressionDto>()

        val verRow = dto.drivers.first { it.driverCode == "VER" }
        assertEquals(92000, verRow.lapTimes["FP1"])
        assertNull(verRow.lapTimes["QUALIFYING"])

        val norRow = dto.drivers.first { it.driverCode == "NOR" }
        assertNull(norRow.lapTimes["FP1"])
        assertEquals(89000, norRow.lapTimes["QUALIFYING"])
    }

    @Test
    fun `GET computes deltaFp1ToQualiMs when both FP1 and QUALIFYING data exist`() = testApp { client ->
        insertRace(key = 1, name = "Bahrain Grand Prix", year = 2026, dateStart = "2026-03-16")
        insertSession(key = 9001, raceKey = 1, name = "Practice 1", type = "FP1",        status = "Finished")
        insertSession(key = 9003, raceKey = 1, name = "Qualifying",  type = "QUALIFYING",  status = "Finished")

        insertLap(sessionKey = 9001, driverNumber = "1", lapTimeMs = 92000)
        insertLap(sessionKey = 9003, driverNumber = "1", lapTimeMs = 89000)
        insertSessionDriver(sessionKey = 9001, number = "1", code = "VER")
        insertSessionDriver(sessionKey = 9003, number = "1", code = "VER")

        val dto = client.get("/api/weekend/progression").body<LapTimeProgressionDto>()

        val verRow = dto.drivers.first { it.driverCode == "VER" }
        assertEquals(-3000, verRow.deltaFp1ToQualiMs) // 89000 - 92000 = -3000ms
    }

    @Test
    fun `GET deltaFp1ToQualiMs is null when FP1 is missing`() = testApp { client ->
        insertRace(key = 1, name = "Bahrain Grand Prix", year = 2026, dateStart = "2026-03-16")
        insertSession(key = 9003, raceKey = 1, name = "Qualifying", type = "QUALIFYING", status = "Finished")

        insertLap(sessionKey = 9003, driverNumber = "1", lapTimeMs = 89000)
        insertSessionDriver(sessionKey = 9003, number = "1", code = "VER")

        val dto = client.get("/api/weekend/progression").body<LapTimeProgressionDto>()

        val verRow = dto.drivers.first { it.driverCode == "VER" }
        assertNull(verRow.deltaFp1ToQualiMs)
    }

    @Test
    fun `GET deltaFp1ToQualiMs is null when QUALIFYING is missing`() = testApp { client ->
        insertRace(key = 1, name = "Bahrain Grand Prix", year = 2026, dateStart = "2026-03-16")
        insertSession(key = 9001, raceKey = 1, name = "Practice 1", type = "FP1", status = "Finished")

        insertLap(sessionKey = 9001, driverNumber = "1", lapTimeMs = 92000)
        insertSessionDriver(sessionKey = 9001, number = "1", code = "VER")

        val dto = client.get("/api/weekend/progression").body<LapTimeProgressionDto>()

        val verRow = dto.drivers.first { it.driverCode == "VER" }
        assertNull(verRow.deltaFp1ToQualiMs)
    }

    @Test
    fun `GET works with partial weekend`() = testApp { client ->
        setupRaceWithSessions()
        // Only FP1 and FP2 have laps
        insertLap(sessionKey = 9001, driverNumber = "1", lapTimeMs = 92000)
        insertLap(sessionKey = 9002, driverNumber = "1", lapTimeMs = 91500)
        insertSessionDriver(sessionKey = 9001, number = "1", code = "VER")
        insertSessionDriver(sessionKey = 9002, number = "1", code = "VER")

        val dto = client.get("/api/weekend/progression").body<LapTimeProgressionDto>()

        assertEquals(listOf("FP1", "FP2"), dto.sessions)
        assertTrue(dto.drivers.isNotEmpty())
        val verRow = dto.drivers.first()
        assertEquals(92000, verRow.lapTimes["FP1"])
        assertEquals(91500, verRow.lapTimes["FP2"])
        assertNull(verRow.deltaFp1ToQualiMs)
    }
}
