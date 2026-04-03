package com.f1analytics.core.domain.port

import com.f1analytics.core.domain.model.WeatherData
import kotlinx.datetime.Instant

interface WeatherRepository {
    suspend fun insert(sessionKey: Int, weather: WeatherData, timestamp: Instant)
    suspend fun findLatest(sessionKey: Int): WeatherData?
}
