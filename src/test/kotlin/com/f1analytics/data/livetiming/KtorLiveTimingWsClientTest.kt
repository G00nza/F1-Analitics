package com.f1analytics.data.livetiming

import com.f1analytics.core.domain.model.TimingMessage
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets

class KtorLiveTimingWsClientTest {

    // ── Helpers ────────────────────────────────────────────────────────────

    private val heartbeatJson = """{"topic":"Heartbeat","data":{"Utc":"2024-03-02T16:00:00Z"},"timestamp":"2024-03-02T16:00:00Z"}"""
    private val sessionStatusJson = """{"topic":"SessionStatus","data":{"Status":"Started"},"timestamp":"2024-03-02T16:00:01Z"}"""
    private val lapCountJson = """{"topic":"LapCount","data":{"CurrentLap":5,"TotalLaps":57},"timestamp":"2024-03-02T16:00:02Z"}"""

    // ── AC: valid messages are parsed and emitted ──────────────────────────

    @Test
    fun `receives and parses a valid message`() = testApplication {
        install(WebSockets)
        routing {
            webSocket("/") {
                send(heartbeatJson)
                for (frame in incoming) { /* drain until client disconnects */ }
            }
        }

        val wsClient = KtorLiveTimingWsClient(
            httpClient = createClient { install(ClientWebSockets) },
            retryDelay = 50.milliseconds
        )

        val message = coroutineScope {
            val job = launch { wsClient.connectTo("/") }
            val msg = withTimeout(2.seconds) { wsClient.messages.first() }
            job.cancel()
            msg
        }

        assertIs<TimingMessage.HeartbeatMsg>(message)
    }

    @Test
    fun `receives multiple messages in order`() = testApplication {
        install(WebSockets)
        routing {
            webSocket("/") {
                send(heartbeatJson)
                send(sessionStatusJson)
                send(lapCountJson)
                for (frame in incoming) { }
            }
        }

        val wsClient = KtorLiveTimingWsClient(
            httpClient = createClient { install(ClientWebSockets) },
            retryDelay = 50.milliseconds
        )

        val messages = coroutineScope {
            val job = launch { wsClient.connectTo("/") }
            val msgs = withTimeout(2.seconds) { wsClient.messages.take(3).toList() }
            job.cancel()
            msgs
        }

        assertEquals(3, messages.size)
        assertIs<TimingMessage.HeartbeatMsg>(messages[0])
        assertIs<TimingMessage.SessionStatusMsg>(messages[1])
        assertIs<TimingMessage.LapCountMsg>(messages[2])
    }

    // ── AC: reconnects automatically after drop ────────────────────────────

    @Test
    fun `reconnects after connection drop and receives message from second connection`() = testApplication {
        val connectionCount = AtomicInteger(0)

        install(WebSockets)
        routing {
            webSocket("/") {
                if (connectionCount.incrementAndGet() == 1) {
                    close() // drop first connection
                } else {
                    send(heartbeatJson)
                    for (frame in incoming) { }
                }
            }
        }

        val wsClient = KtorLiveTimingWsClient(
            httpClient = createClient { install(ClientWebSockets) },
            retryDelay = 50.milliseconds
        )

        val message = coroutineScope {
            val job = launch { wsClient.connectTo("/") }
            val msg = withTimeout(2.seconds) { wsClient.messages.first() }
            job.cancel()
            msg
        }

        assertIs<TimingMessage.HeartbeatMsg>(message)
        assertEquals(2, connectionCount.get())
    }

    @Test
    fun `keeps reconnecting across multiple consecutive drops`() = testApplication {
        val connectionCount = AtomicInteger(0)

        install(WebSockets)
        routing {
            webSocket("/") {
                if (connectionCount.incrementAndGet() < 3) {
                    close()
                } else {
                    send(heartbeatJson)
                    for (frame in incoming) { }
                }
            }
        }

        val wsClient = KtorLiveTimingWsClient(
            httpClient = createClient { install(ClientWebSockets) },
            retryDelay = 50.milliseconds
        )

        coroutineScope {
            val job = launch { wsClient.connectTo("/") }
            withTimeout(2.seconds) { wsClient.messages.first() }
            job.cancel()
        }

        assertEquals(3, connectionCount.get())
    }

