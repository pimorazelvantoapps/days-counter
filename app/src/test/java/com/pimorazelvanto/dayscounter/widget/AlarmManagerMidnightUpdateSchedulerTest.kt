package com.pimorazelvanto.dayscounter.widget

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pimorazelvanto.dayscounter.testsupport.ControlledClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlarmManagerMidnightUpdateSchedulerTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 9, 8)
    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private lateinit var scheduler: AlarmManagerMidnightUpdateScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        alarmManager = context.getSystemService(AlarmManager::class.java)
        scheduler = AlarmManagerMidnightUpdateScheduler(context, alarmManager, ControlledClock(today, zone))
    }

    @Test
    fun `schedules exact wakeup alarm at next local midnight`() {
        scheduler.schedule()

        val alarm = shadowOf(alarmManager).scheduledAlarms.single()
        val expectedMidnight =
            today
                .plusDays(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        assertEquals(expectedMidnight, alarm.triggerAtMs)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.getType())
        assertTrue(alarm.isAllowWhileIdle)
        assertEquals(0L, alarm.windowLengthMs)
    }

    @Test
    fun `rescheduling replaces instead of duplicating`() {
        scheduler.schedule()
        scheduler.schedule()

        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
    }

    @Test
    fun `cancel removes the alarm`() {
        scheduler.schedule()

        scheduler.cancel()

        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    @Suppress("DEPRECATION")
    @Test
    fun `alarm targets DateChangeReceiver with midnight action`() {
        scheduler.schedule()

        // ScheduledAlarm.operation has no non-deprecated accessor in this Robolectric version.
        val operation = shadowOf(alarmManager).scheduledAlarms.single().operation!!
        val intent = shadowOf(operation).savedIntent
        assertEquals(DateChangeReceiver.ACTION_MIDNIGHT, intent.action)
        assertEquals(DateChangeReceiver::class.java.name, intent.component?.className)
    }
}
