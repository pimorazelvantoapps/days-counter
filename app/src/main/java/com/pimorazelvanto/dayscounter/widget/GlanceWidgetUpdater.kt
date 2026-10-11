package com.pimorazelvanto.dayscounter.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.pimorazelvanto.dayscounter.domain.WidgetUpdater

class GlanceWidgetUpdater(
    private val context: Context,
) : WidgetUpdater {
    override suspend fun updateAll() = DaysCounterWidget().updateAll(context)

    override suspend fun hasWidgets(): Boolean =
        GlanceAppWidgetManager(context).getGlanceIds(DaysCounterWidget::class.java).isNotEmpty()
}
