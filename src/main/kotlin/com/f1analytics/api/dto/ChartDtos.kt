package com.f1analytics.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class LapDataDto(
    val driverNumber: String,
    val driverCode: String,
    val teamColor: String,
    val lapNumber: Int,
    val lapTimeMs: Int?,
    val pitOutLap: Boolean,
    val pitInLap: Boolean,
    val isPersonalBest: Boolean,
    val compound: String?,
    val stintNumber: Int,
    val gapToLeaderMs: Int?
)

@Serializable
data class StintDataDto(
    val driverNumber: String,
    val driverCode: String,
    val stintNumber: Int,
    val compound: String?,
    val lapStart: Int?,
    val lapEnd: Int?,
    val isNew: Boolean?
)

@Serializable
data class RacePositionDto(
    val driverNumber: String,
    val driverCode: String,
    val teamColor: String,
    val lapNumber: Int,
    val position: Int
)

@Serializable
data class WeekendInfoDto(
    val meetingName: String,
    val circuitName: String,
    val year: Int,
    val sessions: List<WeekendSessionDto>
)

@Serializable
data class WeekendSessionDto(
    val key: Int,
    val name: String,
    val type: String,
    val status: String
)
