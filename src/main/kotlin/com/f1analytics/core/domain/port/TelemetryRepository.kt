package com.f1analytics.core.domain.port

import com.f1analytics.core.domain.model.CarTelemetryEntry

interface TelemetryRepository {
    suspend fun insertBatch(sessionKey: Int, entries: List<CarTelemetryEntry>)
}
