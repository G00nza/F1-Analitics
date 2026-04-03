package com.f1analytics.core.domain.port

import com.f1analytics.core.domain.model.DriverEntry

interface SessionDriverRepository {
    suspend fun upsertAll(sessionKey: Int, drivers: Map<String, DriverEntry>)
    suspend fun findBySession(sessionKey: Int): List<DriverEntry>
}
