package com.f1analytics.data.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class DatabaseFactoryTest {

    private lateinit var dbFile: File
    private lateinit var db: Database

    @BeforeTest
    fun setup() {
        dbFile = File.createTempFile("f1test", ".db")
        db = DatabaseFactory.init("jdbc:sqlite:${dbFile.absolutePath}")
    }

    @AfterTest
    fun teardown() {
        dbFile.delete()
        // Clean up WAL and shared-memory sidecar files
        File("${dbFile.absolutePath}-wal").delete()
        File("${dbFile.absolutePath}-shm").delete()
    }

    private val expectedTables = listOf(
        "races",
        "sessions",
        "session_drivers",
        "laps",
        "stints",
        "pit_stops",
        "race_control_messages",
        "weather_snapshots",
        "position_snapshots",
        "car_telemetry",
        "race_results",
        "driver_standings"
    )

    private val expectedIndexes = listOf(
        "idx_sessions_year_type",
        "idx_laps_session_driver",
        "idx_laps_session_lap",
        "idx_stints_session_driver",
        "idx_pos_session_ts",
        "idx_telemetry_session_ts"
    )

    @Test
    fun `all tables are created`() {
        transaction(db) {
            val tables = exec("SELECT name FROM sqlite_master WHERE type='table'") { rs ->
                generateSequence { if (rs.next()) rs.getString("name") else null }.toList()
            }.orEmpty()

            expectedTables.forEach { name ->
                assertTrue(name in tables, "Missing table: $name")
            }
        }
    }

    @Test
    fun `all indexes are created`() {
        transaction(db) {
            val indexes = exec("SELECT name FROM sqlite_master WHERE type='index'") { rs ->
                generateSequence { if (rs.next()) rs.getString("name") else null }.toList()
            }.orEmpty()

            expectedIndexes.forEach { name ->
                assertTrue(indexes.any { it.contains(name) }, "Missing index: $name")
            }
        }
    }

    @Test
    fun `init is idempotent — calling twice on the same file does not throw`() {
        DatabaseFactory.init("jdbc:sqlite:${dbFile.absolutePath}")
    }

    @Test
    fun `laps table enforces unique constraint on session_key driver_number lap_number`() {
        transaction(db) {
            exec("INSERT INTO races(key,name,circuit,year) VALUES(1,'Bahrain GP','Bahrain',2024)")
            exec("INSERT INTO sessions(key,race_key,name,type,year,recorded) VALUES(1,1,'Race','RACE',2024,0)")
            exec("INSERT INTO laps(session_key,driver_number,lap_number,timestamp) VALUES(1,'1',1,'2024-03-02T16:00:00Z')")
            var threw = false
            try {
                exec("INSERT INTO laps(session_key,driver_number,lap_number,timestamp) VALUES(1,'1',1,'2024-03-02T16:01:00Z')")
            } catch (_: Exception) {
                threw = true
            }
            assertTrue(threw, "Unique constraint on laps was not enforced")
        }
    }

    @Test
    fun `driver_standings table enforces unique constraint on year after_round driver_code`() {
        transaction(db) {
            exec("INSERT INTO driver_standings(year,after_round,driver_code,points) VALUES(2024,1,'VER',25)")
            var threw = false
            try {
                exec("INSERT INTO driver_standings(year,after_round,driver_code,points) VALUES(2024,1,'VER',25)")
            } catch (_: Exception) {
                threw = true
            }
            assertTrue(threw, "Unique constraint on driver_standings was not enforced")
        }
    }
}
