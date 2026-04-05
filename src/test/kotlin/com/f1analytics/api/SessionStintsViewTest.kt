package com.f1analytics.api

import com.f1analytics.api.dto.StintDataDto
import com.f1analytics.data.db.tables.SessionDriversTable
import com.f1analytics.data.db.tables.StintsTable
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionStintsViewTest : ViewTestBase() {

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
        lapEnd: Int? = null,
        isNew: Boolean? = null
    ) = transaction(db) {
        StintsTable.insert {
            it[StintsTable.sessionKey]   = sessionKey
            it[StintsTable.driverNumber] = driverNumber
            it[StintsTable.stintNumber]  = stintNumber
            it[StintsTable.compound]     = compound
            it[StintsTable.lapStart]     = lapStart
            it[StintsTable.lapEnd]       = lapEnd
            it[StintsTable.isNew]        = isNew
        }
    }

    @Test
    fun `GET returns 400 if session key is not an int`() = testApp { client ->
        val response = client.get("/api/sessions/notanint/stints")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET returns all stints for session`() = testApp { client ->
        insertRace()
        insertSession(key = 9001)
        insertDriver(9001, "1", "VER")
        insertStint(9001, "1", stintNumber = 1, compound = "SOFT",   lapStart = 1,  lapEnd = 20)
        insertStint(9001, "1", stintNumber = 2, compound = "MEDIUM", lapStart = 21, lapEnd = 57)

        val stints = client.get("/api/sessions/9001/stints").body<List<StintDataDto>>()

        assertEquals(2, stints.size)
    }

    @Test
    fun `GET maps driver code from driver entry`() = testApp { client ->
        insertRace()
        insertSession(key = 9001)
        insertDriver(9001, "44", "HAM")
        insertStint(9001, "44", stintNumber = 1)

        val stints = client.get("/api/sessions/9001/stints").body<List<StintDataDto>>()

        assertEquals("44", stints[0].driverNumber)
        assertEquals("HAM", stints[0].driverCode)
    }

    @Test
    fun `GET maps compound, lap range and stint number`() = testApp { client ->
        insertRace()
        insertSession(key = 9001)
        insertDriver(9001, "1", "VER")
        insertStint(9001, "1", stintNumber = 2, compound = "HARD", lapStart = 30, lapEnd = 57)

        val stints = client.get("/api/sessions/9001/stints").body<List<StintDataDto>>()

        assertEquals(2,      stints[0].stintNumber)
        assertEquals("HARD", stints[0].compound)
        assertEquals(30,     stints[0].lapStart)
        assertEquals(57,     stints[0].lapEnd)
    }

    @Test
    fun `GET maps isNew flag`() = testApp { client ->
        insertRace()
        insertSession(key = 9001)
        insertDriver(9001, "1", "VER")
        insertStint(9001, "1", stintNumber = 1, isNew = true)

        val stints = client.get("/api/sessions/9001/stints").body<List<StintDataDto>>()

        assertEquals(true, stints[0].isNew)
    }

    @Test
    fun `GET maps null fields when not set`() = testApp { client ->
        insertRace()
        insertSession(key = 9001)
        insertDriver(9001, "1", "VER")
        insertStint(9001, "1", stintNumber = 1)

        val stints = client.get("/api/sessions/9001/stints").body<List<StintDataDto>>()

        assertNull(stints[0].compound)
        assertNull(stints[0].lapStart)
        assertNull(stints[0].lapEnd)
        assertNull(stints[0].isNew)
    }

    @Test
    fun `GET handles multiple drivers independently`() = testApp { client ->
        insertRace()
        insertSession(key = 9001)
        insertDriver(9001, "1",  "VER")
        insertDriver(9001, "44", "HAM")
        insertStint(9001, "1",  stintNumber = 1, compound = "SOFT")
        insertStint(9001, "44", stintNumber = 1, compound = "MEDIUM")

        val stints = client.get("/api/sessions/9001/stints").body<List<StintDataDto>>()

        assertEquals(2, stints.size)
        val ver = stints.first { it.driverNumber == "1" }
        val ham = stints.first { it.driverNumber == "44" }
        assertEquals("SOFT",   ver.compound)
        assertEquals("MEDIUM", ham.compound)
    }
}
