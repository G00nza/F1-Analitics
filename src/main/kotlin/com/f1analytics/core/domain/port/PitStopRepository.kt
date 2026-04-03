package com.f1analytics.core.domain.port

import kotlinx.datetime.Instant

interface PitStopRepository {
    suspend fun insert(sessionKey: Int, driverNumber: String, lapNumber: Int?, timestamp: Instant)
}
