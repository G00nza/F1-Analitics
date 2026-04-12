package com.f1analytics.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class PostRaceStrategyDto(
    val sessionKey: Int,
    val sessionName: String?,
    val raceName: String?,
    val drivers: List<DriverRaceStrategyDto>,
    val strategyComparison: StrategyComparisonDto,
    val undercutResults: List<UndercutResultDto>,
    val scBeneficiaries: List<ScBeneficiaryDto>
)

@Serializable
data class DriverRaceStrategyDto(
    val driverNumber: String,
    val driverCode: String?,
    val team: String?,
    val finalPosition: Int?,
    val stints: List<DriverRaceStintDto>,
    val stops: Int
)

@Serializable
data class DriverRaceStintDto(
    val compound: String?,
    val lapStart: Int?,
    val lapEnd: Int?,
    val laps: Int?
)

@Serializable
data class StrategyComparisonDto(
    val oneStop: StrategyGroupDto?,
    val twoStop: StrategyGroupDto?,
    val threeOrMore: StrategyGroupDto?
)

@Serializable
data class StrategyGroupDto(
    val driverCount: Int,
    val avgFinishPosition: Double?,
    val drivers: List<String>   // driver codes
)

@Serializable
data class UndercutResultDto(
    val lap: Int?,
    val instigatorCode: String?,
    val rivalCode: String?,
    val predictedOutcome: String?,
    val instigatorFinalPosition: Int?,
    val rivalFinalPosition: Int?
)

@Serializable
data class ScBeneficiaryDto(
    val scLap: Int?,
    val driverCode: String?,
    val positionAtSc: Int?,
    val finalPosition: Int?,
    val positionsGained: Int?
)
