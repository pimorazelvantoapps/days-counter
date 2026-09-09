package com.pimorazelvanto.dayscounter.widget

import android.content.ComponentName
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.pimorazelvanto.dayscounter.DaysCounterApplication
import com.pimorazelvanto.dayscounter.testsupport.FakeAppContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DateChangeReceiverTest {
    private lateinit var application: DaysCounterApplication
    private lateinit var container: FakeAppContainer
    private val receiver = DateChangeReceiver()

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        container = FakeAppContainer()
        application.container = container
    }

    @Test
    fun `handled actions announce the date change, update all widgets and reschedule`() {
        val actions =
            listOf(
                DateChangeReceiver.ACTION_MIDNIGHT,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
            )

        actions.forEach { receiver.onReceive(application, Intent(it)) }

        assertEquals(actions.size, container.clock.dateChangedCount)
        assertEquals(actions.size, container.widgetUpdater.updateCount)
        assertEquals(actions.size, container.midnightUpdateScheduler.scheduleCount)
    }

    @Test
    fun `unrelated action is ignored`() {
        receiver.onReceive(application, Intent(Intent.ACTION_BATTERY_LOW))

        assertEquals(0, container.clock.dateChangedCount)
        assertEquals(0, container.widgetUpdater.updateCount)
        assertEquals(0, container.midnightUpdateScheduler.scheduleCount)
    }

    @Test
    fun `manifest declares a filter for every handled action but the explicit alarm`() {
        val component = ComponentName(application, DateChangeReceiver::class.java)
        val declared =
            shadowOf(application.packageManager)
                .getIntentFiltersForReceiver(component)
                .flatMap { filter -> (0 until filter.countActions()).map(filter::getAction) }
                .toSet()

        assertEquals(
            DateChangeReceiver.HANDLED_ACTIONS - DateChangeReceiver.ACTION_MIDNIGHT,
            declared,
        )
    }

    @Test
    fun `each declared system broadcast is delivered to this receiver`() {
        val receiverName = DateChangeReceiver::class.java.name
        val exclusiveActions =
            listOf(
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_BOOT_COMPLETED,
            )

        exclusiveActions.forEach { action ->
            assertEquals(action, listOf(receiverName), resolvedReceiverNames(action))
        }
        // Glance registers a receiver of its own for this one, so further entries are expected.
        assertTrue(resolvedReceiverNames(Intent.ACTION_MY_PACKAGE_REPLACED).contains(receiverName))
    }

    private fun resolvedReceiverNames(action: String): List<String> =
        application.packageManager
            .queryBroadcastReceivers(Intent(action), 0)
            .map { it.activityInfo.name }
}
