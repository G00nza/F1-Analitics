package com.f1analytics.api

import com.f1analytics.core.domain.model.DriverLiveData
import com.f1analytics.core.domain.model.LapCountData
import com.f1analytics.core.domain.model.LiveSessionState
import com.f1analytics.core.domain.model.RaceControlEntry
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SafetyCarLiveViewTest : ViewTestBase() {

    private val scTimestamp = Instant.parse("2026-03-16T15:20:00Z")

    private fun scLiveState(
        driverData: Map<String, DriverLiveData> = emptyMap(),
        hasScMessage: Boolean = true
    ): LiveSessionState {
        val rc = if (hasScMessage) listOf(
            RaceControlEntry(
                message   = "SAFETY CAR DEPLOYED",
                flag      = "SC",
                scope     = null,
                lap       = 20,
                timestamp = scTimestamp
            )
        ) else emptyList()

        return LiveSessionState(
            sessionKey            = 9001,
            driverData            = driverData,
            raceControlMessages   = rc,
            lapCount              = LapCountData(current = 20, total = 57)
        )
    }

    @Test
    fun returns404WhenNoLiveSession() = testApp { client ->
        val response = client.get("/api/sessions/9001/strategy/safety-car/live")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun returns404WhenNoScInLiveMessages() = testApp { client ->
        insertRace()
        insertSession()
        val stateManager = makeStateManager()
        stateManager.injectState(scLiveState(hasScMessage = false))
        testApp(stateManager) { c ->
            val r = c.get("/api/sessions/9001/strategy/safety-car/live")
            assertEquals(HttpStatusCode.NotFound, r.status)
        }
    }

    @Test
    fun returnsRecommendationsWhenScIsActive() {
        insertRace()
        insertSession()
        insertSessionDriver(9001, "1", "VER", team = "Red Bull")
        insertSessionDriver(9001, "44", "HAM", team = "Mercedes")

        // VER race stints: SOFT (stint 1), HARD (stint 2, current since lap 2)
        // → used = {SOFT, HARD}, MEDIUM still available → hasNewTyres=true
        insertStint(sessionKey = 9001, driverNumber = "1", stintNumber = 1, compound = "SOFT",  lapStart = 1, lapEnd = 1)
        insertStint(sessionKey = 9001, driverNumber = "1", stintNumber = 2, compound = "HARD",  lapStart = 2, lapEnd = null)
        // HAM race stints: SOFT only (lap 1 to current)
        insertStint(sessionKey = 9001, driverNumber = "44", stintNumber = 1, compound = "SOFT", lapStart = 1, lapEnd = null)

        val state = scLiveState(
            driverData = mapOf(
                // VER: P1, HARD, 19 laps on tyres (lapNumber=20, stintLapStart=2)
                // HAM in P2 has interval=5.0 → gapToCarBehind for VER = 5.0s
                "1" to DriverLiveData(
                    position        = 1,
                    inPit           = false,
                    currentCompound = "HARD",
                    stintLapStart   = 2,
                    lapNumber       = 20,
                    stintNumber     = 2
                ),
                // HAM: P2, SOFT, 3 laps on tyres (lapNumber=20, stintLapStart=18)
                "44" to DriverLiveData(
                    position        = 2,
                    inPit           = false,
                    currentCompound = "SOFT",
                    stintLapStart   = 18,
                    lapNumber       = 20,
                    stintNumber     = 1,
                    interval        = "5.0"
                )
            )
        )

        val stateManager = makeStateManager()
        stateManager.injectState(state)

        testApp(stateManager) { client ->
            val response = client.get("/api/sessions/9001/strategy/safety-car/live")
            assertEquals(HttpStatusCode.OK, response.status)

            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals(9001, body["sessionKey"]!!.jsonPrimitive.content.toInt())
            assertEquals(20,   body["scLap"]!!.jsonPrimitive.content.toInt())

            val drivers = body["drivers"]!!.jsonArray
            assertEquals(2, drivers.size)

            val ver = drivers.first { it.jsonObject["driverNumber"]!!.jsonPrimitive.content == "1" }.jsonObject
            val ham = drivers.first { it.jsonObject["driverNumber"]!!.jsonPrimitive.content == "44" }.jsonObject

            // VER: tyreAge=19, lapsRemaining=37, gapBehind=5.0, hasNewTyres=true
            // score = min(95,100)*0.4 + min(160,100)*0.35 + (5/10*100)*0.25 = 38+35+12.5 = 85.5 → 86
            val verScore = ver["score"]!!.jsonPrimitive.content.toInt()
            assertTrue(verScore >= 76, "VER score $verScore should be ≥76 (Pit now)")
            assertEquals("Pit now — free stop", ver["message"]!!.jsonPrimitive.content)

            // HAM: tyreAge=3, lapsRemaining=37, gapBehind=null → freeStop=0
            // score = min(15,100)*0.4 + 100*0.35 + 0 = 6+35 = 41
            val hamScore = ham["score"]!!.jsonPrimitive.content.toInt()
            assertTrue(hamScore in 26..50, "HAM score $hamScore should be 26-50 (Consider pitting)")
            assertEquals("Consider pitting", ham["message"]!!.jsonPrimitive.content)

            assertTrue(verScore > hamScore)
        }
    }

    @Test
    fun returns404WhenScAlreadyClearedInLiveMessages() {
        insertRace()
        insertSession()
        insertSessionDriver(9001, "1", "VER")

        val stateWithClearSc = LiveSessionState(
            sessionKey = 9001,
            raceControlMessages = listOf(
                RaceControlEntry("SAFETY CAR DEPLOYED", "SC",    null, 20, scTimestamp),
                RaceControlEntry("SAFETY CAR IN THIS LAP", "CLEAR", null, 22, scTimestamp.plus(kotlin.time.Duration.parse("2m")))
            ),
            lapCount = LapCountData(current = 23, total = 57)
        )

        val stateManager = makeStateManager()
        stateManager.injectState(stateWithClearSc)

        testApp(stateManager) { client ->
            val response = client.get("/api/sessions/9001/strategy/safety-car/live")
            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }
}
