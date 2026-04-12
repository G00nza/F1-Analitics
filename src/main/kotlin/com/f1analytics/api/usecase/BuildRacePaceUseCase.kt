package com.f1analytics.api.usecase

import com.f1analytics.api.dto.RacePaceDto
import com.f1analytics.api.dto.TeamPaceRowDto

class BuildRacePaceUseCase(
    private val buildTyreDegradationUseCase: BuildTyreDegradationUseCase,
) {

    companion object {
        const val WARNING =
            "Estimated based on FP long runs. Conditions may vary. Laps under Safety Car or yellow flags have been excluded."
    }

    suspend fun execute(sessionKey: Int): RacePaceDto {
        val degradation = buildTyreDegradationUseCase.execute(sessionKey)

        if (!degradation.hasStintData) {
            return RacePaceDto(
                sessionKey = sessionKey,
                warning = WARNING,
                hasStintData = false,
                teams = emptyList(),
            )
        }

        val teamBestAvg = degradation.longRuns
            .filter { it.team != null }
            .groupBy { it.team!! }
            .mapValues { (_, runs) -> runs.minOf { it.avgLapMs } }
            .entries
            .sortedBy { it.value }

        val leaderAvg = teamBestAvg.firstOrNull()?.value

        val teams = teamBestAvg.mapIndexed { index, (team, avg) ->
            TeamPaceRowDto(
                rank = index + 1,
                team = team,
                avgLapMs = avg,
                gapToLeaderMs = if (index == 0) null else avg - leaderAvg!!,
            )
        }

        return RacePaceDto(
            sessionKey = sessionKey,
            warning = WARNING,
            hasStintData = true,
            teams = teams,
        )
    }
}
