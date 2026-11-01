package com.warden.blocker.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.warden.blocker.MainActivity
import com.warden.blocker.R

/**
 * Posts the "protection down" health notification the moment Accessibility is lost —
 * the pattern RethinkDNS uses for tunnel loss: a real-time signal, not a poll result.
 * One standing notification id; each event replaces it so we never stack duplicates.
 *
 * The channel is HIGH importance so it presents even on the lock screen.
 * Clearing happens when the user re-enables the service (the boot worker clears it
 * after confirming Accessibility is back).
 */
object GuardHealthNotifier {

    private const val CHANNEL_ID = "warden_guard_health"
    private const val NOTIF_ID = 1002

    fun notifyAccessibilityDown(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)

        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val settings = PendingIntent.getActivity(
            context, 1, PermissionsHelperAccessibility.openIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.guard_health_title))
            .setContentText(context.getString(R.string.guard_health_text))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(context.getString(R.string.guard_health_text)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(openApp)
            .addAction(0, context.getString(R.string.guard_health_fix), settings)
            .setOngoing(true) // cannot be swiped away — only re-enabling clears it
            .setAutoCancel(false)
            .build()
        runCatching { nm.notify(NOTIF_ID, notification) }
    }

    /** Called when the boot/re-arm worker confirms Accessibility is back. */
    fun clear(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.cancel(NOTIF_ID) }
    }

    private fun ensureChannel(nm: NotificationManager) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            contextString(R.string.guard_health_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = contextString(R.string.guard_health_channel_desc)
            setShowBadge(true)
        }
        runCatching { nm.createNotificationChannel(channel) }
    }

    private fun contextString(resId: Int) =
        com.warden.blocker.WardenApp.instance.getString(resId)

    /** Indirection so the notifier doesn't import PermissionsHelper directly (test seam). */
    object PermissionsHelperAccessibility {
        fun openIntent(context: Context): Intent = Intent(
            android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS,
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
