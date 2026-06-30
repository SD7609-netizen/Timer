package com.intervaltimer.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.intervaltimer.android.R
import com.intervaltimer.android.service.TimerService
import com.intervaltimer.android.ui.MainActivity

class TimerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            val layoutId = resolveLayout(context)
            val views = RemoteViews(context.packageName, layoutId)
            views.setTextViewText(R.id.tvWidgetInterval, "Таймер")
            views.setTextViewText(R.id.tvWidgetLabel, "")
            views.setTextViewText(R.id.tvWidgetTime, "--:--")
            views.setProgressBar(R.id.progressWidget, 100, 0, false)
            val openIntent = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, openIntent)
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    companion object {
        fun resolveLayout(context: Context): Int {
            val prefs = context.getSharedPreferences(TimerService.PREFS_NAME, Context.MODE_PRIVATE)
            return when (prefs.getString(TimerService.PREF_WIDGET_STYLE, "2")) {
                "1" -> R.layout.widget_1_minimal
                "3" -> R.layout.widget_3_full
                "4" -> R.layout.widget_4_wide
                "5" -> R.layout.widget_5_accent
                "6" -> R.layout.widget_6_compact
                else -> R.layout.widget_timer
            }
        }
    }
}
