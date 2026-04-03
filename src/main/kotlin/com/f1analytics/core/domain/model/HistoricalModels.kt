package com.f1analytics.core.domain.model

import kotlinx.datetime.LocalDate

data class RaceResultData(
    val round: Int,
    val raceName: String,
    val driverCode: String,
    val constructorName: String,
    val gridPosition: Int?,
    val finishPosition: Int?,
    val points: Double,
    val status: String,
    val fastestLap: Boolean,
    val lapsCompleted: Int?
)

data class DriverStandingData(
    val position: Int,
    val driverCode: String,
    val points: Double,
    val wins: Int
)

data class ConstructorStandingData(
    val position: Int,
    val constructorName: String,
    val points: Double,
    val wins: Int
)

data class RaceScheduleEntry(
    val round: Int,
    val raceName: String,
    val circuitName: String,
    val country: String,
    val date: LocalDate
)
