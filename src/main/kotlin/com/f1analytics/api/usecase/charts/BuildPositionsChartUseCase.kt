package com.f1analytics.api.usecase.charts

import com.f1analytics.api.dto.ChartDatasetDto
import com.f1analytics.api.dto.ChartPointDto
import com.f1analytics.api.dto.ChartSectionDto
import com.f1analytics.core.domain.model.DriverEntry
import com.f1analytics.core.domain.port.DriverPositionSnapshot

class BuildPositionsChartUseCase {

    fun execute(
        drivers: List<DriverEntry>,
        driverPositions: Map<String, List<DriverPositionSnapshot>>
    ): ChartSectionDto {
        val datasets = drivers.mapNotNull { driver ->
            val positions = driverPositions[driver.number]?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            ChartDatasetDto(
                label = driver.code,
                color = "#${driver.teamColor ?: "000000"}",
                points = positions.mapIndexed { index, snapshot ->
                    ChartPointDto(x = (index + 1).toDouble(), y = snapshot.position?.toDouble())
                }
            )
        }
        return ChartSectionDto(id = "positions", title = "Position Progression", type = "positions", datasets = datasets)
    }
}
