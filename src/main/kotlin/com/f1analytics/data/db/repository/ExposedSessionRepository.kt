package com.f1analytics.data.db.repository

import com.f1analytics.core.domain.model.Session
import com.f1analytics.core.domain.model.SessionType
import com.f1analytics.core.domain.port.SessionRepository
import com.f1analytics.data.db.tables.SessionsTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedSessionRepository(private val db: Database) : SessionRepository {

    override suspend fun upsert(session: Session) = withContext(Dispatchers.IO) {
        transaction(db) {
            SessionsTable.upsert(SessionsTable.key) {
                it[key]       = session.key
                it[raceKey]   = session.raceKey
                it[name]      = session.name
                it[type]      = session.type.name
                it[year]      = session.year
                it[status]    = session.status
                it[dateStart] = session.dateStart
                it[dateEnd]   = session.dateEnd
                it[recorded]  = session.recorded
            }
        }
        Unit
    }

    override suspend fun findByKey(key: Int): Session? = withContext(Dispatchers.IO) {
        transaction(db) {
            SessionsTable.selectAll()
                .where { SessionsTable.key eq key }
                .firstOrNull()
                ?.toSession()
        }
    }

    override suspend fun findByYearAndType(year: Int, type: SessionType): List<Session> =
        withContext(Dispatchers.IO) {
            transaction(db) {
                SessionsTable.selectAll()
                    .where { (SessionsTable.year eq year) and (SessionsTable.type eq type.name) }
                    .map { it.toSession() }
            }
        }

    override suspend fun findLastRecorded(): Session? = withContext(Dispatchers.IO) {
        transaction(db) {
            SessionsTable.selectAll()
                .where { SessionsTable.recorded eq true }
                .orderBy(SessionsTable.dateStart, SortOrder.DESC_NULLS_LAST)
                .firstOrNull()
                ?.toSession()
        }
    }

    override suspend fun updateStatus(key: Int, status: String) = withContext(Dispatchers.IO) {
        transaction(db) {
            SessionsTable.update({ SessionsTable.key eq key }) {
                it[SessionsTable.status] = status
            }
        }
        Unit
    }
}

private fun ResultRow.toSession() = Session(
    key       = this[SessionsTable.key],
    raceKey   = this[SessionsTable.raceKey],
    name      = this[SessionsTable.name],
    type      = SessionType.from(this[SessionsTable.type]),
    year      = this[SessionsTable.year],
    status    = this[SessionsTable.status],
    dateStart = this[SessionsTable.dateStart],
    dateEnd   = this[SessionsTable.dateEnd],
    recorded  = this[SessionsTable.recorded]
)
