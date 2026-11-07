package com.sawn.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.sawn.app.MainActivity
import com.sawn.app.R
import com.sawn.app.sawnContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ويدجت 2×2 (RemoteViews كلاسيكي — لا حاجة لـ Glance/Space جديد):
 * عدّاد أيام محمي + حالة (محمي/متوقف) + زر يفتح التطبيق للتشغيل.
 * يُحدَّث عند التبديل (BootReceiver/GuardHealthWorker).
 */
class SawnWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            updateOne(context, manager, id)
        }
    }

    private fun updateOne(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.sawn_widget)
        CoroutineScope(Dispatchers.Main).launch {
            val container = context.sawnContainer
            val active = runCatching { container.blockEngine.isBlockingActiveNow() }.getOrDefault(false)
            views.setTextViewText(R.id.widget_streak, "صَوْن")
            views.setTextViewText(
                R.id.widget_status,
                if (active) context.getString(R.string.you_are_protected)
                else context.getString(R.string.protection_off),
            )
            val open = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val pi = android.app.PendingIntent.getActivity(
                context, widgetId, open,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)
            manager.updateAppWidget(widgetId, views)
        }
    }
}
