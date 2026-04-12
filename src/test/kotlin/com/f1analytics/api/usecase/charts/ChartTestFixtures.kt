package com.f1analytics.api.usecase.charts

import com.f1analytics.core.domain.model.DriverEntry
import com.f1analytics.core.domain.model.Lap
import com.f1analytics.core.domain.model.Stint
import kotlinx.datetime.Instant

fun lap(
    driverNumber: String = "1",
    lapNumber: Int = 1,
    lapTimeMs: Int? = 90000,
    pitOutLap: Boolean = false,
    pitInLap: Boolean = false,
    isPersonalBest: Boolean = false
) = Lap(
    id = 0,
    sessionKey = 9001,
    driverNumber = driverNumber,
    lapNumber = lapNumber,
    lapTimeMs = lapTimeMs,
    sector1Ms = null,
    sector2Ms = null,
    sector3Ms = null,
    isPersonalBest = isPersonalBest,
    isOverallBest = false,
    pitOutLap = pitOutLap,
    pitInLap = pitInLap,
    timestamp = Instant.parse("2026-03-16T15:00:00Z")
)

fun driver(
    number: String = "1",
    code: String = "VER",
    teamColor: String? = "3671C6"
) = DriverEntry(
    number = number,
    code = code,
    firstName = null,
    lastName = null,
    team = null,
    teamColor = teamColor
)

fun stint(
    driverNumber: String = "1",
    stintNumber: Int = 1,
    compound: String? = "SOFT",
    lapStart: Int? = 1,
    lapEnd: Int? = 10
) = Stint(
    id = 0,
    sessionKey = 9001,
    driverNumber = driverNumber,
    stintNumber = stintNumber,
    compound = compound,
    isNew = null,
    lapStart = lapStart,
    lapEnd = lapEnd
)

fun lapContext(
    lap: Lap = lap(),
    driver: DriverEntry = driver(),
    stint: Stint = stint(),
    gapToLeaderMs: Int? = null
) = LapContext(lap, driver, stint, gapToLeaderMs)
