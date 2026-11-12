package com.sawn.app.system

import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sawn.app.accessibility.AppBlockAccessibilityService
import com.sawn.app.block.SawnEngines
import com.sawn.app.util.PermissionsHelper
import com.sawn.app.vpn.SawnVpnService
import com.sawn.app.vpn.VpnController
import com.sawn.app.sawnContainer
import com.sawn.domain.system.GuardHealthPolicy
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * The watchdog: it notices when a protection layer has stopped, and it tries to bring
 * the accessibility service back before asking the user to help.
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
 *
 * A **grace period** keeps the watchdog honest: for the first
 * [GuardHealthPolicy.PROCESS_GRACE_MS] of the process's life a missing service is
 * "connecting", not "down". Without it, every app launch would raise a false alarm.
 */
class GuardHealthWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val down = runHealthCheck(applicationContext, runAttemptCount)
        // Ask for another run while the service is still down and attempts remain: the
        // exponential backoff spaces the retries out so a stubborn service is not hammered.
        return if (down && GuardHealthPolicy.shouldRetry(runAttemptCount)) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        private const val TAG = "SawnGuard"
        private const val UNIQUE = "guard_health"
        private const val UNIQUE_STRICT = "guard_health_strict"

        /** The fast cadence, used while a time lock or strict mode holds the user. */
        const val STRICT_INTERVAL_MINUTES = 3L

        /** The ordinary cadence — the floor WorkManager allows for a periodic task. */
        const val PERIODIC_INTERVAL_MINUTES = 15L

        /**
         * The health check itself, shared by the periodic watchdog and the fast one.
         *
         * @param attempt how many times the accessibility service has already been asked
         *   to come back during this outage.
         * @return true when the accessibility service is confirmed down.
         */
        internal suspend fun runHealthCheck(context: Context, attempt: Int): Boolean {
            val container = context.sawnContainer
            val settings = container.settings
            // المستخدم أوقف الحماية من داخل التطبيق — "المُوقِف الوحيد": لا تنبيهات/إيقاظ.
            if (settings.userDisabledProtection.first()) return false
            val lockdownActive = settings.isLockdownActiveNow()
            val needsScreening = lockdownActive || (
                settings.masterEnabled.first() &&
                    (
                        settings.socialGuard.first() ||
                            settings.enabledFeatureKeys.first().isNotEmpty() ||
                            settings.pornGuard.first()
                        )
                )
            val accessibilityOn = PermissionsHelper.isAccessibilityEnabled(context)

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

            // The accessibility service: is it merely unbound, or is the permission gone?
            val state = GuardHealthPolicy.accessibilityState(
                enabled = accessibilityOn,
                connected = AppBlockAccessibilityService.connected,
                processAgeMs = processAgeMs(),
            )
            when (state) {
                GuardHealthPolicy.AccessibilityState.DOWN -> {
                    // The user must know: the notification is ongoing and its tap opens
                    // the accessibility settings, where the fix is one switch away.
                    GuardHealthNotifier.notifyAccessibilityDown(context)
                    // Try to heal it first. The attempt count is bounded, so a service
                    // the OEM insists on killing cannot drain the battery.
                    if (GuardHealthPolicy.shouldSelfHeal(state, attempt)) {
                        restartAccessibilityService(context)
                    }
                }
                GuardHealthPolicy.AccessibilityState.ALIVE ->
                    GuardHealthNotifier.clearAccessibility(context)
                // DISABLED is the user's own doing — nothing to say, and nothing to heal.
                // CONNECTING is too early to judge — saying "down" would be a false alarm.
                GuardHealthPolicy.AccessibilityState.DISABLED,
                GuardHealthPolicy.AccessibilityState.CONNECTING ->
                    GuardHealthNotifier.clearAccessibility(context)
            }

            // A lockdown is only meaningful if the uninstall guard and the filter are both live.
            val adminMissing = lockdownActive && !AdminManager.isActive(context)
            if (adminMissing) {
                GuardHealthNotifier.notifyAdminDown(context)
            } else {
                GuardHealthNotifier.clearAdmin(context)
            }

            // The DNS engine is only part of the protection when the user asked for it. In the
            // light mode it is simply not there — that is not a failure and nothing is
            // started, so the VPN permission is never demanded behind the user's back.
            val dnsExpected = settings.protectionSnapshot().vpnEnabled
            val filterShouldRun = needsScreening || container.blockEngine.isBlockingActiveNow()
            if (dnsExpected && filterShouldRun && !SawnVpnService.running &&
                VpnController.consentIntent(context) == null
            ) {
                VpnController.start(context)
            }

            val vpnMissing = dnsExpected && filterShouldRun && !SawnVpnService.running
            if (vpnMissing) {
                GuardHealthNotifier.notifyVpnDown(context)
            } else {
                GuardHealthNotifier.clearVpn(context)
            }

            // الودجت يعرض حالة الحماية والعداد — حدّثه بعد أن استقرّت الحالة.
            com.sawn.app.widget.SawnWidgetProvider.requestUpdate(context)

            return state == GuardHealthPolicy.AccessibilityState.DOWN
        }

        /**
         * How long this process has been alive.
         *
         * The system records the process start time, so this is exact — not a guess from a
         * stored timestamp, which a reboot or a clock change would throw off.
         */
        internal fun processAgeMs(): Long =
            (SystemClock.uptimeMillis() - Process.getStartUptimeMillis()).coerceAtLeast(0L)

        /**
         * Asks the system to bind the accessibility service again.
         *
         * A plain `startService` on an already-enabled accessibility service is the only
         * route available to an app that does not hold `WRITE_SECURE_SETTINGS`: the system
         * re-binds it, which is exactly what is needed after the OEM killed it. When the
         * service was killed by `am force-stop` this does *not* work — that is an Android
         * limitation, not an app bug, and it is documented in SECURITY.md.
         */
        internal fun restartAccessibilityService(context: Context) {
            val intent = Intent(context, AppBlockAccessibilityService::class.java)
            runCatching { context.startService(intent) }
                .onFailure { Log.w(TAG, "Accessibility restart failed: ${it.javaClass.simpleName}") }
        }

        /**
         * Schedules the periodic check, and the fast one when the user is in strict mode
         * or under a time lock.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<GuardHealthWorker>(
                PERIODIC_INTERVAL_MINUTES,
                TimeUnit.MINUTES,
            )
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
         *
         * The attempt count travels with the request, so the self-healing stays bounded
         * across the whole chain instead of restarting at every link.
         */
        fun scheduleStrict(context: Context, attempt: Int = 0) {
            val request = OneTimeWorkRequestBuilder<StrictGuardHealthWorker>()
                .setInitialDelay(STRICT_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setInputData(workDataOf(StrictGuardHealthWorker.ATTEMPT_KEY to attempt))
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
        if (!strict) {
            GuardHealthWorker.cancelStrict(applicationContext)
            return Result.success()
        }
        val attempt = inputData.getInt(ATTEMPT_KEY, 0)
        val down = GuardHealthWorker.runHealthCheck(applicationContext, attempt)
        // The chain continues while the user is in a strict state. The attempt counter
        // restarts once the service is healthy again, and stops growing at the bound —
        // so the healing can never become a loop.
        val next = if (down) {
            (attempt + 1).coerceAtMost(GuardHealthPolicy.MAX_SELF_HEAL_ATTEMPTS)
        } else {
            0
        }
        GuardHealthWorker.scheduleStrict(applicationContext, next)
        return Result.success()
    }

    companion object {
        /** The self-heal attempt count, carried from one link of the chain to the next. */
        const val ATTEMPT_KEY = "guard_health_attempt"
    }
}
