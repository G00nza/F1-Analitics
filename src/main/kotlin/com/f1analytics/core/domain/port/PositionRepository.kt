package com.f1analytics.core.domain.port

import com.f1analytics.core.domain.model.DriverTimingDelta
import kotlinx.datetime.Instant

data class DriverPositionSnapshot(
    val driverNumber: String,
    val position: Int?,
    val gapToLeader: String?,
    val interval: String?
)

interface PositionRepository {
    suspend fun insertSnapshot(sessionKey: Int, deltas: Map<String, DriverTimingDelta>, timestamp: Instant)
    suspend fun findLatestPositions(sessionKey: Int): Map<String, DriverPositionSnapshot>
    suspend fun findAllPositionsByDriver(sessionKey: Int): Map<String, List<DriverPositionSnapshot>>
}
