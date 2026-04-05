package com.f1analytics.api

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class LatestSessionViewTest : ViewTestBase() {

    @Test
    fun `returns 204 when no sessions in DB`() = testApp {
        val response = client.get("/api/sessions/latest")
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `returns session when a recorded session exists`() = testApp {
        insertSession(key = 9001, name = "Race", recorded = true)

        val response = client.get("/api/sessions/latest")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "\"key\":9001")
        assertContains(body, "\"name\":\"Race\"")
        assertContains(body, "\"recorded\":true")
    }

    @Test
    fun `prefers active session over recorded one`() = testApp {
        insertSession(key = 9001, name = "Race", recorded = true)
        insertSession(key = 9002, name = "Sprint", type = "SPRINT", status = "Started", recorded = false)

        val response = client.get("/api/sessions/latest")

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "\"key\":9002")
    }
}
