package com.f1analytics.data.db.repository

import com.f1analytics.core.domain.model.RaceControlEntry
import com.f1analytics.core.domain.model.TimingMessage
import com.f1analytics.core.domain.port.RaceControlRepository
import com.f1analytics.data.db.tables.RaceControlMessagesTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedRaceControlRepository(private val db: Database) : RaceControlRepository {

    override suspend fun insert(
        sessionKey: Int,
        msg: TimingMessage.RaceControlMsg,
        timestamp: Instant
    ) = withContext(Dispatchers.IO) {
        transaction(db) {
            RaceControlMessagesTable.insert {
                it[this.sessionKey] = sessionKey
                it[this.timestamp]  = timestamp
                it[this.message]    = msg.message
                it[this.flag]       = msg.flag
                it[this.scope]      = msg.scope
                it[this.lapNumber]  = msg.lap
            }
        }
        Unit
    }

    override suspend fun findBySession(sessionKey: Int): List<RaceControlEntry> =
        withContext(Dispatchers.IO) {
            transaction(db) {
                RaceControlMessagesTable.selectAll()
                    .where { RaceControlMessagesTable.sessionKey eq sessionKey }
                    .orderBy(RaceControlMessagesTable.timestamp, SortOrder.ASC)
                    .map { row ->
                        RaceControlEntry(
                            message   = row[RaceControlMessagesTable.message],
                            flag      = row[RaceControlMessagesTable.flag],
                            scope     = row[RaceControlMessagesTable.scope],
                            lap       = row[RaceControlMessagesTable.lapNumber],
                            timestamp = row[RaceControlMessagesTable.timestamp]
                        )
                    }
            }
        }
}
