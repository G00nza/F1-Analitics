package com.f1analytics.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class PreRaceStrategyDto(
    val raceKey: Int,
    val totalLaps: Int,
    val hasData: Boolean,
    val drivers: List<DriverStrategyDto>
)

@Serializable
data class DriverStrategyDto(
    val driverNumber: String,
    val driverCode: String,
    val team: String?,
    val expectedStrategy: List<StrategyStintDto>,
    val altStrategy: List<StrategyStintDto>?
)

@Serializable
data class StrategyStintDto(
    val compound: String,
    val laps: Int,
    val pitWindow: PitWindowDto?
)

@Serializable
data class PitWindowDto(
    val lapFrom: Int,
    val lapTo: Int
)
