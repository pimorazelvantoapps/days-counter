package com.pimorazelvanto.dayscounter.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GlanceWidgetUpdaterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val updater = GlanceWidgetUpdater(context)

    @Test
    fun `no placed widget means no widgets`() {
        assertFalse(runBlocking { updater.hasWidgets() })
    }

    @Test
    fun `a bound widget counts although Glance still lists the provider under an old class name`() {
        // After an update the obfuscated name of the widget class can differ from the one Glance
        // recorded for the receiver.
        recordInGlanceRegistry(provider = "a.b")
        val info =
            AppWidgetProviderInfo().apply {
                provider =
                    ComponentName(context, DaysCounterWidgetReceiver::class.java)
            }
        shadowOf(AppWidgetManager.getInstance(context)).addBoundWidget(7, info)

        assertTrue(runBlocking { updater.hasWidgets() })
    }

    /** Writes Glance's receiver-to-provider registry the way Glance itself stores it. */
    private fun recordInGlanceRegistry(provider: String) {
        val receiver = DaysCounterWidgetReceiver::class.java.canonicalName!!
        val scope = CoroutineScope(Dispatchers.IO + Job())
        val registry =
            PreferenceDataStoreFactory.create(scope = scope) {
                context.preferencesDataStoreFile("GlanceAppWidgetManager-${Application.getProcessName()}")
            }
        runBlocking {
            registry.edit {
                it[stringSetPreferencesKey("list::Providers")] = setOf(receiver)
                it[stringPreferencesKey("provider:$receiver")] = provider
            }
            scope.coroutineContext[Job]!!.cancelAndJoin()
        }
    }

    @Test
    fun `a widget the system has bound counts before Glance has recorded it`() {
        val info =
            AppWidgetProviderInfo().apply {
                provider =
                    ComponentName(context, DaysCounterWidgetReceiver::class.java)
            }
        shadowOf(AppWidgetManager.getInstance(context)).addBoundWidget(7, info)

        assertTrue(runBlocking { updater.hasWidgets() })
    }
}
