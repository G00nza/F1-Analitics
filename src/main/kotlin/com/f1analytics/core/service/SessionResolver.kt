package com.f1analytics.core.service

import com.f1analytics.core.domain.model.Session
import com.f1analytics.core.domain.port.SessionRepository

/** F-00.3: Determines which session to display at startup. Only reads the local DB. */
class SessionResolver(private val sessionRepo: SessionRepository) {

    suspend fun resolve(): Session? {
        // 1. Active session right now
        sessionRepo.findActive()?.let { return it }

        // 2. Session that ended within the last 4h
        sessionRepo.findMostRecent()?.let { return it }

        // 3. No session available → UI shows idle state
        return null
    }

    suspend fun findUpcoming(): Session? = sessionRepo.findNextUpcoming()
}
