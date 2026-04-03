package com.f1analytics.data.db.repository

import com.f1analytics.core.domain.model.*
import com.f1analytics.data.db.tables.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExposedInsertOnlyRepositoriesTest : RepositoryTestBase() {

    private val ts = Instant.parse("2024-03-02T16:00:00Z")

    // ── SessionDriver ──────────────────────────────────────────────────────

    @Test
    fun `SessionDriverRepository upsertAll inserts all drivers`() = runTest {
        val repo = ExposedSessionDriverRepository(db)
        val drivers = mapOf(
            "1"  to DriverEntry("1",  "VER", "Max",    "Verstappen", "Red Bull Racing", "3671C6"),
            "44" to DriverEntry("44", "HAM", "Lewis",  "Hamilton",   "Mercedes",        "27F4D3")
        )
        repo.upsertAll(sessionKey, drivers)
        val found = repo.findBySession(sessionKey)
        assertEquals(2, found.size)
        assertTrue(found.any { it.code == "VER" })
        assertTrue(found.any { it.code == "HAM" })
    }

    @Test
    fun `SessionDriverRepository upsertAll updates existing driver data`() = runTest {
        val repo = ExposedSessionDriverRepository(db)
        repo.upsertAll(sessionKey, mapOf("1" to DriverEntry("1", "VER", "Max", "Verstappen", "Red Bull", "OLD")))
        repo.upsertAll(sessionKey, mapOf("1" to DriverEntry("1", "VER", "Max", "Verstappen", "Red Bull Racing", "3671C6")))
        val found = repo.findBySession(sessionKey)
        assertEquals(1, found.size)
        assertEquals("3671C6", found[0].teamColor)
    }

    // ── PitStop ────────────────────────────────────────────────────────────

    @Test
    fun `PitStopRepository insert creates a row`() = runTest {
        ExposedPitStopRepository(db).insert(sessionKey, "1", lapNumber = 12, timestamp = ts)
        val count = transaction(db) {
            PitStopsTable.selectAll()
                .where { PitStopsTable.sessionKey eq sessionKey }
                .count()
        }
        assertEquals(1, count)
    }

    @Test
    fun `PitStopRepository insert allows null lapNumber`() = runTest {
        ExposedPitStopRepository(db).insert(sessionKey, "1", lapNumber = null, timestamp = ts)
        val row = transaction(db) {
            PitStopsTable.selectAll()
                .where { PitStopsTable.sessionKey eq sessionKey }
                .single()
        }
        assertEquals(null, row[PitStopsTable.lapNumber])
    }

    // ── RaceControl ────────────────────────────────────────────────────────

    @Test
    fun `RaceControlRepository insert stores all fields`() = runTest {
        val msg = TimingMessage.RaceControlMsg(
            message = "SAFETY CAR DEPLOYED",
            flag    = "SC",
            scope   = "Track",
            lap     = 12
        )
        ExposedRaceControlRepository(db).insert(sessionKey, msg, ts)
        val row = transaction(db) {
            RaceControlMessagesTable.selectAll()
                .where { RaceControlMessagesTable.sessionKey eq sessionKey }
                .single()
        }
        assertEquals("SAFETY CAR DEPLOYED", row[RaceControlMessagesTable.message])
        assertEquals("SC",    row[RaceControlMessagesTable.flag])
        assertEquals("Track", row[RaceControlMessagesTable.scope])
        assertEquals(12,      row[RaceControlMessagesTable.lapNumber])
    }

    // ── Weather ────────────────────────────────────────────────────────────

    @Test
    fun `WeatherRepository insert stores all fields including nulls`() = runTest {
        val weather = WeatherData(
            airTemp = 28.5, trackTemp = 42.0, humidity = 55.0,
            pressure = 1013.0, windSpeed = 3.2, windDirection = 270, rainfall = false
        )
        ExposedWeatherRepository(db).insert(sessionKey, weather, ts)
        val row = transaction(db) {
            WeatherSnapshotsTable.selectAll()
                .where { WeatherSnapshotsTable.sessionKey eq sessionKey }
                .single()
        }
        assertEquals(28.5, row[WeatherSnapshotsTable.airTemp])
        assertEquals(false, row[WeatherSnapshotsTable.rainfall])
    }

    @Test
    fun `WeatherRepository insert handles all-null weather data`() = runTest {
        ExposedWeatherRepository(db).insert(
            sessionKey,
            WeatherData(null, null, null, null, null, null, null),
            ts
        )
        val count = transaction(db) {
            WeatherSnapshotsTable.selectAll()
                .where { WeatherSnapshotsTable.sessionKey eq sessionKey }
                .count()
        }
        assertEquals(1, count)
    }

    // ── Position ───────────────────────────────────────────────────────────

    @Test
    fun `PositionRepository inserts deltas that carry position data`() = runTest {
        val deltas = mapOf(
            "1"  to DriverTimingDelta(position = 1, gapToLeader = "LEADER"),
            "44" to DriverTimingDelta(position = 2, interval = "+0.432")
        )
        ExposedPositionRepository(db).insertSnapshot(sessionKey, deltas, ts)
        val count = transaction(db) {
            PositionSnapshotsTable.selectAll()
                .where { PositionSnapshotsTable.sessionKey eq sessionKey }
                .count()
        }
        assertEquals(2, count)
    }

    @Test
    fun `PositionRepository skips deltas with no position or gap fields`() = runTest {
        val deltas = mapOf(
            "1" to DriverTimingDelta(lapNumber = 5)  // no position/gap
        )
        ExposedPositionRepository(db).insertSnapshot(sessionKey, deltas, ts)
        val count = transaction(db) {
            PositionSnapshotsTable.selectAll()
                .where { PositionSnapshotsTable.sessionKey eq sessionKey }
                .count()
        }
        assertEquals(0, count)
    }

    // ── Telemetry ──────────────────────────────────────────────────────────

    @Test
    fun `TelemetryRepository insertBatch stores all entries`() = runTest {
        val entries = listOf(
            CarTelemetryEntry(ts, "1",  speed = 320, rpm = 12000, gear = 8, throttle = 100, brake = 0,  drs = 10),
            CarTelemetryEntry(ts, "44", speed = 315, rpm = 11800, gear = 8, throttle = 98,  brake = 0,  drs = 10)
        )
        ExposedTelemetryRepository(db).insertBatch(sessionKey, entries)
        val count = transaction(db) {
            CarTelemetryTable.selectAll()
                .where { CarTelemetryTable.sessionKey eq sessionKey }
                .count()
        }
        assertEquals(2, count)
    }

    @Test
    fun `TelemetryRepository insertBatch is a no-op for empty list`() = runTest {
        ExposedTelemetryRepository(db).insertBatch(sessionKey, emptyList())
        val count = transaction(db) {
            CarTelemetryTable.selectAll()
                .where { CarTelemetryTable.sessionKey eq sessionKey }
                .count()
        }
        assertEquals(0, count)
    }
}
