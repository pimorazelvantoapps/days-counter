package com.pimorazelvanto.dayscounter.widget

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
    fun `handled actions update all widgets and reschedule`() {
        val actions =
            listOf(
                DateChangeReceiver.ACTION_MIDNIGHT,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
            )

        actions.forEach { receiver.onReceive(application, Intent(it)) }

        assertEquals(actions.size, container.widgetUpdater.updateCount)
        assertEquals(actions.size, container.midnightUpdateScheduler.scheduleCount)
    }

    @Test
    fun `unrelated action is ignored`() {
        receiver.onReceive(application, Intent(Intent.ACTION_BATTERY_LOW))

        assertEquals(0, container.widgetUpdater.updateCount)
        assertEquals(0, container.midnightUpdateScheduler.scheduleCount)
    }

    @Test
    fun `receiver is registered in manifest for all system actions`() {
        val packageManager = application.packageManager
        val systemActions =
            listOf(
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
            )

        systemActions.forEach { action ->
            // Contains rather than exact-match: Glance registers its own receivers for
            // some of these actions (e.g. MY_PACKAGE_REPLACED), so other entries are expected.
            val receivers = packageManager.queryBroadcastReceivers(Intent(action), 0)
            val names = receivers.map { it.activityInfo.name }
            assertTrue("receiver for $action", names.contains(DateChangeReceiver::class.java.name))
        }
    }
}
