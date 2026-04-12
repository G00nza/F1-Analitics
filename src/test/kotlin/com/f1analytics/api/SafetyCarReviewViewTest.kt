package com.f1analytics.api

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SafetyCarReviewViewTest : ViewTestBase() {

    private val scTimestamp    = Instant.parse("2026-03-16T15:20:00Z")
    private val clearTimestamp = Instant.parse("2026-03-16T15:23:00Z")
    private val finalTimestamp = Instant.parse("2026-03-16T16:00:00Z")

    @Test
    fun returnsEmptyEventsWhenNoSc() = testApp { client ->
        insertRace()
        insertSession()

        val response = client.get("/api/sessions/9001/strategy/safety-car/review")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(9001, body["sessionKey"]!!.jsonPrimitive.content.toInt())
        assertEquals(0, body["events"]!!.jsonArray.size)
    }

    @Test
    fun computesScEventWithCapitalization() = testApp { client ->
        insertRace()
        insertSession()
        insertSessionDriver(9001, "1", "VER")
        insertSessionDriver(9001, "44", "HAM")

        // RC messages: SC at lap 20, CLEAR at lap 23
        insertRaceControlMessage(9001, lapNumber = 20, flag = "SC",    timestamp = scTimestamp)
        insertRaceControlMessage(9001, lapNumber = 23, flag = "CLEAR", timestamp = clearTimestamp)

        // VER: SOFT stint 1 (lap 1), HARD stint 2 (lap 2–20 at SC → tyreAge=19)
        // VER pitted during SC: MEDIUM stint 3 starts lap 21
        insertStint(9001, "1", stintNumber = 1, compound = "SOFT",   lapStart = 1,  lapEnd = 1)
        insertStint(9001, "1", stintNumber = 2, compound = "HARD",   lapStart = 2,  lapEnd = 20)
        insertStint(9001, "1", stintNumber = 3, compound = "MEDIUM", lapStart = 21, lapEnd = null)

        // HAM: SOFT stint 1 (lap 1 → tyreAge=20 at SC), no pit during SC
        insertStint(9001, "44", stintNumber = 1, compound = "SOFT", lapStart = 1, lapEnd = null)

        // Positions at SC time (scTimestamp)
        insertPositionSnapshot(9001, "1",  position = 1, timestamp = scTimestamp)
        insertPositionSnapshot(9001, "44", position = 2, timestamp = scTimestamp)

        // Final positions
        insertPositionSnapshot(9001, "1",  position = 1, timestamp = finalTimestamp)
        insertPositionSnapshot(9001, "44", position = 4, timestamp = finalTimestamp)

        val response = client.get("/api/sessions/9001/strategy/safety-car/review")
        assertEquals(HttpStatusCode.OK, response.status)

        val body   = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val events = body["events"]!!.jsonArray
        assertEquals(1, events.size)

        val event = events[0].jsonObject
        assertEquals(20, event["scLap"]!!.jsonPrimitive.int)
        assertEquals(23, event["scEndLap"]!!.jsonPrimitive.int)

        val drivers = event["drivers"]!!.jsonArray
        val ver = drivers.first { it.jsonObject["driverNumber"]!!.jsonPrimitive.content == "1" }.jsonObject
        val ham = drivers.first { it.jsonObject["driverNumber"]!!.jsonPrimitive.content == "44" }.jsonObject

        // VER: pittedDuringSc=true, positionAtSc=1, finalPosition=1 → capitalizedCorrectly=true
        assertTrue(ver["pittedDuringSc"]!!.jsonPrimitive.boolean)
        assertEquals(1, ver["positionAtSc"]!!.jsonPrimitive.int)
        assertEquals(1, ver["finalPosition"]!!.jsonPrimitive.int)
        assertTrue(ver["capitalizedCorrectly"]!!.jsonPrimitive.boolean)

        // HAM: pittedDuringSc=false, positionAtSc=2, finalPosition=4 → capitalizedCorrectly=false
        assertFalse(ham["pittedDuringSc"]!!.jsonPrimitive.boolean)
        assertEquals(2, ham["positionAtSc"]!!.jsonPrimitive.int)
        assertEquals(4, ham["finalPosition"]!!.jsonPrimitive.int)
        assertFalse(ham["capitalizedCorrectly"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun appliesNewTyrePenaltyWhenAllDryCompoundsUsed() = testApp { client ->
        insertRace()
        insertSession()
        insertSessionDriver(9001, "1", "VER")

        insertRaceControlMessage(9001, lapNumber = 40, flag = "SC", timestamp = scTimestamp)

        // VER used all 3 dry compounds → no new tyres available → penalty
        insertStint(9001, "1", stintNumber = 1, compound = "SOFT",   lapStart = 1,  lapEnd = 14)
        insertStint(9001, "1", stintNumber = 2, compound = "MEDIUM", lapStart = 15, lapEnd = 28)
        insertStint(9001, "1", stintNumber = 3, compound = "HARD",   lapStart = 29, lapEnd = null)

        val response = client.get("/api/sessions/9001/strategy/safety-car/review")
        assertEquals(HttpStatusCode.OK, response.status)

        val drivers = Json.parseToJsonElement(response.bodyAsText())
            .jsonObject["events"]!!.jsonArray[0]
            .jsonObject["drivers"]!!.jsonArray

        val ver = drivers.first().jsonObject
        assertFalse(ver["hasNewTyresAvailable"]!!.jsonPrimitive.boolean)

        // tyreAge at SC lap 40 = 40 - 29 + 1 = 12 laps
        // rawScore (no lapsRemaining) = 12*5*0.4 + 0 + 0 = 24 → with penalty 24*0.7 = 16.8 → 17
        // Without penalty it would be 24 → message "Stay out"
        // With penalty it's lower → still "Stay out" but score should reflect the penalty
        val scoreWithPenalty = ver["score"]!!.jsonPrimitive.int
        assertTrue(scoreWithPenalty < 24, "Score $scoreWithPenalty should be < 24 (penalty applied)")
    }
}
