package com.f1analytics.api

import com.f1analytics.api.dto.SectorComparisonDto
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SectorComparisonViewTest : ViewTestBase() {

    private val url = "/api/sessions/9001/sector-comparison/9002"

    private fun setupSessions() {
        insertRace(key = 1)
        insertSession(key = 9001, raceKey = 1, name = "Practice 3", type = "FP3")
        insertSession(key = 9002, raceKey = 1, name = "Qualifying", type = "QUALIFYING")
    }

    @Test
    fun `GET returns 400 when driver param is missing`() = testApp { client ->
        val response = client.get(url)
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET returns 404 when driver has no laps in either session`() = testApp { client ->
        setupSessions()
        val response = client.get("$url?driver=1")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET returns correct deltaMs values for each sector`() = testApp { client ->
        setupSessions()
        insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = 1, lapTimeMs = 90000,
            sector1Ms = 26000, sector2Ms = 32000, sector3Ms = 24000)
        insertLap(sessionKey = 9002, driverNumber = "1", lapNumber = 1, lapTimeMs = 88000,
            sector1Ms = 25673, sector2Ms = 31500, sector3Ms = 23000)
        insertSessionDriver(sessionKey = 9001, number = "1", code = "VER")

        val dto = client.get("$url?driver=1").body<SectorComparisonDto>()

        assertEquals(3, dto.sectors.size)
        val s1 = dto.sectors.first { it.sector == 1 }
        assertEquals(26000, s1.sessionAMs)
        assertEquals(25673, s1.sessionBMs)
        assertEquals(25673 - 26000, s1.deltaMs)

        val s2 = dto.sectors.first { it.sector == 2 }
        assertEquals(-500, s2.deltaMs)   // 31500 - 32000

        val s3 = dto.sectors.first { it.sector == 3 }
        assertEquals(-1000, s3.deltaMs) // 23000 - 24000
    }

    @Test
    fun `GET mostImprovedSector is the sector with the most negative delta`() = testApp { client ->
        setupSessions()
        insertLap(sessionKey = 9001, driverNumber = "1", lapTimeMs = 90000,
            sector1Ms = 26000, sector2Ms = 32000, sector3Ms = 24000)
        insertLap(sessionKey = 9002, driverNumber = "1", lapTimeMs = 88000,
            sector1Ms = 25800, sector2Ms = 31000, sector3Ms = 23800) // S2 improved by -1000 (most)
        insertSessionDriver(sessionKey = 9001, number = "1", code = "VER")

        val dto = client.get("$url?driver=1").body<SectorComparisonDto>()

        assertEquals(2, dto.mostImprovedSector) // S2 delta = -1000
    }

    @Test
    fun `GET leastImprovedSector is the sector with the least negative delta`() = testApp { client ->
        setupSessions()
        insertLap(sessionKey = 9001, driverNumber = "1", lapTimeMs = 90000,
            sector1Ms = 26000, sector2Ms = 32000, sector3Ms = 24000)
        insertLap(sessionKey = 9002, driverNumber = "1", lapTimeMs = 89800,
            sector1Ms = 25800, sector2Ms = 31000, sector3Ms = 24100) // S3 got worse by +100
        insertSessionDriver(sessionKey = 9001, number = "1", code = "VER")

        val dto = client.get("$url?driver=1").body<SectorComparisonDto>()

        assertEquals(3, dto.leastImprovedSector) // S3 delta = +100
    }

    @Test
    fun `GET totalDeltaMs is sum of all sector deltas when all are present`() = testApp { client ->
        setupSessions()
        insertLap(sessionKey = 9001, driverNumber = "1", lapTimeMs = 90000,
            sector1Ms = 26000, sector2Ms = 32000, sector3Ms = 24000)
        insertLap(sessionKey = 9002, driverNumber = "1", lapTimeMs = 88000,
            sector1Ms = 25500, sector2Ms = 31500, sector3Ms = 23700)
        insertSessionDriver(sessionKey = 9001, number = "1", code = "VER")

        val dto = client.get("$url?driver=1").body<SectorComparisonDto>()

        // -500 + -500 + -300 = -1300
        assertEquals(-1300, dto.totalDeltaMs)
    }

    @Test
    fun `GET sector deltaMs is null when sector time is missing in one session`() = testApp { client ->
        setupSessions()
        // sector1Ms is null in sessionA
        insertLap(sessionKey = 9001, driverNumber = "1", lapTimeMs = 90000,
            sector1Ms = null, sector2Ms = 32000, sector3Ms = 24000)
        insertLap(sessionKey = 9002, driverNumber = "1", lapTimeMs = 88000,
            sector1Ms = 25500, sector2Ms = 31500, sector3Ms = 23700)
        insertSessionDriver(sessionKey = 9001, number = "1", code = "VER")

        val dto = client.get("$url?driver=1").body<SectorComparisonDto>()

        val s1 = dto.sectors.first { it.sector == 1 }
        assertNull(s1.deltaMs)
        assertNull(s1.sessionAMs)
        assertEquals(25500, s1.sessionBMs)
    }

    @Test
    fun `GET totalDeltaMs is null when any sector delta is missing`() = testApp { client ->
        setupSessions()
        insertLap(sessionKey = 9001, driverNumber = "1", lapTimeMs = 90000,
            sector1Ms = null, sector2Ms = 32000, sector3Ms = 24000)
        insertLap(sessionKey = 9002, driverNumber = "1", lapTimeMs = 88000,
            sector1Ms = 25500, sector2Ms = 31500, sector3Ms = 23700)
        insertSessionDriver(sessionKey = 9001, number = "1", code = "VER")

        val dto = client.get("$url?driver=1").body<SectorComparisonDto>()

        assertNull(dto.totalDeltaMs)
    }

    @Test
    fun `GET mostImprovedSector and leastImprovedSector are null when no sector has full data`() = testApp { client ->
        setupSessions()
        // All sector times null in sessionA
        insertLap(sessionKey = 9001, driverNumber = "1", lapTimeMs = 90000,
            sector1Ms = null, sector2Ms = null, sector3Ms = null)
        insertLap(sessionKey = 9002, driverNumber = "1", lapTimeMs = 88000,
            sector1Ms = 25500, sector2Ms = 31500, sector3Ms = 23700)
        insertSessionDriver(sessionKey = 9001, number = "1", code = "VER")

        val dto = client.get("$url?driver=1").body<SectorComparisonDto>()

        assertNull(dto.mostImprovedSector)
        assertNull(dto.leastImprovedSector)
    }

    @Test
    fun `GET driverCode falls back to driverNumber when no session driver record exists`() = testApp { client ->
        setupSessions()
        insertLap(sessionKey = 9001, driverNumber = "99", lapTimeMs = 90000,
            sector1Ms = 26000, sector2Ms = 32000, sector3Ms = 24000)
        insertLap(sessionKey = 9002, driverNumber = "99", lapTimeMs = 88000,
            sector1Ms = 25500, sector2Ms = 31500, sector3Ms = 23700)
        // no insertSessionDriver for driver "99"

        val dto = client.get("/api/sessions/9001/sector-comparison/9002?driver=99").body<SectorComparisonDto>()

        assertEquals("99", dto.driverCode)
    }

    @Test
    fun `GET uses best lap from each session to read sector times`() = testApp { client ->
        setupSessions()
        // SessionA: two laps — lap 2 is faster (best lap)
        insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = 1, lapTimeMs = 92000,
            sector1Ms = 27000, sector2Ms = 33000, sector3Ms = 25000)
        insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = 2, lapTimeMs = 90000,
            sector1Ms = 26000, sector2Ms = 32000, sector3Ms = 24000) // best
        // SessionB: one lap
        insertLap(sessionKey = 9002, driverNumber = "1", lapNumber = 1, lapTimeMs = 88000,
            sector1Ms = 25500, sector2Ms = 31500, sector3Ms = 23700)
        insertSessionDriver(sessionKey = 9001, number = "1", code = "VER")

        val dto = client.get("$url?driver=1").body<SectorComparisonDto>()

        val s1 = dto.sectors.first { it.sector == 1 }
        assertEquals(26000, s1.sessionAMs) // from best lap (lap 2), not lap 1's 27000
    }

    @Test
    fun `GET works when driver only has laps in sessionB`() = testApp { client ->
        setupSessions()
        // No laps in sessionA for driver "1", only in sessionB
        insertLap(sessionKey = 9002, driverNumber = "1", lapTimeMs = 88000,
            sector1Ms = 25500, sector2Ms = 31500, sector3Ms = 23700)
        insertSessionDriver(sessionKey = 9002, number = "1", code = "VER")

        val dto = client.get("$url?driver=1").body<SectorComparisonDto>()

        assertNotNull(dto)
        assertEquals("VER", dto.driverCode)
        dto.sectors.forEach { sector ->
            assertNull(sector.sessionAMs)
            assertNull(sector.deltaMs)
        }
    }
}
