package com.sawn.core.data

import com.sawn.domain.block.ProtectionMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The engine switches and the mode they imply.
 *
 * The rule that matters: a fresh install starts light (no VPN permission is demanded
 * unasked), while an upgraded install keeps the DNS filter it already had — dropping a
 * running protection on upgrade would be the worst possible surprise.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsStoreEngineModeTest {

    private fun store() = SettingsStore(RuntimeEnvironment.getApplication())

    @Test
    fun `a fresh install starts in the light mode`() = runTest {
        val settings = store()

        settings.initializeEngineMode()

        assertFalse(settings.vpnEnabled.first())
        assertTrue(settings.browserBlockingEnabled.first())
        assertEquals(ProtectionMode.LIGHT, settings.protectionMode.first())
    }

    @Test
    fun `an upgraded install keeps its DNS filter`() = runTest {
        val settings = store()
        // The witness that the app already ran: the onboarding is done.
        settings.setOnboardingDone()

        settings.initializeEngineMode()

        assertTrue(settings.vpnEnabled.first())
        assertEquals(ProtectionMode.FULL, settings.protectionMode.first())
    }

    @Test
    fun `the initialization happens only once`() = runTest {
        val settings = store()
        settings.initializeEngineMode()
        // The user then switches to the full mode.
        settings.setProtectionMode(ProtectionMode.FULL)

        // A later launch must not undo the choice.
        settings.initializeEngineMode()

        assertEquals(ProtectionMode.FULL, settings.protectionMode.first())
    }

    @Test
    fun `the mode drives both engine switches`() = runTest {
        val settings = store()
        settings.initializeEngineMode()

        settings.setProtectionMode(ProtectionMode.FULL)
        assertTrue(settings.vpnEnabled.first())
        assertTrue(settings.browserBlockingEnabled.first())
        assertEquals(ProtectionMode.FULL, settings.protectionMode.first())

        settings.setProtectionMode(ProtectionMode.LIGHT)
        assertFalse(settings.vpnEnabled.first())
        // The light mode keeps the accessibility engines: they are the protection that
        // remains.
        assertTrue(settings.browserBlockingEnabled.first())
        assertEquals(ProtectionMode.LIGHT, settings.protectionMode.first())
    }

    @Test
    fun `the snapshot carries the engine switches`() = runTest {
        val settings = store()
        settings.initializeEngineMode()

        settings.setProtectionMode(ProtectionMode.FULL)
        assertTrue(settings.protectionSnapshot().vpnEnabled)
        assertTrue(settings.protectionSnapshot().browserBlockingEnabled)

        settings.setProtectionMode(ProtectionMode.LIGHT)
        assertFalse(settings.protectionSnapshot().vpnEnabled)
        assertTrue(settings.protectionSnapshot().browserBlockingEnabled)
    }
}
