package com.f1analytics.data.persistence

import com.f1analytics.core.config.AppConfig
import com.f1analytics.core.domain.model.*
import com.f1analytics.core.domain.port.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiveTimingPersistenceServiceTest {

    // ── Recording fakes ────────────────────────────────────────────────────

    private class FakeSessionRepository : SessionRepository {
        val updateStatusCalls = mutableListOf<Pair<Int, String>>()
        override suspend fun updateStatus(key: Int, status: String) { updateStatusCalls += key to status }
        override suspend fun upsert(session: Session) = Unit
        override suspend fun findByKey(key: Int): Session? = null
        override suspend fun findByYearAndType(year: Int, type: SessionType) = emptyList<Session>()
        override suspend fun findLastRecorded(): Session? = null
        override suspend fun findActive(): Session? = null
        override suspend fun findMostRecent(): Session? = null
        override suspend fun findNextUpcoming(): Session? = null
        override suspend fun findByRace(raceKey: Int): List<Session> = emptyList()
    }

    private class FakeSessionDriverRepository : SessionDriverRepository {
        val upsertAllCalls = mutableListOf<Pair<Int, Map<String, DriverEntry>>>()
        override suspend fun upsertAll(sessionKey: Int, drivers: Map<String, DriverEntry>) {
            upsertAllCalls += sessionKey to drivers
        }
        override suspend fun findBySession(sessionKey: Int) = emptyList<DriverEntry>()
    }

    private class FakeLapRepository : LapRepository {
        val upsertDeltasCalls = mutableListOf<Triple<Int, Map<String, DriverTimingDelta>, Instant>>()
        override suspend fun upsertDeltas(sessionKey: Int, deltas: Map<String, DriverTimingDelta>, ts: Instant) {
            upsertDeltasCalls += Triple(sessionKey, deltas, ts)
        }
        override suspend fun findBySession(sessionKey: Int) = emptyList<Lap>()
        override suspend fun findByDriver(sessionKey: Int, driverNumber: String) = emptyList<Lap>()
        override suspend fun findBestLaps(sessionKey: Int) = emptyMap<String, Lap>()
    }

    private class FakeStintRepository : StintRepository {
        val upsertDeltasCalls = mutableListOf<Pair<Int, Map<String, DriverTireStintDelta>>>()
        override suspend fun upsertDeltas(sessionKey: Int, deltas: Map<String, DriverTireStintDelta>) {
            upsertDeltasCalls += sessionKey to deltas
        }
        override suspend fun findBySession(sessionKey: Int) = emptyList<Stint>()
        override suspend fun findCurrentStint(sessionKey: Int, driverNumber: String): Stint? = null
    }

    private class FakePitStopRepository : PitStopRepository {
        override suspend fun insert(sessionKey: Int, driverNumber: String, lapNumber: Int?, timestamp: Instant) = Unit
    }

    private class FakeRaceControlRepository : RaceControlRepository {
        val insertCalls = mutableListOf<Triple<Int, TimingMessage.RaceControlMsg, Instant>>()
        override suspend fun insert(sessionKey: Int, msg: TimingMessage.RaceControlMsg, timestamp: Instant) {
            insertCalls += Triple(sessionKey, msg, timestamp)
        }
        override suspend fun findBySession(sessionKey: Int): List<RaceControlEntry> = emptyList()
    }

    private class FakeWeatherRepository : WeatherRepository {
        val insertCalls = mutableListOf<Triple<Int, WeatherData, Instant>>()
        override suspend fun insert(sessionKey: Int, weather: WeatherData, timestamp: Instant) {
            insertCalls += Triple(sessionKey, weather, timestamp)
        }
        override suspend fun findLatest(sessionKey: Int): WeatherData? = null
    }

    private class FakePositionRepository : PositionRepository {
        val insertSnapshotCalls = mutableListOf<Triple<Int, Map<String, DriverTimingDelta>, Instant>>()
        override suspend fun insertSnapshot(sessionKey: Int, deltas: Map<String, DriverTimingDelta>, timestamp: Instant) {
            insertSnapshotCalls += Triple(sessionKey, deltas, timestamp)
        }
        override suspend fun findLatestPositions(sessionKey: Int): Map<String, DriverPositionSnapshot> = emptyMap()

        override suspend fun findAllPositionsByDriver(sessionKey: Int): Map<String, List<DriverPositionSnapshot>>  = emptyMap()
    }

    private class FakeTelemetryRepository : TelemetryRepository {
        val insertBatchCalls = mutableListOf<Pair<Int, List<CarTelemetryEntry>>>()
        override suspend fun insertBatch(sessionKey: Int, entries: List<CarTelemetryEntry>) {
            insertBatchCalls += sessionKey to entries
        }
    }

    // ── Test fixtures ──────────────────────────────────────────────────────

    private val sessionRepo    = FakeSessionRepository()
    private val driverRepo     = FakeSessionDriverRepository()
    private val lapRepo        = FakeLapRepository()
    private val stintRepo      = FakeStintRepository()
    private val pitRepo        = FakePitStopRepository()
    private val raceControlRepo = FakeRaceControlRepository()
    private val weatherRepo    = FakeWeatherRepository()
    private val positionRepo   = FakePositionRepository()
    private val telemetryRepo  = FakeTelemetryRepository()

    private val ts = Instant.parse("2024-03-02T16:00:00Z")

    private fun service(storeTelemetry: Boolean = true) = LiveTimingPersistenceService(
        sessionRepo, driverRepo, lapRepo, stintRepo, pitRepo,
        raceControlRepo, weatherRepo, positionRepo, telemetryRepo,
        AppConfig(storeTelemetry = storeTelemetry)
    )

    // ── AC: each message type routes to the correct repository ─────────────

    @Test
    fun `DriverListMsg upserts all drivers`() = runTest {
        val drivers = mapOf("1" to DriverEntry("1", "VER", "Max", "Verstappen", "Red Bull Racing", "3671C6"))
        service().persist(42, TimingMessage.DriverListMsg(drivers), ts)
        assertEquals(1, driverRepo.upsertAllCalls.size)
        assertEquals(42, driverRepo.upsertAllCalls[0].first)
        assertEquals(drivers, driverRepo.upsertAllCalls[0].second)
    }

    @Test
    fun `TimingDataMsg updates laps and position snapshot`() = runTest {
        val deltas = mapOf("1" to DriverTimingDelta(position = 1, gapToLeader = "+1.234"))
        service().persist(42, TimingMessage.TimingDataMsg(deltas), ts)
        assertEquals(1, lapRepo.upsertDeltasCalls.size)
        assertEquals(1, positionRepo.insertSnapshotCalls.size)
        assertEquals(ts, lapRepo.upsertDeltasCalls[0].third)
        assertEquals(ts, positionRepo.insertSnapshotCalls[0].third)
    }

    @Test
    fun `TimingAppDataMsg upserts stint deltas`() = runTest {
        val deltas = mapOf("44" to DriverTireStintDelta(compound = "SOFT", isNew = true, lapStart = 1))
        service().persist(42, TimingMessage.TimingAppDataMsg(deltas), ts)
        assertEquals(1, stintRepo.upsertDeltasCalls.size)
        assertEquals(deltas, stintRepo.upsertDeltasCalls[0].second)
    }

    @Test
    fun `RaceControlMsg inserts with timestamp`() = runTest {
        val msg = TimingMessage.RaceControlMsg(message = "Safety Car deployed", flag = "SC", scope = "Track", lap = 12)
        service().persist(42, msg, ts)
        assertEquals(1, raceControlRepo.insertCalls.size)
        val (key, recorded, recordedTs) = raceControlRepo.insertCalls[0]
        assertEquals(42, key)
        assertEquals(msg, recorded)
        assertEquals(ts, recordedTs)
    }

    @Test
    fun `WeatherMsg inserts weather data`() = runTest {
        val weather = WeatherData(22.5, 38.0, 55.0, 1013.0, 3.2, 270, false)
        service().persist(42, TimingMessage.WeatherMsg(weather), ts)
        assertEquals(1, weatherRepo.insertCalls.size)
        assertEquals(weather, weatherRepo.insertCalls[0].second)
    }

    @Test
    fun `SessionStatusMsg updates session status`() = runTest {
        service().persist(42, TimingMessage.SessionStatusMsg("Started"), ts)
        assertEquals(1, sessionRepo.updateStatusCalls.size)
        assertEquals(42 to "Started", sessionRepo.updateStatusCalls[0])
    }

    // ── AC: telemetry guarded by config.storeTelemetry ─────────────────────

    @Test
    fun `CarDataMsg inserts telemetry when storeTelemetry is true`() = runTest {
        val entries = listOf(CarTelemetryEntry(ts, "1", speed = 320, rpm = 12000, gear = 8, throttle = 100, brake = 0, drs = 10))
        service(storeTelemetry = true).persist(42, TimingMessage.CarDataMsg(entries), ts)
        assertEquals(1, telemetryRepo.insertBatchCalls.size)
        assertEquals(entries, telemetryRepo.insertBatchCalls[0].second)
    }

    @Test
    fun `CarDataMsg skips telemetry when storeTelemetry is false`() = runTest {
        val entries = listOf(CarTelemetryEntry(ts, "1", speed = 320, rpm = 12000, gear = 8, throttle = 100, brake = 0, drs = 10))
        service(storeTelemetry = false).persist(42, TimingMessage.CarDataMsg(entries), ts)
        assertTrue(telemetryRepo.insertBatchCalls.isEmpty())
    }

    // ── AC: unhandled message types are silently ignored ───────────────────

    @Test
    fun `HeartbeatMsg does not call any repository`() = runTest {
        service().persist(42, TimingMessage.HeartbeatMsg(ts), ts)
        assertTrue(driverRepo.upsertAllCalls.isEmpty())
        assertTrue(lapRepo.upsertDeltasCalls.isEmpty())
        assertTrue(sessionRepo.updateStatusCalls.isEmpty())
        assertTrue(telemetryRepo.insertBatchCalls.isEmpty())
    }

    @Test
    fun `SessionInfoMsg does not call any repository`() = runTest {
        service().persist(42, TimingMessage.SessionInfoMsg("Bahrain GP", "Bahrain", SessionType.RACE, null), ts)
        assertTrue(driverRepo.upsertAllCalls.isEmpty())
        assertTrue(sessionRepo.updateStatusCalls.isEmpty())
    }

    // ── AC: repository exception does not propagate ────────────────────────

    @Test
    fun `repository exception is caught and does not throw`() = runTest {
        val brokenWeatherRepo = object : WeatherRepository {
            override suspend fun insert(sessionKey: Int, weather: WeatherData, timestamp: Instant) {
                throw RuntimeException("DB connection lost")
            }
            override suspend fun findLatest(sessionKey: Int): WeatherData? = null
        }
        val svc = LiveTimingPersistenceService(
            sessionRepo, driverRepo, lapRepo, stintRepo, pitRepo,
            raceControlRepo, brokenWeatherRepo, positionRepo, telemetryRepo,
            AppConfig()
        )
        // Must not throw
        svc.persist(42, TimingMessage.WeatherMsg(WeatherData(null, null, null, null, null, null, null)), ts)
    }
}
