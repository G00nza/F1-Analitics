package com.f1analytics.api

import com.f1analytics.core.domain.model.StrategyAlert
import com.f1analytics.data.db.repository.ExposedStrategyAlertRepository
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class StrategyAlertsViewTest : ViewTestBase() {

    @Test
    fun returnsEmptyAlertsForSession() = testApp { client ->
        insertRace()
        insertSession()

        val response = client.get("/api/sessions/9001/strategy/alerts")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(9001, body["sessionKey"]!!.jsonPrimitive.content.toInt())
        assertEquals(0, body["alerts"]!!.jsonArray.size)
    }

    @Test
    fun returnsStoredAlertsWithDriverCodes() = testApp { client ->
        insertRace()
        insertSession()
        insertSessionDriver(9001, "1", "VER")
        insertSessionDriver(9001, "44", "HAM")

        val alertRepo = ExposedStrategyAlertRepository(db)
        alertRepo.save(
            StrategyAlert(
                id               = 0,
                sessionKey       = 9001,
                lap              = 28,
                type             = "UNDERCUT",
                instigatorNumber = "1",
                rivalNumber      = "44",
                gapSeconds       = 2.1,
                predictedOutcome = "Potential undercut opportunity on 44",
                confirmedOutcome = null,
                timestamp        = Clock.System.now()
            )
        )

        val response = client.get("/api/sessions/9001/strategy/alerts")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val alerts = body["alerts"]!!.jsonArray
        assertEquals(1, alerts.size)

        val alert = alerts[0].jsonObject
        assertEquals("UNDERCUT", alert["type"]!!.jsonPrimitive.content)
        assertEquals("1", alert["instigatorNumber"]!!.jsonPrimitive.content)
        assertEquals("VER", alert["instigatorCode"]!!.jsonPrimitive.content)
        assertEquals("44", alert["rivalNumber"]!!.jsonPrimitive.content)
        assertEquals("HAM", alert["rivalCode"]!!.jsonPrimitive.content)
        assertEquals(28, alert["lap"]!!.jsonPrimitive.content.toInt())
    }
}
