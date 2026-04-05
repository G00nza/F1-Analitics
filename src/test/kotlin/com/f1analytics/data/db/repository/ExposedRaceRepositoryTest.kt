package com.f1analytics.data.db.repository

import com.f1analytics.data.db.tables.RacesTable
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExposedRaceRepositoryTest : RepositoryTestBase() {

    private val repo get() = ExposedRaceRepository(db)

    // RepositoryTestBase already inserts key=1, year=2024, no dateStart.

    private fun insertRace(
        key: Int,
        name: String = "Grand Prix",
        officialName: String? = null,
        circuit: String = "Circuit",
        country: String? = null,
        year: Int = 2026,
        round: Int? = null,
        dateStart: String? = null,
        dateEnd: String? = null
    ) = transaction(db) {
        RacesTable.insert {
            it[RacesTable.key]          = key
            it[RacesTable.name]         = name
            it[RacesTable.officialName] = officialName
            it[RacesTable.circuit]      = circuit
            it[RacesTable.country]      = country
            it[RacesTable.year]         = year
            it[RacesTable.round]        = round
            it[RacesTable.dateStart]    = dateStart
            it[RacesTable.dateEnd]      = dateEnd
        }
    }

    // ── findByKey ─────────────────────────────────────────────────────────────

    @Test
    fun `findByKey returns race for known key`() = runTest {
        val race = repo.findByKey(1)
        assertNotNull(race)
        assertEquals(1, race.key)
    }

    @Test
    fun `findByKey returns null for unknown key`() = runTest {
        assertNull(repo.findByKey(9999))
    }

    @Test
    fun `findByKey maps all fields`() = runTest {
        insertRace(
            key          = 42,
            name         = "Monaco Grand Prix",
            officialName = "Formula 1 Monaco Grand Prix 2026",
            circuit      = "Circuit de Monaco",
            country      = "Monaco",
            year         = 2026,
            round        = 8,
            dateStart    = "2026-05-25"
        )

        val race = repo.findByKey(42)!!

        assertEquals(42,                                    race.key)
        assertEquals("Monaco Grand Prix",                   race.name)
        assertEquals("Formula 1 Monaco Grand Prix 2026",   race.officialName)
        assertEquals("Circuit de Monaco",                   race.circuit)
        assertEquals("Monaco",                              race.country)
        assertEquals(2026,                                  race.year)
        assertEquals(8,                                     race.round)
        assertEquals("2026-05-25",                          race.dateStart)
    }

    // ── findByYear ────────────────────────────────────────────────────────────

    @Test
    fun `findByYear returns races for matching year`() = runTest {
        insertRace(key = 10, year = 2026)
        insertRace(key = 11, year = 2026)
        insertRace(key = 12, year = 2025)

        val result = repo.findByYear(2026)

        assertEquals(2, result.size)
        assertTrue(result.all { it.year == 2026 })
    }

    @Test
    fun `findByYear returns empty list for unknown year`() = runTest {
        val result = repo.findByYear(1900)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findByYear orders results by round`() = runTest {
        insertRace(key = 20, year = 2026, round = 3)
        insertRace(key = 21, year = 2026, round = 1)
        insertRace(key = 22, year = 2026, round = 2)

        val result = repo.findByYear(2026)

        assertEquals(listOf(1, 2, 3), result.map { it.round })
    }

    // ── findCurrent ───────────────────────────────────────────────────────────

    @Test
    fun `findCurrent returns null when no races have dateStart`() = runTest {
        // base race has no dateStart
        assertNull(repo.findCurrent())
    }

    @Test
    fun `findCurrent returns null when all races are in the future`() = runTest {
        insertRace(key = 30, dateStart = "2099-01-01")

        assertNull(repo.findCurrent())
    }

    @Test
    fun `findCurrent ignores races without dateStart`() = runTest {
        insertRace(key = 40, name = "No Date Race", dateStart = null)
        insertRace(key = 41, name = "Active Race",  dateStart = "2026-04-01", dateEnd = "2026-04-06")

        assertEquals(41, repo.findCurrent()!!.key)
    }

    @Test
    fun `findCurrent returns active race when today is within dateStart and dateEnd`() = runTest {
        insertRace(key = 50, dateStart = "2026-04-04", dateEnd = "2026-04-06")

        assertEquals(50, repo.findCurrent()!!.key)
    }

    @Test
    fun `findCurrent returns active race when dateEnd is null`() = runTest {
        insertRace(key = 60, dateStart = "2026-04-06", dateEnd = null)

        assertEquals(60, repo.findCurrent()!!.key)
    }

    @Test
    fun `findCurrent returns last finished race when no active race`() = runTest {
        insertRace(key = 70, name = "Older Race",  dateStart = "2026-01-01", dateEnd = "2026-01-03")
        insertRace(key = 71, name = "Recent Race", dateStart = "2026-03-01", dateEnd = "2026-03-03")

        assertEquals(71, repo.findCurrent()!!.key)
    }

    @Test
    fun `findCurrent prefers active race over finished race`() = runTest {
        insertRace(key = 80, name = "Finished Race", dateStart = "2026-03-01", dateEnd = "2026-03-03")
        insertRace(key = 81, name = "Active Race",   dateStart = "2026-04-04", dateEnd = "2026-04-06")

        assertEquals(81, repo.findCurrent()!!.key)
    }
}
