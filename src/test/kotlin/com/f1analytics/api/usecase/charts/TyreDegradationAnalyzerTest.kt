package com.f1analytics.api.usecase.charts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    // ── calculateDegRate ──────────────────────────────────────────────────────

    @Test
    fun `calculateDegRate returns null when fewer than 2 laps`() {
        val single = listOf(lap(lapNumber = 1, lapTimeMs = 90000))
        assertNull(TyreDegradationAnalyzer.calculateDegRate(single))
        assertNull(TyreDegradationAnalyzer.calculateDegRate(emptyList()))
    }

    @Test
    fun `calculateDegRate computes rate as ms increase per lap`() {
        // 3 laps: 90000, 90200, 90400 → delta per lap = (90400 - 90000) / 2 = 200
        val laps = listOf(
            lap(lapNumber = 1, lapTimeMs = 90000),
            lap(lapNumber = 2, lapTimeMs = 90200),
            lap(lapNumber = 3, lapTimeMs = 90400),
        )
        assertEquals(200.0, TyreDegradationAnalyzer.calculateDegRate(laps))
    }

    @Test
    fun `calculateDegRate caps negative rate to zero when laps improve`() {
        val laps = listOf(
            lap(lapNumber = 1, lapTimeMs = 91000),
            lap(lapNumber = 2, lapTimeMs = 90500),
            lap(lapNumber = 3, lapTimeMs = 90000),
        )
        assertEquals(0.0, TyreDegradationAnalyzer.calculateDegRate(laps))
    }

    @Test
    fun `calculateDegRate sorts by lapNumber before computing`() {
        // Input is out of order: lap 3 first, lap 1 last
        val laps = listOf(
            lap(lapNumber = 3, lapTimeMs = 90400),
            lap(lapNumber = 1, lapTimeMs = 90000),
            lap(lapNumber = 2, lapTimeMs = 90200),
        )
        // Should sort → first=90000, last=90400 → (90400-90000)/2 = 200
        assertEquals(200.0, TyreDegradationAnalyzer.calculateDegRate(laps))
    }
}
