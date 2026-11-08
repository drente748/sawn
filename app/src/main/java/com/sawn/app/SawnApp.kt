package com.sawn.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Looper
import android.widget.Toast
import com.sawn.app.util.LocaleUtils
import com.sawn.core.data.BlockRepository
import com.sawn.core.data.SettingsStore
import com.sawn.domain.block.AccessController
import com.sawn.domain.block.BlockEngine
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class SawnApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        // معالج تعطل عام: أي استثناء غير مرصود يُعرض رسالة بدلاً من إغلاق التطبيق المفاجئ.
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            Looper.prepare()
            Toast.makeText(
                this@SawnApp,
                "خطأ مؤقت — يُرجى إعادة فتح التطبيق:\n${throwable.message ?: throwable::class.simpleName}",
                Toast.LENGTH_LONG,
            ).show()
            Looper.loop()
        }

        // اللغة تُطبّق قبل أي UI يُبنى. أي فشل لا يوقف التطبيق.
        runBlocking {
            runCatching {
                val locale = EntryPointAccessors.fromApplication(
                    this@SawnApp,
                    LegacyContainer::class.java,
                ).settings.locale.first()
                LocaleUtils.apply(this@SawnApp, locale)
            }
        }

        runCatching { createNotificationChannel() }
        runCatching { com.sawn.app.system.GuardHealthWorker.schedule(this) }
        runCatching { com.sawn.app.system.NightGuardWorker.schedule(this) }
        runCatching { com.sawn.app.system.NudgesWorker.schedule(this) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
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
    val stats: com.sawn.domain.repo.StatsRepository
    val database: com.sawn.core.data.SawnDatabase
}

/** Convenience accessor from any Context. */
val android.content.Context.sawnContainer: LegacyContainer
    get() = EntryPointAccessors.fromApplication(
        applicationContext as SawnApp,
        LegacyContainer::class.java,
    )
