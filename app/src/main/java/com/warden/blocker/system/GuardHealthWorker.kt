package com.warden.blocker.system

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.warden.blocker.util.PermissionsHelper
import com.warden.blocker.wardenContainer
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * NetGuard-style watchdog, adapted for accessibility: every 15 minutes, if the user's
 * configuration says screening should be active (master on, Social Guard or in-app
 * features selected) and the accessibility service is NOT running, post the
 * "protection stopped" notification. When accessibility is back, clear the notification.
 *
 * The real-time signal is the service's own onUnbind/onDestroy → [GuardHealthNotifier];
 * this worker is the backstop for crashes that skip those callbacks and for the case
 * where the user manually re-enables but a later event silently kills the service again.
 */
class GuardHealthWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.wardenContainer
        val needsScreening = container.settings.masterEnabled.first() &&
            (
                container.settings.socialGuard.first() ||
                    container.settings.enabledFeatureKeys.first().isNotEmpty() ||
                    container.settings.pornGuard.first()
                )
        val accessibilityOn = PermissionsHelper.isAccessibilityEnabled(applicationContext)
        when {
            needsScreening && !accessibilityOn -> GuardHealthNotifier.notifyAccessibilityDown(applicationContext)
            accessibilityOn -> GuardHealthNotifier.clear(applicationContext)
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE = "guard_health"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<GuardHealthWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.KEEP, // don't reset the cadence on app relaunch
                request,
            )
        }
    }
}
