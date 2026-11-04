package com.sawn.app.feature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakPolicyTest {

    private val today = 20_000L

    @Test
    fun `a streak recorded today is visible`() {
        assertEquals(7, StreakPolicy.visibleStreak(raw = 7, lastActiveDay = today, today = today))
    }

    @Test
    fun `a streak recorded yesterday is still alive today`() {
        assertEquals(7, StreakPolicy.visibleStreak(raw = 7, lastActiveDay = today - 1, today = today))
    }

    @Test
    fun `a missed day hides the stale streak immediately`() {
        assertEquals(0, StreakPolicy.visibleStreak(raw = 12, lastActiveDay = today - 2, today = today))
    }

    @Test
    fun `a clock rolled backwards never erases progress`() {
        assertEquals(5, StreakPolicy.visibleStreak(raw = 5, lastActiveDay = today + 3, today = today))
    }

    @Test
    fun `nothing recorded yet reads zero`() {
        assertEquals(0, StreakPolicy.visibleStreak(raw = 0, lastActiveDay = -1L, today = today))
        assertEquals(0, StreakPolicy.visibleStreak(raw = 4, lastActiveDay = -1L, today = today))
    }

    @Test
    fun `recording the same day twice does not inflate the streak`() {
        val (first, day) = StreakPolicy.afterActiveDay(raw = 3, lastActiveDay = today - 1, today = today)
        assertEquals(4, first)
        assertEquals(today, day)

        val (again, dayAgain) = StreakPolicy.afterActiveDay(raw = first, lastActiveDay = day, today = today)
        assertEquals(4, again)
        assertEquals(today, dayAgain)
    }

    @Test
    fun `a gap restarts the streak at one`() {
        val (next, day) = StreakPolicy.afterActiveDay(raw = 30, lastActiveDay = today - 5, today = today)
        assertEquals(1, next)
        assertEquals(today, day)
    }

    @Test
    fun `a streak waiting on today's check-in is flagged at risk`() {
        assertTrue(StreakPolicy.isAtRisk(raw = 7, lastActiveDay = today - 1, today = today))
        assertFalse(StreakPolicy.isAtRisk(raw = 7, lastActiveDay = today, today = today))
        assertFalse(StreakPolicy.isAtRisk(raw = 0, lastActiveDay = today - 1, today = today))
    }

    @Test
    fun `a clock rollback keeps the stored length`() {
        val (next, day) = StreakPolicy.afterActiveDay(raw = 9, lastActiveDay = today + 2, today = today)
        assertEquals(9, next)
        assertEquals(today, day)
    }
}
