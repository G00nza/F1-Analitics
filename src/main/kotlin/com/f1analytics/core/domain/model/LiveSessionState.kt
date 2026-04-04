package com.f1analytics.core.domain.model

import kotlinx.datetime.Instant
import kotlin.time.Duration

data class LiveSessionState(
    val sessionKey: Int,
    val sessionName: String? = null,
    val circuitName: String? = null,
    val officialName: String? = null,
    val drivers: Map<String, DriverEntry> = emptyMap(),
    val driverData: Map<String, DriverLiveData> = emptyMap(),
    val raceControlMessages: List<RaceControlEntry> = emptyList(),
    val weather: WeatherData? = null,
    val trackStatus: String? = null,
    val sessionStatus: String? = null,
    val timeRemaining: Duration? = null,
    val lapCount: LapCountData? = null
)

data class DriverLiveData(
    val position: Int? = null,
    val gapToLeader: String? = null,
    val interval: String? = null,
    val lastLapTimeMs: Int? = null,
    val bestLapTimeMs: Int? = null,
    val lapNumber: Int? = null,
    val sector1Ms: Int? = null,
    val sector2Ms: Int? = null,
    val sector3Ms: Int? = null,
    val inPit: Boolean = false,
    val currentCompound: String? = null,
    val isNewTire: Boolean? = null,
    val stintLapStart: Int? = null,
    val stintNumber: Int? = null
)

data class RaceControlEntry(
    val message: String,
    val flag: String?,
    val scope: String?,
    val lap: Int?,
    val timestamp: Instant
)

data class LapCountData(
    val current: Int,
    val total: Int?
)

// ── Merge extensions ──────────────────────────────────────────────────────────

fun LiveSessionState.withDrivers(incoming: Map<String, DriverEntry>): LiveSessionState =
    copy(drivers = drivers + incoming)

fun LiveSessionState.withTimingDeltas(deltas: Map<String, DriverTimingDelta>): LiveSessionState {
    val updated = driverData.toMutableMap()
    for ((num, delta) in deltas) {
        val curr = updated[num] ?: DriverLiveData()
        updated[num] = curr.applyTimingDelta(delta)
    }
    return copy(driverData = updated)
}

fun LiveSessionState.withStintDeltas(deltas: Map<String, DriverTireStintDelta>): LiveSessionState {
    val updated = driverData.toMutableMap()
    for ((num, delta) in deltas) {
        val curr = updated[num] ?: DriverLiveData()
        var d = curr
        delta.compound?.let { d = d.copy(currentCompound = it) }
        delta.isNew?.let { d = d.copy(isNewTire = it) }
        delta.lapStart?.let { d = d.copy(stintLapStart = it) }
        delta.stintNumber?.let { d = d.copy(stintNumber = it) }
        updated[num] = d
    }
    return copy(driverData = updated)
}

fun LiveSessionState.withRaceControl(
    msg: TimingMessage.RaceControlMsg,
    timestamp: Instant
): LiveSessionState {
    val entry = RaceControlEntry(msg.message, msg.flag, msg.scope, msg.lap, timestamp)
    return copy(raceControlMessages = raceControlMessages + entry)
}

private fun DriverLiveData.applyTimingDelta(delta: DriverTimingDelta): DriverLiveData {
    var d = this
    delta.position?.let { d = d.copy(position = it) }
    delta.gapToLeader?.let { d = d.copy(gapToLeader = it) }
    delta.interval?.let { d = d.copy(interval = it) }
    delta.lapNumber?.let { d = d.copy(lapNumber = it) }
    delta.sector1Ms?.let { d = d.copy(sector1Ms = it) }
    delta.sector2Ms?.let { d = d.copy(sector2Ms = it) }
    delta.sector3Ms?.let { d = d.copy(sector3Ms = it) }
    delta.inPit?.let { d = d.copy(inPit = it) }
    if (delta.lastLapTimeMs != null) {
        d = d.copy(lastLapTimeMs = delta.lastLapTimeMs)
        val best = d.bestLapTimeMs
        if (delta.lastLapPersonalBest == true || best == null || delta.lastLapTimeMs < best) {
            d = d.copy(bestLapTimeMs = delta.lastLapTimeMs)
        }
    }
    return d
}
