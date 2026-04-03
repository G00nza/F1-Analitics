package com.f1analytics.core.domain.port

import com.f1analytics.core.domain.model.DriverTimingDelta
import com.f1analytics.core.domain.model.Lap
import kotlinx.datetime.Instant

interface LapRepository {
    suspend fun upsertDeltas(sessionKey: Int, deltas: Map<String, DriverTimingDelta>, ts: Instant)
    suspend fun findBySession(sessionKey: Int): List<Lap>
    suspend fun findByDriver(sessionKey: Int, driverNumber: String): List<Lap>
    suspend fun findBestLaps(sessionKey: Int): Map<String, Lap>
}
