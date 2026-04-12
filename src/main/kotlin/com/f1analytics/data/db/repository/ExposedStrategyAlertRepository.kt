package com.f1analytics.data.db.repository

import com.f1analytics.core.domain.model.StrategyAlert
import com.f1analytics.core.domain.port.StrategyAlertRepository
import com.f1analytics.data.db.tables.StrategyAlertsTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedStrategyAlertRepository(private val db: Database) : StrategyAlertRepository {

    override suspend fun findBySession(sessionKey: Int): List<StrategyAlert> = withContext(Dispatchers.IO) {
        transaction(db) {
            StrategyAlertsTable.selectAll()
                .where { StrategyAlertsTable.sessionKey eq sessionKey }
                .orderBy(StrategyAlertsTable.timestamp, SortOrder.ASC)
                .map { it.toAlert() }
        }
    }

    override suspend fun save(alert: StrategyAlert): Unit = withContext(Dispatchers.IO) {
        transaction(db) {
            StrategyAlertsTable.insert {
                it[sessionKey]       = alert.sessionKey
                it[lap]              = alert.lap
                it[type]             = alert.type
                it[instigatorNumber] = alert.instigatorNumber
                it[rivalNumber]      = alert.rivalNumber
                it[gapSeconds]       = alert.gapSeconds
                it[predictedOutcome] = alert.predictedOutcome
                it[confirmedOutcome] = alert.confirmedOutcome
                it[timestamp]        = alert.timestamp
            }
        }
        Unit
    }
}

private fun ResultRow.toAlert() = StrategyAlert(
    id               = this[StrategyAlertsTable.id],
    sessionKey       = this[StrategyAlertsTable.sessionKey],
    lap              = this[StrategyAlertsTable.lap],
    type             = this[StrategyAlertsTable.type],
    instigatorNumber = this[StrategyAlertsTable.instigatorNumber],
    rivalNumber      = this[StrategyAlertsTable.rivalNumber],
    gapSeconds       = this[StrategyAlertsTable.gapSeconds],
    predictedOutcome = this[StrategyAlertsTable.predictedOutcome],
    confirmedOutcome = this[StrategyAlertsTable.confirmedOutcome],
    timestamp        = this[StrategyAlertsTable.timestamp]
)
