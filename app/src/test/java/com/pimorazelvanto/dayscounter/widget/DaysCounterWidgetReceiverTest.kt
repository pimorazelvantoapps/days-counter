package com.pimorazelvanto.dayscounter.widget

import androidx.test.core.app.ApplicationProvider
import com.pimorazelvanto.dayscounter.DaysCounterApplication
import com.pimorazelvanto.dayscounter.testsupport.FakeAppContainer
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DaysCounterWidgetReceiverTest {
    private lateinit var application: DaysCounterApplication
    private lateinit var container: FakeAppContainer
    private val receiver = DaysCounterWidgetReceiver()

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        container = FakeAppContainer()
        application.container = container
    }

    @Test
    fun `the first placed widget schedules the midnight alarm`() {
        receiver.onEnabled(application)

        assertEquals(1, container.midnightUpdateScheduler.scheduleCount)
        assertEquals(0, container.midnightUpdateScheduler.cancelCount)
    }

    @Test
    fun `the last removed widget cancels the midnight alarm`() {
        receiver.onDisabled(application)

        assertEquals(1, container.midnightUpdateScheduler.cancelCount)
        assertEquals(0, container.midnightUpdateScheduler.scheduleCount)
    }
}
