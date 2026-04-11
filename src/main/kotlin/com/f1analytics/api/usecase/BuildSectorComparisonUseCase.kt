package com.f1analytics.api.usecase

import com.f1analytics.api.dto.SectorComparisonDto
import com.f1analytics.api.dto.SectorRowDto
import com.f1analytics.core.domain.port.LapRepository
import com.f1analytics.core.domain.port.SessionDriverRepository

class BuildSectorComparisonUseCase(
    private val lapRepository: LapRepository,
    private val sessionDriverRepository: SessionDriverRepository,
) {

    suspend fun execute(sessionKeyA: Int, sessionKeyB: Int, driverNumber: String): SectorComparisonDto? {
        val bestLapsA = lapRepository.findBestLaps(sessionKeyA)
        val bestLapsB = lapRepository.findBestLaps(sessionKeyB)

        val lapA = bestLapsA[driverNumber]
        val lapB = bestLapsB[driverNumber]

        if (lapA == null && lapB == null) return null

        val driverCode = resolveDriverCode(driverNumber, sessionKeyA, sessionKeyB)

        val sectorAs = listOf(lapA?.sector1Ms, lapA?.sector2Ms, lapA?.sector3Ms)
        val sectorBs = listOf(lapB?.sector1Ms, lapB?.sector2Ms, lapB?.sector3Ms)

        val sectors = (1..3).map { i ->
            val a = sectorAs[i - 1]
            val b = sectorBs[i - 1]
            val delta = if (a != null && b != null) b - a else null
            SectorRowDto(sector = i, sessionAMs = a, sessionBMs = b, deltaMs = delta)
        }

        val sectorsWithDelta = sectors.filter { it.deltaMs != null }

        val totalDeltaMs = if (sectorsWithDelta.size == 3) sectorsWithDelta.sumOf { it.deltaMs!! } else null

        val mostImprovedSector = sectorsWithDelta.minByOrNull { it.deltaMs!! }?.sector
        val leastImprovedSector = sectorsWithDelta.maxByOrNull { it.deltaMs!! }?.sector

        return SectorComparisonDto(
            sessionKeyA = sessionKeyA,
            sessionKeyB = sessionKeyB,
            driverNumber = driverNumber,
            driverCode = driverCode,
            sectors = sectors,
            totalDeltaMs = totalDeltaMs,
            mostImprovedSector = mostImprovedSector,
            leastImprovedSector = leastImprovedSector,
        )
    }

    private suspend fun resolveDriverCode(
        driverNumber: String,
        sessionKeyA: Int,
        sessionKeyB: Int,
    ): String {
        val fromA = sessionDriverRepository.findBySession(sessionKeyA)
            .firstOrNull { it.number == driverNumber }
        if (fromA != null) return fromA.code

        val fromB = sessionDriverRepository.findBySession(sessionKeyB)
            .firstOrNull { it.number == driverNumber }
        return fromB?.code ?: driverNumber
    }
}
