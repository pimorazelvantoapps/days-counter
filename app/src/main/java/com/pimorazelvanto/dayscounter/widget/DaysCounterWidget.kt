package com.pimorazelvanto.dayscounter.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import com.pimorazelvanto.dayscounter.R
import com.pimorazelvanto.dayscounter.appContainer
import com.pimorazelvanto.dayscounter.config.ConfigActivity
import com.pimorazelvanto.dayscounter.domain.WidgetUiState

class DaysCounterWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val container = context.appContainer
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val config = container.repository.load(appWidgetId)
        val state = WidgetUiState.from(config, container.clock.today(), context.getString(R.string.default_title))
        val openConfig = actionStartActivity(ConfigActivity.createIntent(context, appWidgetId))

        provideContent {
            DaysCounterWidgetContent(state, onClick = openConfig)
        }
    }
}
