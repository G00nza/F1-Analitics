package com.f1analytics.api

import com.f1analytics.api.dto.LiveSessionStateDto
import com.f1analytics.core.domain.model.LiveSessionState
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionStateViewTest : ViewTestBase() {

    @Test
    fun `returns 400 for non-integer key`() = testApp { client ->
        val response = client.get("/api/sessions/abc/state")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `returns 404 when state manager has no state`() = testApp { client ->
        val response = client.get("/api/sessions/9001/state")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `returns 404 when state is for a different session key`() {
        val stateManager = makeStateManager()
        stateManager.injectState(LiveSessionState(sessionKey = 9999))

        testApp(stateManager) { client ->
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

        testApp(stateManager) { client ->
            val dto = client.get("/api/sessions/9001/state").body<LiveSessionStateDto>()

            assertEquals(9001, dto.sessionKey)
            assertEquals("Race", dto.sessionName)
            assertEquals("AllClear", dto.trackStatus)
            assertNull(dto.sessionStatus)
        }
    }
}
