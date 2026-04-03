package com.f1analytics.data.db.repository

import com.f1analytics.core.domain.model.DriverTimingDelta
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ExposedLapRepositoryTest : RepositoryTestBase() {

    private val repo get() = ExposedLapRepository(db)
    private val ts = Instant.parse("2024-03-02T16:00:00Z")

    @Test
    fun `delta without lapNumber is silently skipped`() = runTest {
        repo.upsertDeltas(sessionKey, mapOf("1" to DriverTimingDelta(lastLapTimeMs = 89_432)), ts)
        assertEquals(0, repo.findBySession(sessionKey).size)
    }

    @Test
    fun `first delta for a lap creates a new row`() = runTest {
        repo.upsertDeltas(sessionKey, mapOf("1" to DriverTimingDelta(lapNumber = 5, lastLapTimeMs = 89_432)), ts)
        val laps = repo.findByDriver(sessionKey, "1")
        assertEquals(1, laps.size)
        assertEquals(5, laps[0].lapNumber)
        assertEquals(89_432, laps[0].lapTimeMs)
    }

    @Test
    fun `sectors arriving in separate messages are merged — no overwrite with null`() = runTest {
        // First message: lap time + sector 1
        repo.upsertDeltas(sessionKey, mapOf(
            "1" to DriverTimingDelta(lapNumber = 3, lastLapTimeMs = 90_000, sector1Ms = 28_000)
        ), ts)
        // Second message: sector 2 only
        repo.upsertDeltas(sessionKey, mapOf(
            "1" to DriverTimingDelta(lapNumber = 3, sector2Ms = 31_000)
        ), ts)
        // Third message: sector 3 only
        repo.upsertDeltas(sessionKey, mapOf(
            "1" to DriverTimingDelta(lapNumber = 3, sector3Ms = 31_000)
        ), ts)

        val lap = repo.findByDriver(sessionKey, "1").single()
        assertEquals(90_000, lap.lapTimeMs,  "lap time should be preserved")
        assertEquals(28_000, lap.sector1Ms,  "sector 1 should be preserved")
        assertEquals(31_000, lap.sector2Ms,  "sector 2 should be set")
        assertEquals(31_000, lap.sector3Ms,  "sector 3 should be set")
    }

    @Test
    fun `multiple drivers are handled independently`() = runTest {
        repo.upsertDeltas(sessionKey, mapOf(
            "1"  to DriverTimingDelta(lapNumber = 1, lastLapTimeMs = 89_000),
            "44" to DriverTimingDelta(lapNumber = 1, lastLapTimeMs = 88_500)
        ), ts)
        assertEquals(1, repo.findByDriver(sessionKey, "1").size)
        assertEquals(1, repo.findByDriver(sessionKey, "44").size)
    }

    @Test
    fun `findBestLaps returns the fastest lap per driver`() = runTest {
        repo.upsertDeltas(sessionKey, mapOf("1" to DriverTimingDelta(lapNumber = 1, lastLapTimeMs = 91_000)), ts)
        repo.upsertDeltas(sessionKey, mapOf("1" to DriverTimingDelta(lapNumber = 2, lastLapTimeMs = 89_000)), ts)
        repo.upsertDeltas(sessionKey, mapOf("1" to DriverTimingDelta(lapNumber = 3, lastLapTimeMs = 90_000)), ts)
        val best = repo.findBestLaps(sessionKey)
        assertNotNull(best["1"])
        assertEquals(2, best["1"]!!.lapNumber)
        assertEquals(89_000, best["1"]!!.lapTimeMs)
    }

    @Test
    fun `findBestLaps ignores laps with null lap time`() = runTest {
        repo.upsertDeltas(sessionKey, mapOf("1" to DriverTimingDelta(lapNumber = 1)), ts)  // no time
        repo.upsertDeltas(sessionKey, mapOf("1" to DriverTimingDelta(lapNumber = 2, lastLapTimeMs = 89_000)), ts)
        val best = repo.findBestLaps(sessionKey)
        assertEquals(2, best["1"]!!.lapNumber)
    }

    @Test
    fun `personalBest and overallBest flags are stored`() = runTest {
        repo.upsertDeltas(sessionKey, mapOf(
            "1" to DriverTimingDelta(lapNumber = 5, lastLapTimeMs = 88_000,
                lastLapPersonalBest = true, lastLapOverallBest = true)
        ), ts)
        val lap = repo.findByDriver(sessionKey, "1").single()
        assertEquals(true, lap.isPersonalBest)
        assertEquals(true, lap.isOverallBest)
    }

    @Test
    fun `findBySession returns all laps for the session`() = runTest {
        repo.upsertDeltas(sessionKey, mapOf("1" to DriverTimingDelta(lapNumber = 1, lastLapTimeMs = 89_000)), ts)
        repo.upsertDeltas(sessionKey, mapOf("1" to DriverTimingDelta(lapNumber = 2, lastLapTimeMs = 90_000)), ts)
        repo.upsertDeltas(sessionKey, mapOf("44" to DriverTimingDelta(lapNumber = 1, lastLapTimeMs = 88_000)), ts)
        assertEquals(3, repo.findBySession(sessionKey).size)
    }
}
