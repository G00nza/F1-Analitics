package com.f1analytics.data.db.repository

import com.f1analytics.core.domain.model.DriverTimingDelta
import com.f1analytics.core.domain.model.TimingMessage
import com.f1analytics.core.domain.model.WeatherData
import com.f1analytics.data.db.tables.SessionsTable
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExposedReplayRepositoryTest : RepositoryTestBase() {

    private val repo get() = ExposedReplayRepository(db)
    private val sessionKey = 9001

    private val t1 = Instant.parse("2024-03-02T16:00:01Z")
    private val t2 = Instant.parse("2024-03-02T16:00:02Z")
    private val t3 = Instant.parse("2024-03-02T16:00:03Z")

    @BeforeTest
    fun insertSession() {
        transaction(db) {
            SessionsTable.insert {
                it[key]      = sessionKey
                it[raceKey]  = 1
                it[name]     = "Race"
                it[type]     = "RACE"
                it[year]     = 2024
                it[recorded] = true
            }
        }
    }

    @Test
    fun `findAllEventsBySession returns empty list when no data`() = runTest {
        assertTrue(repo.findAllEventsBySession(sessionKey).isEmpty())
    }

    @Test
    fun `findAllEventsBySession includes position_update events`() = runTest {
        ExposedPositionRepository(db).insertSnapshot(
            sessionKey,
            mapOf("1" to DriverTimingDelta(position = 1, gapToLeader = "LEADER")),
            t1
        )

        val events = repo.findAllEventsBySession(sessionKey)

        assertEquals(1, events.size)
        assertEquals("position_update", events[0].topic)
        assertTrue(events[0].json.contains("\"driverNumber\":\"1\""))
        assertTrue(events[0].json.contains("\"position\":1"))
    }

    @Test
    fun `findAllEventsBySession includes race_control events`() = runTest {
        ExposedRaceControlRepository(db).insert(
            sessionKey,
            TimingMessage.RaceControlMsg("SAFETY CAR DEPLOYED", flag = "SC", scope = "Track", lap = 5),
            t1
        )

        val events = repo.findAllEventsBySession(sessionKey)

        assertEquals(1, events.size)
        assertEquals("race_control", events[0].topic)
        assertTrue(events[0].json.contains("SAFETY CAR DEPLOYED"))
        assertTrue(events[0].json.contains("\"flag\":\"SC\""))
    }

    @Test
    fun `findAllEventsBySession includes lap_completed events only for laps with lapTimeMs`() = runTest {
        val lapRepo = ExposedLapRepository(db)
        // Lap 1: has lapTimeMs — should appear as lap_completed event
        lapRepo.upsertDeltas(sessionKey, mapOf("1" to DriverTimingDelta(lapNumber = 1, lastLapTimeMs = 95000)), t1)
        // Lap 2: no lapTimeMs (pit-in lap) — should be excluded
        lapRepo.upsertDeltas(sessionKey, mapOf("1" to DriverTimingDelta(lapNumber = 2)), t2)

        val events = repo.findAllEventsBySession(sessionKey)

        assertEquals(1, events.count { it.topic == "lap_completed" })
        assertTrue(events.first { it.topic == "lap_completed" }.json.contains("\"lapNumber\":1"))
    }

    @Test
    fun `findAllEventsBySession includes weather events sampled every 10th`() = runTest {
        val weatherRepo = ExposedWeatherRepository(db)
        repeat(11) { i ->
            weatherRepo.insert(
                sessionKey,
                WeatherData(20.0 + i, null, null, null, null, null, null),
                Instant.parse("2024-03-02T16:00:${i.toString().padStart(2, '0')}Z")
            )
        }

        val weatherEvents = repo.findAllEventsBySession(sessionKey).filter { it.topic == "weather" }

        // indices 0 and 10 are sampled (every 10th)
        assertEquals(2, weatherEvents.size)
    }

    @Test
    fun `findAllEventsBySession returns events sorted by timestamp`() = runTest {
        ExposedRaceControlRepository(db).insert(
            sessionKey, TimingMessage.RaceControlMsg("THIRD", null, null, null), t3
        )
        ExposedPositionRepository(db).insertSnapshot(
            sessionKey, mapOf("1" to DriverTimingDelta(position = 1, gapToLeader = "LEADER")), t1
        )
        ExposedRaceControlRepository(db).insert(
            sessionKey, TimingMessage.RaceControlMsg("SECOND", null, null, null), t2
        )

        val events = repo.findAllEventsBySession(sessionKey)

        assertEquals(listOf(t1, t2, t3), events.map { it.timestamp })
    }

    @Test
    fun `findAllEventsBySession returns empty list for unknown session`() = runTest {
        ExposedRaceControlRepository(db).insert(
            sessionKey, TimingMessage.RaceControlMsg("TEST", null, null, null), t1
        )

        assertTrue(repo.findAllEventsBySession(9999).isEmpty())
    }
}
