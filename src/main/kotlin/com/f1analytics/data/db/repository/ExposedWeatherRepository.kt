package com.f1analytics.data.db.repository

import com.f1analytics.core.domain.model.WeatherData
import com.f1analytics.core.domain.port.WeatherRepository
import com.f1analytics.data.db.tables.WeatherSnapshotsTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedWeatherRepository(private val db: Database) : WeatherRepository {

    override suspend fun insert(
        sessionKey: Int,
        weather: WeatherData,
        timestamp: Instant
    ) = withContext(Dispatchers.IO) {
        transaction(db) {
            WeatherSnapshotsTable.insert {
                it[this.sessionKey]    = sessionKey
                it[this.timestamp]     = timestamp
                it[this.airTemp]       = weather.airTemp
                it[this.trackTemp]     = weather.trackTemp
                it[this.humidity]      = weather.humidity
                it[this.pressure]      = weather.pressure
                it[this.windSpeed]     = weather.windSpeed
                it[this.windDirection] = weather.windDirection
                it[this.rainfall]      = weather.rainfall
            }
        }
        Unit
    }

    override suspend fun findLatest(sessionKey: Int): WeatherData? =
        withContext(Dispatchers.IO) {
            transaction(db) {
                WeatherSnapshotsTable.selectAll()
                    .where { WeatherSnapshotsTable.sessionKey eq sessionKey }
                    .orderBy(WeatherSnapshotsTable.timestamp, SortOrder.DESC)
                    .firstOrNull()
                    ?.let { row ->
                        WeatherData(
                            airTemp       = row[WeatherSnapshotsTable.airTemp],
                            trackTemp     = row[WeatherSnapshotsTable.trackTemp],
                            humidity      = row[WeatherSnapshotsTable.humidity],
                            pressure      = row[WeatherSnapshotsTable.pressure],
                            windSpeed     = row[WeatherSnapshotsTable.windSpeed],
                            windDirection = row[WeatherSnapshotsTable.windDirection],
                            rainfall      = row[WeatherSnapshotsTable.rainfall]
                        )
                    }
            }
        }
}
