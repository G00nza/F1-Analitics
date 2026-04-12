package com.f1analytics.api.usecase.charts

import com.f1analytics.core.domain.port.DriverPositionSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildPositionsChartUseCaseTest {

    private val useCase = BuildPositionsChartUseCase()

    @Test
    fun `returns section with correct id, title and type`() {
        val section = useCase.execute(emptyList(), emptyMap())

        assertEquals("positions",            section.id)
        assertEquals("Position Progression", section.title)
        assertEquals("positions",            section.type)
    }

    @Test
    fun `returns empty datasets when no drivers`() {
        assertTrue(useCase.execute(emptyList(), emptyMap()).datasets.isEmpty())
    }

    @Test
    fun `skips drivers with no position snapshots`() {
        val drivers = listOf(driver(number = "1", code = "VER"))

        val datasets = useCase.execute(drivers, emptyMap()).datasets

        assertTrue(datasets.isEmpty())
    }

    @Test
    fun `maps position snapshots to points with 1-based x index`() {
        val drivers = listOf(driver(number = "1", code = "VER"))
        val snapshots = mapOf(
            "1" to listOf(
                DriverPositionSnapshot("1", position = 3, gapToLeader = null, interval = null),
                DriverPositionSnapshot("1", position = 2, gapToLeader = null, interval = null)
            )
        )

        val points = useCase.execute(drivers, snapshots).datasets.single().points

        assertEquals(2,   points.size)
        assertEquals(1.0, points[0].x)
        assertEquals(3.0, points[0].y)
        assertEquals(2.0, points[1].x)
        assertEquals(2.0, points[1].y)
    }

    @Test
    fun `null position maps to null y`() {
        val drivers = listOf(driver(number = "1"))
        val snapshots = mapOf(
            "1" to listOf(DriverPositionSnapshot("1", position = null, gapToLeader = null, interval = null))
        )

        val point = useCase.execute(drivers, snapshots).datasets.single().points.single()

        assertEquals(null, point.y)
    }

    @Test
    fun `dataset label and color come from driver`() {
        val drivers = listOf(driver(number = "1", code = "VER", teamColor = "3671C6"))
        val snapshots = mapOf(
            "1" to listOf(DriverPositionSnapshot("1", position = 1, gapToLeader = null, interval = null))
        )

        val dataset = useCase.execute(drivers, snapshots).datasets.single()

        assertEquals("VER",     dataset.label)
        assertEquals("#3671C6", dataset.color)
    }
}
