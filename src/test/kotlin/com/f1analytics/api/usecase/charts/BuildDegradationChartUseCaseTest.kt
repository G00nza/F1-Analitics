package com.f1analytics.api.usecase.charts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildDegradationChartUseCaseTest {

    private val useCase = BuildDegradationChartUseCase()

    @Test
    fun `returns section with correct id, title and type`() {
        val section = useCase.execute(emptyList(), emptyList(), emptyMap())

        assertEquals("degradation",    section.id)
        assertEquals("Tyre Degradation", section.title)
        assertEquals("degradation",    section.type)
    }

    @Test
    fun `returns empty datasets when no stints`() {
        assertTrue(useCase.execute(emptyList(), emptyList(), emptyMap()).datasets.isEmpty())
    }

    @Test
    fun `skips stints with 5 or fewer valid laps`() {
        val ver = driver()
        val s = stint()
        val contexts = (1..5).map { i ->
            lapContext(lap = lap(lapNumber = i, lapTimeMs = 90000 + i * 100), driver = ver, stint = s)
        }

        val datasets = useCase.execute(contexts, listOf(s), mapOf("1" to ver)).datasets

        assertTrue(datasets.isEmpty())
    }

    @Test
    fun `includes stints with more than 5 valid laps`() {
        val ver = driver()
        val s = stint()
        val contexts = (1..6).map { i ->
            lapContext(lap = lap(lapNumber = i, lapTimeMs = 90000 + i * 100), driver = ver, stint = s)
        }

        val datasets = useCase.execute(contexts, listOf(s), mapOf("1" to ver)).datasets

        assertEquals(1, datasets.size)
    }

    @Test
    fun `calculates delta from first valid lap in stint`() {
        val ver = driver()
        val s = stint(compound = "MEDIUM")
        val contexts = (1..6).map { i ->
            lapContext(lap = lap(lapNumber = i, lapTimeMs = 90000 + (i - 1) * 500), driver = ver, stint = s)
        }

        val points = useCase.execute(contexts, listOf(s), mapOf("1" to ver)).datasets.single().points

        assertEquals(0.0, points[0].y)   // base lap: delta = 0
        assertEquals(0.5, points[1].y)   // 500ms / 1000 = 0.5s
        assertEquals(1.0, points[2].y)
    }

    @Test
    fun `x coordinate is 1-based index within stint`() {
        val ver = driver()
        val s = stint()
        val contexts = (1..6).map { i ->
            lapContext(lap = lap(lapNumber = i, lapTimeMs = 90000), driver = ver, stint = s)
        }

        val points = useCase.execute(contexts, listOf(s), mapOf("1" to ver)).datasets.single().points

        assertEquals(1.0, points[0].x)
        assertEquals(2.0, points[1].x)
    }

    @Test
    fun `excludes pit-out and pit-in laps from valid lap count and delta calculation`() {
        val ver = driver()
        val s = stint(lapStart = 1, lapEnd = 8)
        val contexts = listOf(
            lapContext(lap = lap(lapNumber = 1, lapTimeMs = 80000, pitOutLap = true), driver = ver, stint = s),
            lapContext(lap = lap(lapNumber = 2, lapTimeMs = 90000),                   driver = ver, stint = s),
            lapContext(lap = lap(lapNumber = 3, lapTimeMs = 90500),                   driver = ver, stint = s),
            lapContext(lap = lap(lapNumber = 4, lapTimeMs = 91000),                   driver = ver, stint = s),
            lapContext(lap = lap(lapNumber = 5, lapTimeMs = 91500),                   driver = ver, stint = s),
            lapContext(lap = lap(lapNumber = 6, lapTimeMs = 92000),                   driver = ver, stint = s),
            lapContext(lap = lap(lapNumber = 7, lapTimeMs = 92500),                   driver = ver, stint = s),
            lapContext(lap = lap(lapNumber = 8, lapTimeMs = 80000, pitInLap = true),  driver = ver, stint = s),
        )

        val points = useCase.execute(contexts, listOf(s), mapOf("1" to ver)).datasets.single().points

        // 6 valid laps (2–7), pit-out and pit-in excluded
        assertEquals(6, points.size)
        assertEquals(0.0, points[0].y)          // lap 2 is the base
        assertEquals(0.5, points[1].y)          // lap 3: +500ms
    }

    @Test
    fun `dataset label includes driver code, stint number and compound`() {
        val ver = driver(code = "VER")
        val s = stint(stintNumber = 2, compound = "HARD")
        val contexts = (1..6).map { i ->
            lapContext(lap = lap(lapNumber = i, lapTimeMs = 90000), driver = ver, stint = s)
        }

        val dataset = useCase.execute(contexts, listOf(s), mapOf("1" to ver)).datasets.single()

        assertEquals("VER S2 (HARD)", dataset.label)
        assertEquals("HARD",          dataset.compound)
    }

    @Test
    fun `lapNumber on each point reflects the original lap number`() {
        val ver = driver()
        val s = stint(lapStart = 5, lapEnd = 12)
        val contexts = (5..10).map { i ->
            lapContext(lap = lap(lapNumber = i, lapTimeMs = 90000), driver = ver, stint = s)
        }

        val points = useCase.execute(contexts, listOf(s), mapOf("1" to ver)).datasets.single().points

        assertEquals(5, points[0].lapNumber)
        assertEquals(6, points[1].lapNumber)
    }
}
