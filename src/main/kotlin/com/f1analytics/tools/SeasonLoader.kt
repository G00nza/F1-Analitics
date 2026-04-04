package com.f1analytics.tools

import com.f1analytics.data.db.DatabaseFactory
import com.f1analytics.data.db.tables.DriverStandingsTable
import com.f1analytics.data.db.tables.RaceResultsTable
import com.f1analytics.data.db.tables.RacesTable
import com.f1analytics.data.db.tables.SessionsTable
import com.f1analytics.data.jolpica.KtorJolpicaClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert

private val logger = KotlinLogging.logger {}

/**
 * Standalone script: loads an F1 season from Jolpica into the local SQLite DB.
 *
 * Usage:
 *   ./gradlew loadSeason                       # loads current year
 *   ./gradlew loadSeason --args="2024"         # loads a specific year
 */
fun main(args: Array<String>) = runBlocking {
    val year = args.firstOrNull()?.toIntOrNull()
        ?: Clock.System.now().toLocalDateTime(TimeZone.UTC).year

    logger.info { "Loading season $year from Jolpica…" }

    val db = DatabaseFactory.init()
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
    val client = KtorJolpicaClient(httpClient)

    // ── 1. Season schedule → RacesTable ───────────────────────────────────────
    val schedule = client.getSeasonSchedule(year)
    logger.info { "  ${schedule.size} races in schedule" }

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
    logger.info { "  Schedule saved" }

    // ── 2. Race results for every completed round ─────────────────────────────
    val completedRounds = schedule.filter { it.date < today() }
    logger.info { "  ${completedRounds.size} completed rounds — fetching results…" }

    completedRounds.forEach { entry ->
        val results = runCatching { client.getRaceResults(year, entry.round) }.getOrElse {
            logger.warn { "    Round ${entry.round} results unavailable: ${it.message}" }
            return@forEach
        }
        if (results.isEmpty()) {
            logger.info { "    Round ${entry.round}: no results yet" }
            return@forEach
        }

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
        logger.info { "    Round ${entry.round} (${entry.raceName}): ${results.size} results saved" }
    }

    // ── 3. Driver standings (current/final) ───────────────────────────────────
    val lastRound = completedRounds.maxByOrNull { it.round }?.round ?: 0
    if (lastRound > 0) {
        val standings = runCatching { client.getDriverStandings(year) }.getOrElse {
            logger.warn { "  Driver standings unavailable: ${it.message}" }
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
        logger.info { "  Driver standings after round $lastRound: ${standings.size} entries saved" }
    }

    httpClient.close()
    logger.info { "Season $year loaded successfully" }
}

/** Synthetic primary key for a race: year * 10_000 + round (e.g. 20_260_001) */
private fun syntheticRaceKey(year: Int, round: Int) = year * 10_000 + round

/** Synthetic primary key for the Race session of a round */
private fun syntheticSessionKey(year: Int, round: Int) = year * 10_000 + round + 5_000

private fun today(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.UTC).date
