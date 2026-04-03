package com.f1analytics.core.domain.port

import com.f1analytics.core.domain.model.Session
import com.f1analytics.core.domain.model.SessionType

interface SessionRepository {
    suspend fun upsert(session: Session)
    suspend fun findByKey(key: Int): Session?
    suspend fun findByYearAndType(year: Int, type: SessionType): List<Session>
    suspend fun findLastRecorded(): Session?
    suspend fun updateStatus(key: Int, status: String)
}
