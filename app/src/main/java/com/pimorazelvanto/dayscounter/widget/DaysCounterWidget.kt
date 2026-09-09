package com.pimorazelvanto.dayscounter.widget

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class DaysCounterWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    /**
     * The state is observed inside the composition rather than read once around it: Glance keeps a
     * composition running for about a minute after an update and does not re-run `provideGlance`
     * for further updates that arrive meanwhile, so content captured before [provideContent] can
     * never change. A saved configuration and a passing day boundary would then not reach the
     * widget at all.
     */
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val container = context.appContainer
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val defaultTitle = context.getString(R.string.default_title)
        val states =
            combine(container.repository.observe(appWidgetId), container.clock.days()) { config, today ->
                WidgetUiState.from(config, today, defaultTitle)
            }
        val initialState = states.first()
        val openConfig = actionStartActivity(ConfigActivity.createIntent(context, appWidgetId))

        provideContent {
            val state by states.collectAsState(initialState)
            DaysCounterWidgetContent(state, onClick = openConfig)
        }
    }
}
