package com.f1analytics.core.domain.model

import kotlinx.datetime.Instant
import kotlin.time.Duration

// ── Enums ──────────────────────────────────────────────────────────────────

enum class SessionType {
    FP1, FP2, FP3, QUALIFYING, SPRINT, SPRINT_QUALIFYING, RACE, UNKNOWN;

    companion object {
        fun from(value: String): SessionType = when (value.uppercase().trim()) {
            "FP1", "PRACTICE 1"                      -> FP1
            "FP2", "PRACTICE 2"                      -> FP2
            "FP3", "PRACTICE 3"                      -> FP3
            "QUALIFYING"                             -> QUALIFYING
            "SPRINT"                                 -> SPRINT
            "SPRINT QUALIFYING", "SPRINT_QUALIFYING" -> SPRINT_QUALIFYING
            "RACE"                                   -> RACE
            else                                     -> UNKNOWN
        }
    }
}

// ── Value objects used in messages ─────────────────────────────────────────

data class DriverEntry(
    val number: String,
    val code: String,
    val firstName: String?,
    val lastName: String?,
    val team: String?,
    val teamColor: String?,
)

/** Delta from a single TimingData update for one driver. All fields are nullable —
 *  F1 live timing only sends changed fields (partial updates). */
data class DriverTimingDelta(
    val position: Int? = null,
    val gapToLeader: String? = null,
    val interval: String? = null,
    val lastLapTimeMs: Int? = null,
    val lastLapPersonalBest: Boolean? = null,
    val lastLapOverallBest: Boolean? = null,
    val sector1Ms: Int? = null,
    val sector2Ms: Int? = null,
    val sector3Ms: Int? = null,
    val lapNumber: Int? = null,
    val inPit: Boolean? = null,
    val pitOut: Boolean? = null
)

/** Delta from a single TimingAppData update for one driver (current stint info). */
data class DriverTireStintDelta(
    val stintNumber: Int? = null,
    val compound: String? = null,
    val isNew: Boolean? = null,
    val lapStart: Int? = null,
    val totalLaps: Int? = null
)

data class WeatherData(
    val airTemp: Double?,
    val trackTemp: Double?,
    val humidity: Double?,
    val pressure: Double?,
    val windSpeed: Double?,
    val windDirection: Int?,
    val rainfall: Boolean?
)

/** One telemetry sample for one car (from CarData.z). */
data class CarTelemetryEntry(
    val timestamp: Instant,
    val driverNumber: String,
    val speed: Int?,   // km/h
    val rpm: Int?,
    val gear: Int?,
    val throttle: Int?,  // 0–100
    val brake: Int?,     // 0–100
    val drs: Int?        // 0=closed, 8=eligible, 10=open
)

/** GPS track position for one car (from Position.z). */
data class PositionEntry(
    val timestamp: Instant,
    val driverNumber: String,
    val x: Int?,
    val y: Int?,
    val z: Int?,
    val status: String?  // OnTrack | OffTrack | Pit
)

// ── Sealed class ───────────────────────────────────────────────────────────

sealed class TimingMessage {

    data class SessionInfoMsg(
        val name: String,
        val circuit: String,
        val type: SessionType,
        val officialName: String?
    ) : TimingMessage()

    data class SessionStatusMsg(val status: String) : TimingMessage()

    data class DriverListMsg(val drivers: Map<String, DriverEntry>) : TimingMessage()

    /** Map key = driver number. Only drivers with changed data are included. */
    data class TimingDataMsg(val deltas: Map<String, DriverTimingDelta>) : TimingMessage()

    /** Map key = driver number. Only drivers with changed stint data are included. */
    data class TimingAppDataMsg(val deltas: Map<String, DriverTireStintDelta>) : TimingMessage()

    data class TrackStatusMsg(val status: String, val message: String) : TimingMessage()

    data class RaceControlMsg(
        val message: String,
        val flag: String?,
        val scope: String?,
        val lap: Int?
    ) : TimingMessage()

    data class WeatherMsg(val weather: WeatherData) : TimingMessage()

    data class CarDataMsg(val entries: List<CarTelemetryEntry>) : TimingMessage()

    data class PositionMsg(val entries: List<PositionEntry>) : TimingMessage()

    data class ExtrapolatedClockMsg(
        val remaining: Duration?,
        val extrapolating: Boolean
    ) : TimingMessage()

    data class LapCountMsg(val current: Int, val total: Int?) : TimingMessage()

    data class HeartbeatMsg(val utcTime: Instant) : TimingMessage()
}
