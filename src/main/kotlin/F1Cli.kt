package com.f1analytics

import com.f1analytics.api.usecase.BuildPostRaceStrategyUseCase
import com.f1analytics.data.db.DatabaseFactory
import com.f1analytics.data.db.repository.ExposedLapRepository
import com.f1analytics.data.db.repository.ExposedPositionRepository
import com.f1analytics.data.db.repository.ExposedRaceControlRepository
import com.f1analytics.data.db.repository.ExposedRaceRepository
import com.f1analytics.data.db.repository.ExposedSessionDriverRepository
import com.f1analytics.data.db.repository.ExposedSessionRepository
import com.f1analytics.data.db.repository.ExposedStintRepository
import com.f1analytics.data.db.repository.ExposedStrategyAlertRepository
import com.f1analytics.data.db.tables.DriverStandingsTable
import com.f1analytics.data.db.tables.RaceResultsTable
import com.f1analytics.data.db.tables.RacesTable
import com.f1analytics.data.db.tables.SessionsTable
import com.f1analytics.data.jolpica.KtorJolpicaClient
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert

private val logger = KotlinLogging.logger {}

class F1Cli(private val rawArgs: Array<String>) : CliktCommand(name = "f1", invokeWithoutSubcommand = true) {
    init {
        subcommands(ServeCommand(), DataCommand(), StrategyCommand())
    }

    override fun run() {
        if (currentContext.invokedSubcommand == null) {
            ServeCommand().main(rawArgs)
        }
    }
}

class ServeCommand : CliktCommand(name = "serve", help = "Start the F1 Analytics web server") {
    private val port by option("--port", "-p", help = "Port to listen on").int().default(8080)
    private val noOpen by option("--no-open", help = "Do not open browser automatically").flag()

    override fun run() {
        startServer(port = port, openBrowser = !noOpen)
    }
}

class DataCommand : CliktCommand(name = "data", help = "Manage local F1 data") {
    override fun run() = Unit
    init {
        subcommands(DataInitCommand(), DataSyncCommand(), DataBackfillCommand())
    }
}

class DataInitCommand : CliktCommand(name = "init", help = "Load calendar + standings from Jolpica") {
    private val year by option("--year", "-y", help = "Season year (default: current)").int()
        .default(Clock.System.now().toLocalDateTime(TimeZone.UTC).year)

    override fun run() = runBlocking {
        logger.info { "Loading season $year from Jolpica…" }
        echo("Loading season $year…")

        val db = DatabaseFactory.init()
        val httpClient = HttpClient(CIO) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val client = KtorJolpicaClient(httpClient)

        // 1. Season schedule
        val schedule = client.getSeasonSchedule(year)
        echo("  ${schedule.size} races in schedule")

        transaction(db) {
            schedule.forEach { entry ->
                val raceKey = syntheticRaceKey(year, entry.round)
                RacesTable.upsert(RacesTable.key) {
                    it[key]          = raceKey
                    it[name]         = entry.raceName
                    it[officialName] = entry.raceName
                    it[circuit]      = entry.circuitName
                    it[country]      = entry.country
                    it[RacesTable.year] = year
                    it[round]        = entry.round
                    it[dateStart]    = entry.date.toString()
                    it[dateEnd]      = null
                }

                val sessionKey = syntheticSessionKey(year, entry.round)
                SessionsTable.upsert(SessionsTable.key) {
                    it[SessionsTable.key]       = sessionKey
                    it[SessionsTable.raceKey]   = raceKey
                    it[SessionsTable.name]      = "Race"
                    it[SessionsTable.type]      = "RACE"
                    it[SessionsTable.year]      = year
                    it[SessionsTable.status]    = if (entry.date < today()) "Finished" else null
                    it[SessionsTable.dateStart] = null
                    it[SessionsTable.dateEnd]   = null
                    it[SessionsTable.recorded]  = false
                }
            }
        }

        // 2. Race results for completed rounds
        val completedRounds = schedule.filter { it.date < today() }
        echo("  ${completedRounds.size} completed rounds — fetching results…")

        completedRounds.forEach { entry ->
            val results = runCatching { client.getRaceResults(year, entry.round) }.getOrElse {
                echo("  Round ${entry.round} results unavailable: ${it.message}", err = true)
                return@forEach
            }
            if (results.isEmpty()) return@forEach

            val sessionKey = syntheticSessionKey(year, entry.round)
            transaction(db) {
                RaceResultsTable.deleteWhere { RaceResultsTable.sessionKey eq sessionKey }
                results.forEach { r ->
                    RaceResultsTable.insert {
                        it[RaceResultsTable.sessionKey]      = sessionKey
                        it[RaceResultsTable.driverCode]      = r.driverCode
                        it[RaceResultsTable.constructorName] = r.constructorName
                        it[RaceResultsTable.gridPosition]    = r.gridPosition
                        it[RaceResultsTable.finishPosition]  = r.finishPosition
                        it[RaceResultsTable.points]          = r.points
                        it[RaceResultsTable.status]          = r.status
                        it[RaceResultsTable.fastestLap]      = r.fastestLap
                        it[RaceResultsTable.lapsCompleted]   = r.lapsCompleted
                    }
                }
            }
            echo("    Round ${entry.round} (${entry.raceName}): ${results.size} results saved")
        }

        // 3. Driver standings
        val lastRound = completedRounds.maxByOrNull { it.round }?.round ?: 0
        if (lastRound > 0) {
            val standings = runCatching { client.getDriverStandings(year) }.getOrElse {
                echo("  Driver standings unavailable: ${it.message}", err = true)
                emptyList()
            }
            transaction(db) {
                DriverStandingsTable.deleteWhere {
                    (DriverStandingsTable.year eq year) and (DriverStandingsTable.afterRound eq lastRound)
                }
                standings.forEach { s ->
                    DriverStandingsTable.insert {
                        it[DriverStandingsTable.year]       = year
                        it[DriverStandingsTable.afterRound] = lastRound
                        it[DriverStandingsTable.driverCode] = s.driverCode
                        it[DriverStandingsTable.position]   = s.position
                        it[DriverStandingsTable.points]     = s.points
                        it[DriverStandingsTable.wins]       = s.wins
                    }
                }
            }
            echo("  Driver standings after round $lastRound: ${standings.size} entries saved")
        }

        httpClient.close()
        echo("Season $year loaded successfully")
    }
}

