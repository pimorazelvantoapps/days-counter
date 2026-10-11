package com.pimorazelvanto.dayscounter

import android.app.AlarmManager
import android.content.Context
import android.util.Log
import androidx.datastore.preferences.preferencesDataStoreFile
import com.pimorazelvanto.dayscounter.data.DataStoreWidgetConfigRepository
import com.pimorazelvanto.dayscounter.data.WidgetConfigRepository
import com.pimorazelvanto.dayscounter.data.widgetConfigDataStore
import com.pimorazelvanto.dayscounter.domain.Clock
import com.pimorazelvanto.dayscounter.domain.MidnightUpdateScheduler
import com.pimorazelvanto.dayscounter.domain.SystemClock
import com.pimorazelvanto.dayscounter.domain.WidgetUpdater
import com.pimorazelvanto.dayscounter.widget.AlarmManagerMidnightUpdateScheduler
import com.pimorazelvanto.dayscounter.widget.GlanceWidgetUpdater
import kotlinx.coroutines.CoroutineExceptionHandler
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

    /**
     * Runs the date-change receiver's work after its broadcast returns. A failure there, such as a
     * storage error while the widgets update, is logged instead of reaching the thread's
     * uncaught-exception handler, which would kill the process and every widget update with it.
     */
    override val backgroundScope: CoroutineScope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.Default +
                CoroutineExceptionHandler { _, throwable -> Log.e(LOG_TAG, "Background work failed", throwable) },
        )

    override val clock: Clock = SystemClock()

    override val repository: WidgetConfigRepository =
        DataStoreWidgetConfigRepository(
            widgetConfigDataStore { applicationContext.preferencesDataStoreFile("widget_configs") },
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

private const val LOG_TAG = "DaysCounter"
