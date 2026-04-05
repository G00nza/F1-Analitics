package com.f1analytics

import com.f1analytics.api.SseManager
import com.f1analytics.api.liveSessionRoutes
import com.f1analytics.api.views.LatestSessionView
import com.f1analytics.api.views.LiveEventView
import com.f1analytics.api.views.ReplayEventView
import com.f1analytics.api.views.SessionStateView
import com.f1analytics.api.views.MeetingsView
import com.f1analytics.com.f1analytics.api.views.SessionLapsView
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
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.deflate
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.routing.IgnoreTrailingSlash
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.event.Level
import java.net.Inet4Address
import java.net.NetworkInterface

private val logger = KotlinLogging.logger {}

private const val BRIDGE_PORT = 9001
private const val DEFAULT_PORT = 8080

fun startServer(port: Int = DEFAULT_PORT, openBrowser: Boolean = true) { runBlocking {
    logger.info { "Starting F1 Analytics server" }

    // ── Database ────────────────────────────────────────────────────────────────
    val db = DatabaseFactory.init()

    // ── Repositories ────────────────────────────────────────────────────────────
    val sessionRepo      = ExposedSessionRepository(db)
    val raceRepo         = ExposedRaceRepository(db)
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

    // ── Print access URLs ───────────────────────────────────────────────────────
    val networkIp = detectLocalIp()
    println("")
    println("F1 Analytics server running")
    println("  Local:   http://localhost:$port")
    if (networkIp != null) println("  Network: http://$networkIp:$port")
    println("")

    if (openBrowser) {
        openBrowser("http://localhost:$port")
    }

    // ── HTTP server ─────────────────────────────────────────────────────────────
    embeddedServer(CIO, host = "0.0.0.0", port = port) {
        install(IgnoreTrailingSlash)
        install(ContentNegotiation) { json() }
        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.ContentType)
        }
        install(Compression) {
            gzip()
            deflate()
        }
        install(DefaultHeaders)
        install(CallLogging) { level = Level.INFO }

        routing {
            liveSessionRoutes(
                LiveEventView(sseManager),
                ReplayEventView(replayRepo),
                LatestSessionView(sessionResolver),
                SessionStateView(stateManager),
                MeetingsView(raceRepo, sessionRepo),
                SessionLapsView(lapRepo, driverRepo, stintRepo, positionRepo),
                isSessionActive = { stateManager.stateFlow.value != null }
            )
            // F-08.1: Serve frontend SPA; index.html as fallback for client-side routing
            staticResources("/", "static", index = "index.html")
        }
    }.start(wait = true)
} }

fun main(args: Array<String>) {
    F1Cli(args).main(args)
}

private fun detectLocalIp(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces()?.toList()
        ?.filter { !it.isLoopback && it.isUp }
        ?.flatMap { it.inetAddresses.toList() }
        ?.filterIsInstance<Inet4Address>()
        ?.firstOrNull()
        ?.hostAddress
}.getOrNull()

private fun openBrowser(url: String) {
    runCatching {
        val os = System.getProperty("os.name").lowercase()
        when {
            os.contains("mac")  -> Runtime.getRuntime().exec(arrayOf("open", url))
            os.contains("win")  -> Runtime.getRuntime().exec(arrayOf("cmd", "/c", "start", url))
            else                -> Runtime.getRuntime().exec(arrayOf("xdg-open", url))
        }
    }
}
