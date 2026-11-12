package com.sawn.domain.block

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootRestorePolicyTest {

    private fun state(
        userDisabledProtection: Boolean = false,
        lockdownActive: Boolean = false,
        filterActive: Boolean = false,
        vpnEnabled: Boolean = true,
    ) = BootRestoreState(userDisabledProtection, lockdownActive, filterActive, vpnEnabled)

    @Test
    fun liveLockdown_isRestoredWhole_afterReboot() {
        val decision = BootRestorePolicy.decide(state(lockdownActive = true, filterActive = true))
        assertTrue(decision.reassertCoreFlags)
        assertTrue(decision.startFilter)
        assertTrue(decision.recordProtectedDay)
        assertTrue(decision.warnMissingAdmin)
        assertTrue(decision.actsAtAll)
    }

    @Test
    fun liveLockdown_winsEvenIfTheStopFlagWasLeftSet() {
        val decision = BootRestorePolicy.decide(
            state(userDisabledProtection = true, lockdownActive = true, filterActive = true),
        )
        assertTrue(decision.reassertCoreFlags)
        assertTrue(decision.startFilter)
        assertTrue(decision.warnMissingAdmin)
    }

    @Test
    fun realStop_doesNothingOnReboot() {
        val decision = BootRestorePolicy.decide(state(userDisabledProtection = true))
        assertFalse(decision.actsAtAll)
        assertFalse(decision.startFilter)
        assertFalse(decision.recordProtectedDay)
    }

    @Test
    fun ordinaryProtection_restartsFilterWithoutTouchingSwitches() {
        val decision = BootRestorePolicy.decide(state(filterActive = true))
        assertFalse(decision.reassertCoreFlags)
        assertTrue(decision.startFilter)
        assertTrue(decision.recordProtectedDay)
        assertFalse(decision.warnMissingAdmin)
    }

    @Test
    fun idleInstall_staysIdle() {
        val decision = BootRestorePolicy.decide(state())
        assertFalse(decision.actsAtAll)
    }

    @Test
    fun decisionIsStableForRepeatedBoots() {
        val first = BootRestorePolicy.decide(state(lockdownActive = true, filterActive = true))
        val second = BootRestorePolicy.decide(state(lockdownActive = true, filterActive = true))
        assertEquals(first, second)
    }

    @Test
    fun lightMode_doesNotStartTheDnsFilterAfterReboot() {
        // There is no VPN to restart in the light mode — the accessibility engines carry
        // the protection, and asking for the VPN permission after a reboot would be the
        // app demanding a permission the user deliberately refused.
        val decision = BootRestorePolicy.decide(state(filterActive = true, vpnEnabled = false))
        assertFalse(decision.startFilter)
        // The day still counts as protected: the accessibility engines were enforcing it.
        assertTrue(decision.recordProtectedDay)
    }

    @Test
    fun liveLockdown_forcesTheDnsFilterBackEvenInLightMode() {
        // The lock was written by the user's past self and demands the full stack.
        val decision = BootRestorePolicy.decide(
            state(lockdownActive = true, filterActive = true, vpnEnabled = false),
        )
        assertTrue(decision.startFilter)
        assertTrue(decision.reassertCoreFlags)
        assertTrue(decision.recordProtectedDay)
    }

    @Test
    fun fullMode_startsTheDnsFilterAfterReboot() {
        val decision = BootRestorePolicy.decide(state(filterActive = true, vpnEnabled = true))
        assertTrue(decision.startFilter)
        assertTrue(decision.recordProtectedDay)
    }

    @Test
    fun aRealStop_winsOverTheLightModeToo() {
        val decision = BootRestorePolicy.decide(
            state(userDisabledProtection = true, filterActive = true, vpnEnabled = false),
        )
        assertFalse(decision.actsAtAll)
        assertFalse(decision.recordProtectedDay)
    }
}
