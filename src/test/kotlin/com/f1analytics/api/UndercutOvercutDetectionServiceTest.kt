package com.f1analytics.api

import com.f1analytics.api.usecase.DetectUndercutOvercutUseCase
import com.f1analytics.core.domain.model.DriverLiveData
import com.f1analytics.core.domain.model.LapCountData
import com.f1analytics.core.domain.model.LiveSessionState
import com.f1analytics.core.service.UndercutOvercutDetectionService
import com.f1analytics.core.service.UndercutOvercutDetectionService.Companion.GLOBAL_WATCHLIST_KEY
import com.f1analytics.core.service.UndercutOvercutDetectionService.Companion.encodeDriverList
import com.f1analytics.data.db.repository.ExposedSettingsRepository
import com.f1analytics.data.db.repository.ExposedStrategyAlertRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class UndercutOvercutDetectionServiceTest : ViewTestBase() {

    private fun baseState(
        driverData: Map<String, DriverLiveData> = emptyMap(),
        currentLap: Int = 20
    ) = LiveSessionState(
        sessionKey = 9001,
        driverData = driverData,
        lapCount   = LapCountData(current = currentLap, total = 57)
    )

    @Test
    fun storesUndercutAlertWhenDriverTransitionsToInPit() = runBlocking {
        insertRace()
        insertSession()
        insertSessionDriver(9001, "1", "VER")
        insertSessionDriver(9001, "44", "HAM")

        val settingsRepo = ExposedSettingsRepository(db)
        val alertRepo    = ExposedStrategyAlertRepository(db)
        val stateManager = makeStateManager()

        settingsRepo.set(GLOBAL_WATCHLIST_KEY, encodeDriverList(listOf("1", "44")))

        val serviceScope = CoroutineScope(Dispatchers.Default)
        val service = UndercutOvercutDetectionService(
            stateManager           = stateManager,
            settingsRepository     = settingsRepo,
            strategyAlertRepository = alertRepo,
            detectUseCase          = DetectUndercutOvercutUseCase(),
            scope                  = serviceScope
        )
        service.start()

        // Initial state: VER on track behind HAM
        val initialState = baseState(
            driverData = mapOf(
                "1"  to DriverLiveData(position = 2, inPit = false, stintLapStart = 5, currentCompound = "SOFT"),
                "44" to DriverLiveData(position = 1, inPit = false, stintLapStart = 1, currentCompound = "MEDIUM", interval = "1.5"),
            )
        )
        stateManager.injectState(initialState)
        delay(100)

        // VER pits
        val stateAfterPit = initialState.copy(
            driverData = initialState.driverData.toMutableMap().also {
                it["1"] = it["1"]!!.copy(inPit = true)
            }
        )
        stateManager.injectState(stateAfterPit)
        delay(150)

        val alerts = alertRepo.findBySession(9001)
        assertEquals(1, alerts.size)
        assertEquals("UNDERCUT", alerts[0].type)
        assertEquals("1", alerts[0].instigatorNumber)
        assertEquals("44", alerts[0].rivalNumber)
        assertEquals(20, alerts[0].lap)

        serviceScope.cancel()
    }

    @Test
    fun doesNotStoreDuplicateAlertForSamePair() = runBlocking {
        insertRace()
        insertSession()
        insertSessionDriver(9001, "1", "VER")
        insertSessionDriver(9001, "44", "HAM")

        val settingsRepo = ExposedSettingsRepository(db)
        val alertRepo    = ExposedStrategyAlertRepository(db)
        val stateManager = makeStateManager()

        settingsRepo.set(GLOBAL_WATCHLIST_KEY, encodeDriverList(listOf("1", "44")))

        val serviceScope = CoroutineScope(Dispatchers.Default)
        val service = UndercutOvercutDetectionService(
            stateManager           = stateManager,
            settingsRepository     = settingsRepo,
            strategyAlertRepository = alertRepo,
            detectUseCase          = DetectUndercutOvercutUseCase(),
            scope                  = serviceScope
        )
        service.start()

        val initialState = baseState(
            driverData = mapOf(
                "1"  to DriverLiveData(position = 2, inPit = false, stintLapStart = 5, currentCompound = "SOFT"),
                "44" to DriverLiveData(position = 1, inPit = false, stintLapStart = 1, currentCompound = "MEDIUM"),
            )
        )
        stateManager.injectState(initialState)
        delay(100)

        // VER pits — first transition
        val pitState = initialState.copy(
            driverData = initialState.driverData.toMutableMap().also {
                it["1"] = it["1"]!!.copy(inPit = true)
            }
        )
        stateManager.injectState(pitState)
        delay(100)

        // Emit same pit state again (no new transition)
        stateManager.injectState(pitState)
        delay(100)

        val alerts = alertRepo.findBySession(9001)
        assertEquals(1, alerts.size, "Expected exactly one alert, not a duplicate")

        serviceScope.cancel()
    }

    @Test
    fun onlyActsOnWatchedDrivers() = runBlocking {
        insertRace()
        insertSession()
        insertSessionDriver(9001, "1", "VER")
        insertSessionDriver(9001, "44", "HAM")
        insertSessionDriver(9001, "16", "LEC")

        val settingsRepo = ExposedSettingsRepository(db)
        val alertRepo    = ExposedStrategyAlertRepository(db)
        val stateManager = makeStateManager()

        // Only LEC is watched — VER pitting to undercut HAM should not trigger
        settingsRepo.set(GLOBAL_WATCHLIST_KEY, encodeDriverList(listOf("16")))

        val serviceScope = CoroutineScope(Dispatchers.Default)
        val service = UndercutOvercutDetectionService(
            stateManager           = stateManager,
            settingsRepository     = settingsRepo,
            strategyAlertRepository = alertRepo,
            detectUseCase          = DetectUndercutOvercutUseCase(),
            scope                  = serviceScope
        )
        service.start()

        val initialState = baseState(
            driverData = mapOf(
                "1"  to DriverLiveData(position = 2, inPit = false, stintLapStart = 5, currentCompound = "SOFT"),
                "44" to DriverLiveData(position = 1, inPit = false, stintLapStart = 1, currentCompound = "MEDIUM"),
                "16" to DriverLiveData(position = 3, inPit = false, stintLapStart = 3, currentCompound = "HARD"),
            )
        )
        stateManager.injectState(initialState)
        delay(100)

        // VER pits — neither VER nor HAM is watched
        val pitState = initialState.copy(
            driverData = initialState.driverData.toMutableMap().also {
                it["1"] = it["1"]!!.copy(inPit = true)
            }
        )
        stateManager.injectState(pitState)
        delay(150)

        val alerts = alertRepo.findBySession(9001)
        assertEquals(0, alerts.size, "No alert expected — neither VER nor HAM is watched")

        serviceScope.cancel()
    }

    @Test
    fun storesOvercutAlertForAdjacentPairWithOldTyres() = runBlocking {
        insertRace()
        insertSession()
        insertSessionDriver(9001, "1", "VER")
        insertSessionDriver(9001, "44", "HAM")

        val settingsRepo = ExposedSettingsRepository(db)
        val alertRepo    = ExposedStrategyAlertRepository(db)
        val stateManager = makeStateManager()

        settingsRepo.set(GLOBAL_WATCHLIST_KEY, encodeDriverList(listOf("1", "44")))

        val serviceScope = CoroutineScope(Dispatchers.Default)
        val service = UndercutOvercutDetectionService(
            stateManager           = stateManager,
            settingsRepository     = settingsRepo,
            strategyAlertRepository = alertRepo,
            detectUseCase          = DetectUndercutOvercutUseCase(),
            scope                  = serviceScope
        )
        service.start()

        // Initial state (lap 1) — no old tyres yet
        val initialState = baseState(
            currentLap = 1,
            driverData = mapOf(
                "1"  to DriverLiveData(position = 1, inPit = false, stintLapStart = 1, currentCompound = "HARD"),
                "44" to DriverLiveData(position = 2, inPit = false, stintLapStart = 1, currentCompound = "MEDIUM", interval = "1.0"),
            )
        )
        stateManager.injectState(initialState)
        delay(100)

        // Lap 20 — VER has 19 laps on tyres (>15), HAM has 19 laps, gap 1.0s
        val lateState = baseState(
            currentLap = 20,
            driverData = mapOf(
                "1"  to DriverLiveData(position = 1, inPit = false, stintLapStart = 1, currentCompound = "HARD"),
                "44" to DriverLiveData(position = 2, inPit = false, stintLapStart = 1, currentCompound = "MEDIUM", interval = "1.0"),
            )
        )
        stateManager.injectState(lateState)
        delay(150)

        val alerts = alertRepo.findBySession(9001)
        val overcutAlerts = alerts.filter { it.type == "OVERCUT" }
        assertEquals(1, overcutAlerts.size)
        assertEquals(20, overcutAlerts[0].lap)

        serviceScope.cancel()
    }
}
