package com.f1analytics.api.usecase

import com.f1analytics.core.domain.model.RaceControlEntry
import kotlinx.datetime.Instant
import kotlin.math.roundToInt

object SafetyCarScoring {

    private const val SC_PIT_COST_SECONDS = 10.0

    val DRY_COMPOUNDS = setOf("SOFT", "MEDIUM", "HARD")

    /**
     * Compute a 0-100 score representing how beneficial a pit stop would be under SC.
     *
     * Components:
     *   40% — tyre age    (full at 20+ laps)
     *   35% — laps remaining (full at 25+ laps, zero at ≤5)
     *   25% — free stop   (gap to car behind vs SC pit cost of 10s)
     *
     * Penalty: if the driver has no unused dry compound available, multiply by 0.7.
     */
    fun computeScore(
        tyreAgeLaps: Int?,
        lapsRemaining: Int?,
        gapToCarBehindSeconds: Double?,
        hasNewTyresAvailable: Boolean
    ): Int {
        val age = tyreAgeLaps ?: return 0
        val laps = lapsRemaining ?: return 0

        val tyreScore     = (age * 5).coerceAtMost(100).toDouble()
        val lapsScore     = ((laps - 5) * 5).coerceIn(0, 100).toDouble()
        val freeStopScore = if (gapToCarBehindSeconds != null)
            (gapToCarBehindSeconds / SC_PIT_COST_SECONDS * 100).coerceIn(0.0, 100.0)
        else 0.0

        val raw = tyreScore * 0.4 + lapsScore * 0.35 + freeStopScore * 0.25
        val penalized = if (hasNewTyresAvailable) raw else raw * 0.7
        return penalized.roundToInt().coerceIn(0, 100)
    }

    fun scoreToMessage(score: Int): String = when {
        score >= 76 -> "Pit now — free stop"
        score >= 51 -> "Pit recommended"
        score >= 26 -> "Consider pitting"
        else        -> "Stay out"
    }

    /** Returns true if driver has at least one unused dry compound in this race. */
    fun hasNewTyresAvailable(usedCompounds: Set<String>): Boolean =
        DRY_COMPOUNDS.any { it !in usedCompounds }

    // ── SC event detection ─────────────────────────────────────────────────

    data class ScWindow(
        val startLap: Int?,
        val startTimestamp: Instant,
        val endLap: Int?,
        val endTimestamp: Instant?
    )

    /**
     * Groups RC messages into SC windows.
     * A window starts on the first "SC" flag and ends on the next "CLEAR" flag.
     * If no CLEAR follows, endLap/endTimestamp are null (SC still active).
     */
    fun detectScWindows(messages: List<RaceControlEntry>): List<ScWindow> {
        val windows = mutableListOf<ScWindow>()
        var openWindow: Pair<Int?, Instant>? = null   // (startLap, startTimestamp)

        for (msg in messages) {
            when {
                msg.flag == "SC" && openWindow == null ->
                    openWindow = msg.lap to msg.timestamp

                msg.flag == "CLEAR" && openWindow != null -> {
                    windows.add(ScWindow(openWindow.first, openWindow.second, msg.lap, msg.timestamp))
                    openWindow = null
                }
            }
        }
        // Still-active SC (no closing CLEAR yet)
        if (openWindow != null) {
            windows.add(ScWindow(openWindow.first, openWindow.second, null, null))
        }
        return windows
    }
}
