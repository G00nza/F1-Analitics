package com.f1analytics.core.domain.port

import com.f1analytics.core.domain.model.TimingMessage
import kotlinx.coroutines.flow.Flow

interface LiveTimingClient {
    /** Hot shared flow of parsed timing messages from the bridge. */
    val messages: Flow<TimingMessage>

    /** Connect to the bridge WebSocket and emit messages continuously.
     *  Retries automatically on disconnection — suspends forever until cancelled. */
    suspend fun connect(port: Int)
}
