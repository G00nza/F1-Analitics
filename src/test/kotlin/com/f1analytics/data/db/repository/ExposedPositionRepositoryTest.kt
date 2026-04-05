package com.f1analytics.data.db.repository

import com.f1analytics.core.domain.model.DriverTimingDelta
import com.f1analytics.data.db.tables.SessionsTable
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExposedPositionRepositoryTest : RepositoryTestBase() {

    private val repo get() = ExposedPositionRepository(db)
    private val sessionKey = 1
    private val t1 = Instant.parse("2024-03-02T16:00:00Z")
    private val t2 = Instant.parse("2024-03-02T16:00:01Z")

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
    fun `returns empty map when no snapshots exist`() = runTest {
        val result = repo.findLatestPositions(sessionKey)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns latest position for a single driver`() = runTest {
        repo.insertSnapshot(sessionKey, mapOf("1" to DriverTimingDelta(position = 3)), t1)
        repo.insertSnapshot(sessionKey, mapOf("1" to DriverTimingDelta(position = 1)), t2)

        val result = repo.findLatestPositions(sessionKey)
        assertEquals(1, result["1"]?.position)
    }

    @Test
    fun `returns latest gap and interval values`() = runTest {
        repo.insertSnapshot(sessionKey, mapOf(
            "44" to DriverTimingDelta(position = 2, gapToLeader = "+5.123", interval = "+2.456")
        ), t1)
        repo.insertSnapshot(sessionKey, mapOf(
            "44" to DriverTimingDelta(position = 2, gapToLeader = "+4.800", interval = "+2.100")
        ), t2)

        val snapshot = repo.findLatestPositions(sessionKey)["44"]!!
        assertEquals("+4.800", snapshot.gapToLeader)
        assertEquals("+2.100", snapshot.interval)
    }

    @Test
    fun `returns latest snapshot independently per driver`() = runTest {
        repo.insertSnapshot(sessionKey, mapOf(
            "1"  to DriverTimingDelta(position = 1),
            "44" to DriverTimingDelta(position = 2)
        ), t1)
        repo.insertSnapshot(sessionKey, mapOf(
            "1" to DriverTimingDelta(position = 1, gapToLeader = "LEADER")
        ), t2)

        val result = repo.findLatestPositions(sessionKey)
        assertEquals("LEADER", result["1"]?.gapToLeader)
        assertEquals(2, result["44"]?.position)
    }

    @Test
    fun `delta with no position or gap fields is not inserted`() = runTest {
        repo.insertSnapshot(sessionKey, mapOf("1" to DriverTimingDelta(lastLapTimeMs = 90_000)), t1)
        assertTrue(repo.findLatestPositions(sessionKey).isEmpty())
    }

    @Test
    fun `position can be null when only gap fields are present`() = runTest {
        repo.insertSnapshot(sessionKey, mapOf("1" to DriverTimingDelta(gapToLeader = "+1.5")), t1)

        val snapshot = repo.findLatestPositions(sessionKey)["1"]!!
        assertNull(snapshot.position)
        assertEquals("+1.5", snapshot.gapToLeader)
    }

    // ── findAllPositionsByDriver ───────────────────────────────────────────────

    @Test
    fun `findAllPositionsByDriver returns empty map when no snapshots exist`() = runTest {
        assertTrue(repo.findAllPositionsByDriver(sessionKey).isEmpty())
    }

    @Test
    fun `findAllPositionsByDriver returns all snapshots for a driver in order`() = runTest {
        repo.insertSnapshot(sessionKey, mapOf("1" to DriverTimingDelta(position = 3)), t1)
        repo.insertSnapshot(sessionKey, mapOf("1" to DriverTimingDelta(position = 1)), t2)

        val snapshots = repo.findAllPositionsByDriver(sessionKey)["1"]!!
        assertEquals(2, snapshots.size)
        // repository orders DESC by timestamp, so latest is first
        assertEquals(3, snapshots[0].position)
        assertEquals(1, snapshots[1].position)
    }

    @Test
    fun `findAllPositionsByDriver groups snapshots independently per driver`() = runTest {
        repo.insertSnapshot(sessionKey, mapOf(
            "1"  to DriverTimingDelta(position = 1),
            "44" to DriverTimingDelta(position = 2)
        ), t1)
        repo.insertSnapshot(sessionKey, mapOf(
            "1" to DriverTimingDelta(position = 1, gapToLeader = "LEADER")
        ), t2)

        val result = repo.findAllPositionsByDriver(sessionKey)
        assertEquals(2, result["1"]!!.size)
        assertEquals(1, result["44"]!!.size)
    }

    @Test
    fun `findAllPositionsByDriver preserves all fields per snapshot`() = runTest {
        repo.insertSnapshot(sessionKey, mapOf(
            "44" to DriverTimingDelta(position = 2, gapToLeader = "+3.5", interval = "+1.2")
        ), t1)

        val snapshot = repo.findAllPositionsByDriver(sessionKey)["44"]!!.single()
        assertEquals(2, snapshot.position)
        assertEquals("+3.5", snapshot.gapToLeader)
        assertEquals("+1.2", snapshot.interval)
    }
}
