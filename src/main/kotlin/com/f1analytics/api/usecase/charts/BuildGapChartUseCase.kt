package com.f1analytics.api.usecase.charts

import com.f1analytics.api.dto.ChartDatasetDto
import com.f1analytics.api.dto.ChartPointDto
import com.f1analytics.api.dto.ChartSectionDto

class BuildGapChartUseCase {

    fun execute(lapContexts: List<LapContext>): ChartSectionDto {
        val byDriver = lapContexts.groupBy { it.driver.number }
        val datasets = byDriver.values.map { ctxList ->
            val driver = ctxList.first().driver
            val sorted = ctxList.sortedBy { it.lap.lapNumber }
            ChartDatasetDto(
                label = driver.code,
                color = "#${driver.teamColor ?: "000000"}",
                points = sorted.map { ctx ->
                    ChartPointDto(
                        x = ctx.lap.lapNumber.toDouble(),
                        y = ctx.gapToLeaderMs?.let { it / 1000.0 } ?: 0.0,
                        pitOutLap = ctx.lap.pitOutLap,
                        pitInLap = ctx.lap.pitInLap
                    )
                }
            )
        }
        return ChartSectionDto(id = "gap", title = "Gap to Leader", type = "gap", datasets = datasets)
    }
}
