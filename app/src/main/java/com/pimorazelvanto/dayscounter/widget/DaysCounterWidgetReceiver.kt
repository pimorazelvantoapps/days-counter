package com.pimorazelvanto.dayscounter.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.pimorazelvanto.dayscounter.appContainer
import kotlinx.coroutines.launch

class DaysCounterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DaysCounterWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        context.appContainer.midnightUpdateScheduler.schedule()
    }

    override fun onDeleted(
        context: Context,
        appWidgetIds: IntArray,
    ) {
        super.onDeleted(context, appWidgetIds)
        val container = context.appContainer
        val pendingResult: PendingResult? = goAsync()
        container.backgroundScope.launch {
            try {
                appWidgetIds.forEach { container.repository.delete(it) }
                if (remainingWidgetIds(context).isEmpty()) container.midnightUpdateScheduler.cancel()
            } finally {
                pendingResult?.finish()
            }
        }
    }

    private fun remainingWidgetIds(context: Context): IntArray =
        AppWidgetManager
            .getInstance(context)
            .getAppWidgetIds(ComponentName(context, DaysCounterWidgetReceiver::class.java))
}
