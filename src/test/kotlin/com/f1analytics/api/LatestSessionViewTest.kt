package com.f1analytics.api

import com.f1analytics.api.views.SessionDto
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LatestSessionViewTest : ViewTestBase() {

    @Test
    fun `returns 204 when no sessions in DB`() = testApp { client ->
        val response = client.get("/api/sessions/latest")
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `returns session when a recorded session exists`() = testApp { client ->
        insertRace()
        insertSession(key = 9001, name = "Race", recorded = true)

        val dto = client.get("/api/sessions/latest").body<SessionDto>()

        assertEquals(9001, dto.key)
        assertEquals("Race", dto.name)
        assertTrue(dto.recorded)
    }

    @Test
    fun `prefers active session over recorded one`() = testApp { client ->
        insertRace()
        insertSession(key = 9001, name = "Race", recorded = true)
        insertSession(key = 9002, name = "Sprint", type = "SPRINT", status = "Started", recorded = false)

        val dto = client.get("/api/sessions/latest").body<SessionDto>()

        assertEquals(9002, dto.key)
    }
}
