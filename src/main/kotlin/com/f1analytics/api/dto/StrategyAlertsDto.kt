package com.f1analytics.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class StrategyAlertsDto(
    val sessionKey: Int,
    val alerts: List<StrategyAlertDto>
)

@Serializable
data class StrategyAlertDto(
    val lap: Int?,
    val type: String,
    val instigatorNumber: String,
    val instigatorCode: String?,
    val rivalNumber: String,
    val rivalCode: String?,
    val gapSeconds: Double?,
    val predictedOutcome: String?,
    val confirmedOutcome: String?
)
