package com.f1analytics.api

import com.f1analytics.api.dto.LapDataDto
import com.f1analytics.data.db.tables.PositionSnapshotsTable
import com.f1analytics.data.db.tables.SessionDriversTable
import com.f1analytics.data.db.tables.StintsTable
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.*
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionLapsViewTest : ViewTestBase() {

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
        gapToLeader: String? = null,
        timestamp: Instant = Instant.parse("2024-03-02T16:00:00Z")
    ) = transaction(db) {
        PositionSnapshotsTable.insert {
            it[PositionSnapshotsTable.sessionKey]   = sessionKey
            it[PositionSnapshotsTable.driverNumber] = driverNumber
            it[PositionSnapshotsTable.gapToLeader]  = gapToLeader
            it[PositionSnapshotsTable.timestamp]    = timestamp
        }
    }


    @Test
    fun `GET returns session laps ordered by driver and lap number`() = testApp { client ->
        val t1 = Instant.parse("2024-03-02T16:00:01Z")
        val t2 = Instant.parse("2024-03-02T16:00:02Z")
        insertRace()
        insertSession(key = 9001, type = "RACE")
        insertDriver(9001, "1", "VER")
        insertDriver(9001, "2", "HAM")
        insertStint(9001, "1", stintNumber = 1, lapStart = 1, lapEnd = 5)
        insertStint(9001, "2", stintNumber = 1, lapStart = 1, lapEnd = 5)
        // driver "1" has laps 1 and 2 — needs 2 snapshots (DESC: t2 at index 0, t1 at index 1)
        insertPositionSnapshot(9001, "1", timestamp = t2)
        insertPositionSnapshot(9001, "1", timestamp = t1)
        insertPositionSnapshot(9001, "2", timestamp = t1)
        insertLap(sessionKey = 9001, lapNumber = 2, driverNumber = "1")
        insertLap(sessionKey = 9001, lapNumber = 1, driverNumber = "2")
        insertLap(sessionKey = 9001, lapNumber = 1, driverNumber = "1")

        val laps = client.get("/api/sessions/9001/laps").body<List<LapDataDto>>()

        assertEquals(3, laps.size)
        val lap1 = laps[0]
        assertEquals(1, lap1.lapNumber)
        assertEquals("1", lap1.driverNumber)
        val lap2 = laps[1]
        assertEquals(2, lap2.lapNumber)
        assertEquals("1", lap2.driverNumber)
        val lap3 = laps[2]
        assertEquals(1, lap3.lapNumber)
        assertEquals("2", lap3.driverNumber)
    }

    @Test
    fun `GET returns 400 if session is not an int`() = testApp {
        insertRace()
        insertSession(key = 9001, type = "RACE")

        val response = client.get("/api/sessions/falopa/laps")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET maps driver code and team color from driver entry`() = testApp { client ->
        val ts = Instant.parse("2024-03-02T16:00:01Z")
        insertRace()
        insertSession(key = 9001)
        insertDriver(9001, "1", "VER", teamColor = "3671C6")
        insertStint(9001, "1", stintNumber = 1, compound = "SOFT", lapStart = 1, lapEnd = 1)
        insertPositionSnapshot(9001, "1", timestamp = ts)
        insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = 1)

        val laps = client.get("/api/sessions/9001/laps").body<List<LapDataDto>>()

        assertEquals(1, laps.size)
        assertEquals("VER", laps[0].driverCode)
        assertEquals("#3671C6", laps[0].teamColor)
    }

    @Test
    fun `GET maps compound and stint number from stint`() = testApp { client ->
        val ts = Instant.parse("2024-03-02T16:00:01Z")
        insertRace()
        insertSession(key = 9001)
        insertDriver(9001, "1", "VER")
        insertStint(9001, "1", stintNumber = 2, compound = "MEDIUM", lapStart = 1, lapEnd = 1)
        insertPositionSnapshot(9001, "1", timestamp = ts)
        insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = 1)

        val laps = client.get("/api/sessions/9001/laps").body<List<LapDataDto>>()

        assertEquals("MEDIUM", laps[0].compound)
        assertEquals(2, laps[0].stintNumber)
    }

    @Test
    fun `GET maps pit flags and personal best from lap`() = testApp { client ->
        val ts = Instant.parse("2024-03-02T16:00:01Z")
        insertRace()
        insertSession(key = 9001)
        insertDriver(9001, "1", "VER")
        insertStint(9001, "1", stintNumber = 1, compound = "SOFT", lapStart = 1, lapEnd = 1)
        insertPositionSnapshot(9001, "1", timestamp = ts)
        insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = 1, pitInLap = true, isPersonalBest = true)

        val laps = client.get("/api/sessions/9001/laps").body<List<LapDataDto>>()

        assertEquals(true, laps[0].pitInLap)
        assertEquals(false, laps[0].pitOutLap)
        assertEquals(true, laps[0].isPersonalBest)
    }

    @Test
    fun `GET maps gap to leader from position snapshot`() = testApp { client ->
        val ts = Instant.parse("2024-03-02T16:00:01Z")
        insertRace()
        insertSession(key = 9001)
        insertDriver(9001, "1", "VER")
        insertStint(9001, "1", stintNumber = 1, compound = "SOFT", lapStart = 1, lapEnd = 1)
        insertPositionSnapshot(9001, "1", gapToLeader = "1500", timestamp = ts)
        insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = 1)

        val laps = client.get("/api/sessions/9001/laps").body<List<LapDataDto>>()

        assertEquals(1500, laps[0].gapToLeaderMs)
    }

    @Test
    fun `GET maps null gap to leader when snapshot has no gap`() = testApp { client ->
        val ts = Instant.parse("2024-03-02T16:00:01Z")
        insertRace()
        insertSession(key = 9001)
        insertDriver(9001, "1", "VER")
        insertStint(9001, "1", stintNumber = 1, compound = "SOFT", lapStart = 1, lapEnd = 1)
        insertPositionSnapshot(9001, "1", gapToLeader = null, timestamp = ts)
        insertLap(sessionKey = 9001, driverNumber = "1", lapNumber = 1)

        val laps = client.get("/api/sessions/9001/laps").body<List<LapDataDto>>()

        assertNull(laps[0].gapToLeaderMs)
    }

    @Test
    fun `GET maps multiple drivers independently`() = testApp { client ->
        val t1 = Instant.parse("2024-03-02T16:00:01Z")
        val t2 = Instant.parse("2024-03-02T16:00:02Z")
        insertRace()
        insertSession(key = 9001)
        insertDriver(9001, "1",  "VER", teamColor = "3671C6")
        insertDriver(9001, "44", "HAM", teamColor = "27F4D3")
        insertStint(9001, "1",  stintNumber = 1, compound = "SOFT",   lapStart = 1, lapEnd = 1)
        insertStint(9001, "44", stintNumber = 1, compound = "MEDIUM", lapStart = 1, lapEnd = 1)
        insertPositionSnapshot(9001, "1",  timestamp = t1)
        insertPositionSnapshot(9001, "44", timestamp = t2)
        insertLap(sessionKey = 9001, driverNumber = "1",  lapNumber = 1)
        insertLap(sessionKey = 9001, driverNumber = "44", lapNumber = 1)

        val laps = client.get("/api/sessions/9001/laps").body<List<LapDataDto>>()

        assertEquals(2, laps.size)
        val ver = laps.first { it.driverNumber == "1" }
        val ham = laps.first { it.driverNumber == "44" }
        assertEquals("VER",    ver.driverCode)
        assertEquals("SOFT",   ver.compound)
        assertEquals("HAM",    ham.driverCode)
        assertEquals("MEDIUM", ham.compound)
    }
}
