package com.f1analytics.api.usecase.charts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildBestLapsUseCaseTest {

    private val useCase = BuildBestLapsUseCase()

    @Test
    fun `returns empty list when no laps`() {
        val result = useCase.execute(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns one best lap per driver sorted by lapTimeMs ascending`() {
        val ver = driver(number = "1", code = "VER")
        val ham = driver(number = "44", code = "HAM")
        val contexts = listOf(
            lapContext(lap = lap(driverNumber = "1",  lapNumber = 1, lapTimeMs = 90000), driver = ver),
            lapContext(lap = lap(driverNumber = "1",  lapNumber = 2, lapTimeMs = 88000), driver = ver),
            lapContext(lap = lap(driverNumber = "44", lapNumber = 1, lapTimeMs = 89000), driver = ham),
        )

        val result = useCase.execute(contexts)

        assertEquals(2, result.size)
        assertEquals("VER", result[0].driverCode)
        assertEquals(88000, result[0].lapTimeMs)
        assertEquals("HAM", result[1].driverCode)
        assertEquals(89000, result[1].lapTimeMs)
    }

    @Test
    fun `excludes laps with null lapTimeMs`() {
        val contexts = listOf(
            lapContext(lap = lap(lapTimeMs = null)),
            lapContext(lap = lap(lapNumber = 2, lapTimeMs = 90000))
        )

        val result = useCase.execute(contexts)

        assertEquals(1, result.size)
        assertEquals(90000, result[0].lapTimeMs)
    }

    @Test
    fun `excludes pit-out laps`() {
        val contexts = listOf(
            lapContext(lap = lap(lapNumber = 1, lapTimeMs = 80000, pitOutLap = true)),
            lapContext(lap = lap(lapNumber = 2, lapTimeMs = 90000))
        )

        val result = useCase.execute(contexts)

        assertEquals(1, result.size)
        assertEquals(90000, result[0].lapTimeMs)
    }

    @Test
    fun `excludes pit-in laps`() {
        val contexts = listOf(
            lapContext(lap = lap(lapNumber = 1, lapTimeMs = 80000, pitInLap = true)),
            lapContext(lap = lap(lapNumber = 2, lapTimeMs = 90000))
        )

        val result = useCase.execute(contexts)

        assertEquals(1, result.size)
        assertEquals(90000, result[0].lapTimeMs)
    }

    @Test
    fun `maps driver code, team color, compound and lap number`() {
        val contexts = listOf(
            lapContext(
                lap = lap(lapNumber = 3, lapTimeMs = 90000),
                driver = driver(code = "VER", teamColor = "3671C6"),
                stint = stint(compound = "MEDIUM")
            )
        )

        val result = useCase.execute(contexts)

        val best = result.single()
        assertEquals("VER",     best.driverCode)
        assertEquals("#3671C6", best.teamColor)
        assertEquals("MEDIUM",  best.compound)
        assertEquals(3,         best.lapNumber)
        assertEquals(90000,     best.lapTimeMs)
    }

    @Test
    fun `uses fallback color when teamColor is null`() {
        val contexts = listOf(
            lapContext(driver = driver(teamColor = null))
        )

        val result = useCase.execute(contexts)

        assertEquals("#000000", result.single().teamColor)
    }
}
