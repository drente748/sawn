package com.sawn.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.sawn.app.billing.Entitlements
import com.sawn.app.billing.FreeEntitlements
import com.sawn.domain.block.AccessController
import com.sawn.domain.block.BlockEngine
import com.sawn.app.data.BlockRepository
import com.sawn.app.data.SettingsStore
import com.sawn.app.data.SawnDatabase
import com.sawn.app.usage.UsageStatsHelper

/**
 * Poor-man's DI: a single container of app-scoped singletons reachable from
 * services and activities via [SawnApp.container]. Keeps v1 dependency-free;
 * swap for Hilt later if the graph grows.
 */
class SawnApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
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

class AppContainer(app: Application) {
    val settings = SettingsStore(app)
    val repository = BlockRepository(SawnDatabase.get(app).blockDao())
    val blockEngine = BlockEngine(repository, settings)
    val entitlements: Entitlements = FreeEntitlements() // paid tier arrives later; ads check this first
    val accessController = AccessController(
        repository, blockEngine,
        usageMinutesToday = { pkg -> UsageStatsHelper.todayUsageMinutesFor(app, pkg) },
        trustedNow = { settings.trustedNow() },
        pauseDefeatsToday = { settings.pauseDefeatsToday(settings.trustedNow()) },
    )
}

/** Convenience accessor from any Context. */
val android.content.Context.sawnContainer: AppContainer
    get() = (applicationContext as SawnApp).container
