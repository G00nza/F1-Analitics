package com.f1analytics.core.service

import com.f1analytics.api.usecase.DetectUndercutOvercutUseCase
import com.f1analytics.core.domain.model.LiveSessionState
import com.f1analytics.core.domain.port.SettingsRepository
import com.f1analytics.core.domain.port.StrategyAlertRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}
private val json = Json { ignoreUnknownKeys = true }

/**
 * Observes the live session state and automatically stores undercut/overcut
 * alerts whenever relevant driver transitions are detected.
 */
class UndercutOvercutDetectionService(
    private val stateManager: LiveSessionStateManager,
    private val settingsRepository: SettingsRepository,
    private val strategyAlertRepository: StrategyAlertRepository,
    private val detectUseCase: DetectUndercutOvercutUseCase,
    private val scope: CoroutineScope
) {
    fun start() {
        scope.launch {
            logger.info { "UndercutOvercutDetectionService started" }

            var previousState: LiveSessionState? = null
            var currentSessionKey: Int? = null
            val firedUndercuts: MutableSet<Pair<String, String>> = mutableSetOf()
            val firedOvercuts:  MutableSet<Pair<String, String>> = mutableSetOf()

            stateManager.stateFlow.collect { state ->
                if (state == null) {
                    previousState = null
                    return@collect
                }

                if (state.sessionKey != currentSessionKey) {
                    firedUndercuts.clear()
                    firedOvercuts.clear()
                    currentSessionKey = state.sessionKey
                    previousState = null
                }

                val prev = previousState
                if (prev != null) {
                    val watchlist = activeWatchlist(state.sessionKey)
                    val alerts = detectUseCase.detect(state, prev, watchlist, firedUndercuts, firedOvercuts)
                    alerts.forEach { strategyAlertRepository.save(it) }
                    if (alerts.isNotEmpty()) {
                        logger.info { "Stored ${alerts.size} strategy alert(s) for session ${state.sessionKey}" }
                    }
                }

                previousState = state
            }
        }
    }

    private suspend fun activeWatchlist(sessionKey: Int): Set<String> {
        val raw = settingsRepository.get(sessionWatchlistKey(sessionKey))
            ?: settingsRepository.get(GLOBAL_WATCHLIST_KEY)
        return parseDriverList(raw)
    }

    companion object {
        const val GLOBAL_WATCHLIST_KEY = "watchlist_global"
        fun sessionWatchlistKey(sessionKey: Int) = "watchlist_session_$sessionKey"

        fun parseDriverList(json: String?): Set<String> {
            if (json.isNullOrBlank()) return emptySet()
            return runCatching {
                Json.decodeFromString(ListSerializer(String.serializer()), json).toSet()
            }.getOrElse { emptySet() }
        }

        fun encodeDriverList(drivers: List<String>): String =
            json.encodeToString(ListSerializer(String.serializer()), drivers)
    }
}
