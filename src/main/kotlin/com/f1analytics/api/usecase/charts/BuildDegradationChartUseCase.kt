package com.f1analytics.api.usecase.charts

import com.f1analytics.api.dto.ChartDatasetDto
import com.f1analytics.api.dto.ChartPointDto
import com.f1analytics.api.dto.ChartSectionDto
import com.f1analytics.core.domain.model.DriverEntry
import com.f1analytics.core.domain.model.Stint

class BuildDegradationChartUseCase {

    fun execute(
        lapContexts: List<LapContext>,
        stints: List<Stint>,
        driverByNumber: Map<String, DriverEntry>
    ): ChartSectionDto {
        val lapsByStintKey = lapContexts.groupBy { "${it.lap.driverNumber}-${it.stint.stintNumber}" }
        val datasets = stints.mapNotNull { stint ->
            val ctxList = lapsByStintKey["${stint.driverNumber}-${stint.stintNumber}"]
                ?: return@mapNotNull null
            val driver = driverByNumber[stint.driverNumber] ?: return@mapNotNull null

            val validLapNums = TyreDegradationAnalyzer.validLaps(ctxList.map { it.lap })
                .map { it.lapNumber }.toSet()
            val validLaps = ctxList
                .filter { it.lap.lapNumber in validLapNums }
                .sortedBy { it.lap.lapNumber }

            if (!TyreDegradationAnalyzer.isLongRun(validLaps.size)) return@mapNotNull null

            val baseLapMs = validLaps.first().lap.lapTimeMs!!
            ChartDatasetDto(
                label = "${driver.code} S${stint.stintNumber} (${stint.compound})",
                color = "#${driver.teamColor ?: "000000"}",
                compound = stint.compound,
                points = validLaps.mapIndexed { index, ctx ->
                    ChartPointDto(
                        x = (index + 1).toDouble(),
                        y = (ctx.lap.lapTimeMs!! - baseLapMs) / 1000.0,
                        lapNumber = ctx.lap.lapNumber,
                        compound = ctx.stint.compound
                    )
                }
            )
        }
        return ChartSectionDto(id = "degradation", title = "Tyre Degradation", type = "degradation", datasets = datasets)
    }
}
