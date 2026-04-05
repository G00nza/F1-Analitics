package com.f1analytics.api

import com.f1analytics.api.dto.WeekendInfoDto
import com.f1analytics.data.db.tables.RacesTable
import io.ktor.client.call.body
import io.ktor.client.request.get
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals

class WeekendViewTest : ViewTestBase() {

    private fun insertRaceWithOfficialName(
        key: Int = 1,
        name: String = "Bahrain Grand Prix",
        officialName: String = "Formula 1 Gulf Air Bahrain Grand Prix 2026",
        circuit: String = "Bahrain International Circuit",
        year: Int = 2026,
        dateStart: String = "2026-04-06"
    ) = transaction(db) {
        RacesTable.insert {
            it[RacesTable.key]          = key
            it[RacesTable.name]         = name
            it[RacesTable.officialName] = officialName
            it[RacesTable.circuit]      = circuit
            it[RacesTable.year]         = year
            it[RacesTable.dateStart]    = dateStart
        }
    }

    @Test
    fun `GET returns meeting name, circuit and year`() = testApp { client ->
        insertRaceWithOfficialName(
            name         = "Bahrain Grand Prix",
            officialName = "Formula 1 Gulf Air Bahrain Grand Prix 2026",
            circuit      = "Bahrain International Circuit",
            year         = 2026
        )

        val dto = client.get("/api/weekend").body<WeekendInfoDto>()

        assertEquals("Bahrain Grand Prix", dto.meetingName)
        assertEquals("Formula 1 Gulf Air Bahrain Grand Prix 2026", dto.circuitName)
        assertEquals(2026, dto.year)
    }

    @Test
    fun `GET returns sessions for current race`() = testApp { client ->
        insertRaceWithOfficialName(key = 1)
        insertSession(key = 9001, raceKey = 1, name = "Race",      type = "RACE",      status = "Finished")
        insertSession(key = 9002, raceKey = 1, name = "Qualifying", type = "QUALIFYING", status = "Finished")

        val dto = client.get("/api/weekend").body<WeekendInfoDto>()

        assertEquals(2, dto.sessions.size)
    }

    @Test
    fun `GET maps session key, name, type and status`() = testApp { client ->
        insertRaceWithOfficialName(key = 1)
        insertSession(key = 9001, raceKey = 1, name = "Race", type = "RACE", status = "Finished")

        val dto = client.get("/api/weekend").body<WeekendInfoDto>()

        val session = dto.sessions.single()
        assertEquals(9001,       session.key)
        assertEquals("Race",     session.name)
        assertEquals("RACE",     session.type)
        assertEquals("Finished", session.status)
    }

    @Test
    fun `GET picks the race closest to today when multiple exist`() = testApp { client ->
        insertRaceWithOfficialName(key = 1, name = "Old Race",     officialName = "Old Circuit GP", dateStart = "2024-01-01")
        insertRaceWithOfficialName(key = 2, name = "Current Race", officialName = "Current Circuit GP", dateStart = "2026-04-06")
        insertSession(key = 9001, raceKey = 2, name = "Race", type = "RACE", status = "Finished")

        val dto = client.get("/api/weekend").body<WeekendInfoDto>()

        assertEquals("Current Race", dto.meetingName)
    }
}
