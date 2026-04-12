package com.f1analytics.api.dto

import kotlinx.serialization.Serializable

// ── Live SC impact (while SC is active) ───────────────────────────────────

@Serializable
data class SafetyCarLiveDto(
    val sessionKey: Int,
    val scLap: Int?,
    val drivers: List<SafetyCarDriverRecommendationDto>
)

@Serializable
data class SafetyCarDriverRecommendationDto(
    val driverNumber: String,
    val driverCode: String?,
    val team: String?,
    val position: Int?,
    val compound: String?,
    val tyreAgeLaps: Int?,
    val hasNewTyresAvailable: Boolean,
    val gapToCarBehindSeconds: Double?,
    val lapsRemaining: Int?,
    val score: Int,
    val message: String
)

// ── Post-race SC review ────────────────────────────────────────────────────

@Serializable
data class SafetyCarReviewDto(
    val sessionKey: Int,
    val events: List<SafetyCarReviewEventDto>
)

@Serializable
data class SafetyCarReviewEventDto(
    val scLap: Int?,
    val scEndLap: Int?,
    val drivers: List<SafetyCarDriverResultDto>
)

@Serializable
data class SafetyCarDriverResultDto(
    val driverNumber: String,
    val driverCode: String?,
    val team: String?,
    val compound: String?,
    val tyreAgeLaps: Int?,
    val hasNewTyresAvailable: Boolean,
    val score: Int,
    val message: String,
    val pittedDuringSc: Boolean,
    val positionAtSc: Int?,
    val finalPosition: Int?,
    val capitalizedCorrectly: Boolean?
)
