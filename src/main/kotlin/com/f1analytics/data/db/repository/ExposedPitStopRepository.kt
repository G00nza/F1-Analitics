package com.f1analytics.data.db.repository

import com.f1analytics.core.domain.port.PitStopRepository
import com.f1analytics.data.db.tables.PitStopsTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedPitStopRepository(private val db: Database) : PitStopRepository {

    override suspend fun insert(
        sessionKey: Int,
        driverNumber: String,
        lapNumber: Int?,
        timestamp: Instant
    ) = withContext(Dispatchers.IO) {
        transaction(db) {
            PitStopsTable.insert {
                it[this.sessionKey]   = sessionKey
                it[this.driverNumber] = driverNumber
                it[this.lapNumber]    = lapNumber
                it[this.timestamp]    = timestamp
            }
        }
        Unit
    }
}
