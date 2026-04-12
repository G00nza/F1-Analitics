package com.f1analytics.api

import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains

class DriverWatchlistViewTest : ViewTestBase() {

    @Test
    fun getGlobalWatchlistReturnsEmptyByDefault() = testApp { client ->
        val response = client.get("/api/strategy/watchlist")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(0, body["drivers"]!!.jsonArray.size)
        assertEquals("global", body["source"]!!.jsonPrimitive.content)
    }

    @Test
    fun setAndGetGlobalWatchlist() = testApp { client ->
        client.put("/api/strategy/watchlist") {
            contentType(ContentType.Application.Json)
            setBody("""{"drivers":["1","44","16"]}""")
        }

        val response = client.get("/api/strategy/watchlist")
        assertEquals(HttpStatusCode.OK, response.status)
        val drivers = Json.parseToJsonElement(response.bodyAsText())
            .jsonObject["drivers"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertContains(drivers, "1")
        assertContains(drivers, "44")
        assertContains(drivers, "16")
    }

    @Test
    fun getSessionWatchlistFallsBackToGlobal() = testApp { client ->
        insertRace()
        insertSession()

        client.put("/api/strategy/watchlist") {
            contentType(ContentType.Application.Json)
            setBody("""{"drivers":["1","44"]}""")
        }

        val response = client.get("/api/sessions/9001/strategy/watchlist")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("global", body["source"]!!.jsonPrimitive.content)
        val drivers = body["drivers"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertContains(drivers, "1")
        assertContains(drivers, "44")
    }

    @Test
    fun setSessionWatchlistOverridesGlobal() = testApp { client ->
        insertRace()
        insertSession()

        // Set global
        client.put("/api/strategy/watchlist") {
            contentType(ContentType.Application.Json)
            setBody("""{"drivers":["1","44"]}""")
        }

        // Override for session
        client.put("/api/sessions/9001/strategy/watchlist") {
            contentType(ContentType.Application.Json)
            setBody("""{"drivers":["16"]}""")
        }

        val response = client.get("/api/sessions/9001/strategy/watchlist")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("session", body["source"]!!.jsonPrimitive.content)
        val drivers = body["drivers"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("16"), drivers)
    }
}
