package com.f1analytics.api.usecase.charts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuildLapTimesChartUseCaseTest {

    private val useCase = BuildLapTimesChartUseCase()

    @Test
    fun `returns section with correct id, title and type`() {
        val section = useCase.execute(emptyList())

        assertEquals("lapTimes",             section.id)
        assertEquals("Lap Time Progression", section.title)
        assertEquals("lapTimes",             section.type)
    }

    @Test
    fun `returns empty datasets when no laps`() {
        assertTrue(useCase.execute(emptyList()).datasets.isEmpty())
    }

    @Test
    fun `creates one dataset per driver`() {
        val contexts = listOf(
            lapContext(lap = lap(driverNumber = "1"),  driver = driver(number = "1",  code = "VER")),
            lapContext(lap = lap(driverNumber = "44"), driver = driver(number = "44", code = "HAM"))
        )

        val datasets = useCase.execute(contexts).datasets

        assertEquals(2, datasets.size)
        val labels = datasets.map { it.label }.toSet()
        assertEquals(setOf("VER", "HAM"), labels)
    }

    @Test
    fun `converts lap time from ms to seconds`() {
        val contexts = listOf(lapContext(lap = lap(lapTimeMs = 90000)))

        val point = useCase.execute(contexts).datasets.single().points.single()

        assertEquals(90.0, point.y)
    }

    @Test
    fun `inserts null break point before pit-out lap`() {
        val contexts = listOf(
            lapContext(lap = lap(lapNumber = 1, lapTimeMs = 90000)),
            lapContext(lap = lap(lapNumber = 2, lapTimeMs = 88000, pitOutLap = true))
        )

        val points = useCase.execute(contexts).datasets.single().points

        // lap 1, null break at 1.5, lap 2
        assertEquals(3, points.size)
        assertEquals(1.5,  points[1].x)
        assertNull(points[1].y)
    }

    @Test
    fun `maps pitOutLap, pitInLap, isPersonalBest and compound on points`() {
        val contexts = listOf(
            lapContext(
                lap = lap(lapNumber = 1, pitInLap = true, isPersonalBest = true),
                stint = stint(compound = "HARD")
            )
        )

        val point = useCase.execute(contexts).datasets.single().points.single()

        assertTrue(point.pitInLap)
        assertTrue(point.isPersonalBest)
        assertEquals("HARD", point.compound)
    }

    @Test
    fun `x coordinate equals lap number`() {
        val contexts = listOf(lapContext(lap = lap(lapNumber = 5)))

        val point = useCase.execute(contexts).datasets.single().points.single()

        assertEquals(5.0, point.x)
    }
}
