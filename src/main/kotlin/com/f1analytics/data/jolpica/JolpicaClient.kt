package com.f1analytics.data.jolpica

import com.f1analytics.core.domain.model.*
import com.f1analytics.core.domain.port.HistoricalDataClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.hours

private val logger = KotlinLogging.logger {}

private const val BASE_URL = "https://api.jolpi.ca/ergast/f1"
private val CURRENT_YEAR_TTL = 1.hours

class KtorJolpicaClient(private val httpClient: HttpClient) : HistoricalDataClient {

    // cache key → Pair(fetchedAt epoch ms, data)
    private val cache = mutableMapOf<String, Pair<Long, Any>>()

    override suspend fun getRaceResults(year: Int, round: Int): List<RaceResultData> {
        val key = "results/$year/$round"
        return cached(key, year) {
            val response: JolpicaRaceResultsResponse =
                httpClient.get("$BASE_URL/$year/$round/results.json").body()
            val race = response.mrData.raceTable.races.firstOrNull()
                ?: return@cached emptyList()
            race.results.map { it.toRaceResultData(round, race.raceName) }
        }
    }

    override suspend fun getDriverStandings(year: Int): List<DriverStandingData> {
        val key = "driverStandings/$year"
        return cached(key, year) {
            val response: JolpicaDriverStandingsResponse =
                httpClient.get("$BASE_URL/$year/driverStandings.json").body()
            response.mrData.standingsTable.standingsLists
                .firstOrNull()?.driverStandings?.map { it.toDriverStandingData() }
                ?: emptyList()
        }
    }

    override suspend fun getConstructorStandings(year: Int): List<ConstructorStandingData> {
        val key = "constructorStandings/$year"
        return cached(key, year) {
            val response: JolpicaConstructorStandingsResponse =
                httpClient.get("$BASE_URL/$year/constructorStandings.json").body()
            response.mrData.standingsTable.standingsLists
                .firstOrNull()?.constructorStandings?.map { it.toConstructorStandingData() }
                ?: emptyList()
        }
    }

