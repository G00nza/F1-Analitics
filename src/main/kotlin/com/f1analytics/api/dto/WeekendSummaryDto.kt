package com.f1analytics.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class WeekendSummaryDto(
    val raceKey: Int,
    val meetingName: String,
    val year: Int,
    val sessions: List<String>,
    val drivers: List<DriverWeekendRowDto>
)

@Serializable
data class DriverWeekendRowDto(
    val driverNumber: String,
    val driverCode: String,
    val team: String?,
    val sessionData: Map<String, SessionSummaryEntryDto>
)

@Serializable
data class SessionSummaryEntryDto(
    val position: Int,
    val bestLapMs: Int?,
    val gapToLeaderMs: Int?,
    val isBestPosition: Boolean
)
