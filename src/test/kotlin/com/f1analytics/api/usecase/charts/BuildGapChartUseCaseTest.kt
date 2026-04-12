package com.f1analytics.api.usecase.charts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildGapChartUseCaseTest {

    private val useCase = BuildGapChartUseCase()

    @Test
    fun `returns section with correct id, title and type`() {
        val section = useCase.execute(emptyList())

        assertEquals("gap",            section.id)
        assertEquals("Gap to Leader",  section.title)
        assertEquals("gap",            section.type)
    }

    @Test
    fun `returns empty datasets when no laps`() {
        assertTrue(useCase.execute(emptyList()).datasets.isEmpty())
    }

    @Test
    fun `converts gap from ms to seconds`() {
        val contexts = listOf(lapContext(gapToLeaderMs = 2500))

        val point = useCase.execute(contexts).datasets.single().points.single()

        assertEquals(2.5, point.y)
    }

    @Test
    fun `uses 0_0 when gapToLeaderMs is null`() {
        val contexts = listOf(lapContext(gapToLeaderMs = null))

        val point = useCase.execute(contexts).datasets.single().points.single()

        assertEquals(0.0, point.y)
    }

    @Test
    fun `x coordinate equals lap number`() {
        val contexts = listOf(lapContext(lap = lap(lapNumber = 7)))

        val point = useCase.execute(contexts).datasets.single().points.single()

        assertEquals(7.0, point.x)
    }

    @Test
    fun `maps pitOutLap and pitInLap on points`() {
        val contexts = listOf(
            lapContext(lap = lap(lapNumber = 1, pitOutLap = true)),
            lapContext(lap = lap(lapNumber = 2, pitInLap = true))
        )

        val points = useCase.execute(contexts).datasets.single().points

        assertTrue(points[0].pitOutLap)
        assertTrue(points[1].pitInLap)
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
}
