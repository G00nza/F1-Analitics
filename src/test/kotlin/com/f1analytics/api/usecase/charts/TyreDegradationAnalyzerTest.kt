package com.f1analytics.api.usecase.charts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TyreDegradationAnalyzerTest {

    @Test
    fun `validLaps excludes pit-out laps`() {
        val laps = listOf(
            lap(lapNumber = 1, lapTimeMs = 90000, pitOutLap = true),
            lap(lapNumber = 2, lapTimeMs = 90000),
        )
        val valid = TyreDegradationAnalyzer.validLaps(laps)
        assertEquals(1, valid.size)
        assertEquals(2, valid.single().lapNumber)
    }

    @Test
    fun `validLaps excludes pit-in laps`() {
        val laps = listOf(
            lap(lapNumber = 1, lapTimeMs = 90000),
            lap(lapNumber = 2, lapTimeMs = 90000, pitInLap = true),
        )
        val valid = TyreDegradationAnalyzer.validLaps(laps)
        assertEquals(1, valid.size)
        assertEquals(1, valid.single().lapNumber)
    }

    @Test
    fun `validLaps excludes laps with null lapTime`() {
        val laps = listOf(
            lap(lapNumber = 1, lapTimeMs = null),
            lap(lapNumber = 2, lapTimeMs = 90000),
        )
        val valid = TyreDegradationAnalyzer.validLaps(laps)
        assertEquals(1, valid.size)
        assertEquals(2, valid.single().lapNumber)
    }

    @Test
    fun `validLaps excludes laps slower than 107 percent of fastest`() {
        // fastest = 90000ms → threshold = 96300ms
        val laps = listOf(
            lap(lapNumber = 1, lapTimeMs = 90000),
            lap(lapNumber = 2, lapTimeMs = 96300), // exactly at threshold → included
            lap(lapNumber = 3, lapTimeMs = 96301), // just over → excluded
        )
        val valid = TyreDegradationAnalyzer.validLaps(laps)
        assertEquals(2, valid.size)
        assertTrue(valid.any { it.lapNumber == 1 })
        assertTrue(valid.any { it.lapNumber == 2 })
        assertFalse(valid.any { it.lapNumber == 3 })
    }

    @Test
    fun `validLaps returns empty list when all laps are invalid`() {
        val laps = listOf(
            lap(lapNumber = 1, lapTimeMs = null),
            lap(lapNumber = 2, lapTimeMs = 90000, pitOutLap = true),
        )
        assertTrue(TyreDegradationAnalyzer.validLaps(laps).isEmpty())
    }

    @Test
    fun `validLaps returns empty list when input is empty`() {
        assertTrue(TyreDegradationAnalyzer.validLaps(emptyList()).isEmpty())
    }

    @Test
    fun `isLongRun returns true for more than 5 laps`() {
        assertTrue(TyreDegradationAnalyzer.isLongRun(6))
    }

    @Test
    fun `isLongRun returns false for exactly 5 laps`() {
        assertFalse(TyreDegradationAnalyzer.isLongRun(5))
    }

    @Test
    fun `isLongRun returns false for fewer than 5 laps`() {
        assertFalse(TyreDegradationAnalyzer.isLongRun(3))
    }
}
