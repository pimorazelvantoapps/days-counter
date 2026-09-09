package com.pimorazelvanto.dayscounter.widget

import androidx.test.core.app.ApplicationProvider
import com.pimorazelvanto.dayscounter.DaysCounterApplication
import com.pimorazelvanto.dayscounter.domain.HeaderColor
import com.pimorazelvanto.dayscounter.domain.WidgetConfig
import com.pimorazelvanto.dayscounter.testsupport.FakeAppContainer
import com.pimorazelvanto.dayscounter.testsupport.FakeWidgetConfigRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DaysCounterWidgetReceiverTest {
    private lateinit var application: DaysCounterApplication
    private lateinit var container: FakeAppContainer
    private val repository = FakeWidgetConfigRepository()
    private val receiver = DaysCounterWidgetReceiver()
    private val config = WidgetConfig("Urlaub", LocalDate.of(2027, 3, 15), HeaderColor.BLUE)

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        container = FakeAppContainer(repository = repository)
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

    @Test
    fun `removed widgets lose their configuration while the remaining one keeps it`() {
        repository.saved[1] = config
        repository.saved[2] = config
        repository.saved[3] = config

        // Invoking the callback directly leaves Glance's own goAsync without a pending result,
        // so its cleanup coroutine prints a NullPointerException. The build stays green; the
        // assertion below is about this receiver's repository call.
        receiver.onDeleted(application, intArrayOf(1, 3))

        assertEquals(setOf(2), repository.saved.keys)
    }
}
