package com.f1analytics.core.domain.port

import com.f1analytics.core.domain.model.TimingMessage
import kotlinx.datetime.Instant

interface RaceControlRepository {
    suspend fun insert(sessionKey: Int, msg: TimingMessage.RaceControlMsg, timestamp: Instant)
}
