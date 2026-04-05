package com.f1analytics.api

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReplayEventViewTest : ViewTestBase() {

    @Test
    fun `returns 400 for non-integer session key`() = testApp { client ->
        val response = client.get("/api/events/replay/notanumber")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `returns event-stream content type`() = testApp { client ->
        insertRace()
        insertSession(key = 9001, recorded = true)

        val response = client.get("/api/events/replay/9001")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(
            response.headers[HttpHeaders.ContentType]?.startsWith("text/event-stream") == true,
            "Expected text/event-stream, got: ${response.headers[HttpHeaders.ContentType]}"
        )
    }

    @Test
    fun `completes with empty body when session has no recorded events`() = testApp { client ->
        insertRace()
        insertSession(key = 9001, recorded = true)

        val response = client.get("/api/events/replay/9001")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("", response.bodyAsText())
    }
}
