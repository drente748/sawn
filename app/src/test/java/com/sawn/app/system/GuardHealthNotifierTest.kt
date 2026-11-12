package com.sawn.app.system

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
import androidx.core.app.NotificationCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The notification the user sees when the protection has stopped.
 *
 * It has to be *ongoing* — a notice that can be swiped away is a notice that will be
 * swiped away — and it has to take the user where the fix is: the accessibility
 * settings, not the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [26])
class GuardHealthNotifierTest {

    private val context: Application = RuntimeEnvironment.getApplication()

    private fun postedNotifications(): List<Notification> =
        shadowOf(
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager,
        ).allNotifications

    @Test
    fun theAccessibilityNoticeIsOngoingAndHighPriority() {
        GuardHealthNotifier.notifyAccessibilityDown(context)

        val notification = postedNotifications().single()
        assertTrue(
            "the notice must not be swipeable away",
            notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
        )
        assertEquals(NotificationCompat.PRIORITY_HIGH, notification.priority)
    }

    @Test
    fun tappingTheAccessibilityNoticeOpensTheAccessibilitySettings() {
        GuardHealthNotifier.notifyAccessibilityDown(context)

        val notification = postedNotifications().single()
        val contentIntent = notification.contentIntent
        assertNotNull("the notice must carry a tap target", contentIntent)
        val saved = shadowOf(contentIntent).savedIntent
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, saved.action)
    }

    @Test
    fun theNoticeIsClearedOnceTheServiceIsBack() {
        GuardHealthNotifier.notifyAccessibilityDown(context)
        GuardHealthNotifier.clearAccessibility(context)

        assertTrue(
            "a healthy service must leave no standing notice",
            postedNotifications().none { it.flags and Notification.FLAG_ONGOING_EVENT != 0 },
        )
    }
}
