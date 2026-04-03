package com.f1analytics.data.persistence

import com.f1analytics.core.config.AppConfig
import com.f1analytics.core.domain.model.TimingMessage
import com.f1analytics.core.domain.port.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Instant

private val logger = KotlinLogging.logger {}

class LiveTimingPersistenceService(
    private val sessionRepo: SessionRepository,
    private val driverRepo: SessionDriverRepository,
    private val lapRepo: LapRepository,
    private val stintRepo: StintRepository,
    private val pitRepo: PitStopRepository,
    private val raceControlRepo: RaceControlRepository,
    private val weatherRepo: WeatherRepository,
    private val positionRepo: PositionRepository,
    private val telemetryRepo: TelemetryRepository,
    private val config: AppConfig
) {
    suspend fun persist(sessionKey: Int, msg: TimingMessage, timestamp: Instant) {
        try {
            when (msg) {
                is TimingMessage.DriverListMsg    -> driverRepo.upsertAll(sessionKey, msg.drivers)
                is TimingMessage.TimingDataMsg    -> {
                    lapRepo.upsertDeltas(sessionKey, msg.deltas, timestamp)
                    positionRepo.insertSnapshot(sessionKey, msg.deltas, timestamp)
                }
                is TimingMessage.TimingAppDataMsg -> stintRepo.upsertDeltas(sessionKey, msg.deltas)
                is TimingMessage.RaceControlMsg   -> raceControlRepo.insert(sessionKey, msg, timestamp)
                is TimingMessage.WeatherMsg       -> weatherRepo.insert(sessionKey, msg.weather, timestamp)
                is TimingMessage.CarDataMsg       -> {
                    if (config.storeTelemetry) telemetryRepo.insertBatch(sessionKey, msg.entries)
                }
                is TimingMessage.SessionStatusMsg -> sessionRepo.updateStatus(sessionKey, msg.status)
                else -> { /* SessionInfo and others handled at session initialisation */ }
            }
        } catch (e: Exception) {
            logger.error { "Failed to persist ${msg::class.simpleName}: ${e.message}" }
            // DB errors must not interrupt the live timing flow
        }
    }
}
