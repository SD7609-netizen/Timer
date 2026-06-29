package com.intervaltimer.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.intervaltimer.android.R
import com.intervaltimer.android.ui.MainActivity

class TimerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id, "Таймер", "--:--", 0)
        }
    }

    companion object {
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int,
            intervalName: String,
            timeStr: String,
            progress: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_timer)
            views.setTextViewText(R.id.tvWidgetInterval, intervalName)
            views.setTextViewText(R.id.tvWidgetTime, timeStr)
            views.setProgressBar(R.id.progressWidget, 100, progress, false)

            val openIntent = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, openIntent)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
