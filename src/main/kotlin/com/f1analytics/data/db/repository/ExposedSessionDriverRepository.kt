package com.f1analytics.data.db.repository

import com.f1analytics.core.domain.model.DriverEntry
import com.f1analytics.core.domain.port.SessionDriverRepository
import com.f1analytics.data.db.tables.SessionDriversTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedSessionDriverRepository(private val db: Database) : SessionDriverRepository {

    override suspend fun upsertAll(sessionKey: Int, drivers: Map<String, DriverEntry>) =
        withContext(Dispatchers.IO) {
            transaction(db) {
                for ((_, driver) in drivers) {
                    SessionDriversTable.upsert(
                        SessionDriversTable.sessionKey,
                        SessionDriversTable.number
                    ) {
                        it[this.sessionKey] = sessionKey
                        it[number]          = driver.number
                        it[code]            = driver.code
                        it[firstName]       = driver.firstName
                        it[lastName]        = driver.lastName
                        it[team]            = driver.team
                        it[teamColor]       = driver.teamColor
                    }
                }
            }
            Unit
        }

    override suspend fun findBySession(sessionKey: Int): List<DriverEntry> =
        withContext(Dispatchers.IO) {
            transaction(db) {
                SessionDriversTable.selectAll()
                    .where { SessionDriversTable.sessionKey eq sessionKey }
                    .map {
                        DriverEntry(
                            number    = it[SessionDriversTable.number],
                            code      = it[SessionDriversTable.code],
                            firstName = it[SessionDriversTable.firstName],
                            lastName  = it[SessionDriversTable.lastName],
                            team      = it[SessionDriversTable.team],
                            teamColor = it[SessionDriversTable.teamColor]
                        )
                    }
            }
        }
}
