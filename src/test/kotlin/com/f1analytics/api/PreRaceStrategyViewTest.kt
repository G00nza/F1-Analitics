package com.f1analytics.api

import com.f1analytics.api.dto.PreRaceStrategyDto
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreRaceStrategyViewTest : ViewTestBase() {

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Inserts a stint + 6 valid laps forming a long run.
     * degPerLapMs = lapDeltaMs (ms increase per lap).
     */
    private fun insertLongRun(
        sessionKey: Int,
        driverNumber: String,
        stintNumber: Int,
        compound: String,
        lapStart: Int,
        firstLapMs: Int,
        lapDeltaMs: Int
    ) {
        insertStint(sessionKey, driverNumber, stintNumber, compound, lapStart, lapStart + 5)
        for (i in 0..5) {
            insertLap(sessionKey, driverNumber, lapNumber = lapStart + i, lapTimeMs = firstLapMs + i * lapDeltaMs)
        }
    }

    // ── view tests ────────────────────────────────────────────────────────────

    @Test
    fun `GET returns strategy preview for valid race with FP data`() = testApp { client ->
        insertRace(key = 1)
        insertSession(key = 9001, raceKey = 1, type = "FP2")
        insertSessionDriver(9001, "1", "VER", team = "Red Bull")
        insertLongRun(9001, "1", 1, "SOFT", lapStart = 1, firstLapMs = 90000, lapDeltaMs = 100)
        insertLongRun(9001, "1", 2, "HARD", lapStart = 7, firstLapMs = 92000, lapDeltaMs = 50)

        val response = client.get("/api/races/1/strategy/preview?totalLaps=57")
        assertEquals(HttpStatusCode.OK, response.status)

        val dto = response.body<PreRaceStrategyDto>()
        assertEquals(1, dto.raceKey)
        assertEquals(57, dto.totalLaps)
        assertTrue(dto.hasData)
        assertEquals(1, dto.drivers.size)

        val ver = dto.drivers.single()
        assertEquals("VER", ver.driverCode)
        assertEquals(2, ver.expectedStrategy.size)
        assertNotNull(ver.expectedStrategy.first().pitWindow)
        assertNull(ver.expectedStrategy.last().pitWindow)
    }

    @Test
    fun `GET returns 400 when totalLaps query param is missing`() = testApp { client ->
        insertRace(key = 1)

        val response = client.get("/api/races/1/strategy/preview")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET returns 404 when race does not exist`() = testApp { client ->
        val response = client.get("/api/races/999/strategy/preview?totalLaps=57")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET returns hasData false when race has no FP sessions`() = testApp { client ->
        insertRace(key = 1)

        val dto = client.get("/api/races/1/strategy/preview?totalLaps=57").body<PreRaceStrategyDto>()
        assertTrue(!dto.hasData)
        assertTrue(dto.drivers.isEmpty())
    }

    // ── priority: driver > team > global ─────────────────────────────────────

    @Test
    fun `whenDriverHasOwnLongRun_usesDriverDegradationRate`() = testApp { client ->
        insertRace(key = 1)
        insertSession(key = 9001, raceKey = 1, type = "FP2")

        // VER (Red Bull): SOFT deg=400ms, MEDIUM deg=100ms
        insertSessionDriver(9001, "1", "VER", team = "Red Bull")
        insertLongRun(9001, "1", 1, "SOFT",   lapStart = 1,  firstLapMs = 90000, lapDeltaMs = 400)
        insertLongRun(9001, "1", 2, "MEDIUM", lapStart = 7,  firstLapMs = 95000, lapDeltaMs = 100)

        // PER (Red Bull): SOFT deg=50ms, MEDIUM deg=100ms  (very different SOFT rate from VER)
        insertSessionDriver(9001, "11", "PER", team = "Red Bull")
        insertLongRun(9001, "11", 1, "SOFT",   lapStart = 13, firstLapMs = 90000, lapDeltaMs = 50)
        insertLongRun(9001, "11", 2, "MEDIUM", lapStart = 19, firstLapMs = 95000, lapDeltaMs = 100)

        val dto = client.get("/api/races/1/strategy/preview?totalLaps=57").body<PreRaceStrategyDto>()

        val ver = dto.drivers.find { it.driverCode == "VER" }!!
        val per = dto.drivers.find { it.driverCode == "PER" }!!

        // VER uses own SOFT(400ms): SOFT stint ≈ 11 laps  (round(57*100/(400+100)) = 11)
        val verSoftLaps = ver.expectedStrategy.find { it.compound == "SOFT" }!!.laps
        assertTrue(verSoftLaps in 9..13, "VER SOFT laps expected ~11, got $verSoftLaps")

        // PER uses own SOFT(50ms): SOFT stint ≈ 38 laps  (round(57*100/(100+50)) = 38)
        val perSoftLaps = per.expectedStrategy.find { it.compound == "SOFT" }!!.laps
        assertTrue(perSoftLaps in 36..40, "PER SOFT laps expected ~38, got $perSoftLaps")
    }

    @Test
    fun `whenDriverHasNoData_usesTeammateDegradationRate`() = testApp { client ->
        insertRace(key = 1)
        insertSession(key = 9001, raceKey = 1, type = "FP2")

        // HAM (Mercedes): has both SOFT and HARD data; SOFT deg=200ms, HARD deg=100ms
        insertSessionDriver(9001, "44", "HAM", team = "Mercedes")
        insertLongRun(9001, "44", 1, "SOFT", lapStart = 1, firstLapMs = 90000, lapDeltaMs = 200)
        insertLongRun(9001, "44", 2, "HARD", lapStart = 7, firstLapMs = 93000, lapDeltaMs = 100)

        // GEO (Mercedes): has HARD data but NO SOFT data → falls back to HAM's SOFT(200ms)
        insertSessionDriver(9001, "63", "GEO", team = "Mercedes")
        insertLongRun(9001, "63", 1, "HARD", lapStart = 13, firstLapMs = 93000, lapDeltaMs = 100)

        val dto = client.get("/api/races/1/strategy/preview?totalLaps=57").body<PreRaceStrategyDto>()

        val geo = dto.drivers.find { it.driverCode == "GEO" }!!

        // GEO resolves SOFT via team (HAM's 200ms), HARD via own (100ms)
        // SOFT(200) first → pitLap = round(57*100/(200+100)) = round(19) = 19
        val geoSoftLaps = geo.expectedStrategy.find { it.compound == "SOFT" }!!.laps
        assertTrue(geoSoftLaps in 17..21, "GEO SOFT laps expected ~19 (team fallback), got $geoSoftLaps")
    }

    @Test
    fun `whenNoTeamData_usesGlobalDegradationRate`() = testApp { client ->
        insertRace(key = 1)
        insertSession(key = 9001, raceKey = 1, type = "FP2")

        // VER (Red Bull): has SOFT and HARD data
        insertSessionDriver(9001, "1", "VER", team = "Red Bull")
        insertLongRun(9001, "1", 1, "SOFT", lapStart = 1, firstLapMs = 90000, lapDeltaMs = 200)
        insertLongRun(9001, "1", 2, "HARD", lapStart = 7, firstLapMs = 93000, lapDeltaMs = 100)

        // HAM (Mercedes): has HARD data only, NO SOFT, NO Mercedes teammate with SOFT
        insertSessionDriver(9001, "44", "HAM", team = "Mercedes")
        insertLongRun(9001, "44", 1, "HARD", lapStart = 13, firstLapMs = 93000, lapDeltaMs = 100)

        val dto = client.get("/api/races/1/strategy/preview?totalLaps=57").body<PreRaceStrategyDto>()

        val ham = dto.drivers.find { it.driverCode == "HAM" }!!

        // HAM resolves SOFT via global (VER's 200ms), HARD via own (100ms)
        // SOFT(200) first → pitLap = round(57*100/(200+100)) = 19
        val hamSoftLaps = ham.expectedStrategy.find { it.compound == "SOFT" }!!.laps
        assertTrue(hamSoftLaps in 17..21, "HAM SOFT laps expected ~19 (global fallback), got $hamSoftLaps")
    }

    // ── available compounds ───────────────────────────────────────────────────

    @Test
    fun `compoundWithNoLongRunData_isExcludedFromStrategies`() = testApp { client ->
        insertRace(key = 1)
        insertSession(key = 9001, raceKey = 1, type = "FP2")

        // Only SOFT and MEDIUM have long run data; no HARD data at all
        insertSessionDriver(9001, "1", "VER", team = "Red Bull")
        insertLongRun(9001, "1", 1, "SOFT",   lapStart = 1, firstLapMs = 90000, lapDeltaMs = 100)
        insertLongRun(9001, "1", 2, "MEDIUM", lapStart = 7, firstLapMs = 92000, lapDeltaMs = 50)

        val dto = client.get("/api/races/1/strategy/preview?totalLaps=57").body<PreRaceStrategyDto>()

        val ver = dto.drivers.single()
        val compounds = ver.expectedStrategy.map { it.compound }.toSet()
        assertTrue("HARD" !in compounds, "HARD should be excluded as it has no long run data")
        assertTrue("SOFT" in compounds || "MEDIUM" in compounds)
    }

    @Test
    fun `generatedStrategiesUseAtLeastTwoDifferentCompounds`() = testApp { client ->
        insertRace(key = 1)
        insertSession(key = 9001, raceKey = 1, type = "FP2")

        insertSessionDriver(9001, "1", "VER", team = "Red Bull")
        insertLongRun(9001, "1", 1, "SOFT",   lapStart = 1, firstLapMs = 90000, lapDeltaMs = 100)
        insertLongRun(9001, "1", 2, "MEDIUM", lapStart = 7, firstLapMs = 92000, lapDeltaMs = 50)
        insertLongRun(9001, "1", 3, "HARD",   lapStart = 13, firstLapMs = 93000, lapDeltaMs = 30)

        val dto = client.get("/api/races/1/strategy/preview?totalLaps=57").body<PreRaceStrategyDto>()

        val ver = dto.drivers.single()
        val expectedCompounds = ver.expectedStrategy.map { it.compound }.toSet()
        assertTrue(expectedCompounds.size >= 2, "Expected strategy must use at least 2 different compounds")

        ver.altStrategy?.let { alt ->
            val altCompounds = alt.map { it.compound }.toSet()
            assertTrue(altCompounds.size >= 2, "Alt strategy must use at least 2 different compounds")
        }
    }

    // ── pit window ────────────────────────────────────────────────────────────

    @Test
    fun `pitWindowIsReturnedAsLapRange`() = testApp { client ->
        insertRace(key = 1)
        insertSession(key = 9001, raceKey = 1, type = "FP2")
        insertSessionDriver(9001, "1", "VER", team = "Red Bull")
        insertLongRun(9001, "1", 1, "SOFT", lapStart = 1, firstLapMs = 90000, lapDeltaMs = 100)
        insertLongRun(9001, "1", 2, "HARD", lapStart = 7, firstLapMs = 92000, lapDeltaMs = 50)

        val dto = client.get("/api/races/1/strategy/preview?totalLaps=57").body<PreRaceStrategyDto>()
        val stints = dto.drivers.single().expectedStrategy

        val pitStint = stints.first()
        val lastStint = stints.last()

        assertNull(lastStint.pitWindow)
        val window = pitStint.pitWindow
        assertNotNull(window)
        assertTrue(window.lapFrom < window.lapTo)
        assertTrue(window.lapFrom >= 1)
        assertTrue(window.lapTo < 57)
    }

    @Test
    fun `fasterDegradationProducesEarlierPitWindow`() = testApp { client ->
        insertRace(key = 1)
        insertSession(key = 9001, raceKey = 1, type = "FP2")

        // VER: SOFT deg=400ms (degrades fast) → pits early
        insertSessionDriver(9001, "1", "VER", team = "Red Bull")
        insertLongRun(9001, "1", 1, "SOFT",   lapStart = 1,  firstLapMs = 90000, lapDeltaMs = 400)
        insertLongRun(9001, "1", 2, "MEDIUM", lapStart = 7,  firstLapMs = 97000, lapDeltaMs = 100)

        // HAM: SOFT deg=100ms (degrades slowly) → pits later
        insertSessionDriver(9001, "44", "HAM", team = "Mercedes")
        insertLongRun(9001, "44", 1, "SOFT",   lapStart = 13, firstLapMs = 90000, lapDeltaMs = 100)
        insertLongRun(9001, "44", 2, "MEDIUM", lapStart = 19, firstLapMs = 93000, lapDeltaMs = 100)

        val dto = client.get("/api/races/1/strategy/preview?totalLaps=57").body<PreRaceStrategyDto>()

        val ver = dto.drivers.find { it.driverCode == "VER" }!!
        val ham = dto.drivers.find { it.driverCode == "HAM" }!!

        // VER: SOFT(400) first → pitLap = round(57*100/500) = 11 → window [9, 13]
        // HAM: SOFT(100) first → pitLap = round(57*100/200) = 29 → window [27, 31]
        val verWindow = ver.expectedStrategy.first().pitWindow!!
        val hamWindow = ham.expectedStrategy.first().pitWindow!!

        assertTrue(
            verWindow.lapTo < hamWindow.lapFrom,
            "VER (fast deg) pit window [${verWindow.lapFrom},${verWindow.lapTo}] should end before HAM (slow deg) starts [${hamWindow.lapFrom},${hamWindow.lapTo}]"
        )
    }
}
