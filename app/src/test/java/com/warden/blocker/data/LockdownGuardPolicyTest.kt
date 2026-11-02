package com.warden.blocker.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockdownGuardPolicyTest {
    private val now = 1_000_000L

    @Test
    fun noLockdown_allowsDisable() {
        assertFalse(LockdownGuardPolicy.blocksDisable(0L, now))
    }

    @Test
    fun activeLockdown_blocksDisable() {
        assertTrue(LockdownGuardPolicy.blocksDisable(now + 1000L, now))
    }

    @Test
    fun expiredLockdown_allowsDisable() {
        assertFalse(LockdownGuardPolicy.blocksDisable(now - 1000L, now))
    }

    @Test
    fun activeLockdown_blocksPinRemoval() {
        assertTrue(LockdownGuardPolicy.blocksPinRemoval(now + 1000L, now))
    }

    @Test
    fun noLockdown_allowsPinRemoval() {
        assertFalse(LockdownGuardPolicy.blocksPinRemoval(0L, now))
    }

    @Test
    fun activeLockdown_blocksStrictChange() {
        assertTrue(LockdownGuardPolicy.blocksStrictChange(now + 1000L, now))
    }
}
