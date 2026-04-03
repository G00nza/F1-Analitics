package com.f1analytics.data.db.repository

import com.f1analytics.core.domain.model.DriverTireStintDelta
import com.f1analytics.core.domain.model.Stint
import com.f1analytics.core.domain.port.StintRepository
import com.f1analytics.data.db.tables.StintsTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedStintRepository(private val db: Database) : StintRepository {

    /** Merges stint deltas — same partial-update pattern as laps. */
    override suspend fun upsertDeltas(sessionKey: Int, deltas: Map<String, DriverTireStintDelta>) =
        withContext(Dispatchers.IO) {
            transaction(db) {
                for ((driverNumber, delta) in deltas) {
                    val stintNum = delta.stintNumber ?: continue

                    val exists = StintsTable.selectAll().where {
                        (StintsTable.sessionKey   eq sessionKey) and
                        (StintsTable.driverNumber eq driverNumber) and
                        (StintsTable.stintNumber  eq stintNum)
                    }.count() > 0

                    if (!exists) {
                        StintsTable.insert {
                            it[this.sessionKey]   = sessionKey
                            it[this.driverNumber] = driverNumber
                            it[this.stintNumber]  = stintNum
                            delta.compound?.let  { v -> it[compound]  = v }
                            delta.isNew?.let     { v -> it[isNew]     = v }
                            delta.lapStart?.let  { v -> it[lapStart]  = v }
                            delta.totalLaps?.let { v -> it[lapEnd]    = (delta.lapStart ?: 0) + v - 1 }
                        }
                    } else {
                        StintsTable.update({
                            (StintsTable.sessionKey   eq sessionKey) and
                            (StintsTable.driverNumber eq driverNumber) and
                            (StintsTable.stintNumber  eq stintNum)
                        }) {
                            delta.compound?.let  { v -> it[compound]  = v }
                            delta.isNew?.let     { v -> it[isNew]     = v }
                            delta.lapStart?.let  { v -> it[lapStart]  = v }
                            delta.totalLaps?.let { v -> it[lapEnd]    = (delta.lapStart ?: 0) + v - 1 }
                        }
                    }
                }
            }
            Unit
        }

    override suspend fun findBySession(sessionKey: Int): List<Stint> = withContext(Dispatchers.IO) {
        transaction(db) {
            StintsTable.selectAll()
                .where { StintsTable.sessionKey eq sessionKey }
                .map { it.toStint() }
        }
    }

    override suspend fun findCurrentStint(sessionKey: Int, driverNumber: String): Stint? =
        withContext(Dispatchers.IO) {
            transaction(db) {
                StintsTable.selectAll().where {
                    (StintsTable.sessionKey   eq sessionKey) and
                    (StintsTable.driverNumber eq driverNumber)
                }
                    .orderBy(StintsTable.stintNumber, SortOrder.DESC)
                    .firstOrNull()
                    ?.toStint()
            }
        }
}

private fun ResultRow.toStint() = Stint(
    id           = this[StintsTable.id],
    sessionKey   = this[StintsTable.sessionKey],
    driverNumber = this[StintsTable.driverNumber],
    stintNumber  = this[StintsTable.stintNumber],
    compound     = this[StintsTable.compound],
    isNew        = this[StintsTable.isNew],
    lapStart     = this[StintsTable.lapStart],
    lapEnd       = this[StintsTable.lapEnd]
)
