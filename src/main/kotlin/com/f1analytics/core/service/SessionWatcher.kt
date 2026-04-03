package com.f1analytics.core.service

import com.f1analytics.api.SseManager
import com.f1analytics.api.SessionStatusEvent
import com.f1analytics.core.domain.port.SessionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val logger = KotlinLogging.logger {}

/** F-00.4: Polls the DB for upcoming sessions and kicks off loading when one starts. */
class SessionWatcher(
    private val sessionRepo: SessionRepository,
    private val stateManager: LiveSessionStateManager,
    private val sseManager: SseManager,
    private val scope: CoroutineScope
) {
    fun start() {
        scope.launch {
            logger.info { "SessionWatcher started" }
            while (isActive) {
                val upcoming = sessionRepo.findNextUpcoming()
                if (upcoming == null) {
                    delay(5.minutes)
                    continue
                }

                val dateStart = upcoming.dateStart
                if (dateStart == null) {
                    delay(5.minutes)
                    continue
                }

                val startsIn: Duration = dateStart - Clock.System.now()

                when {
                    startsIn > 10.minutes -> {
                        logger.info { "Next session '${upcoming.name}' starts in $startsIn — sleeping" }
                        delay(startsIn - 10.minutes)
                    }

                    startsIn > Duration.ZERO -> {
                        logger.info { "Session '${upcoming.name}' starts in $startsIn — notifying browsers" }
                        sseManager.broadcast(
                            SessionStatusEvent.StartingSoon(
                                sessionName    = upcoming.name,
                                sessionKey     = upcoming.key,
                                startsInSeconds = startsIn.inWholeSeconds.toDouble()
                            )
                        )
                        delay(startsIn)
                    }

                    else -> {
                        // Session should be active — load state from DB
                        logger.info { "Session '${upcoming.name}' should be active — loading from DB" }
                        stateManager.loadFromDb(upcoming.key)
                        delay(1.minutes)
                    }
                }
            }
        }
    }
}
