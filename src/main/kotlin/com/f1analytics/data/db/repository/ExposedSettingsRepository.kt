package com.f1analytics.data.db.repository

import com.f1analytics.core.domain.port.SettingsRepository
import com.f1analytics.data.db.tables.SettingsTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class ExposedSettingsRepository(private val db: Database) : SettingsRepository {

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        transaction(db) {
            SettingsTable.selectAll()
                .where { SettingsTable.key eq key }
                .firstOrNull()
                ?.get(SettingsTable.value)
        }
    }

    override suspend fun set(key: String, value: String): Unit = withContext(Dispatchers.IO) {
        transaction(db) {
            val exists = SettingsTable.selectAll()
                .where { SettingsTable.key eq key }
                .count() > 0
            if (exists) {
                SettingsTable.update({ SettingsTable.key eq key }) {
                    it[SettingsTable.value] = value
                }
            } else {
                SettingsTable.insert {
                    it[SettingsTable.key] = key
                    it[SettingsTable.value] = value
                }
            }
        }
        Unit
    }
}
