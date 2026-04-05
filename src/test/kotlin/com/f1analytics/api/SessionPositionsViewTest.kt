package com.f1analytics.api

import com.f1analytics.api.dto.RacePositionDto
import com.f1analytics.data.db.tables.PositionSnapshotsTable
import com.f1analytics.data.db.tables.SessionDriversTable
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionPositionsViewTest : ViewTestBase() {

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

    private fun insertPositionSnapshot(
        sessionKey: Int,
        driverNumber: String,
        position: Int? = null,
        timestamp: Instant = Instant.parse("2024-03-02T16:00:00Z")
    ) = transaction(db) {
        PositionSnapshotsTable.insert {
            it[PositionSnapshotsTable.sessionKey]   = sessionKey
            it[PositionSnapshotsTable.driverNumber] = driverNumber
            it[PositionSnapshotsTable.position]     = position
            it[PositionSnapshotsTable.timestamp]    = timestamp
        }
    }

    @Test
    fun `GET returns 400 if session key is not an int`() = testApp { client ->
        val response = client.get("/api/sessions/notanint/positions")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET returns one entry per snapshot with lap number as index`() = testApp { client ->
        val t1 = Instant.parse("2024-03-02T16:00:01Z")
        val t2 = Instant.parse("2024-03-02T16:00:02Z")
        insertRace()
        insertSession(key = 9001)
        insertDriver(9001, "1", "VER")
        insertPositionSnapshot(9001, "1", position = 1, timestamp = t1)
        insertPositionSnapshot(9001, "1", position = 2, timestamp = t2)

        val positions = client.get("/api/sessions/9001/positions").body<List<RacePositionDto>>()

        assertEquals(2, positions.size)
        assertEquals(1, positions[0].lapNumber)
        assertEquals(1, positions[0].position)
        assertEquals(2, positions[1].lapNumber)
        assertEquals(2, positions[1].position)
    }

    @Test
    fun `GET maps driver code and team color`() = testApp { client ->
        insertRace()
        insertSession(key = 9001)
        insertDriver(9001, "1", "VER", teamColor = "3671C6")
        insertPositionSnapshot(9001, "1", position = 1)

        val positions = client.get("/api/sessions/9001/positions").body<List<RacePositionDto>>()

        assertEquals(1, positions.size)
        assertEquals("VER", positions[0].driverCode)
        assertEquals("#3671C6", positions[0].teamColor)
    }

    @Test
    fun `GET defaults team color to #000000 when null`() = testApp { client ->
        insertRace()
        insertSession(key = 9001)
        insertDriver(9001, "1", "VER", teamColor = null)
        insertPositionSnapshot(9001, "1", position = 1)

        val positions = client.get("/api/sessions/9001/positions").body<List<RacePositionDto>>()

        assertEquals("#000000", positions[0].teamColor)
    }

    @Test
    fun `GET returns empty list for driver with no snapshots`() = testApp { client ->
        insertRace()
        insertSession(key = 9001)
        insertDriver(9001, "1", "VER")

        val positions = client.get("/api/sessions/9001/positions").body<List<RacePositionDto>>()

        assertEquals(0, positions.size)
    }

    @Test
    fun `GET handles multiple drivers independently`() = testApp { client ->
        val t1 = Instant.parse("2024-03-02T16:00:01Z")
        val t2 = Instant.parse("2024-03-02T16:00:02Z")
        insertRace()
        insertSession(key = 9001)
        insertDriver(9001, "1",  "VER", teamColor = "3671C6")
        insertDriver(9001, "44", "HAM", teamColor = "27F4D3")
        insertPositionSnapshot(9001, "1",  position = 1, timestamp = t1)
        insertPositionSnapshot(9001, "44", position = 2, timestamp = t2)

        val positions = client.get("/api/sessions/9001/positions").body<List<RacePositionDto>>()

        assertEquals(2, positions.size)
        val ver = positions.first { it.driverNumber == "1" }
        val ham = positions.first { it.driverNumber == "44" }
        assertEquals(1, ver.position)
        assertEquals(2, ham.position)
        assertEquals("#3671C6", ver.teamColor)
        assertEquals("#27F4D3", ham.teamColor)
    }
}
