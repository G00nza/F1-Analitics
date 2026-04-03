package com.f1analytics.core.domain.model

import kotlinx.datetime.Instant

data class Session(
    val key: Int,
    val raceKey: Int?,
    val name: String,
    val type: SessionType,
    val year: Int,
    val status: String?,
    val dateStart: Instant?,
    val dateEnd: Instant?,
    val recorded: Boolean
)

data class Lap(
    val id: Int,
    val sessionKey: Int,
    val driverNumber: String,
    val lapNumber: Int,
    val lapTimeMs: Int?,
    val sector1Ms: Int?,
    val sector2Ms: Int?,
    val sector3Ms: Int?,
    val isPersonalBest: Boolean,
    val isOverallBest: Boolean,
    val pitOutLap: Boolean,
    val pitInLap: Boolean,
    val timestamp: Instant
)

data class Stint(
    val id: Int,
    val sessionKey: Int,
    val driverNumber: String,
    val stintNumber: Int,
    val compound: String?,
    val isNew: Boolean?,
    val lapStart: Int?,
    val lapEnd: Int?
)
