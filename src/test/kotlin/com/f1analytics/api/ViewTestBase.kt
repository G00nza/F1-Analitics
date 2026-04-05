package com.f1analytics.api

import com.f1analytics.api.views.LatestSessionView
import com.f1analytics.api.views.LiveEventView
import com.f1analytics.api.views.MeetingsView
import com.f1analytics.api.views.ReplayEventView
import com.f1analytics.api.views.SessionStateView
import com.f1analytics.core.domain.model.LiveSessionState
import com.f1analytics.core.service.LiveSessionStateManager
import com.f1analytics.core.service.SessionResolver
import com.f1analytics.data.db.DatabaseFactory
import com.f1analytics.data.db.repository.ExposedLapRepository
import com.f1analytics.data.db.repository.ExposedPositionRepository
import com.f1analytics.data.db.repository.ExposedRaceControlRepository
import com.f1analytics.data.db.repository.ExposedRaceRepository
import com.f1analytics.data.db.repository.ExposedReplayRepository
import com.f1analytics.data.db.repository.ExposedSessionDriverRepository
import com.f1analytics.data.db.repository.ExposedSessionRepository
import com.f1analytics.data.db.repository.ExposedStintRepository
import com.f1analytics.data.db.repository.ExposedWeatherRepository
import com.f1analytics.data.db.tables.RacesTable
import com.f1analytics.data.db.tables.SessionsTable
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

abstract class ViewTestBase {

    protected lateinit var db: Database
    private lateinit var dbFile: File

    @BeforeTest
    fun setupDb() {
        dbFile = File.createTempFile("f1apitest", ".db")
        db = DatabaseFactory.init("jdbc:sqlite:${dbFile.absolutePath}")
    }

    protected fun insertRace(
        key: Int = 1,
        name: String = "Bahrain Grand Prix",
        circuit: String = "Bahrain International Circuit",
        year: Int = 2026,
        round: Int = 1,
        dateStart: String = "2026-03-16"
    ) = transaction(db) {
        RacesTable.insert {
            it[RacesTable.key]       = key
            it[RacesTable.name]      = name
            it[RacesTable.circuit]   = circuit
            it[RacesTable.year]      = year
            it[RacesTable.round]     = round
            it[RacesTable.dateStart] = dateStart
        }
    }

    @AfterTest
    fun teardownDb() {
        dbFile.delete()
        File("${dbFile.absolutePath}-wal").delete()
        File("${dbFile.absolutePath}-shm").delete()
    }

    protected fun insertSession(
        key: Int = 9001,
        raceKey: Int = 1,
        name: String = "Race",
        type: String = "RACE",
        status: String? = null,
        recorded: Boolean = false,
        dateStart: Instant? = Instant.parse("2026-03-16T15:00:00Z")
    ) = transaction(db) {
        SessionsTable.insert {
            it[SessionsTable.key]       = key
            it[SessionsTable.raceKey]   = raceKey
            it[SessionsTable.name]      = name
            it[SessionsTable.type]      = type
            it[SessionsTable.year]      = 2026
            it[SessionsTable.status]    = status
            it[SessionsTable.dateStart] = dateStart
            it[SessionsTable.recorded]  = recorded
        }
    }

    protected fun makeStateManager() = LiveSessionStateManager(
        driverRepo      = ExposedSessionDriverRepository(db),
        lapRepo         = ExposedLapRepository(db),
        stintRepo       = ExposedStintRepository(db),
        raceControlRepo = ExposedRaceControlRepository(db),
        weatherRepo     = ExposedWeatherRepository(db),
        positionRepo    = ExposedPositionRepository(db)
    )

    @Suppress("UNCHECKED_CAST")
    protected fun LiveSessionStateManager.injectState(state: LiveSessionState) {
        val field = this.javaClass.getDeclaredField("_stateFlow")
        field.isAccessible = true
        (field.get(this) as MutableStateFlow<LiveSessionState?>).value = state
    }

    protected fun testApp(
        stateManager: LiveSessionStateManager = makeStateManager(),
        block: suspend ApplicationTestBuilder.(HttpClient) -> Unit
    ) = testApplication {
        install(ContentNegotiation) { json() }
        routing {
            liveSessionRoutes(
                liveEventView     = LiveEventView(SseManager(stateManager)),
                replayEventView   = ReplayEventView(ExposedReplayRepository(db)),
                latestSessionView = LatestSessionView(SessionResolver(ExposedSessionRepository(db))),
                sessionStateView  = SessionStateView(stateManager),
                meetingsView      = MeetingsView(ExposedRaceRepository(db), ExposedSessionRepository(db))
            )
        }
        val jsonClient = createClient { install(ClientContentNegotiation) { json() } }
        block(jsonClient)
    }
}
