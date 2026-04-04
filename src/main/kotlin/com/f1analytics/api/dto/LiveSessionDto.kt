package com.f1analytics.api.dto

import com.f1analytics.core.domain.model.*
import kotlinx.serialization.Serializable

@Serializable
data class LiveSessionStateDto(
    val sessionKey: Int,
    val sessionName: String?,
    val circuitName: String?,
    val officialName: String?,
    val drivers: Map<String, DriverEntryDto>,
    val driverData: Map<String, DriverLiveDataDto>,
    val raceControlMessages: List<RaceControlEntryDto>,
    val weather: WeatherDataDto?,
    val trackStatus: String?,
    val sessionStatus: String?,
    val timeRemainingMs: Long?,
    val lapCount: LapCountDto?
)

@Serializable
data class DriverEntryDto(
    val number: String,
    val code: String,
    val firstName: String?,
    val lastName: String?,
    val team: String?,
    val teamColor: String?
)

@Serializable
data class DriverLiveDataDto(
    val position: Int?,
    val gapToLeader: String?,
    val interval: String?,
    val lastLapTimeMs: Int?,
    val bestLapTimeMs: Int?,
    val lapNumber: Int?,
    val sector1Ms: Int?,
    val sector2Ms: Int?,
    val sector3Ms: Int?,
    val inPit: Boolean,
    val currentCompound: String?,
    val isNewTire: Boolean?,
    val stintLapStart: Int?,
    val stintNumber: Int?
)

@Serializable
data class RaceControlEntryDto(
    val message: String,
    val flag: String?,
    val scope: String?,
    val lap: Int?,
    val timestamp: String
)

@Serializable
data class WeatherDataDto(
    val airTemp: Double?,
    val trackTemp: Double?,
    val humidity: Double?,
    val pressure: Double?,
    val windSpeed: Double?,
    val windDirection: Int?,
    val rainfall: Boolean?
)

@Serializable
data class LapCountDto(val current: Int, val total: Int?)

@Serializable
data class SessionStartingSoonDto(
    val sessionName: String,
    val sessionKey: Int,
    val startsInSeconds: Double
)

// ── Mapping functions ──────────────────────────────────────────────────────────

fun LiveSessionState.toDto() = LiveSessionStateDto(
    sessionKey          = sessionKey,
    sessionName         = sessionName,
    circuitName         = circuitName,
    officialName        = officialName,
    drivers             = drivers.mapValues { it.value.toDto() },
    driverData          = driverData.mapValues { it.value.toDto() },
    raceControlMessages = raceControlMessages.map { it.toDto() },
    weather             = weather?.toDto(),
    trackStatus         = trackStatus,
    sessionStatus       = sessionStatus,
    timeRemainingMs     = timeRemaining?.inWholeMilliseconds,
    lapCount            = lapCount?.let { LapCountDto(it.current, it.total) }
)

fun DriverEntry.toDto() = DriverEntryDto(number, code, firstName, lastName, team, teamColor)

fun DriverLiveData.toDto() = DriverLiveDataDto(
    position        = position,
    gapToLeader     = gapToLeader,
    interval        = interval,
    lastLapTimeMs   = lastLapTimeMs,
    bestLapTimeMs   = bestLapTimeMs,
    lapNumber       = lapNumber,
    sector1Ms       = sector1Ms,
    sector2Ms       = sector2Ms,
    sector3Ms       = sector3Ms,
    inPit           = inPit,
    currentCompound = currentCompound,
    isNewTire       = isNewTire,
    stintLapStart   = stintLapStart,
    stintNumber     = stintNumber
)

fun RaceControlEntry.toDto() = RaceControlEntryDto(
    message   = message,
    flag      = flag,
    scope     = scope,
    lap       = lap,
    timestamp = timestamp.toString()
)

fun WeatherData.toDto() = WeatherDataDto(
    airTemp       = airTemp,
    trackTemp     = trackTemp,
    humidity      = humidity,
    pressure      = pressure,
    windSpeed     = windSpeed,
    windDirection = windDirection,
    rainfall      = rainfall
)
