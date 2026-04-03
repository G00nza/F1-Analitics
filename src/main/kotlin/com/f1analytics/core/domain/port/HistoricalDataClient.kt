package com.f1analytics.core.domain.port

import com.f1analytics.core.domain.model.ConstructorStandingData
import com.f1analytics.core.domain.model.DriverStandingData
import com.f1analytics.core.domain.model.RaceResultData
import com.f1analytics.core.domain.model.RaceScheduleEntry

interface HistoricalDataClient {
    suspend fun getRaceResults(year: Int, round: Int): List<RaceResultData>
    suspend fun getDriverStandings(year: Int): List<DriverStandingData>
    suspend fun getConstructorStandings(year: Int): List<ConstructorStandingData>
    suspend fun getSeasonSchedule(year: Int): List<RaceScheduleEntry>
}
