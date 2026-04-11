package com.f1analytics.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class SectorComparisonDto(
    val sessionKeyA: Int,
    val sessionKeyB: Int,
    val driverNumber: String,
    val driverCode: String,
    val sectors: List<SectorRowDto>,
    val totalDeltaMs: Int?,
    val mostImprovedSector: Int?,
    val leastImprovedSector: Int?,
)

@Serializable
data class SectorRowDto(
    val sector: Int,
    val sessionAMs: Int?,
    val sessionBMs: Int?,
    val deltaMs: Int?,
)
