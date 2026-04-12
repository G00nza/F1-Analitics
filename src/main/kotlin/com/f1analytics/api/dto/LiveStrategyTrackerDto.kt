package com.f1analytics.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class LiveStrategyTrackerDto(
    val sessionKey: Int,
    val currentLap: Int?,
    val totalLaps: Int,
    val drivers: List<DriverTrackerRowDto>
)

@Serializable
data class DriverTrackerRowDto(
    val driverNumber: String,
    val driverCode: String,
    val team: String?,
    val position: Int?,
    val compound: String?,
    val stintLaps: Int?,
    val inPit: Boolean,
    val fpWindow: PitWindowDto?,
    val realWindow: PitWindowDto?,
    val isOverdue: Boolean,
    val windowsDiverge: Boolean
)
