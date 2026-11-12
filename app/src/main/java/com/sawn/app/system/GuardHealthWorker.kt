package com.sawn.app.system

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sawn.app.block.SawnEngines
import com.sawn.app.util.PermissionsHelper
import com.sawn.app.vpn.SawnVpnService
import com.sawn.app.vpn.VpnController
import com.sawn.app.sawnContainer
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * The watchdog: it notices when a protection layer has stopped.
 *
 * Two cadences run:
 *  - the **periodic** check every 15 minutes, which is the floor WorkManager allows and
 *    is enough for the ordinary case;
 *  - a **strict-mode** check every [STRICT_INTERVAL_MINUTES] minutes, because a user who
 *    locked themselves in deserves to know within minutes — not a quarter of an hour —
 *    that a layer died. It is a self-rescheduling one-time request, because WorkManager
 *    refuses a periodic interval below 15 minutes.
 *
 * What counts as "a layer died" depends on the mode: the DNS filter is only *expected*
 * when it is part of the protection. In the light mode its absence is not a failure and
 * must not raise a notification the user cannot act on.
 */
class GuardHealthWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.sawnContainer
        val settings = container.settings
        // المستخدم أوقف الحماية من داخل التطبيق — "المُوقِف الوحيد": لا تنبيهات/إيقاظ.
        if (settings.userDisabledProtection.first()) {
            return Result.success()
        }
        val lockdownActive = settings.isLockdownActiveNow()
        val needsScreening = lockdownActive || (
            settings.masterEnabled.first() &&
                (
                    settings.socialGuard.first() ||
                        settings.enabledFeatureKeys.first().isNotEmpty() ||
                        settings.pornGuard.first()
                    )
            )
        val accessibilityOn = PermissionsHelper.isAccessibilityEnabled(applicationContext)

        // سلسلة الأيام لا تعتمد على فتح الواجهة: طالما الحجب فعّالٌ فهذا اليوم محميّ.
        if (container.blockEngine.isBlockingActiveNow()) {
            settings.recordActiveDay(
                SawnEngines.active(
                    settings = settings,
                    vpnRunning = SawnVpnService.running,
                    accessibilityEnabled = accessibilityOn,
                ),
            )
        }
        if (needsScreening && !accessibilityOn) {
            GuardHealthNotifier.notifyAccessibilityDown(applicationContext)
        } else {
            GuardHealthNotifier.clearAccessibility(applicationContext)
        }

        // A lockdown is only meaningful if the uninstall guard and the filter are both live.
        val adminMissing = lockdownActive && !AdminManager.isActive(applicationContext)
        if (adminMissing) {
            GuardHealthNotifier.notifyAdminDown(applicationContext)
        } else {
            GuardHealthNotifier.clearAdmin(applicationContext)
        }

        // The DNS engine is only part of the protection when the user asked for it. In the
        // light mode it is simply not there — that is not a failure and nothing is
        // started, so the VPN permission is never demanded behind the user's back.
        val dnsExpected = settings.protectionSnapshot().vpnEnabled
        val filterShouldRun = needsScreening || container.blockEngine.isBlockingActiveNow()
        if (dnsExpected && filterShouldRun && !SawnVpnService.running &&
            VpnController.consentIntent(applicationContext) == null
        ) {
            VpnController.start(applicationContext)
        }

        val vpnMissing = dnsExpected && filterShouldRun && !SawnVpnService.running
        if (vpnMissing) {
            GuardHealthNotifier.notifyVpnDown(applicationContext)
        } else {
            GuardHealthNotifier.clearVpn(applicationContext)
        }

        // الودجت يعرض حالة الحماية والعداد — حدّثه بعد أن استقرّت الحالة.
        com.sawn.app.widget.SawnWidgetProvider.requestUpdate(applicationContext)

        return Result.success()
    }

    companion object {
        private const val UNIQUE = "guard_health"
        private const val UNIQUE_STRICT = "guard_health_strict"

        /** The fast cadence, used while a time lock or strict mode holds the user. */
        const val STRICT_INTERVAL_MINUTES = 3L

        /**
         * Schedules the periodic check, and the fast one when the user is in strict mode
         * or under a time lock.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<GuardHealthWorker>(15, TimeUnit.MINUTES)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    BACKOFF_SECONDS,
                    TimeUnit.SECONDS,
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.UPDATE, // adopt new constraints on upgrade
                request,
            )
            scheduleStrict(context)
        }

        /**
         * The strict-mode check: a self-rescheduling one-time request, because WorkManager
         * will not schedule a periodic task below 15 minutes.
         */
        fun scheduleStrict(context: Context) {
            val request = OneTimeWorkRequestBuilder<StrictGuardHealthWorker>()
                .setInitialDelay(STRICT_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    BACKOFF_SECONDS,
                    TimeUnit.SECONDS,
                )
                .build()
            runCatching {
                WorkManager.getInstance(context).enqueueUniqueWork(
                    UNIQUE_STRICT,
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
            }
        }

        /** Stops the fast check — the user is no longer in a strict state. */
        fun cancelStrict(context: Context) {
            runCatching {
                WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_STRICT)
            }
        }

        /** Exponential backoff for a retried check: a minute is the shortest WorkManager allows. */
        private const val BACKOFF_SECONDS = 60L
    }
}

/**
 * The strict-mode fast check.
 *
 * It runs the same health check, then schedules the next one — but only while the user
 * is still in a strict state. When they are not, the chain stops by itself.
 */
class StrictGuardHealthWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = applicationContext.sawnContainer.settings
        val strict = settings.strictMode.first() || settings.isLockdownActiveNow()
        if (strict) {
            GuardHealthWorker.scheduleStrict(applicationContext)
        } else {
            GuardHealthWorker.cancelStrict(applicationContext)
        }
        return Result.success()
    }
}
