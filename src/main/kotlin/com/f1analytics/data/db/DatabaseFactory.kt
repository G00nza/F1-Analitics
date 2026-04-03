package com.f1analytics.data.db

import com.f1analytics.data.db.tables.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

private val logger = KotlinLogging.logger {}

private val allTables = arrayOf(
    RacesTable,
    SessionsTable,
    SessionDriversTable,
    LapsTable,
    StintsTable,
    PitStopsTable,
    RaceControlMessagesTable,
    WeatherSnapshotsTable,
    PositionSnapshotsTable,
    CarTelemetryTable,
    RaceResultsTable,
    DriverStandingsTable
)

object DatabaseFactory {

    fun init(jdbcUrl: String = "jdbc:sqlite:f1analytics.db"): Database {
        // setupConnection runs on every raw JDBC connection open — the only
        // reliable place to set SQLite PRAGMAs in Exposed.
        val db = Database.connect(
            url = jdbcUrl,
            driver = "org.sqlite.JDBC",
            setupConnection = { conn ->
                conn.createStatement().execute("PRAGMA foreign_keys = ON")
            }
        )
        // SchemaUtils.create uses CREATE TABLE IF NOT EXISTS (idempotent).
        // We run one table per transaction to avoid SQLITE_BUSY: SQLite locks
        // the file on COMMIT if any SELECT cursor is still open in the same
        // transaction (Exposed's table-existence checks open such cursors).
        allTables.forEach { table ->
            transaction(db) { SchemaUtils.create(table) }
        }
        logger.info { "Database initialised: $jdbcUrl" }
        return db
    }
}
