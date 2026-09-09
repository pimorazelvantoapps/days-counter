package com.pimorazelvanto.dayscounter

import android.app.AlarmManager
import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.pimorazelvanto.dayscounter.data.DataStoreWidgetConfigRepository
import com.pimorazelvanto.dayscounter.data.WidgetConfigRepository
import com.pimorazelvanto.dayscounter.domain.Clock
import com.pimorazelvanto.dayscounter.domain.MidnightUpdateScheduler
import com.pimorazelvanto.dayscounter.domain.SystemClock
import com.pimorazelvanto.dayscounter.domain.WidgetUpdater
import com.pimorazelvanto.dayscounter.widget.AlarmManagerMidnightUpdateScheduler
import com.pimorazelvanto.dayscounter.widget.GlanceWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

interface AppContainer {
    val clock: Clock
    val repository: WidgetConfigRepository
    val widgetUpdater: WidgetUpdater
    val midnightUpdateScheduler: MidnightUpdateScheduler
    val backgroundScope: CoroutineScope
}

class DefaultAppContainer(
    context: Context,
) : AppContainer {
    private val applicationContext = context.applicationContext

    override val backgroundScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val clock: Clock = SystemClock()

    /**
     * An unreadable store file is replaced by an empty one instead of making every read throw:
     * the app has no launcher icon, so a widget that can only crash would leave clearing the
     * app data in the system settings as the sole way back. Every widget falls back to its
     * placeholder and can be configured again.
     */
    override val repository: WidgetConfigRepository =
        DataStoreWidgetConfigRepository(
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            ) {
                applicationContext.preferencesDataStoreFile("widget_configs")
            },
        )

    override val widgetUpdater: WidgetUpdater = GlanceWidgetUpdater(applicationContext)

    override val midnightUpdateScheduler: MidnightUpdateScheduler =
        AlarmManagerMidnightUpdateScheduler(
            applicationContext,
            applicationContext.getSystemService(AlarmManager::class.java),
            clock,
        )
}

val Context.appContainer: AppContainer
    get() = (applicationContext as DaysCounterApplication).container
