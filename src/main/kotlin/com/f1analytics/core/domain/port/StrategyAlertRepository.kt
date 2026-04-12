package com.f1analytics.core.domain.port

import com.f1analytics.core.domain.model.StrategyAlert

interface StrategyAlertRepository {
    suspend fun findBySession(sessionKey: Int): List<StrategyAlert>
    suspend fun save(alert: StrategyAlert)
}
