package com.f1analytics.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class RacePaceDto(
    val sessionKey: Int,
    val warning: String,
    val hasStintData: Boolean,
    val teams: List<TeamPaceRowDto>,
)

@Serializable
data class TeamPaceRowDto(
    val rank: Int,
    val team: String,
    val avgLapMs: Int,
    val gapToLeaderMs: Int?,
)