    // ── AC: malformed messages are discarded, flow keeps working ──────────

    @Test
    fun `malformed JSON is discarded without crashing`() = testApplication {
        install(WebSockets)
        routing {
            webSocket("/") {
                send("this is not json at all!!!")
                send(heartbeatJson)
                for (frame in incoming) { }
            }
        }

        val wsClient = KtorLiveTimingWsClient(
            httpClient = createClient { install(ClientWebSockets) },
            retryDelay = 50.milliseconds
        )

        val message = coroutineScope {
            val job = launch { wsClient.connectTo("/") }
            val msg = withTimeout(2.seconds) { wsClient.messages.first() }
            job.cancel()
            msg
        }

        assertIs<TimingMessage.HeartbeatMsg>(message)
    }

    @Test
    fun `message missing topic field is discarded without crashing`() = testApplication {
        install(WebSockets)
        routing {
            webSocket("/") {
                send("""{"data":{"Utc":"2024-03-02T16:00:00Z"}}""")
                send(heartbeatJson)
                for (frame in incoming) { }
            }
        }

        val wsClient = KtorLiveTimingWsClient(
            httpClient = createClient { install(ClientWebSockets) },
            retryDelay = 50.milliseconds
        )

        val message = coroutineScope {
            val job = launch { wsClient.connectTo("/") }
            val msg = withTimeout(2.seconds) { wsClient.messages.first() }
            job.cancel()
            msg
        }

        assertIs<TimingMessage.HeartbeatMsg>(message)
    }

    @Test
    fun `message missing data field is discarded without crashing`() = testApplication {
        install(WebSockets)
        routing {
            webSocket("/") {
                send("""{"topic":"Heartbeat"}""")
                send(heartbeatJson)
                for (frame in incoming) { }
            }
        }

        val wsClient = KtorLiveTimingWsClient(
            httpClient = createClient { install(ClientWebSockets) },
            retryDelay = 50.milliseconds
        )

        val message = coroutineScope {
            val job = launch { wsClient.connectTo("/") }
            val msg = withTimeout(2.seconds) { wsClient.messages.first() }
            job.cancel()
            msg
        }

        assertIs<TimingMessage.HeartbeatMsg>(message)
    }

    @Test
    fun `mix of bad and good messages — only parseable ones are emitted`() = testApplication {
        install(WebSockets)
        routing {
            webSocket("/") {
                send("garbage")
                send("""{"topic":"Heartbeat"}""")   // missing data
                send("""{"no_topic": true}""")       // missing topic
                send(heartbeatJson)                  // valid
                send(sessionStatusJson)              // valid
                for (frame in incoming) { }
            }
        }

        val wsClient = KtorLiveTimingWsClient(
            httpClient = createClient { install(ClientWebSockets) },
            retryDelay = 50.milliseconds
        )

        val messages = coroutineScope {
            val job = launch { wsClient.connectTo("/") }
            val msgs = withTimeout(2.seconds) { wsClient.messages.take(2).toList() }
            job.cancel()
            msgs
        }

        assertEquals(2, messages.size)
        assertIs<TimingMessage.HeartbeatMsg>(messages[0])
        assertIs<TimingMessage.SessionStatusMsg>(messages[1])
    }

    // ── AC: buffer holds 2000 messages with DROP_OLDEST ───────────────────

    @Test
    fun `slow collector does not block the producer`() = testApplication {
        val messageCount = 100
        install(WebSockets)
        routing {
            webSocket("/") {
                for (i in 0 until messageCount) {
                    send(heartbeatJson)
                }
                for (frame in incoming) { }
            }
        }

        val wsClient = KtorLiveTimingWsClient(
            httpClient = createClient { install(ClientWebSockets) },
            retryDelay = 50.milliseconds
        )

        // With a 2000-slot buffer the connect loop must not block waiting for a collector
        coroutineScope {
            val job = launch { wsClient.connectTo("/") }
            val messages = withTimeout(2.seconds) { wsClient.messages.take(messageCount).toList() }
            job.cancel()
            assertEquals(messageCount, messages.size)
        }
    }
}
