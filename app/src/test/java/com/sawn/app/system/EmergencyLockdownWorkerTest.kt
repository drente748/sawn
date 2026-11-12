package com.sawn.app.system

import android.app.Application
import android.os.SystemClock
import com.sawn.core.data.SettingsStore
import com.sawn.domain.lockdown.EmergencyLockdown
import com.sawn.domain.lockdown.LockdownReason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The end of a panic lock.
 *
 * The worker is the only thing that may end the lock — the user cannot. When it runs,
 * the session is gone and the protection goes back to whatever it was.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [26])
class EmergencyLockdownWorkerTest {

    private val context: Application = RuntimeEnvironment.getApplication()
    private lateinit var settings: SettingsStore

    @Before
    fun setUp() {
        settings = SettingsStore(context)
    }

    @Test
    fun `the worker clears the session when the timer runs out`() = runBlocking {
        settings.saveLockdownSession(
            EmergencyLockdown.start(
                durationMinutes = 15,
                now = SystemClock.elapsedRealtime(),
                reason = LockdownReason.PANIC,
            ),
        )
        assertNotNull("the lock must be running before the timer", settings.emergencyLockdownSession.first())

        EmergencyLockdownWorker.endLockdown(settings)

        assertNull("the lock must be over", settings.emergencyLockdownSession.first())
    }

    @Test
    fun `the session survives until the worker runs`() = runBlocking {
        val session = EmergencyLockdown.start(
            durationMinutes = 30,
            now = SystemClock.elapsedRealtime(),
            reason = LockdownReason.QS_TILE,
        )
        settings.saveLockdownSession(session)

        assertEquals(session, settings.emergencyLockdownSession.first())
    }
}
