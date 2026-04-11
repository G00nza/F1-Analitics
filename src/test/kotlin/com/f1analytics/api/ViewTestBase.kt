package com.f1analytics.api

import com.f1analytics.api.usecase.BuildLapTimeProgressionUseCase
import com.f1analytics.api.usecase.BuildWeekendSummaryUseCase
import com.f1analytics.api.views.LapTimeProgressionView
import com.f1analytics.api.views.LatestSessionView
import com.f1analytics.api.views.LiveEventView
import com.f1analytics.api.views.MeetingsView
import com.f1analytics.api.views.ReplayEventView
import com.f1analytics.api.views.SessionChartsView
import com.f1analytics.api.views.SessionStateView
import com.f1analytics.api.views.WeekendSummaryView
import com.f1analytics.com.f1analytics.api.views.WeekendView
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
import com.f1analytics.data.db.tables.LapsTable
import com.f1analytics.data.db.tables.RacesTable
import com.f1analytics.data.db.tables.SessionDriversTable
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
            it[RacesTable.key] = key
            it[RacesTable.name] = name
            it[RacesTable.circuit] = circuit
            it[RacesTable.year] = year
            it[RacesTable.round] = round
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
            it[SessionsTable.key] = key
            it[SessionsTable.raceKey] = raceKey
            it[SessionsTable.name] = name
            it[SessionsTable.type] = type
            it[SessionsTable.year] = 2026
            it[SessionsTable.status] = status
            it[SessionsTable.dateStart] = dateStart
            it[SessionsTable.recorded] = recorded
        }
    }

    protected fun insertLap(
        sessionKey: Int = 9001,
        driverNumber: String = "",
        lapNumber: Int = 1,
        lapTimeMs: Int? = 96458,
        sector1Ms: Int? = null,
        sector2Ms: Int? = 18163,
        sector3Ms: Int? = 38796,
        isPersonalBest: Boolean = false,
        isOverallBest: Boolean = false,
        pitOutLap: Boolean = false,
        pitInLap: Boolean = false,
        timestamp: Instant = Instant.parse("2026-03-16T15:00:00Z"),
    ) = transaction(db) {
        LapsTable.insert {
            it[LapsTable.sessionKey] = sessionKey
            it[LapsTable.driverNumber] = driverNumber
            it[LapsTable.lapNumber] = lapNumber
            it[LapsTable.lapTimeMs] = lapTimeMs
            it[LapsTable.sector1Ms] = sector1Ms
            it[LapsTable.sector2Ms] = sector2Ms
            it[LapsTable.sector3Ms] = sector3Ms
            it[LapsTable.isPersonalBest] = isPersonalBest
            it[LapsTable.isOverallBest] = isOverallBest
            it[LapsTable.pitOutLap] = pitOutLap
            it[LapsTable.pitInLap] = pitInLap
            it[LapsTable.timestamp] = timestamp
        }
    }

    protected fun insertSessionDriver(
        sessionKey: Int,
        number: String,
        code: String,
        team: String? = null,
        teamColor: String? = null
    ) = transaction(db) {
        SessionDriversTable.insert {
            it[SessionDriversTable.sessionKey] = sessionKey
            it[SessionDriversTable.number]     = number
            it[SessionDriversTable.code]       = code
            it[SessionDriversTable.team]       = team
            it[SessionDriversTable.teamColor]  = teamColor
        }
    }

    protected fun makeStateManager() = LiveSessionStateManager(
        driverRepo = ExposedSessionDriverRepository(db),
        lapRepo = ExposedLapRepository(db),
        stintRepo = ExposedStintRepository(db),
        raceControlRepo = ExposedRaceControlRepository(db),
        weatherRepo = ExposedWeatherRepository(db),
        positionRepo = ExposedPositionRepository(db)
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
                liveEventView = LiveEventView(SseManager(stateManager)),
                replayEventView = ReplayEventView(ExposedReplayRepository(db)),
                latestSessionView = LatestSessionView(SessionResolver(ExposedSessionRepository(db))),
                sessionStateView = SessionStateView(stateManager),
                meetingsView = MeetingsView(ExposedRaceRepository(db), ExposedSessionRepository(db)),
                sessionChartsView = SessionChartsView(
                    ExposedLapRepository(db),
                    ExposedSessionDriverRepository(db),
                    ExposedStintRepository(db),
                    ExposedPositionRepository(db)
                ),
                weekendView = WeekendView(ExposedRaceRepository(db), ExposedSessionRepository(db)),
                weekendSummaryView = WeekendSummaryView(
                    ExposedRaceRepository(db),
                    BuildWeekendSummaryUseCase(
                        ExposedSessionRepository(db),
                        ExposedLapRepository(db),
                        ExposedSessionDriverRepository(db),
                    )
                ),
                lapTimeProgressionView = LapTimeProgressionView(
                    ExposedRaceRepository(db),
                    BuildLapTimeProgressionUseCase(
                        ExposedSessionRepository(db),
                        ExposedLapRepository(db),
                        ExposedSessionDriverRepository(db),
                    )
                ),
            )
        }
        val jsonClient = createClient { install(ClientContentNegotiation) { json() } }
        block(jsonClient)
    }
}
