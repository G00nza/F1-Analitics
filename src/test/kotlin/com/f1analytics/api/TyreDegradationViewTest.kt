package com.f1analytics.api

import com.f1analytics.api.dto.TyreDegradationDto
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TyreDegradationViewTest : ViewTestBase() {

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `GET returns hasStintData false when no stints exist for session`() = testApp { client ->
        insertRace()
        insertSession(key = 9001, raceKey = 1, type = "FP2")

        val dto = client.get("/api/sessions/9001/tyre-degradation").body<TyreDegradationDto>()

        assertFalse(dto.hasStintData)
        assertTrue(dto.longRuns.isEmpty())
        assertTrue(dto.shortRuns.isEmpty())
    }

    @Test
    fun `GET returns hasStintData true when stints exist`() = testApp { client ->
        insertRace()
        insertSession(key = 9001, raceKey = 1, type = "FP2")
        insertStint(sessionKey = 9001, driverNumber = "1", stintNumber = 1, lapStart = 1, lapEnd = 10)

        val dto = client.get("/api/sessions/9001/tyre-degradation").body<TyreDegradationDto>()

        assertTrue(dto.hasStintData)
    }

    @Test
    fun `GET includes stint in longRuns when it has more than 5 valid laps`() = testApp { client ->
        insertRace()
        insertSession(key = 9001, raceKey = 1, type = "FP2")
        insertSessionDriver(sessionKey = 9001, number = "1", code = "VER")
        insertStint(sessionKey = 9001, driverNumber = "1", stintNumber = 1, compound = "HARD", lapStart = 1, lapEnd = 8)
        (1..6).forEach { i -> insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = i, lapTimeMs = 90000 + i * 100) }

        val dto = client.get("/api/sessions/9001/tyre-degradation").body<TyreDegradationDto>()

        assertEquals(1, dto.longRuns.size)
        assertTrue(dto.shortRuns.isEmpty())
        val run = dto.longRuns.single()
        assertEquals("VER", run.driverCode)
        assertEquals("HARD", run.compound)
        assertEquals(6, run.lapCount)
    }

    @Test
    fun `GET includes stint in shortRuns when it has 5 or fewer valid laps`() = testApp { client ->
        insertRace()
        insertSession(key = 9001, raceKey = 1, type = "FP2")
        insertSessionDriver(sessionKey = 9001, number = "1", code = "VER")
        insertStint(sessionKey = 9001, driverNumber = "1", stintNumber = 1, compound = "SOFT", lapStart = 1, lapEnd = 5)
        (1..5).forEach { i -> insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = i, lapTimeMs = 90000) }

        val dto = client.get("/api/sessions/9001/tyre-degradation").body<TyreDegradationDto>()

        assertTrue(dto.longRuns.isEmpty())
        assertEquals(1, dto.shortRuns.size)
        val run = dto.shortRuns.single()
        assertEquals("VER", run.driverCode)
        assertEquals("SOFT", run.compound)
        assertEquals(5, run.lapCount)
    }

    @Test
    fun `GET excludes pit-out and pit-in laps from lap count and calculation`() = testApp { client ->
        insertRace()
        insertSession(key = 9001, raceKey = 1, type = "FP2")
        insertSessionDriver(sessionKey = 9001, number = "1", code = "VER")
        insertStint(sessionKey = 9001, driverNumber = "1", stintNumber = 1, lapStart = 1, lapEnd = 9)
        // pit-out + 6 normal + pit-in = 8 laps inserted; only 6 valid
        insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = 1, lapTimeMs = 80000, pitOutLap = true)
        (2..7).forEach { i -> insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = i, lapTimeMs = 90000) }
        insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = 8, lapTimeMs = 80000, pitInLap = true)

        val dto = client.get("/api/sessions/9001/tyre-degradation").body<TyreDegradationDto>()

        assertEquals(1, dto.longRuns.size)
        assertEquals(6, dto.longRuns.single().lapCount)
    }

    @Test
    fun `GET excludes laps slower than 107 percent of fastest in stint`() = testApp { client ->
        insertRace()
        insertSession(key = 9001, raceKey = 1, type = "FP2")
        insertSessionDriver(sessionKey = 9001, number = "1", code = "VER")
        // fastest = 90000 → threshold = 96300; lap 8 at 97000 is excluded
        insertStint(sessionKey = 9001, driverNumber = "1", stintNumber = 1, lapStart = 1, lapEnd = 8)
        (1..6).forEach { i -> insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = i, lapTimeMs = 90000) }
        insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = 7, lapTimeMs = 97000) // excluded
        insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = 8, lapTimeMs = 90200)

        val dto = client.get("/api/sessions/9001/tyre-degradation").body<TyreDegradationDto>()

        assertEquals(1, dto.longRuns.size)
        assertEquals(7, dto.longRuns.single().lapCount) // 7 valid (laps 1–6 + lap 8)
    }

    @Test
    fun `GET computes degPerLapMs correctly`() = testApp { client ->
        insertRace()
        insertSession(key = 9001, raceKey = 1, type = "FP2")
        insertSessionDriver(sessionKey = 9001, number = "1", code = "VER")
        // 7 laps increasing by 100ms each → deg = (90600 - 90000) / (7 - 1) = 100.0
        insertStint(sessionKey = 9001, driverNumber = "1", stintNumber = 1, lapStart = 1, lapEnd = 7)
        (0..6).forEach { i -> insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = i + 1, lapTimeMs = 90000 + i * 100) }

        val dto = client.get("/api/sessions/9001/tyre-degradation").body<TyreDegradationDto>()

        val run = dto.longRuns.single()
        assertEquals(100.0, run.degPerLapMs)
        assertEquals(90000, run.firstLapMs)
        assertEquals(90600, run.lastLapMs)
    }

    @Test
    fun `GET firstLapMs and lastLapMs are based on valid laps sorted by lap number`() = testApp { client ->
        insertRace()
        insertSession(key = 9001, raceKey = 1, type = "FP2")
        insertSessionDriver(sessionKey = 9001, number = "1", code = "VER")
        insertStint(sessionKey = 9001, driverNumber = "1", stintNumber = 1, lapStart = 1, lapEnd = 8)
        insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = 1, lapTimeMs = 85000, pitOutLap = true) // excluded
        (2..7).forEach { i -> insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = i, lapTimeMs = 90000 + (i - 2) * 50) }

        val dto = client.get("/api/sessions/9001/tyre-degradation").body<TyreDegradationDto>()

        val run = dto.longRuns.single()
        assertEquals(90000, run.firstLapMs) // lap 2 (first valid)
        assertEquals(90250, run.lastLapMs)  // lap 7 (last valid)
    }

    @Test
    fun `GET driverCode falls back to driverNumber when no session driver record`() = testApp { client ->
        insertRace()
        insertSession(key = 9001, raceKey = 1, type = "FP2")
        // no insertSessionDriver for driver "99"
        insertStint(sessionKey = 9001, driverNumber = "99", stintNumber = 1, lapStart = 1, lapEnd = 8)
        (1..6).forEach { i -> insertLap(sessionKey = 9001, driverNumber = "99", lapNumber = i, lapTimeMs = 90000) }

        val dto = client.get("/api/sessions/9001/tyre-degradation").body<TyreDegradationDto>()

        assertEquals("99", dto.longRuns.single().driverCode)
    }
}
