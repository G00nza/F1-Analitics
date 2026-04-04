package com.f1analytics

import com.f1analytics.api.SseManager
import com.f1analytics.api.liveSessionRoutes
import com.f1analytics.api.views.LatestSessionView
import com.f1analytics.api.views.LiveEventView
import com.f1analytics.api.views.ReplayEventView
import com.f1analytics.core.config.AppConfig
import com.f1analytics.core.service.LiveSessionService
import com.f1analytics.core.service.LiveSessionStateManager
import com.f1analytics.core.service.SessionResolver
import com.f1analytics.core.service.SessionWatcher
import com.f1analytics.data.bridge.BridgeProcess
import com.f1analytics.data.db.DatabaseFactory
import com.f1analytics.data.db.repository.*
import com.f1analytics.data.livetiming.KtorLiveTimingWsClient
import com.f1analytics.data.persistence.LiveTimingPersistenceService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.IgnoreTrailingSlash
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val logger = KotlinLogging.logger {}

private const val BRIDGE_PORT = 9001
private const val SERVER_PORT = 8080

fun main(args: Array<String>) { runBlocking {
    logger.info { "Starting F1 Analytics server" }

    // ── Database ────────────────────────────────────────────────────────────────
    val db = DatabaseFactory.init()

    // ── Repositories ────────────────────────────────────────────────────────────
    val sessionRepo      = ExposedSessionRepository(db)
    val driverRepo       = ExposedSessionDriverRepository(db)
    val lapRepo          = ExposedLapRepository(db)
    val stintRepo        = ExposedStintRepository(db)
    val pitRepo          = ExposedPitStopRepository(db)
    val raceControlRepo  = ExposedRaceControlRepository(db)
    val weatherRepo      = ExposedWeatherRepository(db)
    val positionRepo     = ExposedPositionRepository(db)
    val telemetryRepo    = ExposedTelemetryRepository(db)
    val replayRepo       = ExposedReplayRepository(db)

    // ── Services ────────────────────────────────────────────────────────────────
    val config           = AppConfig()
    val appScope         = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val stateManager     = LiveSessionStateManager(
        driverRepo      = driverRepo,
        lapRepo         = lapRepo,
        stintRepo       = stintRepo,
        raceControlRepo = raceControlRepo,
        weatherRepo     = weatherRepo,
        positionRepo    = positionRepo
    )

    val sseManager       = SseManager(stateManager)
    val sessionResolver  = SessionResolver(sessionRepo)

    // Load the most relevant session into memory at startup
    sessionResolver.resolve()?.also { session ->
        logger.info { "Loading session '${session.name}' (key=${session.key}) from DB" }
        stateManager.loadFromDb(session)
    } ?: logger.info { "No session found — starting in idle mode" }

    // ── Bridge process ──────────────────────────────────────────────────────────
    val bridge = BridgeProcess(BRIDGE_PORT)
    bridge.start()
    Runtime.getRuntime().addShutdownHook(Thread { bridge.stop() })

    // ── Live timing client ──────────────────────────────────────────────────────
    val httpClient       = HttpClient(ClientCIO) { install(WebSockets) }
    val wsClient         = KtorLiveTimingWsClient(httpClient)
    val persistenceService = LiveTimingPersistenceService(
        sessionRepo     = sessionRepo,
        driverRepo      = driverRepo,
        lapRepo         = lapRepo,
        stintRepo       = stintRepo,
        pitRepo         = pitRepo,
        raceControlRepo = raceControlRepo,
        weatherRepo     = weatherRepo,
        positionRepo    = positionRepo,
        telemetryRepo   = telemetryRepo,
        config          = config
    )

    // F-00.1: Start the event processing loop
    val liveSessionService = LiveSessionService(wsClient, persistenceService, stateManager, appScope)
    liveSessionService.start()

    // Start WsClient connecting to the bridge
    appScope.launch {
        wsClient.connect(BRIDGE_PORT)
    }

    // F-00.4: Session watcher
    val sessionWatcher = SessionWatcher(sessionRepo, stateManager, sseManager, appScope)
    sessionWatcher.start()

    // ── HTTP server ─────────────────────────────────────────────────────────────
    embeddedServer(CIO, port = SERVER_PORT) {
        install(IgnoreTrailingSlash)
        install(ContentNegotiation) { json() }

        routing {
            liveSessionRoutes(
                LiveEventView(sseManager),
                ReplayEventView(replayRepo),
                LatestSessionView(sessionResolver)
            )
            // F-08.1: Serve frontend SPA; index.html as fallback for client-side routing
            staticResources("/", "static", index = "index.html")
        }
    }.start(wait = true)
} }
