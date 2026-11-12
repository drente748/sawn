package com.sawn.app.tile

import android.app.Application
import android.os.Looper
import com.sawn.core.data.SettingsStore
import com.sawn.domain.lockdown.LockdownReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The panic tile.
 *
 * A tap in the shade starts the lock. The tile can only *start* one — there is no
 * way to stop it from here, which is the whole point of the feature.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [26])
class SawnLockdownTileServiceTest {

    private val context: Application = RuntimeEnvironment.getApplication()
    private lateinit var settings: SettingsStore

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        settings = SettingsStore(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `a tap starts the panic lock`() = runBlocking {
        val service = Robolectric.buildService(SawnLockdownTileService::class.java).create().get()
        service.settingsOverride = settings

        service.onClick()
        shadowOf(Looper.getMainLooper()).idle()

        // The write lands on the DataStore's own dispatcher — wait for the session.
        val session = settings.emergencyLockdownSession.first { it != null }
        assertNotNull(session)
        assertEquals(SawnLockdownTileService.PANIC_MINUTES, session!!.durationMinutes)
        assertEquals(LockdownReason.QS_TILE, session.reason)
    }

    @Test
    fun `the tile starts the shortest lock the app offers`() {
        assertEquals(15, SawnLockdownTileService.PANIC_MINUTES)
    }
}
