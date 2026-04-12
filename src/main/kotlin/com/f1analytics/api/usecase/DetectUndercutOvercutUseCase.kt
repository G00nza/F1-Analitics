package com.f1analytics.api.usecase

import com.f1analytics.core.domain.model.LiveSessionState
import com.f1analytics.core.domain.model.StrategyAlert
import kotlinx.datetime.Clock

/**
 * Pure detection logic — no side effects, no DB access.
 * Called by UndercutOvercutDetectionService on each state update.
 */
class DetectUndercutOvercutUseCase {

    companion object {
        private const val UNDERCUT_POSITION_GAP = 3   // check rivals within N positions ahead
        private const val OVERCUT_STINT_AGE_MIN = 15  // tyres must be at least this many laps old
        private const val OVERCUT_GAP_THRESHOLD = 3.0 // seconds between adjacent pair
    }

    /**
     * @param state         current live state
     * @param previousState previous live state (null on first update)
     * @param watchlist     driver numbers to monitor
     * @param firedUndercuts mutable dedup set for undercut pairs (instigator, rival) — modified in place
     * @param firedOvercuts  mutable dedup set for overcut pairs (candidate, target) — modified in place
     */
    fun detect(
        state: LiveSessionState,
        previousState: LiveSessionState,
        watchlist: Set<String>,
        firedUndercuts: MutableSet<Pair<String, String>>,
        firedOvercuts: MutableSet<Pair<String, String>>
    ): List<StrategyAlert> {
        if (watchlist.isEmpty()) return emptyList()

        val alerts = mutableListOf<StrategyAlert>()
        val now = Clock.System.now()
        val currentLap = state.lapCount?.current

        // ── Undercut: event-driven when a driver transitions inPit false→true ──
        val newlyInPit = state.driverData.entries
            .filter { (num, data) ->
                data.inPit && previousState.driverData[num]?.inPit == false
            }
            .map { it.key }

        for (instigator in newlyInPit) {
            val instigatorPos = state.driverData[instigator]?.position ?: continue

            // Rivals that are ahead, on track, within position gap, and at least one is watched
            val rivals = state.driverData.entries
                .filter { (num, data) ->
                    num != instigator &&
                    !data.inPit &&
                    data.position != null &&
                    data.position < instigatorPos &&
                    (instigatorPos - data.position) <= UNDERCUT_POSITION_GAP &&
                    (instigator in watchlist || num in watchlist)
                }

            for ((rival, rivalData) in rivals) {
                val pairKey = Pair(instigator, rival)
                if (pairKey in firedUndercuts) continue
                firedUndercuts.add(pairKey)

                val gap = rivalData.interval?.toDoubleOrNull()
                alerts.add(
                    StrategyAlert(
                        id               = 0,
                        sessionKey       = state.sessionKey,
                        lap              = currentLap,
                        type             = "UNDERCUT",
                        instigatorNumber = instigator,
                        rivalNumber      = rival,
                        gapSeconds       = gap,
                        predictedOutcome = buildUndercutPrediction(instigator, rival, gap),
                        confirmedOutcome = null,
                        timestamp        = now
                    )
                )
            }
        }

        // ── Overcut: periodic poll on adjacent position pairs ──
        if (currentLap != null) {
            val onTrack = state.driverData.entries
                .filter { it.value.position != null && !it.value.inPit }
                .sortedBy { it.value.position }

            for (i in 0 until onTrack.size - 1) {
                val (frontNum, frontData) = onTrack[i]
                val (backNum, backData) = onTrack[i + 1]

                if (frontNum !in watchlist && backNum !in watchlist) continue

                val frontStintAge = frontData.stintLapStart?.let { currentLap - it } ?: continue
                val backStintAge  = backData.stintLapStart?.let { currentLap - it } ?: continue

                if (maxOf(frontStintAge, backStintAge) < OVERCUT_STINT_AGE_MIN) continue

                val gap = backData.interval?.toDoubleOrNull() ?: continue
                if (gap > OVERCUT_GAP_THRESHOLD) continue

                // The driver with older tyres is the overcut candidate (staying out to build a gap)
                val (overcutDriver, overcutTarget) = if (frontStintAge > backStintAge)
                    Pair(frontNum, backNum)
                else
                    Pair(backNum, frontNum)

                val pairKey = Pair(overcutDriver, overcutTarget)
                if (pairKey in firedOvercuts) continue
                firedOvercuts.add(pairKey)

                alerts.add(
                    StrategyAlert(
                        id               = 0,
                        sessionKey       = state.sessionKey,
                        lap              = currentLap,
                        type             = "OVERCUT",
                        instigatorNumber = overcutDriver,
                        rivalNumber      = overcutTarget,
                        gapSeconds       = gap,
                        predictedOutcome = null,
                        confirmedOutcome = null,
                        timestamp        = now
                    )
                )
            }
        }

        return alerts
    }

    private fun buildUndercutPrediction(instigator: String, rival: String, gap: Double?): String =
        if (gap != null)
            "If $instigator laps faster for ~3 laps, undercut on $rival may succeed (gap: ${"%.1f".format(gap)}s)"
        else
            "Potential undercut opportunity on $rival"
}
