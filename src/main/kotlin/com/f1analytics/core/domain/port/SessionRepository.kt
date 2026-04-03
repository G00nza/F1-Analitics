package com.f1analytics.core.domain.port

import com.f1analytics.core.domain.model.Session
import com.f1analytics.core.domain.model.SessionType

interface SessionRepository {
    suspend fun upsert(session: Session)
    suspend fun findByKey(key: Int): Session?
    suspend fun findByYearAndType(year: Int, type: SessionType): List<Session>
    suspend fun findLastRecorded(): Session?
    suspend fun updateStatus(key: Int, status: String)
    /** Returns the session currently active (status = "Started"), if any. */
    suspend fun findActive(): Session?
    /** Returns the most recently ended session (dateEnd within the last 4h), if any. */
    suspend fun findMostRecent(): Session?
    /** Returns the next session whose dateStart is in the future, if any. */
    suspend fun findNextUpcoming(): Session?
}
