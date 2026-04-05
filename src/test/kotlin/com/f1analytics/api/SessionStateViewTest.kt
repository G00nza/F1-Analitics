package com.f1analytics.api

import com.f1analytics.core.domain.model.LiveSessionState
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class SessionStateViewTest : ViewTestBase() {

    @Test
    fun `returns 400 for non-integer key`() = testApp {
        val response = client.get("/api/sessions/abc/state")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `returns 404 when state manager has no state`() = testApp {
        val response = client.get("/api/sessions/9001/state")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `returns 404 when state is for a different session key`() {
        val stateManager = makeStateManager()
        stateManager.injectState(LiveSessionState(sessionKey = 9999))

        testApp(stateManager = stateManager) {
            val response = client.get("/api/sessions/9001/state")
            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @Test
    fun `returns state when session key matches`() {
        val stateManager = makeStateManager()
        stateManager.injectState(
            LiveSessionState(sessionKey = 9001, sessionName = "Race", trackStatus = "AllClear")
        )

        testApp(stateManager = stateManager) {
            val response = client.get("/api/sessions/9001/state")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertContains(body, "\"sessionKey\":9001")
            assertContains(body, "\"sessionName\":\"Race\"")
            assertContains(body, "\"trackStatus\":\"AllClear\"")
        }
    }
}
