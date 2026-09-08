package com.pimorazelvanto.dayscounter.widget

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

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        context.appContainer.midnightUpdateScheduler.cancel()
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
            } finally {
                pendingResult?.finish()
            }
        }
    }
}
