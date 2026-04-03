package com.f1analytics.data.db.repository

import com.f1analytics.core.domain.model.DriverTimingDelta
import com.f1analytics.core.domain.model.Lap
import com.f1analytics.core.domain.port.LapRepository
import com.f1analytics.data.db.tables.LapsTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedLapRepository(private val db: Database) : LapRepository {

    /** Merges a delta into the laps table.
     *  F1 sends partial updates — sectors can arrive in separate messages.
     *  We never overwrite an existing non-null field with null; we only apply
     *  the fields that are present in the delta. */
    override suspend fun upsertDeltas(
        sessionKey: Int,
        deltas: Map<String, DriverTimingDelta>,
        ts: Instant
    ) = withContext(Dispatchers.IO) {
        transaction(db) {
            for ((driverNumber, delta) in deltas) {
                val lapNum = delta.lapNumber ?: continue  // can't identify row without lap number

                val exists = LapsTable.selectAll().where {
                    (LapsTable.sessionKey   eq sessionKey) and
                    (LapsTable.driverNumber eq driverNumber) and
                    (LapsTable.lapNumber    eq lapNum)
                }.count() > 0

                if (!exists) {
                    LapsTable.insert {
                        it[this.sessionKey]   = sessionKey
                        it[this.driverNumber] = driverNumber
                        it[this.lapNumber]    = lapNum
                        it[this.timestamp]    = ts
                        delta.lastLapTimeMs?.let { v -> it[lapTimeMs]     = v }
                        delta.sector1Ms?.let     { v -> it[sector1Ms]     = v }
                        delta.sector2Ms?.let     { v -> it[sector2Ms]     = v }
                        delta.sector3Ms?.let     { v -> it[sector3Ms]     = v }
                        delta.lastLapPersonalBest?.let { v -> it[isPersonalBest] = v }
                        delta.lastLapOverallBest?.let  { v -> it[isOverallBest]  = v }
                        delta.pitOut?.let { v -> it[pitOutLap] = v }
                        delta.inPit?.let  { v -> it[pitInLap]  = v }
                    }
                } else {
                    LapsTable.update({
                        (LapsTable.sessionKey   eq sessionKey) and
                        (LapsTable.driverNumber eq driverNumber) and
                        (LapsTable.lapNumber    eq lapNum)
                    }) {
                        it[timestamp] = ts
                        delta.lastLapTimeMs?.let { v -> it[lapTimeMs]     = v }
                        delta.sector1Ms?.let     { v -> it[sector1Ms]     = v }
                        delta.sector2Ms?.let     { v -> it[sector2Ms]     = v }
                        delta.sector3Ms?.let     { v -> it[sector3Ms]     = v }
                        delta.lastLapPersonalBest?.let { v -> it[isPersonalBest] = v }
                        delta.lastLapOverallBest?.let  { v -> it[isOverallBest]  = v }
                        delta.pitOut?.let { v -> it[pitOutLap] = v }
                        delta.inPit?.let  { v -> it[pitInLap]  = v }
                    }
                }
            }
        }
        Unit
    }

    override suspend fun findBySession(sessionKey: Int): List<Lap> = withContext(Dispatchers.IO) {
        transaction(db) {
            LapsTable.selectAll()
                .where { LapsTable.sessionKey eq sessionKey }
                .map { it.toLap() }
        }
    }

    override suspend fun findByDriver(sessionKey: Int, driverNumber: String): List<Lap> =
        withContext(Dispatchers.IO) {
            transaction(db) {
                LapsTable.selectAll().where {
                    (LapsTable.sessionKey eq sessionKey) and
                    (LapsTable.driverNumber eq driverNumber)
                }.map { it.toLap() }
            }
        }

    override suspend fun findBestLaps(sessionKey: Int): Map<String, Lap> =
        withContext(Dispatchers.IO) {
            transaction(db) {
                LapsTable.selectAll().where {
                    (LapsTable.sessionKey eq sessionKey) and
                    LapsTable.lapTimeMs.isNotNull()
                }
                    .map { it.toLap() }
                    .groupBy { it.driverNumber }
                    .mapValues { (_, laps) -> laps.minBy { it.lapTimeMs!! } }
            }
        }
}

private fun ResultRow.toLap() = Lap(
    id            = this[LapsTable.id],
    sessionKey    = this[LapsTable.sessionKey],
    driverNumber  = this[LapsTable.driverNumber],
    lapNumber     = this[LapsTable.lapNumber],
    lapTimeMs     = this[LapsTable.lapTimeMs],
    sector1Ms     = this[LapsTable.sector1Ms],
    sector2Ms     = this[LapsTable.sector2Ms],
    sector3Ms     = this[LapsTable.sector3Ms],
    isPersonalBest = this[LapsTable.isPersonalBest],
    isOverallBest  = this[LapsTable.isOverallBest],
    pitOutLap     = this[LapsTable.pitOutLap],
    pitInLap      = this[LapsTable.pitInLap],
    timestamp     = this[LapsTable.timestamp]
)
