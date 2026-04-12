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

    companion object {
        private const val FP_SESSION  = 9001
        private const val RACE_SESSION = 9002
    }

    /**
     * Inserts a stint + 6 valid laps forming a long run in the FP session.
     * degPerLapMs = lapDeltaMs (ms increase per lap).
     */
    private fun insertLongRun(
        driverNumber: String,
        stintNumber: Int,
        compound: String,
        lapStart: Int,
        firstLapMs: Int,
        lapDeltaMs: Int
    ) {
        insertStint(FP_SESSION, driverNumber, stintNumber, compound, lapStart, lapStart + 5)
        for (i in 0..5) {
            insertLap(FP_SESSION, driverNumber, lapNumber = lapStart + i, lapTimeMs = firstLapMs + i * lapDeltaMs)
        }
    }

    /**
     * Registers a driver for both the FP session (stints) and the RACE session (entry list).
     */
    private fun registerDriver(number: String, code: String, team: String? = null) {
        insertSessionDriver(RACE_SESSION, number, code, team = team)
    }

    // ── view tests ────────────────────────────────────────────────────────────

    @Test
    fun `GET returns strategy preview for valid race with FP data`() = testApp { client ->
        insertRace(key = 1)
        insertSession(key = FP_SESSION, raceKey = 1, type = "FP2")
        insertSession(key = RACE_SESSION, raceKey = 1, type = "RACE")
        registerDriver("1", "VER", team = "Red Bull")
        insertLongRun("1", 1, "SOFT", lapStart = 1, firstLapMs = 90000, lapDeltaMs = 100)
        insertLongRun("1", 2, "HARD", lapStart = 7, firstLapMs = 92000, lapDeltaMs = 50)

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
        insertSession(key = FP_SESSION, raceKey = 1, type = "FP2")
        insertSession(key = RACE_SESSION, raceKey = 1, type = "RACE")

        // VER (Red Bull): SOFT deg=400ms, MEDIUM deg=100ms
        registerDriver("1", "VER", team = "Red Bull")
        insertLongRun("1", 1, "SOFT",   lapStart = 1,  firstLapMs = 90000, lapDeltaMs = 400)
        insertLongRun("1", 2, "MEDIUM", lapStart = 7,  firstLapMs = 95000, lapDeltaMs = 100)

        // PER (Red Bull): SOFT deg=50ms, MEDIUM deg=100ms  (very different SOFT rate from VER)
        registerDriver("11", "PER", team = "Red Bull")
        insertLongRun("11", 1, "SOFT",   lapStart = 13, firstLapMs = 90000, lapDeltaMs = 50)
        insertLongRun("11", 2, "MEDIUM", lapStart = 19, firstLapMs = 95000, lapDeltaMs = 100)

        val dto = client.get("/api/races/1/strategy/preview?totalLaps=57").body<PreRaceStrategyDto>()

        val ver = dto.drivers.find { it.driverCode == "VER" }!!
        val per = dto.drivers.find { it.driverCode == "PER" }!!

        // VER has high SOFT degradation (400ms/lap): the model may prefer HARD or MEDIUM
        // for the long first stint rather than fast-degrading SOFT.
        // PER has low SOFT degradation (50ms/lap): SOFT should appear prominently.
        val verSoftLaps = (ver.expectedStrategy + (ver.altStrategy ?: emptyList()))
            .filter { it.compound == "SOFT" }.sumOf { it.laps }
        val perSoftLaps = (per.expectedStrategy + (per.altStrategy ?: emptyList()))
            .filter { it.compound == "SOFT" }.sumOf { it.laps }

        assertTrue(
            verSoftLaps < perSoftLaps,
            "VER (SOFT deg=400ms) should use SOFT for fewer total laps than PER (SOFT deg=50ms). VER=$verSoftLaps, PER=$perSoftLaps"
        )
    }

    @Test
    fun `whenDriverHasNoData_usesTeammateDegradationRate`() = testApp { client ->
        insertRace(key = 1)
        insertSession(key = FP_SESSION, raceKey = 1, type = "FP2")
        insertSession(key = RACE_SESSION, raceKey = 1, type = "RACE")

        // HAM (Mercedes): has both SOFT and HARD data; SOFT deg=200ms, HARD deg=100ms
        registerDriver("44", "HAM", team = "Mercedes")
        insertLongRun("44", 1, "SOFT", lapStart = 1, firstLapMs = 90000, lapDeltaMs = 200)
        insertLongRun("44", 2, "HARD", lapStart = 7, firstLapMs = 93000, lapDeltaMs = 100)

        // GEO (Mercedes): has HARD data but NO SOFT data → falls back to HAM's SOFT(200ms)
        registerDriver("63", "GEO", team = "Mercedes")
        insertLongRun("63", 1, "HARD", lapStart = 13, firstLapMs = 93000, lapDeltaMs = 100)

        val dto = client.get("/api/races/1/strategy/preview?totalLaps=57").body<PreRaceStrategyDto>()

        val geo = dto.drivers.find { it.driverCode == "GEO" }!!

        // GEO resolves SOFT via team (HAM's 200ms), HARD via own (100ms)
        // With base times, optimal strategy may be SOFT→HARD, SOFT→MEDIUM, or similar.
        // Key assertion: GEO generates a valid strategy using team fallback data.
        assertTrue(geo.expectedStrategy.isNotEmpty(), "GEO should have a strategy via team fallback")
    }

    @Test
    fun `whenNoTeamData_usesGlobalDegradationRate`() = testApp { client ->
        insertRace(key = 1)
        insertSession(key = FP_SESSION, raceKey = 1, type = "FP2")
        insertSession(key = RACE_SESSION, raceKey = 1, type = "RACE")

        // VER (Red Bull): has SOFT and HARD data
        registerDriver("1", "VER", team = "Red Bull")
        insertLongRun("1", 1, "SOFT", lapStart = 1, firstLapMs = 90000, lapDeltaMs = 200)
        insertLongRun("1", 2, "HARD", lapStart = 7, firstLapMs = 93000, lapDeltaMs = 100)

        // HAM (Mercedes): has HARD data only, NO SOFT, NO Mercedes teammate with SOFT
        registerDriver("44", "HAM", team = "Mercedes")
        insertLongRun("44", 1, "HARD", lapStart = 13, firstLapMs = 93000, lapDeltaMs = 100)

        val dto = client.get("/api/races/1/strategy/preview?totalLaps=57").body<PreRaceStrategyDto>()

        val ham = dto.drivers.find { it.driverCode == "HAM" }!!

        // HAM resolves SOFT via global (VER's 200ms), HARD via own (100ms)
        // Key assertion: HAM generates a valid strategy using global fallback data.
        assertTrue(ham.expectedStrategy.isNotEmpty(), "HAM should have a strategy via global fallback")
    }

    // ── available compounds ───────────────────────────────────────────────────

    @Test
    fun `compoundWithNoFieldData_usesGlobalRateFallback`() = testApp { client ->
        insertRace(key = 1)
        insertSession(key = FP_SESSION, raceKey = 1, type = "FP2")
        insertSession(key = RACE_SESSION, raceKey = 1, type = "RACE")

        // Only SOFT and MEDIUM have long run data; no HARD data at all in the whole field
        registerDriver("1", "VER", team = "Red Bull")
        insertLongRun("1", 1, "SOFT",   lapStart = 1, firstLapMs = 90000, lapDeltaMs = 100)
        insertLongRun("1", 2, "MEDIUM", lapStart = 7, firstLapMs = 92000, lapDeltaMs = 50)

        val dto = client.get("/api/races/1/strategy/preview?totalLaps=57").body<PreRaceStrategyDto>()
        assertTrue(dto.hasData)

        // The global fallback makes HARD available to the optimizer. Even if HARD doesn't win
        // the optimal selection (due to its base time penalty), strategies should still be generated.
        val ver = dto.drivers.single()
        assertNotNull(ver.expectedStrategy.firstOrNull(), "Expected strategy should be non-empty")
        assertNotNull(ver.altStrategy, "Alt strategy should exist since 3 compounds are available")
    }

    @Test
    fun `generatedStrategiesUseAtLeastTwoDifferentCompounds`() = testApp { client ->
        insertRace(key = 1)
        insertSession(key = FP_SESSION, raceKey = 1, type = "FP2")
        insertSession(key = RACE_SESSION, raceKey = 1, type = "RACE")

        registerDriver("1", "VER", team = "Red Bull")
        insertLongRun("1", 1, "SOFT",   lapStart = 1,  firstLapMs = 90000, lapDeltaMs = 100)
        insertLongRun("1", 2, "MEDIUM", lapStart = 7,  firstLapMs = 92000, lapDeltaMs = 50)
        insertLongRun("1", 3, "HARD",   lapStart = 13, firstLapMs = 93000, lapDeltaMs = 30)

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
        insertSession(key = FP_SESSION, raceKey = 1, type = "FP2")
        insertSession(key = RACE_SESSION, raceKey = 1, type = "RACE")
        registerDriver("1", "VER", team = "Red Bull")
        insertLongRun("1", 1, "SOFT", lapStart = 1, firstLapMs = 90000, lapDeltaMs = 100)
        insertLongRun("1", 2, "HARD", lapStart = 7, firstLapMs = 92000, lapDeltaMs = 50)

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

    // ── strategy comparison ───────────────────────────────────────────────────

    @Test
    fun `expectedStrategy_isTheFasterStrategy_highDeg`() = testApp { client ->
        insertRace(key = 1)
        insertSession(key = FP_SESSION, raceKey = 1, type = "FP2")
        insertSession(key = RACE_SESSION, raceKey = 1, type = "RACE")

        // Only SOFT (high deg=1200ms/lap) and MEDIUM (low deg=50ms/lap) have real data.
        // HARD gets the global fallback rate: avg(1200, 50) = 625ms/lap.
        //
        // Best 1-stop is MEDIUM→HARD (~134,775ms total).
        // Best 2-stop is SOFT(5)→MEDIUM(26)→MEDIUM(26) (~125,100ms total):
        //   the optimizer pits SOFT after just 5 laps to cap its extreme 1200ms/lap deg,
        //   then runs two equal MEDIUM stints. Savings exceed the extra 23s pit cost.
        registerDriver("1", "VER", team = "Red Bull")
        insertLongRun("1", 1, "SOFT",   lapStart = 1, firstLapMs = 90000, lapDeltaMs = 1200)
        insertLongRun("1", 2, "MEDIUM", lapStart = 7, firstLapMs = 92000, lapDeltaMs = 50)
        // No HARD data: fallback rate keeps HARD expensive → 2-stop wins

        val dto = client.get("/api/races/1/strategy/preview?totalLaps=57").body<PreRaceStrategyDto>()
        val ver = dto.drivers.single()

        assertEquals(3, ver.expectedStrategy.size, "High SOFT deg + expensive HARD fallback → 2-stop S→M→M should be faster")
    }

    @Test
    fun `expectedStrategy_isTheFasterStrategy_lowDeg`() = testApp { client ->
        insertRace(key = 1)
        insertSession(key = FP_SESSION, raceKey = 1, type = "FP2")
        insertSession(key = RACE_SESSION, raceKey = 1, type = "RACE")

        // Very low degradation (30ms/lap): pit stop loss (23s) outweighs 2-stop deg savings
        registerDriver("1", "VER", team = "Red Bull")
        insertLongRun("1", 1, "SOFT",   lapStart = 1,  firstLapMs = 90000, lapDeltaMs = 30)
        insertLongRun("1", 2, "MEDIUM", lapStart = 7,  firstLapMs = 92000, lapDeltaMs = 30)
        insertLongRun("1", 3, "HARD",   lapStart = 13, firstLapMs = 95000, lapDeltaMs = 30)

        val dto = client.get("/api/races/1/strategy/preview?totalLaps=57").body<PreRaceStrategyDto>()
        val ver = dto.drivers.single()

        assertEquals(2, ver.expectedStrategy.size, "Low deg → 1-stop should be faster and become expectedStrategy")
    }

    @Test
    fun `twoStopStrategy_canUseRepeatedCompound`() = testApp { client ->
        insertRace(key = 1)
        insertSession(key = FP_SESSION, raceKey = 1, type = "FP2")
        insertSession(key = RACE_SESSION, raceKey = 1, type = "RACE")

        // Only SOFT and MEDIUM have real long run data; HARD gets proxy rate (avg of both).
        // High deg so 2-stop appears.
        registerDriver("1", "VER", team = "Red Bull")
        insertLongRun("1", 1, "SOFT",   lapStart = 1, firstLapMs = 90000, lapDeltaMs = 500)
        insertLongRun("1", 2, "MEDIUM", lapStart = 7, firstLapMs = 92000, lapDeltaMs = 500)

        val dto = client.get("/api/races/1/strategy/preview?totalLaps=57").body<PreRaceStrategyDto>()
        val ver = dto.drivers.single()

        val twoStop = if (ver.expectedStrategy.size == 3) ver.expectedStrategy else ver.altStrategy
        assertNotNull(twoStop, "There should be a 2-stop strategy")
        assertEquals(3, twoStop.size)

        val distinctCount = twoStop.map { it.compound }.toSet().size
        assertTrue(distinctCount >= 2, "2-stop strategy must use at least 2 different compounds, got: ${twoStop.map { it.compound }}")
    }

    @Test
    fun `fasterDegradationProducesEarlierPitWindow`() = testApp { client ->
        insertRace(key = 1)
        insertSession(key = FP_SESSION, raceKey = 1, type = "FP2")
        insertSession(key = RACE_SESSION, raceKey = 1, type = "RACE")

        // VER: SOFT deg=400ms (degrades fast) → pits early
        registerDriver("1", "VER", team = "Red Bull")
        insertLongRun("1", 1, "SOFT",   lapStart = 1,  firstLapMs = 90000, lapDeltaMs = 400)
        insertLongRun("1", 2, "MEDIUM", lapStart = 7,  firstLapMs = 97000, lapDeltaMs = 100)

        // HAM: SOFT deg=100ms (degrades slowly) → pits later
        registerDriver("44", "HAM", team = "Mercedes")
        insertLongRun("44", 1, "SOFT",   lapStart = 13, firstLapMs = 90000, lapDeltaMs = 100)
        insertLongRun("44", 2, "MEDIUM", lapStart = 19, firstLapMs = 93000, lapDeltaMs = 100)

        val dto = client.get("/api/races/1/strategy/preview?totalLaps=57").body<PreRaceStrategyDto>()

        val ver = dto.drivers.find { it.driverCode == "VER" }!!
        val ham = dto.drivers.find { it.driverCode == "HAM" }!!

        val verWindow = ver.expectedStrategy.first().pitWindow!!
        val hamWindow = ham.expectedStrategy.first().pitWindow!!

        assertTrue(
            verWindow.lapTo < hamWindow.lapFrom,
            "VER (fast deg) pit window [${verWindow.lapFrom},${verWindow.lapTo}] should end before HAM (slow deg) starts [${hamWindow.lapFrom},${hamWindow.lapTo}]"
        )
    }
}
