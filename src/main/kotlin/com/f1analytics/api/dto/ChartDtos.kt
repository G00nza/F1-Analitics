package com.f1analytics.api.dto

import kotlinx.serialization.Serializable

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

// ── Unified charts response ──────────────────────────────────────────────────

@Serializable
data class SessionChartsDto(
    val bestLaps: List<BestLapDto>,
    val charts: List<ChartSectionDto>
)

@Serializable
data class BestLapDto(
    val driverCode: String,
    val teamColor: String,
    val lapTimeMs: Int,
    val compound: String?,
    val lapNumber: Int
)

@Serializable
data class ChartSectionDto(
    val id: String,
    val title: String,
    val type: String,
    val datasets: List<ChartDatasetDto>
)

@Serializable
data class ChartDatasetDto(
    val label: String,
    val color: String,
    val compound: String? = null,
    val points: List<ChartPointDto>
)

@Serializable
data class ChartPointDto(
    val x: Double,
    val y: Double? = null,
    val pitOutLap: Boolean = false,
    val pitInLap: Boolean = false,
    val isPersonalBest: Boolean = false,
    val compound: String? = null,
    val lapNumber: Int? = null
)
