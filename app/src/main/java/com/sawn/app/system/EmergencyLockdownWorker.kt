package com.sawn.app.system

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sawn.app.sawnContainer
import com.sawn.core.data.SettingsStore
import java.util.concurrent.TimeUnit

/**
 * The end of a panic lock.
 *
 * WorkManager keeps the request across a reboot, so the lock ends on time even if the
 * device was off — a lock that could be ended by unplugging the phone would not be a
 * lock.
 *
 * The worker does not stop the protection: it clears the *session*, and the engines
 * go back to whatever the user had before the panic.
 */
class EmergencyLockdownWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        endLockdown(applicationContext.sawnContainer.settings)
        return Result.success()
    }

    companion object {
        private const val UNIQUE = "emergency_lockdown"

        /**
         * The worker's whole effect: the session is over, so the protection goes back to
         * whatever the user had before the panic.
         */
        suspend fun endLockdown(settings: SettingsStore) {
            settings.clearLockdownSession()
        }

        /**
         * Arms the timer that ends the lock.
         *
         * Replacing the request (rather than adding one) means an extension replaces
         * the old deadline instead of leaving the earlier one to fire early.
         */
        fun schedule(context: Context, durationMinutes: Int) {
            val request = OneTimeWorkRequestBuilder<EmergencyLockdownWorker>()
                .setInitialDelay(durationMinutes.coerceAtLeast(1).toLong(), TimeUnit.MINUTES)
                .build()
            runCatching {
                WorkManager.getInstance(context).enqueueUniqueWork(
                    UNIQUE,
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
            }
        }

        /** Drops the timer — used when the session is cleared by other means. */
        fun cancel(context: Context) {
            runCatching { WorkManager.getInstance(context).cancelUniqueWork(UNIQUE) }
        }
    }
}
