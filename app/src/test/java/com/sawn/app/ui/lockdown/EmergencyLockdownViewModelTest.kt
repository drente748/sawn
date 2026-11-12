package com.sawn.app.ui.lockdown

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import com.sawn.app.system.EmergencyLockdownEnforcer
import com.sawn.core.data.SettingsStore
import com.sawn.domain.lockdown.EmergencyLockdown
import com.sawn.domain.lockdown.LockdownReason
import com.sawn.domain.lockdown.LockdownSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The emergency lockdown, from the interface's side.
 *
 * The promise is one-sided: the button starts a lock, and nothing in the interface —
 * not the user, not the clock — can take it back before the timer is done.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [26])
class EmergencyLockdownViewModelTest {

    private val context: Application = RuntimeEnvironment.getApplication()
    private lateinit var settings: SettingsStore

    @Before
    fun setUp() {
        settings = SettingsStore(context)
    }

    @After
    fun tearDown() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    /**
     * Waits for the stored session to satisfy [predicate].
     *
     * The view model writes through the main looper and the store commits on its own
     * thread, so the looper is pumped between the reads — otherwise the coroutine that
     * writes would never get past its first suspension.
     */
    private fun awaitSession(predicate: (LockdownSession?) -> Boolean): LockdownSession? {
        repeat(100) {
            shadowOf(Looper.getMainLooper()).idle()
            val session = runBlocking { settings.emergencyLockdownSession.first() }
            if (predicate(session)) return session
            Thread.sleep(20)
        }
        return null
    }

    @Test
    fun `startPanic activates a lock that cannot be cancelled`() {
        val vm = EmergencyLockdownViewModel(context, settings)

        vm.startPanic()

        val session = awaitSession { it != null }
        assertNotNull("the session must be persisted", session)
        assertTrue(
            "the lock must be active",
            EmergencyLockdown.isActive(session, SystemClock.elapsedRealtime()),
        )
        assertFalse(
            "the lock must not be cancellable",
            EmergencyLockdown.canCancel(session!!, SystemClock.elapsedRealtime()),
        )
    }

    @Test
    fun `extending the lock only makes it longer`() {
        val vm = EmergencyLockdownViewModel(context, settings)

        vm.startPanic()
        val before = awaitSession { it != null }!!
        assertNotNull(before)

        vm.extend(15)

        val after = awaitSession { it != null && it.endsAt > before.endsAt }
        assertNotNull("the extension must be stored", after)
        assertTrue("the deadline must move later", after!!.endsAt > before.endsAt)
        assertTrue(
            "the lock must still be active",
            EmergencyLockdown.isActive(after, SystemClock.elapsedRealtime()),
        )
    }

    @Test
    fun `the lock is written with a monotonic deadline and a wall fallback`() = runBlocking {
        EmergencyLockdownEnforcer.start(
            settings = settings,
            context = context,
            durationMinutes = 30,
            reason = LockdownReason.EMERGENCY,
        )

        val session = settings.emergencyLockdownSession.first { it != null }!!
        assertEquals(30, session.durationMinutes)
        assertEquals(session.durationMinutes * 60_000L, session.endsAt - session.startedAt)
        assertTrue("the wall deadline must be written too", session.wallEndsAt > 0L)
        assertTrue(
            "the lock must be active right after it started",
            EmergencyLockdown.isActive(session, SystemClock.elapsedRealtime()),
        )
    }
}
