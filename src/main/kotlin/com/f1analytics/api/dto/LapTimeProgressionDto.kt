package com.f1analytics.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class LapTimeProgressionDto(
    val raceKey: Int,
    val meetingName: String,
    val year: Int,
    val sessions: List<String>,
    val fpDataWarning: String,
    val drivers: List<DriverProgressionRowDto>
)

@Serializable
data class DriverProgressionRowDto(
    val driverNumber: String,
    val driverCode: String,
    val team: String?,
    val lapTimes: Map<String, Int?>,
    val deltaFp1ToQualiMs: Int?
)
