package com.f1analytics.data.db.repository

import com.f1analytics.core.domain.model.Session
import com.f1analytics.core.domain.model.SessionType
import com.f1analytics.core.domain.port.SessionRepository
import com.f1analytics.data.db.tables.SessionsTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.hours
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
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

    override suspend fun findActive(): Session? = withContext(Dispatchers.IO) {
        transaction(db) {
            SessionsTable.selectAll()
                .where { SessionsTable.status eq "Started" }
                .firstOrNull()
                ?.toSession()
        }
    }

    override suspend fun findMostRecent(): Session? = withContext(Dispatchers.IO) {
        val fourHoursAgo = Clock.System.now().minus(4.hours)
        transaction(db) {
            SessionsTable.selectAll()
                .where {
                    SessionsTable.recorded eq true and (
                        SessionsTable.dateEnd.isNull() or
                        (SessionsTable.dateEnd greaterEq fourHoursAgo)
                    )
                }
                .orderBy(SessionsTable.dateStart, SortOrder.DESC_NULLS_LAST)
                .firstOrNull()
                ?.toSession()
        }
    }

    override suspend fun findNextUpcoming(): Session? = withContext(Dispatchers.IO) {
        val now = Clock.System.now()
        transaction(db) {
            SessionsTable.selectAll()
                .where {
                    SessionsTable.dateStart.isNotNull() and
                    (SessionsTable.dateStart greater now) and
                    (SessionsTable.status.isNull() or (SessionsTable.status neq "Finished"))
                }
                .orderBy(SessionsTable.dateStart, SortOrder.ASC_NULLS_LAST)
                .firstOrNull()
                ?.toSession()
        }
    }

    override suspend fun findByRace(raceKey: Int): List<Session> = withContext(Dispatchers.IO) {
        transaction(db) {
            SessionsTable.selectAll()
                .where { SessionsTable.raceKey eq raceKey }
                .orderBy(SessionsTable.dateStart, SortOrder.ASC_NULLS_LAST)
                .map { it.toSession() }
        }
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
