package com.f1analytics.data.db.repository

import com.f1analytics.core.domain.model.Race
import com.f1analytics.core.domain.port.RaceRepository
import com.f1analytics.data.db.tables.RacesTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedRaceRepository(private val db: Database) : RaceRepository {

    override suspend fun findByYear(year: Int): List<Race> = withContext(Dispatchers.IO) {
        transaction(db) {
            RacesTable.selectAll()
                .where { RacesTable.year eq year }
                .orderBy(RacesTable.round)
                .map { it.toRace() }
        }
    }

    override suspend fun findByKey(key: Int): Race? = withContext(Dispatchers.IO) {
        transaction(db) {
            RacesTable.selectAll()
                .where { RacesTable.key eq key }
                .firstOrNull()
                ?.toRace()
        }
    }

    override suspend fun findCurrent(): Race? = withContext(Dispatchers.IO) {
        val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date.toString()
        transaction(db) {
            val races = RacesTable.selectAll()
                .map { it.toRace() }
                .filter { it.dateStart != null }

            // Active: weekend has started and not yet ended
            val active = races
                .filter { it.dateStart!! <= today && (it.dateEnd == null || it.dateEnd >= today) }
                .maxByOrNull { it.dateStart!! }
            if (active != null) return@transaction active

            // Fallback: most recent finished race
            races.filter { it.dateStart!! < today }
                .maxByOrNull { it.dateStart!! }
        }
    }
}

private fun ResultRow.toRace() = Race(
    key          = this[RacesTable.key],
    name         = this[RacesTable.name],
    officialName = this[RacesTable.officialName],
    circuit      = this[RacesTable.circuit],
    country      = this[RacesTable.country],
    year         = this[RacesTable.year],
    round        = this[RacesTable.round],
    dateStart    = this[RacesTable.dateStart],
    dateEnd      = this[RacesTable.dateEnd]
)
