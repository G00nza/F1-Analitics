package com.f1analytics.api

import com.f1analytics.api.dto.LiveStrategyTrackerDto
import com.f1analytics.core.domain.model.DriverEntry
import com.f1analytics.core.domain.model.DriverLiveData
import com.f1analytics.core.domain.model.LapCountData
import com.f1analytics.core.domain.model.LiveSessionState
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveStrategyTrackerViewTest : ViewTestBase() {

    // ── helpers ───────────────────────────────────────────────────────────────

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

    private fun makeState(
        sessionKey: Int = 9001,
        currentLap: Int = 15,
        drivers: Map<String, DriverEntry> = emptyMap(),
        driverData: Map<String, DriverLiveData> = emptyMap()
    ) = LiveSessionState(
        sessionKey = sessionKey,
        lapCount = LapCountData(current = currentLap, total = null),
        drivers = drivers,
        driverData = driverData
    )

    private fun driverEntry(number: String, code: String, team: String? = null) =
        DriverEntry(number, code, null, null, team, null)

    private fun driverLiveData(
        lapNumber: Int,
        compound: String,
        stintLapStart: Int,
        position: Int = 1,
        inPit: Boolean = false
    ) = DriverLiveData(
        lapNumber = lapNumber,
        currentCompound = compound,
        stintLapStart = stintLapStart,
        position = position,
        inPit = inPit
    )

    // ── view / error handling tests ───────────────────────────────────────────

    @Test
    fun `returns 404 when no live state`() = testApp { client ->
        val response = client.get("/api/sessions/9001/strategy/tracker?totalLaps=57")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `returns 404 when live state is for a different session`() {
        val stateManager = makeStateManager()
        stateManager.injectState(makeState(sessionKey = 9999))

        testApp(stateManager) { client ->
            val response = client.get("/api/sessions/9001/strategy/tracker?totalLaps=57")
            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @Test
    fun `returns 400 when totalLaps is missing`() {
        val stateManager = makeStateManager()
        stateManager.injectState(makeState(sessionKey = 9001))

        testApp(stateManager) { client ->
            val response = client.get("/api/sessions/9001/strategy/tracker")
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun `returns tracker with driver data from live state`() {
        val stateManager = makeStateManager()
        stateManager.injectState(
            makeState(
                sessionKey = 9001,
                currentLap = 12,
                drivers = mapOf("1" to driverEntry("1", "VER", "Red Bull")),
                driverData = mapOf("1" to driverLiveData(lapNumber = 12, compound = "SOFT", stintLapStart = 5, position = 1))
            )
        )

        testApp(stateManager) { client ->
            insertRace(key = 1)
            insertSession(key = 9001, raceKey = 1, type = "RACE")

            val dto = client.get("/api/sessions/9001/strategy/tracker?totalLaps=57").body<LiveStrategyTrackerDto>()

            assertEquals(HttpStatusCode.OK, client.get("/api/sessions/9001/strategy/tracker?totalLaps=57").status)
            assertEquals(9001, dto.sessionKey)
            assertEquals(12, dto.currentLap)
            assertEquals(57, dto.totalLaps)

            val ver = dto.drivers.single()
            assertEquals("VER", ver.driverCode)
            assertEquals(1, ver.position)
            assertEquals("SOFT", ver.compound)
            assertEquals(8, ver.stintLaps)  // 12 - 5 + 1
            assertFalse(ver.inPit)
        }
    }

    // ── FP window ─────────────────────────────────────────────────────────────

    @Test
    fun `fpWindowIsComputedFromFPDegradationData`() {
        val stateManager = makeStateManager()
        stateManager.injectState(
            makeState(
                sessionKey = 9001,
                currentLap = 15,
                drivers = mapOf("1" to driverEntry("1", "VER", "Red Bull")),
                driverData = mapOf("1" to driverLiveData(lapNumber = 15, compound = "SOFT", stintLapStart = 1))
            )
        )

        testApp(stateManager) { client ->
            insertRace(key = 1)
            insertSession(key = 9001, raceKey = 1, type = "RACE")
            insertSession(key = 9002, raceKey = 1, type = "FP2")
            insertSessionDriver(9002, "1", "VER", team = "Red Bull")
            // SOFT deg=200ms, HARD deg=50ms
            // bestAlt = HARD(50ms)
            // remainingFromStart = 57 - 1 + 1 = 57
            // optimalStintLength = round(57 * 50 / (200+50)) = round(11.4) = 11
            // fpOptimalPitLap = 1 + 11 - 1 = 11 → fpWindow = [9, 13]
            insertLongRun(9002, "1", 1, "SOFT", lapStart = 1, firstLapMs = 90000, lapDeltaMs = 200)
            insertLongRun(9002, "1", 2, "HARD", lapStart = 7, firstLapMs = 94000, lapDeltaMs = 50)

            val dto = client.get("/api/sessions/9001/strategy/tracker?totalLaps=57").body<LiveStrategyTrackerDto>()
            val ver = dto.drivers.single()
            val window = ver.fpWindow
            assertNotNull(window)
            assertEquals(9, window.lapFrom)
            assertEquals(13, window.lapTo)
        }
    }

    @Test
    fun `fpWindowIsNullWhenNoFpDataForCurrentCompound`() {
        val stateManager = makeStateManager()
        stateManager.injectState(
            makeState(
                sessionKey = 9001,
                currentLap = 10,
                drivers = mapOf("1" to driverEntry("1", "VER", "Red Bull")),
                driverData = mapOf("1" to driverLiveData(lapNumber = 10, compound = "SOFT", stintLapStart = 1))
            )
        )

        testApp(stateManager) { client ->
            insertRace(key = 1)
            insertSession(key = 9001, raceKey = 1, type = "RACE")
            insertSession(key = 9002, raceKey = 1, type = "FP2")
            insertSessionDriver(9002, "1", "VER", team = "Red Bull")
            // Only HARD data in FP, no SOFT data at all
            insertLongRun(9002, "1", 1, "HARD", lapStart = 1, firstLapMs = 93000, lapDeltaMs = 50)

            val dto = client.get("/api/sessions/9001/strategy/tracker?totalLaps=57").body<LiveStrategyTrackerDto>()
            assertNull(dto.drivers.single().fpWindow)
        }
    }

    // ── Real window ───────────────────────────────────────────────────────────

    @Test
    fun `realWindowIsComputedFromRaceStintLaps`() {
        val stateManager = makeStateManager()
        stateManager.injectState(
            makeState(
                sessionKey = 9001,
                currentLap = 15,
                drivers = mapOf("1" to driverEntry("1", "VER", "Red Bull")),
                driverData = mapOf("1" to driverLiveData(lapNumber = 15, compound = "SOFT", stintLapStart = 1))
            )
        )

        testApp(stateManager) { client ->
            insertRace(key = 1)
            insertSession(key = 9001, raceKey = 1, type = "RACE")
            insertSession(key = 9002, raceKey = 1, type = "FP2")
            insertSessionDriver(9002, "1", "VER", team = "Red Bull")
            // FP: SOFT deg=200ms, HARD deg=50ms → fpOptimalPitLap=11
            insertLongRun(9002, "1", 1, "SOFT", lapStart = 1, firstLapMs = 90000, lapDeltaMs = 200)
            insertLongRun(9002, "1", 2, "HARD", lapStart = 7, firstLapMs = 94000, lapDeltaMs = 50)

            // Race stint: SOFT with deg=100ms (different from FP)
            // realOptimalStintLength = round(57 * 50 / (100+50)) = round(19) = 19
            // realOptimalPitLap = 1 + 19 - 1 = 19 → realWindow = [17, 21]
            insertSessionDriver(9001, "1", "VER", team = "Red Bull")
            insertStint(9001, "1", 1, "SOFT", lapStart = 1, lapEnd = 57)
            for (i in 0..5) {
                insertLap(9001, "1", lapNumber = i + 1, lapTimeMs = 91000 + i * 100)
            }

            val dto = client.get("/api/sessions/9001/strategy/tracker?totalLaps=57").body<LiveStrategyTrackerDto>()
            val ver = dto.drivers.single()
            val rw = ver.realWindow
            assertNotNull(rw)
            assertEquals(17, rw.lapFrom)
            assertEquals(21, rw.lapTo)
        }
    }

    @Test
    fun `realWindowIsNullWhenNotEnoughRaceStintLaps`() {
        val stateManager = makeStateManager()
        stateManager.injectState(
            makeState(
                sessionKey = 9001,
                currentLap = 5,
                drivers = mapOf("1" to driverEntry("1", "VER", "Red Bull")),
                driverData = mapOf("1" to driverLiveData(lapNumber = 5, compound = "SOFT", stintLapStart = 1))
            )
        )

        testApp(stateManager) { client ->
            insertRace(key = 1)
            insertSession(key = 9001, raceKey = 1, type = "RACE")
            insertSession(key = 9002, raceKey = 1, type = "FP2")
            insertSessionDriver(9002, "1", "VER", team = "Red Bull")
            insertLongRun(9002, "1", 1, "SOFT", lapStart = 1, firstLapMs = 90000, lapDeltaMs = 200)
            insertLongRun(9002, "1", 2, "HARD", lapStart = 7, firstLapMs = 94000, lapDeltaMs = 50)

            // Only 4 race laps — not enough for a long run (needs > 5)
            insertSessionDriver(9001, "1", "VER", team = "Red Bull")
            insertStint(9001, "1", 1, "SOFT", lapStart = 1, lapEnd = 57)
            for (i in 0..3) {
                insertLap(9001, "1", lapNumber = i + 1, lapTimeMs = 91000 + i * 100)
            }

            val dto = client.get("/api/sessions/9001/strategy/tracker?totalLaps=57").body<LiveStrategyTrackerDto>()
            assertNull(dto.drivers.single().realWindow)
        }
    }

    // ── OVERDUE ───────────────────────────────────────────────────────────────

    @Test
    fun `marksDriverAsOverdueWhenCurrentLapExceedsRealWindow`() {
        val stateManager = makeStateManager()
        // currentLap=25, realWindow=[17,21] → OVERDUE
        stateManager.injectState(
            makeState(
                sessionKey = 9001,
                currentLap = 25,
                drivers = mapOf("1" to driverEntry("1", "VER", "Red Bull")),
                driverData = mapOf("1" to driverLiveData(lapNumber = 25, compound = "SOFT", stintLapStart = 1))
            )
        )

        testApp(stateManager) { client ->
            insertRace(key = 1)
            insertSession(key = 9001, raceKey = 1, type = "RACE")
            insertSession(key = 9002, raceKey = 1, type = "FP2")
            insertSessionDriver(9002, "1", "VER", team = "Red Bull")
            insertLongRun(9002, "1", 1, "SOFT", lapStart = 1, firstLapMs = 90000, lapDeltaMs = 200)
            insertLongRun(9002, "1", 2, "HARD", lapStart = 7, firstLapMs = 94000, lapDeltaMs = 50)

            insertSessionDriver(9001, "1", "VER", team = "Red Bull")
            insertStint(9001, "1", 1, "SOFT", lapStart = 1, lapEnd = 57)
            for (i in 0..5) {
                insertLap(9001, "1", lapNumber = i + 1, lapTimeMs = 91000 + i * 100)
            }

            val dto = client.get("/api/sessions/9001/strategy/tracker?totalLaps=57").body<LiveStrategyTrackerDto>()
            assertTrue(dto.drivers.single().isOverdue)
        }
    }

    @Test
    fun `notOverdueWhenCurrentLapIsWithinWindow`() {
        val stateManager = makeStateManager()
        // currentLap=19, realWindow=[17,21] → not overdue
        stateManager.injectState(
            makeState(
                sessionKey = 9001,
                currentLap = 19,
                drivers = mapOf("1" to driverEntry("1", "VER", "Red Bull")),
                driverData = mapOf("1" to driverLiveData(lapNumber = 19, compound = "SOFT", stintLapStart = 1))
            )
        )

        testApp(stateManager) { client ->
            insertRace(key = 1)
            insertSession(key = 9001, raceKey = 1, type = "RACE")
            insertSession(key = 9002, raceKey = 1, type = "FP2")
            insertSessionDriver(9002, "1", "VER", team = "Red Bull")
            insertLongRun(9002, "1", 1, "SOFT", lapStart = 1, firstLapMs = 90000, lapDeltaMs = 200)
            insertLongRun(9002, "1", 2, "HARD", lapStart = 7, firstLapMs = 94000, lapDeltaMs = 50)

            insertSessionDriver(9001, "1", "VER", team = "Red Bull")
            insertStint(9001, "1", 1, "SOFT", lapStart = 1, lapEnd = 57)
            for (i in 0..5) {
                insertLap(9001, "1", lapNumber = i + 1, lapTimeMs = 91000 + i * 100)
            }

            val dto = client.get("/api/sessions/9001/strategy/tracker?totalLaps=57").body<LiveStrategyTrackerDto>()
            assertFalse(dto.drivers.single().isOverdue)
        }
    }

    // ── Diverge ───────────────────────────────────────────────────────────────

    @Test
    fun `windowsDivergeWhenFpAndRealOptimalDifferByMoreThanTwoLaps`() {
        val stateManager = makeStateManager()
        stateManager.injectState(
            makeState(
                sessionKey = 9001,
                currentLap = 15,
                drivers = mapOf("1" to driverEntry("1", "VER", "Red Bull")),
                driverData = mapOf("1" to driverLiveData(lapNumber = 15, compound = "SOFT", stintLapStart = 1))
            )
        )

        testApp(stateManager) { client ->
            insertRace(key = 1)
            insertSession(key = 9001, raceKey = 1, type = "RACE")
            insertSession(key = 9002, raceKey = 1, type = "FP2")
            insertSessionDriver(9002, "1", "VER", team = "Red Bull")
            // FP: SOFT deg=200ms, HARD=50ms → fpOptimalPitLap = 1 + round(57*50/250) - 1 = 11
            insertLongRun(9002, "1", 1, "SOFT", lapStart = 1, firstLapMs = 90000, lapDeltaMs = 200)
            insertLongRun(9002, "1", 2, "HARD", lapStart = 7, firstLapMs = 94000, lapDeltaMs = 50)

            // Race: SOFT deg=100ms → realOptimalPitLap = 1 + round(57*50/150) - 1 = 19
            // |11 - 19| = 8 > 2 → diverge = true
            insertSessionDriver(9001, "1", "VER", team = "Red Bull")
            insertStint(9001, "1", 1, "SOFT", lapStart = 1, lapEnd = 57)
            for (i in 0..5) {
                insertLap(9001, "1", lapNumber = i + 1, lapTimeMs = 91000 + i * 100)
            }

            val dto = client.get("/api/sessions/9001/strategy/tracker?totalLaps=57").body<LiveStrategyTrackerDto>()
            assertTrue(dto.drivers.single().windowsDiverge)
        }
    }

    @Test
    fun `windowsDoNotDivergeWhenDifferenceIsTwoOrLess`() {
        val stateManager = makeStateManager()
        stateManager.injectState(
            makeState(
                sessionKey = 9001,
                currentLap = 15,
                drivers = mapOf("1" to driverEntry("1", "VER", "Red Bull")),
                driverData = mapOf("1" to driverLiveData(lapNumber = 15, compound = "SOFT", stintLapStart = 1))
            )
        )

        testApp(stateManager) { client ->
            insertRace(key = 1)
            insertSession(key = 9001, raceKey = 1, type = "RACE")
            insertSession(key = 9002, raceKey = 1, type = "FP2")
            insertSessionDriver(9002, "1", "VER", team = "Red Bull")
            // FP: SOFT deg=100ms, HARD=50ms → fpOptimalPitLap = 1 + round(57*50/150) - 1 = 19
            insertLongRun(9002, "1", 1, "SOFT", lapStart = 1, firstLapMs = 90000, lapDeltaMs = 100)
            insertLongRun(9002, "1", 2, "HARD", lapStart = 7, firstLapMs = 94000, lapDeltaMs = 50)

            // Race: SOFT deg=110ms → realOptimalPitLap = 1 + round(57*50/160) - 1 = 18
            // |19 - 18| = 1 ≤ 2 → diverge = false
            insertSessionDriver(9001, "1", "VER", team = "Red Bull")
            insertStint(9001, "1", 1, "SOFT", lapStart = 1, lapEnd = 57)
            for (i in 0..5) {
                insertLap(9001, "1", lapNumber = i + 1, lapTimeMs = 91000 + i * 110)
            }

            val dto = client.get("/api/sessions/9001/strategy/tracker?totalLaps=57").body<LiveStrategyTrackerDto>()
            assertFalse(dto.drivers.single().windowsDiverge)
        }
    }
}
