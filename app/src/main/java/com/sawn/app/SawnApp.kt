package com.sawn.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.sawn.core.data.BlockRepository
import com.sawn.core.data.SettingsStore
import com.sawn.domain.block.AccessController
import com.sawn.domain.block.BlockEngine
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent

@HiltAndroidApp
class SawnApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        // NetGuard-style watchdog: periodic backstop that posts/clears the health
        // notification when the accessibility service dies silently. 15-minute minimum
        // is WorkManager's floor; the real-time signal is the service's onUnbind.
        com.sawn.app.system.GuardHealthWorker.schedule(this)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            VPN_CHANNEL_ID,
            getString(R.string.vpn_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val VPN_CHANNEL_ID = "sawn_blocking"

        /** App-scoped reference for notifiers that run outside an Activity context. */
        lateinit var instance: SawnApp
            private set
    }
}

/**
 * Legacy entry point for the pre-Hilt call sites that still reach the graph through a
 * Context. Reads the SAME Hilt singletons — there is only one wiring path.
 * Call sites migrate to proper injection (@Inject / hiltViewModel) one screen at a time.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface LegacyContainer {
    val settings: SettingsStore
    val repository: BlockRepository
    val blockEngine: BlockEngine
    val accessController: AccessController
}

/** Convenience accessor from any Context. */
val android.content.Context.sawnContainer: LegacyContainer
    get() = EntryPointAccessors.fromApplication(
        applicationContext as SawnApp,
        LegacyContainer::class.java,
    )
