package com.f1analytics.data.livetiming

import com.f1analytics.core.domain.model.TimingMessage
import com.f1analytics.core.domain.port.LiveTimingClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

class KtorLiveTimingWsClient(
    private val httpClient: HttpClient,
    private val retryDelay: Duration = 3.seconds
) : LiveTimingClient {

    private val _messages = MutableSharedFlow<TimingMessage>(
        extraBufferCapacity = 2000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val messages: Flow<TimingMessage> = _messages.asSharedFlow()

    override suspend fun connect(port: Int) = connectTo("ws://localhost:$port")

    internal suspend fun connectTo(url: String) {
        while (true) {
            try {
                httpClient.webSocket(url) {
                    logger.info { "Connected to bridge at $url" }
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            parseAndEmit(frame.readText())
                        }
                    }
                    logger.info { "Bridge WebSocket session ended" }
                }
            } catch (e: Exception) {
                logger.warn { "Bridge connection lost: ${e.message}. Retrying in $retryDelay." }
                delay(retryDelay)
            }
        }
    }

    private suspend fun parseAndEmit(raw: String) {
        try {
            val envelope = Json.parseToJsonElement(raw).jsonObject
            val topic = envelope["topic"]?.jsonPrimitive?.contentOrNull ?: run {
                logger.warn { "Message missing 'topic': ${raw.take(120)}" }
                return
            }
            val data = envelope["data"] ?: run {
                logger.warn { "Message missing 'data' for topic '$topic'" }
                return
            }
            val timestamp: Instant = envelope["timestamp"]?.jsonPrimitive?.contentOrNull
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: Clock.System.now()

            val message = TimingMessageParser.parse(topic, data, timestamp)
            if (message != null) {
                _messages.emit(message)
            }
        } catch (e: Exception) {
            logger.warn { "Unparseable WebSocket message discarded: ${e.message}. Raw: ${raw.take(200)}" }
        }
    }
}
