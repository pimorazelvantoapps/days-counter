package com.pimorazelvanto.dayscounter.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.updateAll
import com.pimorazelvanto.dayscounter.domain.WidgetUpdater

class GlanceWidgetUpdater(
    private val context: Context,
) : WidgetUpdater {
    override suspend fun updateAll() = DaysCounterWidget().updateAll(context)

    /**
     * Asks the system rather than Glance: Glance looks widgets up by the class name it recorded
     * for the receiver, and after an app update R8 may have given the widget class another name,
     * so Glance would report none until it records the receiver again.
     */
    override suspend fun hasWidgets(): Boolean =
        AppWidgetManager
            .getInstance(context)
            .getAppWidgetIds(ComponentName(context, DaysCounterWidgetReceiver::class.java))
            .isNotEmpty()
}
