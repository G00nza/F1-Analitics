package com.f1analytics.api.usecase.charts

import com.f1analytics.api.dto.BestLapDto

class BuildBestLapsUseCase {

    fun execute(lapContexts: List<LapContext>): List<BestLapDto> {
        val bestByDriver = mutableMapOf<String, LapContext>()
        for (ctx in lapContexts) {
            if (ctx.lap.lapTimeMs == null || ctx.lap.pitOutLap || ctx.lap.pitInLap) continue
            val cur = bestByDriver[ctx.lap.driverNumber]
            if (cur == null || ctx.lap.lapTimeMs < cur.lap.lapTimeMs!!) {
                bestByDriver[ctx.lap.driverNumber] = ctx
            }
        }
        return bestByDriver.values
            .sortedBy { it.lap.lapTimeMs }
            .map { ctx ->
                BestLapDto(
                    driverCode = ctx.driver.code,
                    teamColor = "#${ctx.driver.teamColor ?: "000000"}",
                    lapTimeMs = ctx.lap.lapTimeMs!!,
                    compound = ctx.stint.compound,
                    lapNumber = ctx.lap.lapNumber
                )
            }
    }
}
