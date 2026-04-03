package com.f1analytics.data.db.repository

import com.f1analytics.core.domain.model.CarTelemetryEntry
import com.f1analytics.core.domain.port.TelemetryRepository
import com.f1analytics.data.db.tables.CarTelemetryTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedTelemetryRepository(private val db: Database) : TelemetryRepository {

    override suspend fun insertBatch(sessionKey: Int, entries: List<CarTelemetryEntry>) =
        withContext(Dispatchers.IO) {
            if (entries.isEmpty()) return@withContext
            transaction(db) {
                CarTelemetryTable.batchInsert(entries) { entry ->
                    this[CarTelemetryTable.sessionKey]   = sessionKey
                    this[CarTelemetryTable.timestamp]    = entry.timestamp
                    this[CarTelemetryTable.driverNumber] = entry.driverNumber
                    this[CarTelemetryTable.speed]        = entry.speed
                    this[CarTelemetryTable.rpm]          = entry.rpm
                    this[CarTelemetryTable.gear]         = entry.gear
                    this[CarTelemetryTable.throttle]     = entry.throttle
                    this[CarTelemetryTable.brake]        = entry.brake
                    this[CarTelemetryTable.drs]          = entry.drs
                }
            }
            Unit
        }
}
