package com.f1analytics.api.usecase.charts

import com.f1analytics.api.dto.ChartDatasetDto
import com.f1analytics.api.dto.ChartPointDto
import com.f1analytics.api.dto.ChartSectionDto

class BuildLapTimesChartUseCase {

    fun execute(lapContexts: List<LapContext>): ChartSectionDto {
        val byDriver = lapContexts.groupBy { it.driver.number }
        val datasets = byDriver.values.map { ctxList ->
            val driver = ctxList.first().driver
            val sorted = ctxList.sortedBy { it.lap.lapNumber }
            val points = mutableListOf<ChartPointDto>()
            for (ctx in sorted) {
                if (ctx.lap.pitOutLap) {
                    points.add(ChartPointDto(x = ctx.lap.lapNumber - 0.5, y = null))
                }
                points.add(
                    ChartPointDto(
                        x = ctx.lap.lapNumber.toDouble(),
                        y = ctx.lap.lapTimeMs?.let { it / 1000.0 },
                        pitOutLap = ctx.lap.pitOutLap,
                        pitInLap = ctx.lap.pitInLap,
                        isPersonalBest = ctx.lap.isPersonalBest,
                        compound = ctx.stint.compound
                    )
                )
            }
            ChartDatasetDto(label = driver.code, color = "#${driver.teamColor ?: "000000"}", points = points)
        }
        return ChartSectionDto(id = "lapTimes", title = "Lap Time Progression", type = "lapTimes", datasets = datasets)
    }
}