class DataSyncCommand : CliktCommand(name = "sync", help = "Backfill results for a specific round") {
    private val round by option("--round", "-r", help = "Round number to sync").int()
    private val year by option("--year", "-y", help = "Season year (default: current)").int()
        .default(Clock.System.now().toLocalDateTime(TimeZone.UTC).year)

    override fun run() = runBlocking {
        val r = round ?: run { echo("--round is required", err = true); return@runBlocking }

        echo("Syncing round $r of $year…")

        val db = DatabaseFactory.init()
        val httpClient = HttpClient(CIO) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val client = KtorJolpicaClient(httpClient)

        val results = runCatching { client.getRaceResults(year, r) }.getOrElse {
            echo("Failed to fetch results: ${it.message}", err = true)
            httpClient.close()
            return@runBlocking
        }

        val sessionKey = syntheticSessionKey(year, r)
        transaction(db) {
            RaceResultsTable.deleteWhere { RaceResultsTable.sessionKey eq sessionKey }
            results.forEach { res ->
                RaceResultsTable.insert {
                    it[RaceResultsTable.sessionKey]      = sessionKey
                    it[RaceResultsTable.driverCode]      = res.driverCode
                    it[RaceResultsTable.constructorName] = res.constructorName
                    it[RaceResultsTable.gridPosition]    = res.gridPosition
                    it[RaceResultsTable.finishPosition]  = res.finishPosition
                    it[RaceResultsTable.points]          = res.points
                    it[RaceResultsTable.status]          = res.status
                    it[RaceResultsTable.fastestLap]      = res.fastestLap
                    it[RaceResultsTable.lapsCompleted]   = res.lapsCompleted
                }
            }
        }

        httpClient.close()
        echo("Round $r synced: ${results.size} results saved")
    }
}

class DataBackfillCommand : CliktCommand(
    name = "backfill",
    help = "Backfill lap, stint, telemetry, weather and race-control data from FastF1"
) {
    private val year by option("--year", "-y", help = "Season year (default: current)").int()
        .default(Clock.System.now().toLocalDateTime(TimeZone.UTC).year)
    private val round by option("--round", "-r", help = "Round number (default: all completed)").int()
    private val noTelemetry by option("--no-telemetry", help = "Skip high-frequency car telemetry").flag()

    override fun run() {
        val args = buildList {
            add("python3")
            add("bridge/backfill.py")
            add(year.toString())
            if (round != null) add(round.toString()) else add("all")
            if (noTelemetry) add("--no-telemetry")
        }

        echo("Running FastF1 backfill for $year${round?.let { " round $it" } ?: " (all completed rounds)"}…")

        val process = ProcessBuilder(args)
            .inheritIO()
            .start()

        val exit = process.waitFor()
        if (exit != 0) {
            echo("Backfill exited with code $exit", err = true)
        }
    }
}

