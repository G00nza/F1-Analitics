package com.f1analytics.data.db.repository

import com.f1analytics.data.db.DatabaseFactory
import com.f1analytics.data.db.tables.RacesTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

abstract class RepositoryTestBase {

    protected lateinit var db: Database
    private lateinit var dbFile: File

    @BeforeTest
    fun setupDb() {
        dbFile = File.createTempFile("f1repotest", ".db")
        db = DatabaseFactory.init("jdbc:sqlite:${dbFile.absolutePath}")
        transaction(db) {
            RacesTable.insert {
                it[key]     = 1
                it[name]    = "Bahrain Grand Prix"
                it[circuit] = "Bahrain International Circuit"
                it[year]    = 2024
            }
        }
    }

    @AfterTest
    fun teardownDb() {
        dbFile.delete()
        File("${dbFile.absolutePath}-wal").delete()
        File("${dbFile.absolutePath}-shm").delete()
    }
}
