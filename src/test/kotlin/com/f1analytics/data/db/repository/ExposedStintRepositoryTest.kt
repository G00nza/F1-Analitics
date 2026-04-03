package com.f1analytics.data.db.repository

import com.f1analytics.core.domain.model.DriverTireStintDelta
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ExposedStintRepositoryTest : RepositoryTestBase() {

    private val repo get() = ExposedStintRepository(db)

    @Test
    fun `delta without stintNumber is silently skipped`() = runTest {
        repo.upsertDeltas(sessionKey, mapOf("1" to DriverTireStintDelta(compound = "SOFT")))
        assertEquals(0, repo.findBySession(sessionKey).size)
    }

    @Test
    fun `first delta creates a new stint row`() = runTest {
        repo.upsertDeltas(sessionKey, mapOf(
            "1" to DriverTireStintDelta(stintNumber = 1, compound = "SOFT", isNew = true, lapStart = 1)
        ))
        val stints = repo.findBySession(sessionKey)
        assertEquals(1, stints.size)
        assertEquals("SOFT", stints[0].compound)
        assertEquals(true, stints[0].isNew)
    }

    @Test
    fun `compound and lapStart arriving in separate messages are merged`() = runTest {
        repo.upsertDeltas(sessionKey, mapOf("1" to DriverTireStintDelta(stintNumber = 1, compound = "MEDIUM")))
        repo.upsertDeltas(sessionKey, mapOf("1" to DriverTireStintDelta(stintNumber = 1, lapStart = 5)))

        val stint = repo.findBySession(sessionKey).single()
        assertEquals("MEDIUM", stint.compound, "compound should be preserved")
        assertEquals(5, stint.lapStart, "lapStart should be updated")
    }

    @Test
    fun `findCurrentStint returns the highest stint number`() = runTest {
        repo.upsertDeltas(sessionKey, mapOf("1" to DriverTireStintDelta(stintNumber = 1, compound = "SOFT")))
        repo.upsertDeltas(sessionKey, mapOf("1" to DriverTireStintDelta(stintNumber = 2, compound = "MEDIUM")))
        val current = repo.findCurrentStint(sessionKey, "1")
        assertNotNull(current)
        assertEquals(2, current.stintNumber)
        assertEquals("MEDIUM", current.compound)
    }

    @Test
    fun `findCurrentStint returns null when no stints exist`() = runTest {
        assertNull(repo.findCurrentStint(sessionKey, "99"))
    }

    @Test
    fun `multiple drivers tracked independently`() = runTest {
        repo.upsertDeltas(sessionKey, mapOf(
            "1"  to DriverTireStintDelta(stintNumber = 1, compound = "SOFT"),
            "44" to DriverTireStintDelta(stintNumber = 1, compound = "HARD")
        ))
        assertEquals("SOFT", repo.findCurrentStint(sessionKey, "1")?.compound)
        assertEquals("HARD", repo.findCurrentStint(sessionKey, "44")?.compound)
    }
}
