package com.warden.blocker.block

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LockdownPolicyTest {
    private val now = 1_000_000L

    @Test
    fun zeroUntil_isNeverActive() {
        assertFalse(LockdownPolicy.isActive(0L, now))
    }

    @Test
    fun futureUntil_isActive_beforeIt() {
        assertTrue(LockdownPolicy.isActive(now + 10L, now))
    }

    @Test
    fun expiredUntil_isInactive() {
        assertFalse(LockdownPolicy.isActive(now, now))
        assertFalse(LockdownPolicy.isActive(now - 10L, now))
    }

    @Test
    fun manualValidation_acceptsBounds() {
        assertTrue(LockdownPolicy.validateManual(1L, LockdownPolicy.DurationUnit.MINUTES)!! > 0)
        assertTrue(LockdownPolicy.validateManual(12L, LockdownPolicy.DurationUnit.MONTHS)!! > 0)
        assertTrue(LockdownPolicy.validateManual(6L, LockdownPolicy.DurationUnit.HOURS)!! > 0)
        assertTrue(LockdownPolicy.validateManual(3L, LockdownPolicy.DurationUnit.WEEKS)!! > 0)
        assertTrue(LockdownPolicy.validateManual(2L, LockdownPolicy.DurationUnit.DAYS)!! > 0)
    }

    @Test
    fun manualValidation_rejectsOutOfRange() {
        assertNull(LockdownPolicy.validateManual(0L, LockdownPolicy.DurationUnit.MINUTES))
        assertNull(LockdownPolicy.validateManual(-5L, LockdownPolicy.DurationUnit.DAYS))
        assertNull(LockdownPolicy.validateManual(13L, LockdownPolicy.DurationUnit.MONTHS))
        assertTrue(LockdownPolicy.validateManual(1L, LockdownPolicy.DurationUnit.MONTHS)!! >= 28L * 24 * 3600_000L)
    }

    @Test
    fun monthConversion_isSane() {
        val ms = LockdownPolicy.validateManual(1L, LockdownPolicy.DurationUnit.MONTHS)!!
        assertEquals(30L * 24 * 3600_000L, ms)
    }
}
