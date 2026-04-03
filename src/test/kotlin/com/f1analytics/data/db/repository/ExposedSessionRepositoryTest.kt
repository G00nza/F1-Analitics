package com.f1analytics.data.db.repository

import com.f1analytics.core.domain.model.Session
import com.f1analytics.core.domain.model.SessionType
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ExposedSessionRepositoryTest : RepositoryTestBase() {

    private val repo get() = ExposedSessionRepository(db)

    private fun aSession(
        key: Int = 99,
        type: SessionType = SessionType.QUALIFYING,
        status: String? = null,
        recorded: Boolean = false
    ) = Session(
        key       = key,
        raceKey   = 1,
        name      = "Qualifying",
        type      = type,
        year      = 2024,
        status    = status,
        dateStart = Instant.parse("2024-03-02T14:00:00Z"),
        dateEnd   = null,
        recorded  = recorded
    )

    @Test
    fun `upsert inserts a new session`() = runTest {
        repo.upsert(aSession(key = 99))
        assertNotNull(repo.findByKey(99))
    }

    @Test
    fun `upsert replaces existing session`() = runTest {
        repo.upsert(aSession(key = 99, status = "Started"))
        repo.upsert(aSession(key = 99, status = "Finished"))
        assertEquals("Finished", repo.findByKey(99)!!.status)
    }

    @Test
    fun `findByKey returns null for unknown key`() = runTest {
        assertNull(repo.findByKey(9999))
    }

    @Test
    fun `findByYearAndType returns matching sessions`() = runTest {
        repo.upsert(aSession(key = 10, type = SessionType.QUALIFYING))
        repo.upsert(aSession(key = 11, type = SessionType.RACE))
        val result = repo.findByYearAndType(2024, SessionType.QUALIFYING)
        assertEquals(1, result.size)
        assertEquals(10, result[0].key)
    }

    @Test
    fun `findLastRecorded returns most recent recorded session`() = runTest {
        repo.upsert(aSession(key = 20, recorded = true).copy(
            dateStart = Instant.parse("2024-03-01T10:00:00Z")
        ))
        repo.upsert(aSession(key = 21, recorded = true).copy(
            dateStart = Instant.parse("2024-03-02T14:00:00Z")
        ))
        repo.upsert(aSession(key = 22, recorded = false).copy(
            dateStart = Instant.parse("2024-03-02T18:00:00Z")
        ))
        val last = repo.findLastRecorded()
        assertNotNull(last)
        assertEquals(21, last.key)
    }

    @Test
    fun `updateStatus changes only the status field`() = runTest {
        repo.upsert(aSession(key = 30, status = "Started"))
        repo.updateStatus(30, "Finished")
        assertEquals("Finished", repo.findByKey(30)!!.status)
        assertEquals(SessionType.QUALIFYING, repo.findByKey(30)!!.type)
    }
}
