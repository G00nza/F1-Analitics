package com.f1analytics.core.domain.port

import com.f1analytics.core.domain.model.DriverTireStintDelta
import com.f1analytics.core.domain.model.Stint

interface StintRepository {
    suspend fun upsertDeltas(sessionKey: Int, deltas: Map<String, DriverTireStintDelta>)
    suspend fun findBySession(sessionKey: Int): List<Stint>
    suspend fun findCurrentStint(sessionKey: Int, driverNumber: String): Stint?
}