class StrategyCommand : CliktCommand(name = "strategy", help = "Strategy analysis commands") {
    override fun run() = Unit
    init {
        subcommands(StrategyReviewCommand())
    }
}

class StrategyReviewCommand : CliktCommand(name = "review", help = "Print post-race strategy review for the latest race") {
    override fun run() = runBlocking {
        val db = DatabaseFactory.init()
        val sessionRepo = ExposedSessionRepository(db)

        val session = sessionRepo.findLatestRace()
        if (session == null) {
            echo("No race session found in the database.", err = true)
            return@runBlocking
        }

        val useCase = BuildPostRaceStrategyUseCase(
            sessionRepository       = sessionRepo,
            raceRepository          = ExposedRaceRepository(db),
            stintRepository         = ExposedStintRepository(db),
            sessionDriverRepository = ExposedSessionDriverRepository(db),
            positionRepository      = ExposedPositionRepository(db),
            raceControlRepository   = ExposedRaceControlRepository(db),
            strategyAlertRepository = ExposedStrategyAlertRepository(db),
        )

        val review = useCase.execute(session.key)

        echo("=== Post-Race Strategy Review ===")
        echo("Race: ${review.raceName ?: "Unknown"}")
        echo("Session: ${review.sessionName ?: "Unknown"} (key=${review.sessionKey})")
        echo("")

        echo("--- Driver Strategies ---")
        review.drivers.sortedBy { it.finalPosition ?: Int.MAX_VALUE }.forEach { d ->
            val pos = d.finalPosition?.let { "P$it" } ?: "DNF"
            val code = d.driverCode ?: d.driverNumber
            val stintSummary = d.stints.joinToString(" → ") { s ->
                val compound = s.compound ?: "?"
                val laps = s.laps?.let { "${it}L" } ?: "?"
                "$compound($laps)"
            }
            echo("  $pos  ${code.padEnd(3)}  ${d.stops}-stop  $stintSummary")
        }
        echo("")

        echo("--- Strategy Comparison ---")
        listOf(
            "1-stop"  to review.strategyComparison.oneStop,
            "2-stop"  to review.strategyComparison.twoStop,
            "3+-stop" to review.strategyComparison.threeOrMore,
        ).forEach { (label, group) ->
            if (group != null) {
                val avg = group.avgFinishPosition?.let { String.format("%.1f", it) } ?: "N/A"
                echo("  $label: ${group.driverCount} drivers, avg finish P$avg  [${group.drivers.joinToString(", ")}]")
            }
        }
        echo("")

        if (review.undercutResults.isNotEmpty()) {
            echo("--- Undercut Results ---")
            review.undercutResults.forEach { u ->
                val instigator = u.instigatorCode ?: "?"
                val rival = u.rivalCode ?: "?"
                val lap = u.lap?.let { "lap $it" } ?: "?"
                val outcome = if ((u.instigatorFinalPosition ?: Int.MAX_VALUE) < (u.rivalFinalPosition ?: Int.MAX_VALUE))
                    "SUCCESS" else "FAILED"
                echo("  $instigator vs $rival @ $lap → $outcome (P${u.instigatorFinalPosition} vs P${u.rivalFinalPosition})")
            }
            echo("")
        }

        if (review.scBeneficiaries.isNotEmpty()) {
            echo("--- Safety Car Beneficiaries ---")
            review.scBeneficiaries.forEach { b ->
                val code = b.driverCode ?: "?"
                val gained = b.positionsGained?.let { "+$it" } ?: "?"
                echo("  ${code.padEnd(3)}  SC lap ${b.scLap}  P${b.positionAtSc} → P${b.finalPosition}  ($gained positions)")
            }
            echo("")
        }
    }
}

private fun syntheticRaceKey(year: Int, round: Int) = year * 10_000 + round
private fun syntheticSessionKey(year: Int, round: Int) = year * 10_000 + round + 5_000
private fun today(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
