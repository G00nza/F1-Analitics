package com.f1analytics.api

import com.f1analytics.api.dto.SessionStartingSoonDto
import com.f1analytics.api.dto.toDto
import com.f1analytics.core.service.LiveSessionStateManager
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.Writer
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}
private val json = Json { encodeDefaults = true }

@Serializable
private data class HeartbeatDto(val timestamp: String)

@Serializable
private data class SessionStatusDto(val status: String)

/**
 * Simple SSE sink backed by a [Writer] (from respondTextWriter).
 * Writes SSE-formatted lines synchronously on the IO dispatcher.
 */
class SseSink(private val writer: Writer) {
    fun sendEvent(data: String, event: String) {
        writer.write("event: $event\r\n")
        writer.write("data: $data\r\n\r\n")
        writer.flush()
    }
}

/** F-06.2: Fans out the session StateFlow to all connected SSE browsers. */
class SseManager(private val stateManager: LiveSessionStateManager) {

    private val _systemEvents = MutableSharedFlow<SessionStatusEvent>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    suspend fun handleLiveClient(sink: SseSink) {
        logger.info { "SSE client connected" }
        try {
            val currentState = stateManager.stateFlow.value
            if (currentState != null) {
                // Send the current full state immediately on connect
                sink.sendEvent(json.encodeToString(currentState.toDto()), "session_state")
            } else {
                // No active session — tell the client to show idle view
                sink.sendEvent(json.encodeToString(SessionStatusDto("IDLE")), "session_status")
            }

            coroutineScope {
                // F-06.2: Heartbeat — named event with timestamp prevents proxy timeouts
                launch {
                    while (true) {
                        delay(30.seconds)
                        val hb = HeartbeatDto(Clock.System.now().toString())
                        sink.sendEvent(json.encodeToString(hb), "heartbeat")
                    }
                }

                // Incremental state updates
                launch {
                    stateManager.stateFlow
                        .filterNotNull()
                        .distinctUntilChanged()
                        .collect { state ->
                            sink.sendEvent(json.encodeToString(state.toDto()), "session_state")
                        }
                }

                // System events (session_starting_soon, etc.)
                launch {
                    _systemEvents.collect { event ->
                        val data = when (event) {
                            is SessionStatusEvent.StartingSoon -> json.encodeToString(
                                SessionStartingSoonDto(event.sessionName, event.sessionKey, event.startsInSeconds)
                            )
                        }
                        sink.sendEvent(data, event.eventName)
                    }
                }
            }
        } finally {
            logger.info { "SSE client disconnected" }
        }
    }

    suspend fun broadcast(event: SessionStatusEvent) {
        _systemEvents.emit(event)
    }
}