    override suspend fun getSeasonSchedule(year: Int): List<RaceScheduleEntry> {
        val key = "schedule/$year"
        return cached(key, year) {
            val response: JolpicaScheduleResponse =
                httpClient.get("$BASE_URL/$year.json").body()
            response.mrData.raceTable.races.map { it.toRaceScheduleEntry() }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cached(key: String, year: Int, fetch: suspend () -> T): T {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val currentYear = Clock.System.now().toLocalDateTime(TimeZone.UTC).year
        val cached = cache[key]

        if (cached != null) {
            val (fetchedAt, data) = cached
            val isPastSeason = year < currentYear
            val age = nowMs - fetchedAt
            if (isPastSeason || age < CURRENT_YEAR_TTL.inWholeMilliseconds) {
                logger.debug { "Cache hit for $key" }
                return data as T
            }
        }

        logger.info { "Fetching $key from Jolpica" }
        val data = fetch()
        cache[key] = nowMs to data
        return data
    }
}

// ── Serialization DTOs ────────────────────────────────────────────────────────

@Serializable
private data class JolpicaRaceResultsResponse(
    @SerialName("MRData") val mrData: RaceResultsMRData
)

@Serializable
private data class RaceResultsMRData(
    @SerialName("RaceTable") val raceTable: RaceResultsRaceTable
)

@Serializable
private data class RaceResultsRaceTable(
    @SerialName("Races") val races: List<RaceWithResults> = emptyList()
)

@Serializable
private data class RaceWithResults(
    @SerialName("raceName") val raceName: String,
    @SerialName("Results") val results: List<ResultEntry> = emptyList()
)

@Serializable
private data class ResultEntry(
    @SerialName("position") val position: String,
    @SerialName("points") val points: String,
    @SerialName("Driver") val driver: DriverRef,
    @SerialName("Constructor") val constructor: ConstructorRef,
    @SerialName("grid") val grid: String,
    @SerialName("laps") val laps: String,
    @SerialName("status") val status: String,
    @SerialName("FastestLap") val fastestLap: FastestLapRef? = null
) {
    fun toRaceResultData(round: Int, raceName: String) = RaceResultData(
        round = round,
        raceName = raceName,
        driverCode = driver.code ?: driver.driverId,
        constructorName = constructor.name,
        gridPosition = grid.toIntOrNull(),
        finishPosition = position.toIntOrNull(),
        points = points.toDoubleOrNull() ?: 0.0,
        status = status,
        fastestLap = fastestLap?.rank == "1",
        lapsCompleted = laps.toIntOrNull()
    )
}

@Serializable
private data class DriverRef(
    @SerialName("code") val code: String? = null,
    @SerialName("driverId") val driverId: String
)

@Serializable
private data class ConstructorRef(
    @SerialName("name") val name: String
)

@Serializable
private data class FastestLapRef(
    @SerialName("rank") val rank: String
)

// Driver standings

@Serializable
private data class JolpicaDriverStandingsResponse(
    @SerialName("MRData") val mrData: DriverStandingsMRData
)

@Serializable
private data class DriverStandingsMRData(
    @SerialName("StandingsTable") val standingsTable: DriverStandingsTable
)

@Serializable
private data class DriverStandingsTable(
    @SerialName("StandingsLists") val standingsLists: List<DriverStandingsList> = emptyList()
)

@Serializable
private data class DriverStandingsList(
    @SerialName("DriverStandings") val driverStandings: List<DriverStandingEntry> = emptyList()
)

@Serializable
private data class DriverStandingEntry(
    @SerialName("position") val position: String,
    @SerialName("points") val points: String,
    @SerialName("wins") val wins: String,
    @SerialName("Driver") val driver: DriverRef
) {
    fun toDriverStandingData() = DriverStandingData(
        position = position.toInt(),
        driverCode = driver.code ?: driver.driverId,
        points = points.toDouble(),
        wins = wins.toInt()
    )
}

// Constructor standings

@Serializable
private data class JolpicaConstructorStandingsResponse(
    @SerialName("MRData") val mrData: ConstructorStandingsMRData
)

@Serializable
private data class ConstructorStandingsMRData(
    @SerialName("StandingsTable") val standingsTable: ConstructorStandingsTable
)

@Serializable
private data class ConstructorStandingsTable(
    @SerialName("StandingsLists") val standingsLists: List<ConstructorStandingsList> = emptyList()
)

@Serializable
private data class ConstructorStandingsList(
    @SerialName("ConstructorStandings") val constructorStandings: List<ConstructorStandingEntry> = emptyList()
)

@Serializable
private data class ConstructorStandingEntry(
    @SerialName("position") val position: String,
    @SerialName("points") val points: String,
    @SerialName("wins") val wins: String,
    @SerialName("Constructor") val constructor: ConstructorRef
) {
    fun toConstructorStandingData() = ConstructorStandingData(
        position = position.toInt(),
        constructorName = constructor.name,
        points = points.toDouble(),
        wins = wins.toInt()
    )
}

// Season schedule

@Serializable
private data class JolpicaScheduleResponse(
    @SerialName("MRData") val mrData: ScheduleMRData
)

@Serializable
private data class ScheduleMRData(
    @SerialName("RaceTable") val raceTable: ScheduleRaceTable
)

@Serializable
private data class ScheduleRaceTable(
    @SerialName("Races") val races: List<RaceScheduleDto> = emptyList()
)

@Serializable
private data class RaceScheduleDto(
    @SerialName("round") val round: String,
    @SerialName("raceName") val raceName: String,
    @SerialName("Circuit") val circuit: CircuitRef,
    @SerialName("date") val date: String
) {
    fun toRaceScheduleEntry() = RaceScheduleEntry(
        round = round.toInt(),
        raceName = raceName,
        circuitName = circuit.circuitName,
        country = circuit.location.country,
        date = LocalDate.parse(date)
    )
}

@Serializable
private data class CircuitRef(
    @SerialName("circuitName") val circuitName: String,
    @SerialName("Location") val location: LocationRef
)

@Serializable
private data class LocationRef(
    @SerialName("country") val country: String
)
