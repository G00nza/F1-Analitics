package com.f1analytics.core.service

import com.f1analytics.core.domain.port.LiveTimingClient
import com.f1analytics.data.persistence.LiveTimingPersistenceService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

private val logger = KotlinLogging.logger {}

/** F-00.1: Consumes the SharedFlow from the WsClient and feeds both the
 *  persistence layer and the in-memory state manager. */
class LiveSessionService(
    private val wsClient: LiveTimingClient,
    private val persistenceService: LiveTimingPersistenceService,
    private val stateManager: LiveSessionStateManager,
    private val scope: CoroutineScope
) {
    fun start() {
        scope.launch {
            logger.info { "LiveSessionService started" }
            wsClient.messages.collect { message ->
                val ts = Clock.System.now()
                val sessionKey = stateManager.currentSessionKey
                if (sessionKey != -1) {
                    persistenceService.persist(sessionKey, message, ts)
                }
                stateManager.merge(message, ts)
            }
        }
    }
}
