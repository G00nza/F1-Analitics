package com.f1analytics.api

import com.f1analytics.api.views.MeetingDto
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MeetingsViewTest : ViewTestBase() {

    @Test
    fun `GET meetings returns empty list when no races for requested year`() = testApp { client ->
        val meetings = client.get("/api/meetings?year=2020").body<List<MeetingDto>>()
        assertTrue(meetings.isEmpty())
    }

    @Test
    fun `GET meetings returns meetings with sessions for the given year`() = testApp { client ->
        insertRace()
        insertSession(key = 9001, name = "Race", type = "RACE")
        insertSession(key = 9002, name = "Qualifying", type = "QUALIFYING")

        val meetings = client.get("/api/meetings?year=2026").body<List<MeetingDto>>()

        assertEquals(1, meetings.size)
        val meeting = meetings[0]
        assertEquals("Bahrain Grand Prix", meeting.name)
        assertEquals("Bahrain International Circuit", meeting.circuit)
        assertEquals(2, meeting.sessions.size)
        assertEquals(setOf("Race", "Qualifying"), meeting.sessions.map { it.name }.toSet())
    }

    @Test
    fun `GET meetings returns race with empty sessions list when race has no sessions`() = testApp { client ->
        insertRace()

        val meetings = client.get("/api/meetings?year=2026").body<List<MeetingDto>>()

        assertEquals(1, meetings.size)
        assertEquals(1, meetings[0].key)
        assertTrue(meetings[0].sessions.isEmpty())
    }

    @Test
    fun `GET meetings current returns 204 when no races exist`() = testApp { client ->
        val response = client.get("/api/meetings/current")
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `GET meetings current returns the nearest race with its sessions`() = testApp { client ->
        insertRace()
        insertSession(key = 9001, name = "Race", type = "RACE")

        val meeting = client.get("/api/meetings/current").body<MeetingDto>()

        assertEquals(1, meeting.key)
        assertEquals("Bahrain Grand Prix", meeting.name)
        assertEquals(1, meeting.sessions.size)
        assertEquals("Race", meeting.sessions[0].name)
    }
}
