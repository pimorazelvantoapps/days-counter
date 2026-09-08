package com.pimorazelvanto.dayscounter.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

class GlanceWidgetUpdater(
    private val context: Context,
) : WidgetUpdater {
    override suspend fun updateAll() = DaysCounterWidget().updateAll(context)
}
