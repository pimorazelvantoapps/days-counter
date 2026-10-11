package com.pimorazelvanto.dayscounter.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.test.core.app.ApplicationProvider
import com.pimorazelvanto.dayscounter.DaysCounterApplication
import com.pimorazelvanto.dayscounter.domain.HeaderColor
import com.pimorazelvanto.dayscounter.domain.WidgetConfig
import com.pimorazelvanto.dayscounter.testsupport.FakeAppContainer
import com.pimorazelvanto.dayscounter.testsupport.FakeWidgetConfigRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DaysCounterWidgetTest {
    private lateinit var application: DaysCounterApplication
    private val repository = FakeWidgetConfigRepository()
    private val config = WidgetConfig("Urlaub", LocalDate.of(2027, 3, 15), HeaderColor.BLUE)

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        application.container = FakeAppContainer(repository = repository)
    }

    @Test
    fun `deleting a widget removes its configuration and keeps the others`() {
        repository.saved[1] = config
        repository.saved[2] = config
        val glanceId = GlanceAppWidgetManager(application).getGlanceIdBy(boundWidget(appWidgetId = 1))

        runBlocking { DaysCounterWidget().onDelete(application, glanceId) }

        assertEquals(setOf(2), repository.saved.keys)
    }

    /** Glance accepts only IDs the AppWidgetManager knows, so the test binds one first. */
    private fun boundWidget(appWidgetId: Int): Int {
        val info =
            AppWidgetProviderInfo().apply {
                provider =
                    ComponentName(application, DaysCounterWidgetReceiver::class.java)
            }
        shadowOf(AppWidgetManager.getInstance(application)).addBoundWidget(appWidgetId, info)
        return appWidgetId
    }
}
