package com.f1analytics.data.db.repository

import com.f1analytics.core.domain.model.TimingMessage
import com.f1analytics.data.db.tables.SessionsTable
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExposedRaceControlRepositoryTest : RepositoryTestBase() {

    private val repo get() = ExposedRaceControlRepository(db)
    private val sessionKey = 9001
    private val t1 = Instant.parse("2024-03-02T16:00:01Z")
    private val t2 = Instant.parse("2024-03-02T16:00:02Z")

    @BeforeTest
    fun insertSession() = transaction(db) {
        SessionsTable.insert {
            it[key]      = sessionKey
            it[raceKey]  = 1
            it[name]     = "Race"
            it[type]     = "RACE"
            it[year]     = 2024
            it[recorded] = true
        }
    }

    private fun msg(
        message: String = "TEST",
        flag: String? = null,
        scope: String? = null,
        lap: Int? = null
    ) = TimingMessage.RaceControlMsg(message, flag, scope, lap)

    @Test
    fun `findBySession returns empty list when no messages`() = runTest {
        assertTrue(repo.findBySession(sessionKey).isEmpty())
    }

    @Test
    fun `findBySession returns all messages for session`() = runTest {
        repo.insert(sessionKey, msg("SAFETY CAR DEPLOYED"), t1)
        repo.insert(sessionKey, msg("SAFETY CAR IN THIS LAP"), t2)

        assertEquals(2, repo.findBySession(sessionKey).size)
    }

    @Test
    fun `findBySession maps all fields`() = runTest {
        repo.insert(sessionKey, msg("SAFETY CAR DEPLOYED", flag = "SC", scope = "Track", lap = 12), t1)

        val entry = repo.findBySession(sessionKey).single()

        assertEquals("SAFETY CAR DEPLOYED", entry.message)
        assertEquals("SC",    entry.flag)
        assertEquals("Track", entry.scope)
        assertEquals(12,      entry.lap)
        assertEquals(t1,      entry.timestamp)
    }

    @Test
    fun `findBySession maps null optional fields`() = runTest {
        repo.insert(sessionKey, msg("DRS ENABLED"), t1)

        val entry = repo.findBySession(sessionKey).single()

        assertEquals(null, entry.flag)
        assertEquals(null, entry.scope)
        assertEquals(null, entry.lap)
    }

    @Test
    fun `findBySession orders results by timestamp ascending`() = runTest {
        repo.insert(sessionKey, msg("SECOND"), t2)
        repo.insert(sessionKey, msg("FIRST"),  t1)

        val entries = repo.findBySession(sessionKey)

        assertEquals("FIRST",  entries[0].message)
        assertEquals("SECOND", entries[1].message)
    }

    @Test
    fun `findBySession returns empty list for unknown session`() = runTest {
        repo.insert(sessionKey, msg("TEST"), t1)

        assertTrue(repo.findBySession(9999).isEmpty())
    }
}
