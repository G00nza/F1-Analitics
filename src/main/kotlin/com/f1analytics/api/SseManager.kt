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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.Writer
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}
private val json = Json { encodeDefaults = true }

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

    fun sendComment(comment: String) {
        writer.write(": $comment\r\n\r\n")
        writer.flush()
    }
}

/** F-00.6: Fans out the session StateFlow to all connected SSE browsers. */
class SseManager(private val stateManager: LiveSessionStateManager) {

    private val _systemEvents = MutableSharedFlow<SessionStatusEvent>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    suspend fun handleLiveClient(sink: SseSink) {
        logger.info { "SSE client connected" }
        try {
            // Send the current full state immediately.
            stateManager.stateFlow.value?.let {
                sink.sendEvent(json.encodeToString(it.toDto()), "session_state")
            }

            coroutineScope {
                // Heartbeat — prevents proxy connection timeouts
                launch {
                    while (true) {
                        delay(30.seconds)
                        sink.sendComment("heartbeat")
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
