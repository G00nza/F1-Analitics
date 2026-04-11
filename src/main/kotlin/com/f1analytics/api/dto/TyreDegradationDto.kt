package com.f1analytics.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class TyreDegradationDto(
    val sessionKey: Int,
    val hasStintData: Boolean,
    val longRuns: List<TyreLongRunDto>,
    val shortRuns: List<TyreShortRunDto>
)

@Serializable
data class TyreLongRunDto(
    val driverNumber: String,
    val driverCode: String,
    val team: String?,
    val compound: String?,
    val stintNumber: Int,
    val lapCount: Int,
    val firstLapMs: Int,
    val lastLapMs: Int,
    val degPerLapMs: Double
)

@Serializable
data class TyreShortRunDto(
    val driverNumber: String,
    val driverCode: String,
    val compound: String?,
    val stintNumber: Int,
    val lapCount: Int
)
