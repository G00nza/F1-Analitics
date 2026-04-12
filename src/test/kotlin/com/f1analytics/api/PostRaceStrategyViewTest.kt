package com.f1analytics.api

import com.f1analytics.core.domain.model.StrategyAlert
import com.f1analytics.data.db.repository.ExposedStrategyAlertRepository
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostRaceStrategyViewTest : ViewTestBase() {

    private val raceStart   = Instant.parse("2026-03-16T15:00:00Z")
    private val raceEnd     = Instant.parse("2026-03-16T17:00:00Z")

    @Test
    fun returnsDriverStrategiesWithStintsAndStops() = testApp { client ->
        insertRace()
        insertSession()
        insertSessionDriver(9001, "1",  "VER")
        insertSessionDriver(9001, "44", "HAM")

        // VER: 1-stop — SOFT(1-14) → HARD(15+)
        insertStint(9001, "1",  stintNumber = 1, compound = "SOFT", lapStart = 1,  lapEnd = 14)
        insertStint(9001, "1",  stintNumber = 2, compound = "HARD", lapStart = 15, lapEnd = null)

        // HAM: 2-stop — SOFT(1-10) → MEDIUM(11-24) → HARD(25+)
        insertStint(9001, "44", stintNumber = 1, compound = "SOFT",   lapStart = 1,  lapEnd = 10)
        insertStint(9001, "44", stintNumber = 2, compound = "MEDIUM", lapStart = 11, lapEnd = 24)
        insertStint(9001, "44", stintNumber = 3, compound = "HARD",   lapStart = 25, lapEnd = null)

        // Final positions
        insertPositionSnapshot(9001, "1",  position = 1, timestamp = raceEnd)
        insertPositionSnapshot(9001, "44", position = 2, timestamp = raceEnd)

        val response = client.get("/api/sessions/9001/strategy/post-race")
        assertEquals(HttpStatusCode.OK, response.status)

        val body    = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val drivers = body["drivers"]!!.jsonArray

        val ver = drivers.first { it.jsonObject["driverNumber"]!!.jsonPrimitive.content == "1" }.jsonObject
        val ham = drivers.first { it.jsonObject["driverNumber"]!!.jsonPrimitive.content == "44" }.jsonObject

        assertEquals(1, ver["stops"]!!.jsonPrimitive.int)
        assertEquals(2, ham["stops"]!!.jsonPrimitive.int)

        val verStints = ver["stints"]!!.jsonArray
        assertEquals(2, verStints.size)
        assertEquals("SOFT", verStints[0].jsonObject["compound"]!!.jsonPrimitive.content)
        assertEquals(14,     verStints[0].jsonObject["laps"]!!.jsonPrimitive.int)
        assertEquals("HARD", verStints[1].jsonObject["compound"]!!.jsonPrimitive.content)

        assertEquals(1, ver["finalPosition"]!!.jsonPrimitive.int)
        assertEquals(2, ham["finalPosition"]!!.jsonPrimitive.int)

        assertEquals("VER", ver["driverCode"]!!.jsonPrimitive.content)
    }

    @Test
    fun computesStrategyComparisonByStopCount() = testApp { client ->
        insertRace()
        insertSession()
        insertSessionDriver(9001, "1",  "VER")
        insertSessionDriver(9001, "44", "HAM")
        insertSessionDriver(9001, "16", "LEC")

        // VER and HAM: 1-stop
        insertStint(9001, "1",  stintNumber = 1, compound = "SOFT", lapStart = 1, lapEnd = 20)
        insertStint(9001, "1",  stintNumber = 2, compound = "HARD", lapStart = 21)
        insertStint(9001, "44", stintNumber = 1, compound = "SOFT", lapStart = 1, lapEnd = 18)
        insertStint(9001, "44", stintNumber = 2, compound = "HARD", lapStart = 19)

        // LEC: 2-stop
        insertStint(9001, "16", stintNumber = 1, compound = "SOFT",   lapStart = 1,  lapEnd = 12)
        insertStint(9001, "16", stintNumber = 2, compound = "MEDIUM", lapStart = 13, lapEnd = 28)
        insertStint(9001, "16", stintNumber = 3, compound = "HARD",   lapStart = 29)

        // Final positions
        insertPositionSnapshot(9001, "1",  position = 1, timestamp = raceEnd)
        insertPositionSnapshot(9001, "44", position = 3, timestamp = raceEnd)
        insertPositionSnapshot(9001, "16", position = 2, timestamp = raceEnd)

        val response = client.get("/api/sessions/9001/strategy/post-race")
        assertEquals(HttpStatusCode.OK, response.status)

        val comparison = Json.parseToJsonElement(response.bodyAsText())
            .jsonObject["strategyComparison"]!!.jsonObject

        val oneStop = comparison["oneStop"]!!.jsonObject
        assertEquals(2, oneStop["driverCount"]!!.jsonPrimitive.int)
        // avg finish = (P1 + P3) / 2 = 2.0
        assertEquals(2.0, oneStop["avgFinishPosition"]!!.jsonPrimitive.double)

        val twoStop = comparison["twoStop"]!!.jsonObject
        assertEquals(1, twoStop["driverCount"]!!.jsonPrimitive.int)
        assertEquals(2.0, twoStop["avgFinishPosition"]!!.jsonPrimitive.double)  // LEC at P2

        assertTrue(comparison["threeOrMore"]!! is JsonNull)
    }

    @Test
    fun includesUndercutResultsFromAlerts() = testApp { client ->
        insertRace()
        insertSession()
        insertSessionDriver(9001, "1",  "VER")
        insertSessionDriver(9001, "16", "LEC")

        insertStint(9001, "1",  stintNumber = 1, compound = "SOFT", lapStart = 1)
        insertStint(9001, "16", stintNumber = 1, compound = "SOFT", lapStart = 1)

        insertPositionSnapshot(9001, "1",  position = 1, timestamp = raceEnd)
        insertPositionSnapshot(9001, "16", position = 3, timestamp = raceEnd)

        val alertRepo = ExposedStrategyAlertRepository(db)
        alertRepo.save(
            StrategyAlert(
                id = 0, sessionKey = 9001, lap = 14, type = "UNDERCUT",
                instigatorNumber = "1", rivalNumber = "16",
                gapSeconds = 0.3, predictedOutcome = "Potential undercut opportunity on 16",
                confirmedOutcome = null, timestamp = Clock.System.now()
            )
        )

        val response = client.get("/api/sessions/9001/strategy/post-race")
        assertEquals(HttpStatusCode.OK, response.status)

        val undercutResults = Json.parseToJsonElement(response.bodyAsText())
            .jsonObject["undercutResults"]!!.jsonArray

        assertEquals(1, undercutResults.size)
        val result = undercutResults[0].jsonObject
        assertEquals("VER", result["instigatorCode"]!!.jsonPrimitive.content)
        assertEquals("LEC", result["rivalCode"]!!.jsonPrimitive.content)
        assertEquals(14,    result["lap"]!!.jsonPrimitive.int)
        assertEquals(1,     result["instigatorFinalPosition"]!!.jsonPrimitive.int)
        assertEquals(3,     result["rivalFinalPosition"]!!.jsonPrimitive.int)
    }

    @Test
    fun includesScBeneficiariesFromScEvents() = testApp { client ->
        val scTimestamp    = Instant.parse("2026-03-16T15:30:00Z")
        val clearTimestamp = Instant.parse("2026-03-16T15:33:00Z")

        insertRace()
        insertSession()
        insertSessionDriver(9001, "1",  "VER")
        insertSessionDriver(9001, "14", "ALO")

        insertStint(9001, "1",  stintNumber = 1, compound = "SOFT", lapStart = 1)
        insertStint(9001, "14", stintNumber = 1, compound = "HARD", lapStart = 1)

        insertRaceControlMessage(9001, lapNumber = 31, flag = "SC",    timestamp = scTimestamp)
        insertRaceControlMessage(9001, lapNumber = 34, flag = "CLEAR", timestamp = clearTimestamp)

        // Positions at SC time
        insertPositionSnapshot(9001, "1",  position = 1, timestamp = scTimestamp)
        insertPositionSnapshot(9001, "14", position = 6, timestamp = scTimestamp)

        // Final positions — ALO gained 3 positions (P6 → P3)
        insertPositionSnapshot(9001, "1",  position = 1, timestamp = raceEnd)
        insertPositionSnapshot(9001, "14", position = 3, timestamp = raceEnd)

        val response = client.get("/api/sessions/9001/strategy/post-race")
        assertEquals(HttpStatusCode.OK, response.status)

        val scBeneficiaries = Json.parseToJsonElement(response.bodyAsText())
            .jsonObject["scBeneficiaries"]!!.jsonArray

        assertEquals(1, scBeneficiaries.size)   // only ALO gained positions
        val alo = scBeneficiaries[0].jsonObject
        assertEquals("ALO", alo["driverCode"]!!.jsonPrimitive.content)
        assertEquals(6,     alo["positionAtSc"]!!.jsonPrimitive.int)
        assertEquals(3,     alo["finalPosition"]!!.jsonPrimitive.int)
        assertEquals(3,     alo["positionsGained"]!!.jsonPrimitive.int)
        assertEquals(31,    alo["scLap"]!!.jsonPrimitive.int)
    }
}
