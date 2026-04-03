package com.f1analytics.core.domain.port

import com.f1analytics.core.domain.model.DriverTimingDelta
import kotlinx.datetime.Instant

interface PositionRepository {
    suspend fun insertSnapshot(sessionKey: Int, deltas: Map<String, DriverTimingDelta>, timestamp: Instant)
}
