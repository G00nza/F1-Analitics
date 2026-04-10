package com.f1analytics.api

import com.f1analytics.api.dto.SessionChartsDto
import com.f1analytics.data.db.tables.PositionSnapshotsTable
import com.f1analytics.data.db.tables.SessionDriversTable
import com.f1analytics.data.db.tables.StintsTable
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SessionChartsViewTest : ViewTestBase() {

    private fun insertDriver(
        sessionKey: Int,
        number: String,
        code: String,
        teamColor: String? = null
    ) = transaction(db) {
        SessionDriversTable.insert {
            it[SessionDriversTable.sessionKey] = sessionKey
            it[SessionDriversTable.number]     = number
            it[SessionDriversTable.code]       = code
            it[SessionDriversTable.teamColor]  = teamColor
        }
    }

    private fun insertStint(
        sessionKey: Int,
        driverNumber: String,
        stintNumber: Int,
        compound: String? = null,
        lapStart: Int? = null,
        lapEnd: Int? = null
    ) = transaction(db) {
        StintsTable.insert {
            it[StintsTable.sessionKey]   = sessionKey
            it[StintsTable.driverNumber] = driverNumber
            it[StintsTable.stintNumber]  = stintNumber
            it[StintsTable.compound]     = compound
            it[StintsTable.lapStart]     = lapStart
            it[StintsTable.lapEnd]       = lapEnd
        }
    }

    private fun insertPositionSnapshot(
        sessionKey: Int,
        driverNumber: String,
        position: Int? = null,
        gapToLeader: String? = null,
        timestamp: Instant = Instant.parse("2024-03-02T16:00:00Z")
    ) = transaction(db) {
        PositionSnapshotsTable.insert {
            it[PositionSnapshotsTable.sessionKey]   = sessionKey
            it[PositionSnapshotsTable.driverNumber] = driverNumber
            it[PositionSnapshotsTable.position]     = position
            it[PositionSnapshotsTable.gapToLeader]  = gapToLeader
            it[PositionSnapshotsTable.timestamp]    = timestamp
        }
    }

    @Test
    fun `GET returns 400 if session key is not an int`() = testApp { client ->
        val response = client.get("/api/sessions/notanint/charts")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET returns empty best laps and charts sections when no data`() = testApp { client ->
        insertRace()
        insertSession(key = 9001)

        val result = client.get("/api/sessions/9001/charts").body<SessionChartsDto>()

        assertTrue(result.bestLaps.isEmpty())
        assertEquals(4, result.charts.size)
        assertTrue(result.charts.all { it.datasets.isEmpty() })
    }

    @Test
    fun `best laps are sorted by lapTimeMs ascending`() = testApp { client ->
        val ts = Instant.parse("2024-03-02T16:00:01Z")
        insertRace()
        insertSession(key = 9001)
        insertDriver(9001, "1",  "VER")
        insertDriver(9001, "44", "HAM")
        insertStint(9001, "1",  stintNumber = 1, lapStart = 1, lapEnd = 2)
        insertStint(9001, "44", stintNumber = 1, lapStart = 1, lapEnd = 2)
        insertPositionSnapshot(9001, "1",  timestamp = ts)
        insertPositionSnapshot(9001, "44", timestamp = ts)
        insertLap(sessionKey = 9001, driverNumber = "1",  lapNumber = 1, lapTimeMs = 90000)
        insertLap(sessionKey = 9001, driverNumber = "44", lapNumber = 1, lapTimeMs = 85000)

        val result = client.get("/api/sessions/9001/charts").body<SessionChartsDto>()

        assertEquals(2, result.bestLaps.size)
        assertEquals("HAM", result.bestLaps[0].driverCode)
        assertEquals("VER", result.bestLaps[1].driverCode)
    }

    @Test
    fun `best laps exclude pit laps`() = testApp { client ->
        val ts = Instant.parse("2024-03-02T16:00:01Z")
        insertRace()
        insertSession(key = 9001)
        insertDriver(9001, "1", "VER")
        insertStint(9001, "1", stintNumber = 1, lapStart = 1, lapEnd = 3)
        insertPositionSnapshot(9001, "1", timestamp = ts)
        insertPositionSnapshot(9001, "1", timestamp = Instant.parse("2024-03-02T16:00:02Z"))
        insertPositionSnapshot(9001, "1", timestamp = Instant.parse("2024-03-02T16:00:03Z"))
        // Lap 1 is a pit-out, lap 2 is normal but slower, lap 3 is fast but pit-in
        insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = 1, lapTimeMs = 80000, pitOutLap = true)
        insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = 2, lapTimeMs = 90000)
        insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = 3, lapTimeMs = 85000, pitInLap = true)

        val result = client.get("/api/sessions/9001/charts").body<SessionChartsDto>()

        assertEquals(1, result.bestLaps.size)
        assertEquals(90000, result.bestLaps[0].lapTimeMs)
        assertEquals(2, result.bestLaps[0].lapNumber)
    }

    @Test
    fun `best laps map driver code, team color, compound and lap number`() = testApp { client ->
        val ts = Instant.parse("2024-03-02T16:00:01Z")
        insertRace()
        insertSession(key = 9001)
        insertDriver(9001, "1", "VER", teamColor = "3671C6")
        insertStint(9001, "1", stintNumber = 1, compound = "SOFT", lapStart = 1, lapEnd = 1)
        insertPositionSnapshot(9001, "1", timestamp = ts)
        insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = 1, lapTimeMs = 90000)

        val result = client.get("/api/sessions/9001/charts").body<SessionChartsDto>()

        val best = result.bestLaps.single()
        assertEquals("VER",      best.driverCode)
        assertEquals("#3671C6",  best.teamColor)
        assertEquals("SOFT",     best.compound)
        assertEquals(1,          best.lapNumber)
        assertEquals(90000,      best.lapTimeMs)
    }

    @Test
    fun `response contains exactly 4 chart sections with correct ids`() = testApp { client ->
        insertRace()
        insertSession(key = 9001)

        val result = client.get("/api/sessions/9001/charts").body<SessionChartsDto>()

        val ids = result.charts.map { it.id }
        assertEquals(listOf("lapTimes", "positions", "gap", "degradation"), ids)
    }

    @Test
    fun `lap times chart inserts null break point before pit-out lap`() = testApp { client ->
        val ts = Instant.parse("2024-03-02T16:00:01Z")
        insertRace()
        insertSession(key = 9001)
        insertDriver(9001, "1", "VER")
        insertStint(9001, "1", stintNumber = 1, lapStart = 1, lapEnd = 2)
        insertPositionSnapshot(9001, "1", timestamp = ts)
        insertPositionSnapshot(9001, "1", timestamp = Instant.parse("2024-03-02T16:00:02Z"))
        insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = 1, lapTimeMs = 90000)
        insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = 2, lapTimeMs = 88000, pitOutLap = true)

        val result = client.get("/api/sessions/9001/charts").body<SessionChartsDto>()

        val lapTimesSection = result.charts.first { it.id == "lapTimes" }
        val dataset = lapTimesSection.datasets.single()
        // 2 laps + 1 null break before lap 2
        assertEquals(3, dataset.points.size)
        val breakPoint = dataset.points[1]
        assertEquals(1.5, breakPoint.x)
        assertEquals(null, breakPoint.y)
    }

    @Test
    fun `gap chart converts ms to seconds and uses 0 for leader`() = testApp { client ->
        val t1 = Instant.parse("2024-03-02T16:00:01Z")
        val t2 = Instant.parse("2024-03-02T16:00:02Z")
        insertRace()
        insertSession(key = 9001)
        insertDriver(9001, "1",  "VER")
        insertDriver(9001, "44", "HAM")
        insertStint(9001, "1",  stintNumber = 1, lapStart = 1, lapEnd = 1)
        insertStint(9001, "44", stintNumber = 1, lapStart = 1, lapEnd = 1)
        insertPositionSnapshot(9001, "1",  gapToLeader = null,   timestamp = t1)
        insertPositionSnapshot(9001, "44", gapToLeader = "2.500", timestamp = t2)
        insertLap(sessionKey = 9001, driverNumber = "1",  lapNumber = 1, lapTimeMs = 85000)
        insertLap(sessionKey = 9001, driverNumber = "44", lapNumber = 1, lapTimeMs = 87500)

        val result = client.get("/api/sessions/9001/charts").body<SessionChartsDto>()

        val gapSection = result.charts.first { it.id == "gap" }
        val ver = gapSection.datasets.first { it.label == "VER" }
        val ham = gapSection.datasets.first { it.label == "HAM" }
        assertEquals(0.0,   ver.points.single().y)
        assertEquals(2500.0 / 1000.0, ham.points.single().y)
    }

    @Test
    fun `degradation chart skips stints with 5 or fewer valid laps`() = testApp { client ->
        val ts = { i: Int -> Instant.parse("2024-03-02T16:00:0${i}Z") }
        insertRace()
        insertSession(key = 9001)
        insertDriver(9001, "1", "VER")
        insertStint(9001, "1", stintNumber = 1, compound = "SOFT", lapStart = 1, lapEnd = 5)
        repeat(5) { i ->
            insertPositionSnapshot(9001, "1", timestamp = ts(i + 1))
            insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = i + 1, lapTimeMs = 90000 + i * 100)
        }

        val result = client.get("/api/sessions/9001/charts").body<SessionChartsDto>()

        val degradation = result.charts.first { it.id == "degradation" }
        assertTrue(degradation.datasets.isEmpty())
    }

    @Test
    fun `degradation chart calculates delta from first valid lap in stint`() = testApp { client ->
        val ts = { i: Int -> Instant.parse("2024-03-02T16:00:${String.format("%02d", i)}Z") }
        insertRace()
        insertSession(key = 9001)
        insertDriver(9001, "1", "VER")
        insertStint(9001, "1", stintNumber = 1, compound = "MEDIUM", lapStart = 1, lapEnd = 6)
        repeat(6) { i ->
            insertPositionSnapshot(9001, "1", timestamp = ts(i + 1))
            insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = i + 1, lapTimeMs = 90000 + i * 500)
        }

        val result = client.get("/api/sessions/9001/charts").body<SessionChartsDto>()

        val degradation = result.charts.first { it.id == "degradation" }
        val dataset = degradation.datasets.single()
        assertEquals("MEDIUM", dataset.compound)
        // First point is delta 0 (base lap)
        assertEquals(0.0, dataset.points.first().y)
        // Second point is +0.5s (500ms / 1000)
        assertEquals(0.5, dataset.points[1].y)
    }

    @Test
    fun `positions chart maps lap number and position from snapshots`() = testApp { client ->
        val t1 = Instant.parse("2024-03-02T16:00:01Z")
        val t2 = Instant.parse("2024-03-02T16:00:02Z")
        insertRace()
        insertSession(key = 9001)
        insertDriver(9001, "1", "VER")
        insertPositionSnapshot(9001, "1", position = 3, timestamp = t1)
        insertPositionSnapshot(9001, "1", position = 2, timestamp = t2)

        val result = client.get("/api/sessions/9001/charts").body<SessionChartsDto>()

        val posSection = result.charts.first { it.id == "positions" }
        val dataset = posSection.datasets.single()
        assertEquals(2, dataset.points.size)
        assertEquals(1.0, dataset.points[0].x)
        assertEquals(3.0, dataset.points[0].y)
        assertEquals(2.0, dataset.points[1].x)
        assertEquals(2.0, dataset.points[1].y)
    }
}
