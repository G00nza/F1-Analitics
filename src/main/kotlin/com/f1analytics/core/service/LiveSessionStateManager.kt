package com.f1analytics.core.service

import com.f1analytics.core.domain.model.*
import com.f1analytics.core.domain.port.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

private val logger = KotlinLogging.logger {}

class LiveSessionStateManager(
    private val driverRepo: SessionDriverRepository,
    private val lapRepo: LapRepository,
    private val stintRepo: StintRepository,
    private val raceControlRepo: RaceControlRepository,
    private val weatherRepo: WeatherRepository,
    private val positionRepo: PositionRepository
) {
    private val _stateFlow = MutableStateFlow<LiveSessionState?>(null)
    val stateFlow: StateFlow<LiveSessionState?> = _stateFlow.asStateFlow()

    var currentSessionKey: Int = -1
        private set

    /** Rebuilds the full state from DB — equivalent to having listened live from the start. */
    suspend fun loadFromDb(session: Session) {
        currentSessionKey = session.key
        val drivers   = driverRepo.findBySession(session.key)
        val laps      = lapRepo.findBySession(session.key)
        val stints    = stintRepo.findBySession(session.key)
        val raceCtrl  = raceControlRepo.findBySession(session.key)
        val weather   = weatherRepo.findLatest(session.key)
        val positions = positionRepo.findLatestPositions(session.key)

        _stateFlow.value = buildState(session.key, drivers, laps, stints, raceCtrl, weather, positions)
            .copy(sessionName = session.name)
        logger.info { "State loaded from DB for session ${session.key}: ${drivers.size} drivers, ${laps.size} laps" }
    }

    /** Applies an incremental delta received from the bridge (already persisted to DB). */
    fun merge(message: TimingMessage, timestamp: Instant = Clock.System.now()) {
        _stateFlow.update { current ->
            when (message) {
                is TimingMessage.DriverListMsg      -> current?.withDrivers(message.drivers)
                is TimingMessage.TimingDataMsg      -> current?.withTimingDeltas(message.deltas)
                is TimingMessage.TimingAppDataMsg   -> current?.withStintDeltas(message.deltas)
                is TimingMessage.RaceControlMsg     -> current?.withRaceControl(message, timestamp)
                is TimingMessage.WeatherMsg         -> current?.copy(weather = message.weather)
                is TimingMessage.TrackStatusMsg     -> current?.copy(trackStatus = message.status)
                is TimingMessage.SessionStatusMsg   -> current?.copy(sessionStatus = message.status)
                is TimingMessage.ExtrapolatedClockMsg -> current?.copy(timeRemaining = message.remaining)
                is TimingMessage.LapCountMsg        -> current?.copy(lapCount = LapCountData(message.current, message.total))
                is TimingMessage.SessionInfoMsg     -> current?.copy(
                    sessionName  = message.name,
                    circuitName  = message.circuit,
                    officialName = message.officialName
                )
                is TimingMessage.HeartbeatMsg       -> current  // no state change
                else                                -> current
            }
        }
    }
}

private fun buildState(
    sessionKey: Int,
    drivers: List<DriverEntry>,
    laps: List<Lap>,
    stints: List<Stint>,
    raceCtrl: List<RaceControlEntry>,
    weather: WeatherData?,
    positions: Map<String, DriverPositionSnapshot>
): LiveSessionState {
    val lapsByDriver   = laps.groupBy { it.driverNumber }
    val stintsByDriver = stints.groupBy { it.driverNumber }
    val allDriverNums  = (drivers.map { it.number } + lapsByDriver.keys + stintsByDriver.keys + positions.keys).toSet()

    val driverData = allDriverNums.associateWith { num ->
        val driverLaps  = lapsByDriver[num] ?: emptyList()
        val driverStints = stintsByDriver[num] ?: emptyList()
        val pos         = positions[num]

        val lastLap     = driverLaps.maxByOrNull { it.lapNumber }
        val bestLap     = driverLaps.filter { it.lapTimeMs != null }.minByOrNull { it.lapTimeMs!! }
        val currentStint = driverStints.maxByOrNull { it.stintNumber }

        DriverLiveData(
            position        = pos?.position,
            gapToLeader     = pos?.gapToLeader,
            interval        = pos?.interval,
            lastLapTimeMs   = lastLap?.lapTimeMs,
            bestLapTimeMs   = bestLap?.lapTimeMs,
            lapNumber       = lastLap?.lapNumber,
            currentCompound = currentStint?.compound,
            isNewTire       = currentStint?.isNew,
            stintLapStart   = currentStint?.lapStart,
            stintNumber     = currentStint?.stintNumber
        )
    }

    return LiveSessionState(
        sessionKey            = sessionKey,
        drivers               = drivers.associateBy { it.number },
        driverData            = driverData,
        raceControlMessages   = raceCtrl,
        weather               = weather
    )
}
