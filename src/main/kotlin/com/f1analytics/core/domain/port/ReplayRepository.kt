package com.f1analytics.core.domain.port

import kotlinx.datetime.Instant

data class ReplayEvent(
    val timestamp: Instant,
    val topic: String,
    val json: String
)

interface ReplayRepository {
    /** Returns all recordable events for the session in ascending timestamp order. */
    suspend fun findAllEventsBySession(sessionKey: Int): List<ReplayEvent>
}
