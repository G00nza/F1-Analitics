package com.f1analytics.data.db.repository

import com.f1analytics.core.domain.port.ReplayEvent
import com.f1analytics.core.domain.port.ReplayRepository
import com.f1analytics.data.db.tables.LapsTable
import com.f1analytics.data.db.tables.PositionSnapshotsTable
import com.f1analytics.data.db.tables.RaceControlMessagesTable
import com.f1analytics.data.db.tables.WeatherSnapshotsTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedReplayRepository(private val db: Database) : ReplayRepository {

    override suspend fun findAllEventsBySession(sessionKey: Int): List<ReplayEvent> =
        withContext(Dispatchers.IO) {
            transaction(db) {
                val events = mutableListOf<ReplayEvent>()

                // Position + gap snapshots
                PositionSnapshotsTable.selectAll()
                    .where { PositionSnapshotsTable.sessionKey eq sessionKey }
                    .forEach { row ->
                        events += ReplayEvent(
                            timestamp = row[PositionSnapshotsTable.timestamp],
                            topic = "position_update",
                            json = buildJsonObject {
                                put("driverNumber", row[PositionSnapshotsTable.driverNumber])
                                row[PositionSnapshotsTable.position]?.let { put("position", it) }
                                row[PositionSnapshotsTable.gapToLeader]?.let { put("gapToLeader", it) }
                                row[PositionSnapshotsTable.interval]?.let { put("interval", it) }
                            }.toString()
                        )
                    }

                // Race control messages
                RaceControlMessagesTable.selectAll()
                    .where { RaceControlMessagesTable.sessionKey eq sessionKey }
                    .forEach { row ->
                        events += ReplayEvent(
                            timestamp = row[RaceControlMessagesTable.timestamp],
                            topic = "race_control",
                            json = buildJsonObject {
                                put("message", row[RaceControlMessagesTable.message])
                                row[RaceControlMessagesTable.flag]?.let { put("flag", it) }
                                row[RaceControlMessagesTable.scope]?.let { put("scope", it) }
                                row[RaceControlMessagesTable.lapNumber]?.let { put("lapNumber", it) }
                            }.toString()
                        )
                    }

                // Weather snapshots (one event per snapshot but sampled every 10th to reduce volume)
                WeatherSnapshotsTable.selectAll()
                    .where { WeatherSnapshotsTable.sessionKey eq sessionKey }
                    .orderBy(WeatherSnapshotsTable.timestamp, SortOrder.ASC)
                    .forEachIndexed { index, row ->
                        if (index % 10 == 0) {  // ~1 per minute at 10s intervals
                            events += ReplayEvent(
                                timestamp = row[WeatherSnapshotsTable.timestamp],
                                topic = "weather",
                                json = buildJsonObject {
                                    row[WeatherSnapshotsTable.airTemp]?.let { put("airTemp", it) }
                                    row[WeatherSnapshotsTable.trackTemp]?.let { put("trackTemp", it) }
                                    row[WeatherSnapshotsTable.humidity]?.let { put("humidity", it) }
                                    row[WeatherSnapshotsTable.rainfall]?.let { put("rainfall", it) }
                                }.toString()
                            )
                        }
                    }

                // Completed laps (only those with a recorded lap time)
                LapsTable.selectAll()
                    .where { LapsTable.sessionKey eq sessionKey }
                    .forEach { row ->
                        val lapTimeMs = row[LapsTable.lapTimeMs] ?: return@forEach
                        events += ReplayEvent(
                            timestamp = row[LapsTable.timestamp],
                            topic = "lap_completed",
                            json = buildJsonObject {
                                put("driverNumber", row[LapsTable.driverNumber])
                                put("lapNumber", row[LapsTable.lapNumber])
                                put("lapTimeMs", lapTimeMs)
                                row[LapsTable.sector1Ms]?.let { put("sector1Ms", it) }
                                row[LapsTable.sector2Ms]?.let { put("sector2Ms", it) }
                                row[LapsTable.sector3Ms]?.let { put("sector3Ms", it) }
                                put("isPersonalBest", row[LapsTable.isPersonalBest])
                            }.toString()
                        )
                    }

                events.sortedBy { it.timestamp }
            }
        }
}
