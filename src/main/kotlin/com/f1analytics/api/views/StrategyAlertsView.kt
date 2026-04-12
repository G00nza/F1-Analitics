package com.f1analytics.api.views

import com.f1analytics.api.dto.StrategyAlertDto
import com.f1analytics.api.dto.StrategyAlertsDto
import com.f1analytics.core.domain.port.SessionDriverRepository
import com.f1analytics.core.domain.port.StrategyAlertRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

class StrategyAlertsView(
    private val strategyAlertRepository: StrategyAlertRepository,
    private val sessionDriverRepository: SessionDriverRepository,
) {
    suspend fun handle(call: ApplicationCall) {
        val sessionKey = call.parameters["sessionKey"]?.toIntOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, "Invalid session key")

        val alerts = strategyAlertRepository.findBySession(sessionKey)
        val drivers = sessionDriverRepository.findBySession(sessionKey).associateBy { it.number }

        val dtos = alerts.map { alert ->
            StrategyAlertDto(
                lap              = alert.lap,
                type             = alert.type,
                instigatorNumber = alert.instigatorNumber,
                instigatorCode   = drivers[alert.instigatorNumber]?.code,
                rivalNumber      = alert.rivalNumber,
                rivalCode        = drivers[alert.rivalNumber]?.code,
                gapSeconds       = alert.gapSeconds,
                predictedOutcome = alert.predictedOutcome,
                confirmedOutcome = alert.confirmedOutcome
            )
        }

        call.respond(StrategyAlertsDto(sessionKey = sessionKey, alerts = dtos))
    }
}
