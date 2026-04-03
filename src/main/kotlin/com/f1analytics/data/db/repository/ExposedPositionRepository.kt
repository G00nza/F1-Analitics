package com.f1analytics.data.db.repository

import com.f1analytics.core.domain.model.DriverTimingDelta
import com.f1analytics.core.domain.port.PositionRepository
import com.f1analytics.data.db.tables.PositionSnapshotsTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedPositionRepository(private val db: Database) : PositionRepository {

    override suspend fun insertSnapshot(
        sessionKey: Int,
        deltas: Map<String, DriverTimingDelta>,
        timestamp: Instant
    ) = withContext(Dispatchers.IO) {
        // Only persist deltas that carry at least one position/gap field
        val relevant = deltas.entries.filter { (_, d) ->
            d.position != null || d.gapToLeader != null || d.interval != null
        }
        if (relevant.isEmpty()) return@withContext

        transaction(db) {
            PositionSnapshotsTable.batchInsert(relevant) { (driverNumber, delta) ->
                this[PositionSnapshotsTable.sessionKey]   = sessionKey
                this[PositionSnapshotsTable.timestamp]    = timestamp
                this[PositionSnapshotsTable.driverNumber] = driverNumber
                this[PositionSnapshotsTable.position]     = delta.position
                this[PositionSnapshotsTable.gapToLeader]  = delta.gapToLeader
                this[PositionSnapshotsTable.interval]     = delta.interval
            }
        }
        Unit
    }
}
