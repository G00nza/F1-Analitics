package com.f1analytics.data.db.repository

import com.f1analytics.core.domain.model.WeatherData
import com.f1analytics.data.db.tables.SessionsTable
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExposedWeatherRepositoryTest : RepositoryTestBase() {

    private val repo get() = ExposedWeatherRepository(db)
    private val sessionKey = 9001
    private val t1 = Instant.parse("2024-03-02T16:00:01Z")
    private val t2 = Instant.parse("2024-03-02T16:00:02Z")

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

    private fun weather(
        airTemp: Double? = 28.0,
        trackTemp: Double? = 42.0,
        humidity: Double? = 55.0,
        pressure: Double? = 1013.0,
        windSpeed: Double? = 3.2,
        windDirection: Int? = 270,
        rainfall: Boolean? = false
    ) = WeatherData(airTemp, trackTemp, humidity, pressure, windSpeed, windDirection, rainfall)

    @Test
    fun `findLatest returns null when no snapshots exist`() = runTest {
        assertNull(repo.findLatest(sessionKey))
    }

    @Test
    fun `findLatest returns the most recent snapshot`() = runTest {
        repo.insert(sessionKey, weather(airTemp = 25.0), t1)
        repo.insert(sessionKey, weather(airTemp = 30.0), t2)

        val result = repo.findLatest(sessionKey)!!

        assertEquals(30.0, result.airTemp)
    }

    @Test
    fun `findLatest maps all fields`() = runTest {
        repo.insert(sessionKey, weather(
            airTemp       = 28.5,
            trackTemp     = 42.0,
            humidity      = 55.0,
            pressure      = 1013.0,
            windSpeed     = 3.2,
            windDirection = 270,
            rainfall      = true
        ), t1)

        val result = repo.findLatest(sessionKey)!!

        assertEquals(28.5,  result.airTemp)
        assertEquals(42.0,  result.trackTemp)
        assertEquals(55.0,  result.humidity)
        assertEquals(1013.0, result.pressure)
        assertEquals(3.2,   result.windSpeed)
        assertEquals(270,   result.windDirection)
        assertEquals(true,  result.rainfall)
    }

    @Test
    fun `findLatest returns null for unknown session`() = runTest {
        repo.insert(sessionKey, weather(), t1)

        assertNull(repo.findLatest(9999))
    }

    @Test
    fun `findLatest maps null fields`() = runTest {
        repo.insert(sessionKey, WeatherData(null, null, null, null, null, null, null), t1)

        val result = repo.findLatest(sessionKey)!!

        assertNull(result.airTemp)
        assertNull(result.rainfall)
    }
}
